package com.hfstudio.flamechunk.server.sampler;

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

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class PerformanceSampler {

    private static PerformanceSampler activeSampler;

    private final Int2ObjectOpenHashMap<DimensionTimings> dimensions = new Int2ObjectOpenHashMap<DimensionTimings>();
    private final SnapshotBuilder snapshotBuilder = new SnapshotBuilder();
    private final SnapshotCodec snapshotCodec = new SnapshotCodec();
    private final ZstdCompressionCodec compressionCodec = new ZstdCompressionCodec();
    private EntityPlayerMP target;
    private ICommandSender feedback;
    private int durationSeconds;
    private long totalTicks;
    private long sampledTicks;
    private boolean active;
    private int droppedDimensions;
    private int droppedChunks;

    public PerformanceSampler() {
        activeSampler = this;
    }

    public static void begin() {
        if (activeSampler != null) {
            activeSampler.resetTimings();
        }
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

    public static boolean isActive() {
        return activeSampler != null && activeSampler.active;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
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
        ScanSnapshot snapshot = snapshotBuilder.build(durationSeconds, sampledTicks, dimensions);
        if (target != null) {
            byte[] encoded = snapshotCodec.encode(snapshot);
            byte[] compressed = compressionCodec.compress(encoded);
            FlameChunk.network.sendTo(new SnapshotPacket(encoded.length, compressed), target);
        }
        if (droppedDimensions > 0 || droppedChunks > 0) {
            FlameChunk.LOG.warn("FlameChunk scan dropped {} dimensions and {} chunks because configured bounds were reached",
                    droppedDimensions, droppedChunks);
        }
        if (feedback != null) {
            feedback.addChatMessage(new ChatComponentTranslation("flamechunk.command.completed", sampledTicks));
        }
        target = null;
        feedback = null;
        resetTimings();
    }

    private void sendProgress() {
        FlameChunk.network.sendTo(new ScanProgressPacket(sampledTicks, totalTicks), target);
    }

    private void resetTimings() {
        dimensions.clear();
    }

    private void recordTiming(TickCategory category, World world, int chunkX, int chunkZ, long elapsedNanos) {
        if (!active || world == null || world.isRemote || elapsedNanos < 0L) {
            return;
        }
        DimensionTimings dimension = getDimension(world.provider.dimensionId);
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
        DimensionTimings dimension = getDimension(world.provider.dimensionId);
        if (dimension != null) {
            dimension.global.add(category, elapsedNanos);
        }
    }

    private DimensionTimings getDimension(int dimensionId) {
        DimensionTimings dimension = dimensions.get(dimensionId);
        if (dimension == null) {
            if (dimensions.size() >= ServerConfig.maxDimensions) {
                droppedDimensions++;
                return null;
            }
            dimension = new DimensionTimings(dimensionId);
            dimensions.put(dimensionId, dimension);
        }
        return dimension;
    }

    public static class DimensionTimings {

        public final int dimensionId;
        public final ChunkTiming global = new ChunkTiming();
        public final Long2ObjectOpenHashMap<ChunkTiming> chunks = new Long2ObjectOpenHashMap<ChunkTiming>();

        public DimensionTimings(int dimensionId) {
            this.dimensionId = dimensionId;
        }
    }
}
