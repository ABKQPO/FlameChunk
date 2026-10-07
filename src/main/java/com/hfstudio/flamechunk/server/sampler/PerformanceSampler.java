package com.hfstudio.flamechunk.server.sampler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.ChunkTiming;
import com.hfstudio.flamechunk.common.data.ChunkTypeTiming;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ObjectHotspot;
import com.hfstudio.flamechunk.common.data.ScanLimits;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.network.SnapshotCodec;
import com.hfstudio.flamechunk.common.network.ZstdCompressionCodec;
import com.hfstudio.flamechunk.common.network.packet.ClearSnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanProgressPacket;
import com.hfstudio.flamechunk.common.network.packet.SnapshotPacket;
import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.integration.ServerUtilitiesBridge;

import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class PerformanceSampler {

    private static final int MAX_PENDING_REQUESTS = 64;
    private static final int MAX_TYPE_AGGREGATES_PER_SCAN = 8192;
    private static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();
    private static PerformanceSampler activeSampler;

    private final Int2ObjectOpenHashMap<DimensionTimings> dimensions = new Int2ObjectOpenHashMap<>();
    private final SnapshotBuilder snapshotBuilder = new SnapshotBuilder();
    public final ObjectHotspotStore objectHotspots = new ObjectHotspotStore();
    private final SnapshotCodec snapshotCodec = new SnapshotCodec();
    private final ZstdCompressionCodec compressionCodec = new ZstdCompressionCodec();
    private final ServerUtilitiesBridge serverUtilities;
    private final ConcurrentLinkedQueue<PendingRequest> pendingRequests = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingRequestCount = new AtomicInteger();
    private EntityPlayerMP target;
    private ICommandSender feedback;
    private int durationSeconds;
    private long totalTicks;
    private long sampledTicks;
    private long lastGcCollectionMillis;
    private boolean active;
    private int droppedDimensions;
    private int droppedChunks;
    private int typeAggregateCount;
    private ScanSnapshot lastSnapshot;

    public PerformanceSampler() {
        this(ServerUtilitiesBridge.NONE);
    }

    public PerformanceSampler(ServerUtilitiesBridge serverUtilities) {
        activeSampler = this;
        this.serverUtilities = serverUtilities == null ? ServerUtilitiesBridge.NONE : serverUtilities;
    }

    public static void record(TickCategory category, World world, int chunkX, int chunkZ, long elapsedNanos) {
        if (activeSampler != null) {
            activeSampler.recordTiming(category, world, chunkX, chunkZ, elapsedNanos, null);
        }
    }

    public static void recordObjectTiming(TickCategory category, World world, int chunkX, int chunkZ, String typeName,
        long elapsedNanos) {
        if (activeSampler != null) {
            activeSampler.recordTiming(category, world, chunkX, chunkZ, elapsedNanos, typeName);
        }
    }

    public static void recordGlobal(TickCategory category, World world, long elapsedNanos) {
        if (activeSampler != null) {
            activeSampler.recordGlobalTiming(category, world, elapsedNanos);
        }
    }

    public static void recordEntityTiming(World world, Entity entity, long elapsedNanos) {
        if (!isActive() || world == null || world.isRemote || entity == null) {
            return;
        }
        String typeName = EntityList.getEntityString(entity);
        if (typeName == null) {
            typeName = entity.getClass()
                .getSimpleName();
        }
        activeSampler
            .recordTiming(TickCategory.ENTITY, world, entity.chunkCoordX, entity.chunkCoordZ, elapsedNanos, typeName);
        activeSampler.objectHotspots.record(
            world.provider.dimensionId,
            TickCategory.ENTITY,
            typeName,
            entity.getEntityId(),
            entity.getUniqueID()
                .getMostSignificantBits(),
            entity.getUniqueID()
                .getLeastSignificantBits(),
            MathHelper.floor_double(entity.posX),
            MathHelper.floor_double(entity.posY),
            MathHelper.floor_double(entity.posZ),
            elapsedNanos);
    }

    public static void recordTileEntityTiming(World world, TileEntity tileEntity, long elapsedNanos) {
        if (tileEntity != null) {
            recordBlockTiming(
                TickCategory.BLOCK_ENTITY,
                world,
                tileEntity.xCoord,
                tileEntity.yCoord,
                tileEntity.zCoord,
                tileEntity.getClass()
                    .getSimpleName(),
                elapsedNanos);
        }
    }

    public static void recordBlockTiming(TickCategory category, World world, int x, int y, int z, String typeName,
        long elapsedNanos) {
        if (!isActive() || world == null || world.isRemote) {
            return;
        }
        activeSampler.recordTiming(category, world, x >> 4, z >> 4, elapsedNanos, typeName);
        activeSampler.objectHotspots
            .record(world.provider.dimensionId, category, typeName, -1, 0L, 0L, x, y, z, elapsedNanos);
    }

    public static void recordGlobalObjectTiming(TickCategory category, World world, String typeName,
        long elapsedNanos) {
        if (activeSampler != null) {
            activeSampler.accumulateGlobalObjectTiming(category, world, typeName, elapsedNanos);
        }
    }

    public static boolean requestScan(EntityPlayerMP player, int seconds) {
        return activeSampler != null && activeSampler.startScan(player, seconds);
    }

    public static boolean requestConsoleScan(ICommandSender sender, int seconds) {
        return activeSampler != null && activeSampler.startScan(null, sender, seconds);
    }

    public static boolean enqueueScan(EntityPlayerMP player, int seconds) {
        if (activeSampler != null && player != null) {
            return activeSampler.enqueuePendingRequest(player, seconds);
        }
        return false;
    }

    public static boolean isActive() {
        return activeSampler != null && activeSampler.active;
    }

    public static boolean stopScan(ICommandSender sender) {
        if (activeSampler == null || sender == null || !activeSampler.active) {
            return false;
        }
        if (sender instanceof EntityPlayerMP player) {
            if ((ServerConfig.requireOperator && !player.canCommandSenderUseCommand(2, "flamechunk"))
                || !activeSampler.serverUtilities.hasPermission(player, "flamechunk.scan")) {
                return false;
            }
        }
        activeSampler.finishScan();
        return true;
    }

    public static boolean sendLastReport(ICommandSender sender) {
        if (activeSampler == null || sender == null || activeSampler.lastSnapshot == null) {
            return false;
        }
        if (sender instanceof EntityPlayerMP player) {
            if ((ServerConfig.requireOperator && !player.canCommandSenderUseCommand(2, "flamechunk"))
                || !activeSampler.serverUtilities.hasPermission(player, "flamechunk.scan")) {
                return false;
            }
        }
        activeSampler.writeReport(sender, activeSampler.lastSnapshot);
        return true;
    }

    public static long beginTiming() {
        return isActive() ? System.nanoTime() : 0L;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            processPendingRequests();
        }
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }
        sampledTicks++;
        if (sampledTicks % 20L == 0L) {
            captureGarbageCollection();
        }
        if (target != null) {
            if (sampledTicks % 20L == 0L) {
                sendProgress();
            }
            if (sampledTicks % Math.max(20, ServerConfig.liveSnapshotIntervalTicks) == 0L) {
                sendLiveSnapshot();
            }
        }
        if (sampledTicks >= totalTicks) {
            finishScan();
        }
    }

    public void onServerStopping(FMLServerStoppingEvent event) {
        active = false;
        target = null;
        feedback = null;
        pendingRequests.clear();
        pendingRequestCount.set(0);
        lastSnapshot = null;
        resetTimings();
    }

    private boolean startScan(EntityPlayerMP player, int seconds) {
        return startScan(player, player, seconds);
    }

    private boolean startScan(EntityPlayerMP player, ICommandSender sender, int seconds) {
        if (active || !ScanLimits.isValidDuration(seconds)) {
            return false;
        }
        if (ServerConfig.requireOperator && player != null && !player.canCommandSenderUseCommand(2, "flamechunk")) {
            return false;
        }
        if (!serverUtilities.hasPermission(player, "flamechunk.scan")) {
            return false;
        }
        durationSeconds = seconds;
        totalTicks = durationSeconds * 20L;
        sampledTicks = 0L;
        lastGcCollectionMillis = gcCollectionMillis();
        target = player;
        feedback = sender;
        active = true;
        droppedDimensions = 0;
        droppedChunks = 0;
        resetTimings();
        if (target != null) {
            sendStatus(target, ScanProgressPacket.STARTED);
            FlameChunk.network.sendTo(new ClearSnapshotPacket(), target);
            sendProgress();
        }
        if (feedback != null) {
            feedback.addChatMessage(new ChatComponentTranslation("flamechunk.command.started", durationSeconds));
        }
        return true;
    }

    private void finishScan() {
        captureGarbageCollection();
        active = false;
        try {
            int actualDurationSeconds = (int) Math.max(1L, Math.min(durationSeconds, (sampledTicks + 19L) / 20L));
            ScanSnapshot snapshot = snapshotBuilder.build(
                actualDurationSeconds,
                sampledTicks,
                dimensions,
                ServerConfig.maxChunksPerDimension,
                objectHotspots);
            lastSnapshot = snapshot;
            if (target != null && target.playerNetServerHandler != null) {
                byte[] encoded = snapshotCodec.encode(snapshot);
                byte[] compressed = compressionCodec.compress(encoded);
                FlameChunk.network.sendTo(new SnapshotPacket(encoded.length, compressed), target);
            }
            if (droppedDimensions > 0 || droppedChunks > 0) {
                FlameChunk.LOG.warn(
                    "FlameChunk scan dropped {} dimensions and {} chunks because configured bounds were reached",
                    droppedDimensions,
                    droppedChunks);
            }
            if (feedback != null) {
                feedback.addChatMessage(new ChatComponentTranslation("flamechunk.command.completed", sampledTicks));
            }
        } catch (RuntimeException exception) {
            FlameChunk.LOG.error("Unable to publish FlameChunk scan", exception);
            if (feedback != null) {
                feedback.addChatMessage(new ChatComponentTranslation("flamechunk.command.failed"));
            }
        } finally {
            target = null;
            feedback = null;
            resetTimings();
        }
    }

    private void sendProgress() {
        if (target != null && target.playerNetServerHandler != null) {
            FlameChunk.network.sendTo(new ScanProgressPacket(sampledTicks, totalTicks), target);
        }
    }

    private void sendLiveSnapshot() {
        if (target == null || target.playerNetServerHandler == null) {
            return;
        }
        try {
            ScanSnapshot snapshot = snapshotBuilder.build(
                durationSeconds,
                sampledTicks,
                dimensions,
                Math.min(ServerConfig.maxChunksPerDimension, ServerConfig.liveSnapshotChunkLimit),
                objectHotspots);
            byte[] encoded = snapshotCodec.encode(snapshot);
            byte[] compressed = compressionCodec.compress(encoded);
            FlameChunk.network.sendTo(new SnapshotPacket(encoded.length, compressed, false), target);
        } catch (RuntimeException exception) {
            FlameChunk.LOG.debug("Unable to publish a live FlameChunk snapshot", exception);
        }
    }

    private void resetTimings() {
        dimensions.clear();
        objectHotspots.clear();
        typeAggregateCount = 0;
    }

    private void processPendingRequests() {
        int processed = 0;
        PendingRequest request;
        while (processed++ < 8 && (request = pendingRequests.poll()) != null) {
            pendingRequestCount.decrementAndGet();
            if (request.player.playerNetServerHandler == null) {
                continue;
            }
            if (active) {
                sendStatus(request.player, ScanProgressPacket.BUSY);
            } else if (!startScan(request.player, request.player, request.seconds)) {
                sendStatus(request.player, ScanProgressPacket.DENIED);
            }
        }
    }

    private void writeReport(ICommandSender sender, ScanSnapshot snapshot) {
        sender.addChatMessage(
            new ChatComponentTranslation(
                "flamechunk.command.report.header",
                snapshot.getDurationSeconds(),
                snapshot.getSampledTicks()));
        for (DimensionSnapshot dimension : snapshot.getDimensions()) {
            sender.addChatMessage(
                new ChatComponentTranslation("flamechunk.command.report.dimension", dimension.getDimensionId()));
            long[] globalNanos = dimension.getGlobalNanos();
            for (TickCategory category : TickCategory.values()) {
                long categoryNanos = globalNanos[category.ordinal()];
                if (categoryNanos > 0L) {
                    sender.addChatMessage(
                        new ChatComponentTranslation(
                            "flamechunk.command.report.category",
                            category.name(),
                            categoryNanos / 1000000.0D / Math.max(1L, snapshot.getSampledTicks())));
                }
            }
            List<ChunkTypeTiming> globalTypeTimings = dimension.getGlobalTypeTimings();
            int globalTypeLimit = Math.min(4, globalTypeTimings.size());
            for (int typeIndex = 0; typeIndex < globalTypeLimit; typeIndex++) {
                ChunkTypeTiming timing = globalTypeTimings.get(typeIndex);
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "flamechunk.command.report.hotspot",
                        timing.getCategory()
                            .name(),
                        timing.getTypeName(),
                        timing.getNanos() / 1000000.0D / Math.max(1L, snapshot.getSampledTicks()),
                        timing.getCount(),
                        timing.getPeakNanos() / 1000000.0D));
            }
            List<ChunkSnapshot> chunks = new ArrayList<>(dimension.getChunks());
            chunks.sort((left, right) -> Long.compare(right.totalNanos(), left.totalNanos()));
            int shown = 0;
            for (ChunkSnapshot chunk : chunks) {
                if (chunk.totalNanos() <= 0L) {
                    continue;
                }
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "flamechunk.command.report.chunk",
                        chunk.getChunkX(),
                        chunk.getChunkZ(),
                        chunk.calculateMspt(snapshot.getSampledTicks()),
                        chunk.getTicketSource()
                            .length() == 0 ? "none" : chunk.getTicketSource()));
                int typeCount = 0;
                for (ChunkTypeTiming typeTiming : chunk.getTypeTimings()) {
                    sender.addChatMessage(
                        new ChatComponentTranslation(
                            "flamechunk.command.report.hotspot",
                            typeTiming.getCategory()
                                .name(),
                            typeTiming.getTypeName(),
                            typeTiming.getNanos() / 1000000.0D / Math.max(1L, snapshot.getSampledTicks()),
                            typeTiming.getCount(),
                            typeTiming.getPeakNanos() / 1000000.0D));
                    if (++typeCount >= 4) {
                        break;
                    }
                }
                shown++;
                if (shown >= 5) {
                    break;
                }
            }
            int objectLimit = Math.min(5, dimension.objectHotspots.size());
            for (int index = 0; index < objectLimit; index++) {
                ObjectHotspot hotspot = dimension.objectHotspots.get(index);
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "flamechunk.command.report.object",
                        hotspot.category.name(),
                        hotspot.typeName,
                        hotspot.x,
                        hotspot.y,
                        hotspot.z,
                        hotspot.calculateMspt(snapshot.getSampledTicks()),
                        hotspot.peakNanos / 1000000.0D,
                        hotspot.count));
            }
        }
    }

    private boolean enqueuePendingRequest(EntityPlayerMP player, int seconds) {
        while (true) {
            int current = pendingRequestCount.get();
            if (current >= MAX_PENDING_REQUESTS || !pendingRequestCount.compareAndSet(current, current + 1)) {
                if (current >= MAX_PENDING_REQUESTS) {
                    return false;
                }
                continue;
            }
            pendingRequests.offer(new PendingRequest(player, seconds));
            return true;
        }
    }

    private void sendStatus(EntityPlayerMP player, int status) {
        if (player != null && player.playerNetServerHandler != null) {
            FlameChunk.network.sendTo(ScanProgressPacket.forStatus(status), player);
        }
    }

    private void recordTiming(TickCategory category, World world, int chunkX, int chunkZ, long elapsedNanos,
        String typeName) {
        if (!active || world == null || world.isRemote || elapsedNanos < 0L) {
            return;
        }
        DimensionTimings dimension = getDimension(world);
        if (dimension == null) {
            return;
        }
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        ChunkTiming timing = dimension.chunks.get(key);
        if (timing == null) {
            if (dimension.chunks.size() >= ServerConfig.maxChunksPerDimension) {
                droppedChunks++;
                return;
            }
            timing = new ChunkTiming();
            dimension.chunks.put(key, timing);
        }
        if (typeName == null) {
            timing.add(category, elapsedNanos);
        } else {
            if (timing
                .addObjectTiming(category, typeName, elapsedNanos, typeAggregateCount < MAX_TYPE_AGGREGATES_PER_SCAN)) {
                typeAggregateCount++;
            }
        }
    }

    private void recordGlobalTiming(TickCategory category, World world, long elapsedNanos) {
        if (!active || world == null || world.isRemote || elapsedNanos < 0L) {
            return;
        }
        DimensionTimings dimension = getDimension(world);
        if (dimension != null) {
            dimension.global.add(category, elapsedNanos);
        }
    }

    public void accumulateGlobalObjectTiming(TickCategory category, World world, String typeName, long elapsedNanos) {
        if (!active || world == null || world.isRemote || elapsedNanos < 0L) {
            return;
        }
        DimensionTimings dimension = getDimension(world);
        if (dimension != null && dimension.global
            .addObjectTiming(category, typeName, elapsedNanos, typeAggregateCount < MAX_TYPE_AGGREGATES_PER_SCAN)) {
            typeAggregateCount++;
        }
    }

    private void captureGarbageCollection() {
        long currentMillis;
        try {
            currentMillis = gcCollectionMillis();
        } catch (RuntimeException exception) {
            FlameChunk.LOG.debug("Unable to read Java garbage collection time", exception);
            return;
        }
        long elapsedMillis = Math.max(0L, currentMillis - lastGcCollectionMillis);
        lastGcCollectionMillis = currentMillis;
        if (elapsedMillis == 0L) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null) {
            return;
        }
        for (World world : server.worldServers) {
            if (world != null && !world.isRemote) {
                long elapsedNanos = elapsedMillis > Long.MAX_VALUE / 1000000L ? Long.MAX_VALUE
                    : elapsedMillis * 1000000L;
                recordGlobalTiming(TickCategory.GARBAGE_COLLECTION, world, elapsedNanos);
                return;
            }
        }
    }

    private long gcCollectionMillis() {
        long totalMillis = 0L;
        for (GarbageCollectorMXBean bean : GC_BEANS) {
            long collectionMillis = bean.getCollectionTime();
            if (collectionMillis > 0L) {
                totalMillis = Long.MAX_VALUE - totalMillis < collectionMillis ? Long.MAX_VALUE
                    : totalMillis + collectionMillis;
            }
        }
        return totalMillis;
    }

    private DimensionTimings getDimension(World world) {
        int dimensionId = world.provider.dimensionId;
        DimensionTimings dimension = dimensions.get(dimensionId);
        if (dimension == null) {
            if (dimensions.size() >= ServerConfig.maxDimensions) {
                droppedDimensions++;
                return null;
            }
            dimension = new DimensionTimings(dimensionId, world);
            dimensions.put(dimensionId, dimension);
        }
        return dimension;
    }

    public static class DimensionTimings {

        public final int dimensionId;
        public final World world;
        public final ChunkTiming global = new ChunkTiming();
        public final Long2ObjectOpenHashMap<ChunkTiming> chunks = new Long2ObjectOpenHashMap<>();

        public DimensionTimings(int dimensionId, World world) {
            this.dimensionId = dimensionId;
            this.world = world;
        }
    }

    public static class PendingRequest {

        public final EntityPlayerMP player;
        public final int seconds;

        public PendingRequest(EntityPlayerMP player, int seconds) {
            this.player = player;
            this.seconds = seconds;
        }
    }
}
