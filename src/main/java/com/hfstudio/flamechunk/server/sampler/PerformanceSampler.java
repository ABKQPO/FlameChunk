package com.hfstudio.flamechunk.server.sampler;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.data.ChunkTiming;
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
    private static PerformanceSampler activeSampler;

    private final Int2ObjectOpenHashMap<DimensionTimings> dimensions = new Int2ObjectOpenHashMap<>();
    private final SnapshotBuilder snapshotBuilder = new SnapshotBuilder();
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
    private boolean active;
    private int droppedDimensions;
    private int droppedChunks;

    public PerformanceSampler() {
        this(ServerUtilitiesBridge.NONE);
    }

    public PerformanceSampler(ServerUtilitiesBridge serverUtilities) {
        activeSampler = this;
        this.serverUtilities = serverUtilities == null ? ServerUtilitiesBridge.NONE : serverUtilities;
    }

    public static void record(TickCategory category, World world, int chunkX, int chunkZ, long elapsedNanos) {
        if (activeSampler != null) {
            activeSampler.recordTiming(category, world, chunkX, chunkZ, elapsedNanos);
        }
    }

    public static void recordGlobal(TickCategory category, World world, long elapsedNanos) {
        if (activeSampler != null) {
            activeSampler.recordGlobalTiming(category, world, elapsedNanos);
        }
    }

    public static boolean requestScan(EntityPlayerMP player, int seconds) {
        return activeSampler != null && activeSampler.startScan(player, seconds);
    }

    public static boolean requestConsoleScan(ICommandSender sender, int seconds) {
        return activeSampler != null && activeSampler.startScan(null, sender, seconds);
    }

    public static void enqueueScan(EntityPlayerMP player, int seconds) {
        if (activeSampler != null && player != null) {
            activeSampler.enqueuePendingRequest(player, seconds);
        }
    }

    public static boolean isActive() {
        return activeSampler != null && activeSampler.active;
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
        if (target != null && sampledTicks % 20L == 0L) {
            sendProgress();
        }
        if (sampledTicks >= totalTicks) {
            finishScan();
        }
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        active = false;
        target = null;
        feedback = null;
        pendingRequests.clear();
        pendingRequestCount.set(0);
        resetTimings();
    }

    private boolean startScan(EntityPlayerMP player, int seconds) {
        return startScan(player, player, seconds);
    }

    private boolean startScan(EntityPlayerMP player, ICommandSender sender, int seconds) {
        if (active || seconds < 1 || seconds > 60) {
            return false;
        }
        if (ServerConfig.requireOperator && player != null && !player.canCommandSenderUseCommand(2, "flamechunk")) {
            return false;
        }
        if (!serverUtilities.hasPermission(player, "flamechunk.scan")) {
            return false;
        }
        durationSeconds = Math.max(1, Math.min(60, seconds));
        totalTicks = durationSeconds * 20L;
        sampledTicks = 0L;
        target = player;
        feedback = sender;
        active = true;
        droppedDimensions = 0;
        droppedChunks = 0;
        resetTimings();
        if (target != null) {
            FlameChunk.network.sendTo(new ClearSnapshotPacket(), target);
            sendProgress();
        }
        if (feedback != null) {
            feedback.addChatMessage(new ChatComponentTranslation("flamechunk.command.started", durationSeconds));
        }
        return true;
    }

    private void finishScan() {
        active = false;
        try {
            ScanSnapshot snapshot = snapshotBuilder.build(durationSeconds, sampledTicks, dimensions);
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

    private void resetTimings() {
        dimensions.clear();
    }

    private void processPendingRequests() {
        int processed = 0;
        PendingRequest request;
        while (processed++ < 8 && (request = pendingRequests.poll()) != null) {
            pendingRequestCount.decrementAndGet();
            if (!active) {
                startScan(request.player, request.player, request.seconds);
            }
        }
    }

    private void enqueuePendingRequest(EntityPlayerMP player, int seconds) {
        while (true) {
            int current = pendingRequestCount.get();
            if (current >= MAX_PENDING_REQUESTS || !pendingRequestCount.compareAndSet(current, current + 1)) {
                return;
            }
            pendingRequests.offer(new PendingRequest(player, seconds));
            return;
        }
    }

    private void recordTiming(TickCategory category, World world, int chunkX, int chunkZ, long elapsedNanos) {
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
        timing.add(category, elapsedNanos);
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
