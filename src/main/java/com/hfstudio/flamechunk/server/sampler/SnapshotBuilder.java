package com.hfstudio.flamechunk.server.sampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.Entity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import com.google.common.collect.ImmutableSetMultimap;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.ChunkTiming;
import com.hfstudio.flamechunk.common.data.ChunkTypeTiming;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.tick.TickCategory;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class SnapshotBuilder {

    public ScanSnapshot build(int durationSeconds, long sampledTicks,
        Int2ObjectOpenHashMap<PerformanceSampler.DimensionTimings> dimensions) {
        return build(durationSeconds, sampledTicks, dimensions, ServerConfig.maxChunksPerDimension);
    }

    public ScanSnapshot build(int durationSeconds, long sampledTicks,
        Int2ObjectOpenHashMap<PerformanceSampler.DimensionTimings> dimensions, int chunkLimitPerDimension) {
        return build(durationSeconds, sampledTicks, dimensions, chunkLimitPerDimension, null);
    }

    public ScanSnapshot build(int durationSeconds, long sampledTicks,
        Int2ObjectOpenHashMap<PerformanceSampler.DimensionTimings> dimensions, int chunkLimitPerDimension,
        ObjectHotspotStore objectHotspots) {
        if (chunkLimitPerDimension < 1) {
            throw new IllegalArgumentException("Chunk limit must be positive");
        }
        List<PerformanceSampler.DimensionTimings> dimensionValues = new ArrayList<>(dimensions.values());
        dimensionValues.sort(Comparator.comparingInt(left -> left.dimensionId));
        DimensionSnapshot[] snapshots = new DimensionSnapshot[dimensionValues.size()];
        for (int index = 0; index < dimensionValues.size(); index++) {
            PerformanceSampler.DimensionTimings dimension = dimensionValues.get(index);
            ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> forcedChunks = ForgeChunkManager
                .getPersistentChunksFor(dimension.world);
            Long2ObjectOpenHashMap<ChunkTiming> chunksByPosition = new Long2ObjectOpenHashMap<>(dimension.chunks);
            for (ChunkCoordIntPair forcedChunk : forcedChunks.keySet()) {
                if (chunksByPosition.size() >= ServerConfig.maxChunksPerDimension) {
                    break;
                }
                long key = ((long) forcedChunk.chunkXPos << 32) ^ (forcedChunk.chunkZPos & 0xffffffffL);
                if (!chunksByPosition.containsKey(key)) {
                    chunksByPosition.put(key, new ChunkTiming());
                }
            }
            List<Long2ObjectMap.Entry<ChunkTiming>> chunks = new ArrayList<>(chunksByPosition.long2ObjectEntrySet());
            if (chunks.size() > chunkLimitPerDimension) {
                chunks.sort((left, right) -> {
                    int timeOrder = Long.compare(
                        right.getValue()
                            .totalNanos(),
                        left.getValue()
                            .totalNanos());
                    return timeOrder != 0 ? timeOrder : Long.compare(left.getLongKey(), right.getLongKey());
                });
                chunks = new ArrayList<>(chunks.subList(0, chunkLimitPerDimension));
            }
            chunks.sort((left, right) -> {
                int leftX = (int) (left.getLongKey() >> 32);
                int rightX = (int) (right.getLongKey() >> 32);
                int xCompare = Integer.compare(leftX, rightX);
                if (xCompare != 0) {
                    return xCompare;
                }
                return Integer.compare((int) left.getLongKey(), (int) right.getLongKey());
            });
            Long2ObjectOpenHashMap<ChunkTiming> selectedChunks = new Long2ObjectOpenHashMap<>(chunks.size());
            for (Long2ObjectMap.Entry<ChunkTiming> entry : chunks) {
                selectedChunks.put(entry.getLongKey(), entry.getValue());
            }
            Long2IntOpenHashMap entityCounts = collectEntityCounts(dimension.world, selectedChunks);
            ChunkSnapshot[] chunkSnapshots = new ChunkSnapshot[chunks.size()];
            for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                Long2ObjectMap.Entry<ChunkTiming> entry = chunks.get(chunkIndex);
                long key = entry.getLongKey();
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                ChunkCoordIntPair position = new ChunkCoordIntPair(chunkX, chunkZ);
                TicketMetadata ticket = ticketMetadata(forcedChunks, position);
                List<ChunkTypeTiming> typeTimings = new ArrayList<>(16);
                typeTimings.addAll(
                    entry.getValue()
                        .topObjectTimings(TickCategory.ENTITY, 8));
                typeTimings.addAll(
                    entry.getValue()
                        .topObjectTimings(TickCategory.BLOCK_ENTITY, 8));
                typeTimings.addAll(
                    entry.getValue()
                        .topObjectTimings(TickCategory.RANDOM_TICK, 8));
                typeTimings.addAll(
                    entry.getValue()
                        .topObjectTimings(TickCategory.SCHEDULED_TICK, 8));
                typeTimings.addAll(
                    entry.getValue()
                        .topObjectTimings(TickCategory.BLOCK_EVENT, 8));
                typeTimings.addAll(
                    entry.getValue()
                        .topObjectTimings(TickCategory.BLOCK_UPDATE, 8));
                typeTimings.sort(
                    Comparator.comparingLong(ChunkTypeTiming::getNanos)
                        .reversed());
                if (typeTimings.size() > 16) {
                    typeTimings = new ArrayList<>(typeTimings.subList(0, 16));
                }
                chunkSnapshots[chunkIndex] = new ChunkSnapshot(
                    dimension.dimensionId,
                    chunkX,
                    chunkZ,
                    entry.getValue()
                        .copyNanos(),
                    entry.getValue()
                        .copyCounts(),
                    entityCounts.get(key),
                    loadLevel(dimension.world, chunkX, chunkZ),
                    ticket.code,
                    ticket.name,
                    typeTimings);
            }
            snapshots[index] = new DimensionSnapshot(
                dimension.dimensionId,
                chunkSnapshots,
                dimension.global.copyNanos(),
                dimension.global.copyCounts(),
                dimension.global.topObjectTimings(TickCategory.HANDLER, 16),
                objectHotspots == null ? Collections.emptyList() : objectHotspots.snapshot(dimension.dimensionId));
        }
        return new ScanSnapshot(durationSeconds, sampledTicks, snapshots);
    }

    public Long2IntOpenHashMap collectEntityCounts(World world, Long2ObjectOpenHashMap<ChunkTiming> trackedChunks) {
        Long2IntOpenHashMap counts = new Long2IntOpenHashMap(trackedChunks.size());
        counts.defaultReturnValue(0);
        for (Entity entity : world.loadedEntityList) {
            if (entity == null || entity.isDead) {
                continue;
            }
            long key = ((long) entity.chunkCoordX << 32) ^ (entity.chunkCoordZ & 0xffffffffL);
            if (trackedChunks.containsKey(key)) {
                int count = counts.get(key);
                if (count < Integer.MAX_VALUE) {
                    counts.put(key, count + 1);
                }
            }
        }
        return counts;
    }

    public static byte loadLevel(World world, int chunkX, int chunkZ) {
        if (world == null || world.getChunkProvider() == null) {
            return 32;
        }
        return (byte) (world.getChunkProvider()
            .chunkExists(chunkX, chunkZ) ? 0 : 32);
    }

    public static TicketMetadata ticketMetadata(
        ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> tickets, ChunkCoordIntPair position) {
        if (!tickets.containsKey(position)) {
            return TicketMetadata.NONE;
        }
        ForgeChunkManager.Ticket selected = null;
        for (ForgeChunkManager.Ticket ticket : tickets.get(position)) {
            if (selected == null || ticketPriority(ticket) > ticketPriority(selected)) {
                selected = ticket;
            }
        }
        if (selected == null) {
            return TicketMetadata.NONE;
        }
        if (selected.isPlayerTicket()) {
            String player = selected.getPlayerName();
            return new TicketMetadata(2, player == null || player.length() == 0 ? "player" : "player:" + player);
        }
        Entity entity = selected.getEntity();
        if (entity != null) {
            return new TicketMetadata(
                3,
                "entity:" + entity.getClass()
                    .getSimpleName());
        }
        String modId = selected.getModId();
        String type = selected.getType() == null ? "unknown"
            : selected.getType()
                .name()
                .toLowerCase(Locale.ENGLISH);
        if (modId == null || modId.length() == 0) {
            return new TicketMetadata(4, type);
        }
        return new TicketMetadata(4, trimTicketSource(modId + ":" + type));
    }

    public static int ticketPriority(ForgeChunkManager.Ticket ticket) {
        if (ticket.isPlayerTicket()) {
            return 3;
        }
        return ticket.getEntity() == null ? 1 : 2;
    }

    public static String trimTicketSource(String value) {
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    public static class TicketMetadata {

        public static final TicketMetadata NONE = new TicketMetadata(0, "");
        public final int code;
        public final String name;

        public TicketMetadata(int code, String name) {
            this.code = code;
            this.name = name;
        }
    }

}
