package com.hfstudio.flamechunk.server.guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.ChunkEntry;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;
import com.hfstudio.flamechunk.common.network.PeerChannels;
import com.hfstudio.flamechunk.common.network.packet.WeakChunkSnapshotPacket;
import com.hfstudio.flamechunk.server.integration.ServerUtilitiesBridge;

import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class WeakChunkInspector {

    public static final int MAX_TRACKED_CHUNKS = 8192;
    public static final int MAX_TRACKED_TYPES_PER_CHUNK = 128;
    public static final int MAX_REPORTED_CHUNKS = WeakChunkSnapshot.MAX_CHUNKS;
    public static final int MAX_REPORTED_TYPES = WeakChunkSnapshot.MAX_ENTITY_TYPES;
    public static final int MAX_PENDING_SNAPSHOT_REQUESTS = 64;
    public static WeakChunkInspector activeInspector;

    public final ServerUtilitiesBridge serverUtilities;
    public final Map<World, ScanState> states = new WeakHashMap<>();
    public final ConcurrentLinkedQueue<SnapshotRequest> snapshotRequests = new ConcurrentLinkedQueue<>();
    public final AtomicInteger snapshotRequestCount = new AtomicInteger();

    public WeakChunkInspector(ServerUtilitiesBridge serverUtilities) {
        this.serverUtilities = serverUtilities;
        activeInspector = this;
    }

    public static boolean enqueueSnapshotRequest(EntityPlayerMP player) {
        return ServerConfig.weakChunkDiagnostics && activeInspector != null && activeInspector.enqueueRequest(player);
    }

    @SubscribeEvent
    public void onWorldTick(WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!ServerConfig.weakChunkDiagnostics) {
            clearStates();
            clearPendingSnapshotRequests();
            return;
        }
        World world = event.world;
        if (world == null || world.isRemote || world.getChunkProvider() == null) {
            return;
        }
        ScanState state = states.get(world);
        if (state == null) {
            state = new ScanState();
            states.put(world, state);
        }
        if (!state.scanning) {
            if (++state.ticksSinceScan < ServerConfig.weakChunkCheckIntervalTicks) {
                return;
            }
            state.begin(world, ServerConfig.weakChunkMaximumScannedEntities);
        }
        if (state.scan(world, ServerConfig.weakChunkEntitiesPerTick, ServerConfig.weakChunkMaximumScannedEntities)) {
            WeakChunkSnapshot snapshot = state.finish(world.provider.dimensionId, world.getTotalWorldTime());
            publishDiagnostics(world, snapshot);
        }
        serveSnapshotRequests(world, state.latestSnapshot);
    }

    public WeakChunkSnapshot getLatestSnapshot(World world) {
        ScanState state = states.get(world);
        return state == null ? null : state.latestSnapshot;
    }

    public void onServerStopping(FMLServerStoppingEvent event) {
        clearPendingSnapshotRequests();
        clearStates();
    }

    private void clearPendingSnapshotRequests() {
        while (snapshotRequests.poll() != null) {
            snapshotRequestCount.decrementAndGet();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        ScanState state = states.remove(event.world);
        if (state != null) {
            state.releaseEntitySnapshot();
        }
    }

    public void clearStates() {
        for (ScanState state : states.values()) {
            state.releaseEntitySnapshot();
        }
        states.clear();
    }

    public void publishDiagnostics(World world, WeakChunkSnapshot snapshot) {
        int loadedChunks = world.getChunkProvider()
            .getLoadedChunkCount();
        if (loadedChunks > ServerConfig.weakChunkMinimum && snapshot.getChunks()
            .isEmpty()) {
            return;
        }
        FlameChunk.LOG.debug(
            "Weak chunk scan completed for dimension {}: {} loaded chunks, {} high-entity weak chunks{}",
            world.provider.dimensionId,
            loadedChunks,
            snapshot.getChunks()
                .size(),
            snapshot.isTruncated() ? " (entity or chunk inspection cap reached)" : "");
        for (ChunkEntry entry : snapshot.getChunks()) {
            FlameChunk.LOG.debug(
                "Weak chunk in dimension {} at ({}, {}): {} entities, top types {}, claim {}",
                world.provider.dimensionId,
                entry.getChunkX(),
                entry.getChunkZ(),
                entry.getEntityCount(),
                formatTypes(entry.getEntityTypes()),
                serverUtilities.describeClaim(world, entry.getChunkX(), entry.getChunkZ()));
        }
    }

    public boolean enqueueRequest(EntityPlayerMP player) {
        if (player == null) {
            return false;
        }
        while (true) {
            int current = snapshotRequestCount.get();
            if (current >= MAX_PENDING_SNAPSHOT_REQUESTS) {
                return false;
            }
            if (!snapshotRequestCount.compareAndSet(current, current + 1)) {
                continue;
            }
            snapshotRequests.offer(new SnapshotRequest(player, player.dimension));
            return true;
        }
    }

    public void serveSnapshotRequests(World world, WeakChunkSnapshot snapshot) {
        int count = snapshotRequestCount.get();
        for (int index = 0; index < count; index++) {
            SnapshotRequest request = snapshotRequests.poll();
            if (request == null) {
                return;
            }
            if (request.player.playerNetServerHandler == null) {
                snapshotRequestCount.decrementAndGet();
                continue;
            }
            if (request.player.dimension != request.dimensionId) {
                snapshotRequestCount.decrementAndGet();
                continue;
            }
            if (request.dimensionId != world.provider.dimensionId) {
                requeue(request);
                continue;
            }
            if (snapshot == null) {
                requeue(request);
                continue;
            }
            if ((ServerConfig.requireOperator && !request.player.canCommandSenderUseCommand(2, "flamechunk"))
                || !serverUtilities.hasPermission(request.player, "flamechunk.scan")) {
                snapshotRequestCount.decrementAndGet();
                continue;
            }
            if (FlameChunk.network != null && PeerChannels.canSend(request.player)) {
                try {
                    FlameChunk.network.sendTo(new WeakChunkSnapshotPacket(snapshot), request.player);
                } catch (RuntimeException exception) {
                    FlameChunk.LOG.debug("Unable to send a FlameChunk weak chunk snapshot", exception);
                }
            }
            snapshotRequestCount.decrementAndGet();
        }
    }

    public void requeue(SnapshotRequest request) {
        snapshotRequests.offer(request);
    }

    public static class SnapshotRequest {

        public final EntityPlayerMP player;
        public final int dimensionId;

        public SnapshotRequest(EntityPlayerMP player, int dimensionId) {
            this.player = player;
            this.dimensionId = dimensionId;
        }
    }

    public String formatTypes(List<EntityTypeCount> entityTypes) {
        StringBuilder value = new StringBuilder();
        for (EntityTypeCount entityType : entityTypes) {
            if (value.length() > 0) {
                value.append(", ");
            }
            value.append(entityType.getTypeId())
                .append('=')
                .append(entityType.getCount());
        }
        return value.toString();
    }

    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    public static int unpackX(long key) {
        return (int) (key >> 32);
    }

    public static int unpackZ(long key) {
        return (int) key;
    }

    public static List<Entity> findWeakEntities(World world, int chunkX, int chunkZ, String typeId,
        int inspectionLimit) {
        if (world == null || world.isRemote
            || typeId == null
            || inspectionLimit < 1
            || world.getChunkProvider() == null
            || world.getChunkProvider()
                .chunkExists(chunkX, chunkZ)) {
            return null;
        }
        List<Entity> matches = new ArrayList<>();
        int inspected = 0;
        for (Entity entity : world.loadedEntityList) {
            if (++inspected > inspectionLimit) {
                return null;
            }
            if (entity != null && !entity.isDead
                && !(entity instanceof EntityPlayer)
                && entity.chunkCoordX == chunkX
                && entity.chunkCoordZ == chunkZ
                && typeId.equals(entityType(entity))) {
                matches.add(entity);
            }
        }
        return matches;
    }

    public static String entityType(Entity entity) {
        if (entity instanceof EntityItem item) {
            ItemStack stack = item.getEntityItem();
            if (stack == null || stack.getItem() == null) {
                return "item:unknown";
            }
            return "item:" + Item.getIdFromItem(stack.getItem());
        }
        String id = EntityList.getEntityString(entity);
        return id == null || id.length() == 0 ? "entity:unknown" : "entity:" + id;
    }

    public static class ScanState {

        public final Long2IntOpenHashMap entityCounts = new Long2IntOpenHashMap();
        public final Long2ObjectOpenHashMap<Object2IntOpenHashMap<String>> typeCounts = new Long2ObjectOpenHashMap<>();
        private List<Entity> entitySnapshot = Collections.emptyList();
        private boolean snapshotCaptured;
        public boolean scanning;
        public int cursor;
        public int inspected;
        public int retainedEntityCount;
        public int ticksSinceScan;
        public boolean truncated;
        public WeakChunkSnapshot latestSnapshot;

        public ScanState() {
            entityCounts.defaultReturnValue(0);
        }

        public void begin() {
            scanning = true;
            cursor = 0;
            inspected = 0;
            truncated = false;
            ticksSinceScan = 0;
            releaseEntitySnapshot();
            entitySnapshot = Collections.emptyList();
            snapshotCaptured = false;
            entityCounts.clear();
            typeCounts.clear();
        }

        private void begin(World world, int maximumEntities) {
            begin();
            List<Entity> entities = world.loadedEntityList;
            entitySnapshot = WeakEntitySnapshotBudget.capture(entities, maximumEntities);
            retainedEntityCount = entitySnapshot.size();
            snapshotCaptured = true;
            truncated = entities.size() > entitySnapshot.size();
        }

        public void releaseEntitySnapshot() {
            WeakEntitySnapshotBudget.release(retainedEntityCount);
            retainedEntityCount = 0;
            entitySnapshot = Collections.emptyList();
            snapshotCaptured = false;
        }

        public boolean scan(World world, int entitiesPerTick, int maximumEntities) {
            List<Entity> entities = snapshotCaptured ? entitySnapshot : world.loadedEntityList;
            int limit = Math.min(entities.size(), Math.min(maximumEntities, cursor + entitiesPerTick));
            while (cursor < limit) {
                Entity entity = entities.get(cursor++);
                inspected++;
                if (entity == null || entity.isDead
                    || entity instanceof EntityPlayer
                    || world.getChunkProvider()
                        .chunkExists(entity.chunkCoordX, entity.chunkCoordZ)) {
                    continue;
                }
                long key = pack(entity.chunkCoordX, entity.chunkCoordZ);
                if (!entityCounts.containsKey(key) && entityCounts.size() >= MAX_TRACKED_CHUNKS) {
                    truncated = true;
                    continue;
                }
                entityCounts.addTo(key, 1);
                Object2IntOpenHashMap<String> counts = typeCounts.get(key);
                if (counts == null) {
                    counts = new Object2IntOpenHashMap<>();
                    counts.defaultReturnValue(0);
                    typeCounts.put(key, counts);
                }
                String type = entityType(entity);
                if (!counts.containsKey(type) && counts.size() >= MAX_TRACKED_TYPES_PER_CHUNK) {
                    truncated = true;
                    continue;
                }
                counts.addTo(type, 1);
            }
            if (inspected >= maximumEntities && cursor < entities.size()) {
                truncated = true;
                return true;
            }
            return cursor >= entities.size();
        }

        public WeakChunkSnapshot finish(int dimensionId, long generatedAtTick) {
            List<Long2IntMap.Entry> candidates = new ArrayList<>();
            for (Long2IntMap.Entry entry : entityCounts.long2IntEntrySet()) {
                if (entry.getIntValue()
                    >= Math.max(ServerConfig.weakChunkEntityThreshold, WeakChunkSnapshot.ENTITY_WARNING_THRESHOLD)) {
                    candidates.add(entry);
                }
            }
            candidates.sort((left, right) -> {
                int countOrder = Integer.compare(right.getIntValue(), left.getIntValue());
                if (countOrder != 0) {
                    return countOrder;
                }
                int xOrder = Integer.compare(unpackX(left.getLongKey()), unpackX(right.getLongKey()));
                return xOrder != 0 ? xOrder : Integer.compare(unpackZ(left.getLongKey()), unpackZ(right.getLongKey()));
            });
            List<ChunkEntry> chunks = new ArrayList<>(Math.min(MAX_REPORTED_CHUNKS, candidates.size()));
            for (int index = 0; index < candidates.size() && index < MAX_REPORTED_CHUNKS; index++) {
                Long2IntMap.Entry candidate = candidates.get(index);
                long key = candidate.getLongKey();
                chunks.add(
                    new ChunkEntry(unpackX(key), unpackZ(key), candidate.getIntValue(), topTypes(typeCounts.get(key))));
            }
            latestSnapshot = new WeakChunkSnapshot(dimensionId, generatedAtTick, truncated, chunks);
            scanning = false;
            releaseEntitySnapshot();
            return latestSnapshot;
        }

        public List<EntityTypeCount> topTypes(Object2IntOpenHashMap<String> counts) {
            if (counts == null || counts.isEmpty()) {
                return Collections.emptyList();
            }
            List<Object2IntMap.Entry<String>> entries = new ArrayList<>(counts.object2IntEntrySet());
            entries.sort((left, right) -> {
                int countOrder = Integer.compare(right.getIntValue(), left.getIntValue());
                return countOrder != 0 ? countOrder
                    : left.getKey()
                        .compareTo(right.getKey());
            });
            List<EntityTypeCount> result = new ArrayList<>(Math.min(MAX_REPORTED_TYPES, entries.size()));
            for (int index = 0; index < entries.size() && index < MAX_REPORTED_TYPES; index++) {
                Object2IntMap.Entry<String> entry = entries.get(index);
                String typeId = entry.getKey();
                if (typeId.length() > WeakChunkSnapshot.MAX_TYPE_ID_LENGTH) {
                    typeId = typeId.substring(0, WeakChunkSnapshot.MAX_TYPE_ID_LENGTH);
                }
                result.add(new EntityTypeCount(typeId, entry.getIntValue()));
            }
            return result;
        }
    }
}
