package com.hfstudio.flamechunk.server.sampler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.entity.Entity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import com.google.common.collect.ImmutableSetMultimap;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.ChunkTiming;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

public class SnapshotBuilder {

    public ScanSnapshot build(int durationSeconds, long sampledTicks,
        Int2ObjectOpenHashMap<PerformanceSampler.DimensionTimings> dimensions) {
        List<PerformanceSampler.DimensionTimings> dimensionValues = new ArrayList<>(dimensions.values());
        dimensionValues.sort(Comparator.comparingInt(left -> left.dimensionId));
        DimensionSnapshot[] snapshots = new DimensionSnapshot[dimensionValues.size()];
        for (int index = 0; index < dimensionValues.size(); index++) {
            PerformanceSampler.DimensionTimings dimension = dimensionValues.get(index);
            Long2IntOpenHashMap entityCounts = collectEntityCounts(dimension.world);
            ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> forcedChunks = ForgeChunkManager
                .getPersistentChunksFor(dimension.world);
            List<Long2ObjectMap.Entry<ChunkTiming>> chunks = new ArrayList<>(dimension.chunks.long2ObjectEntrySet());
            chunks.sort((left, right) -> {
                int leftX = (int) (left.getLongKey() >> 32);
                int rightX = (int) (right.getLongKey() >> 32);
                int xCompare = Integer.compare(leftX, rightX);
                if (xCompare != 0) {
                    return xCompare;
                }
                return Integer.compare((int) left.getLongKey(), (int) right.getLongKey());
            });
            ChunkSnapshot[] chunkSnapshots = new ChunkSnapshot[chunks.size()];
            for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                Long2ObjectMap.Entry<ChunkTiming> entry = chunks.get(chunkIndex);
                long key = entry.getLongKey();
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                ChunkCoordIntPair position = new ChunkCoordIntPair(chunkX, chunkZ);
                TicketMetadata ticket = ticketMetadata(forcedChunks, position);
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
                    ticket.name);
            }
            snapshots[index] = new DimensionSnapshot(
                dimension.dimensionId,
                chunkSnapshots,
                dimension.global.copyNanos(),
                dimension.global.copyCounts());
        }
        return new ScanSnapshot(durationSeconds, sampledTicks, snapshots);
    }

    private Long2IntOpenHashMap collectEntityCounts(World world) {
        Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
        counts.defaultReturnValue(0);
        for (Object value : world.loadedEntityList) {
            if (!(value instanceof Entity entity) || entity.isDead) {
                continue;
            }
            long key = ((long) entity.chunkCoordX << 32) ^ (entity.chunkCoordZ & 0xffffffffL);
            counts.addTo(key, 1);
        }
        return counts;
    }

    private static byte loadLevel(World world, int chunkX, int chunkZ) {
        if (world == null || world.getChunkProvider() == null) {
            return 32;
        }
        return (byte) (world.getChunkProvider()
            .chunkExists(chunkX, chunkZ) ? 0 : 32);
    }

    private static TicketMetadata ticketMetadata(
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

    private static int ticketPriority(ForgeChunkManager.Ticket ticket) {
        if (ticket.isPlayerTicket()) {
            return 3;
        }
        return ticket.getEntity() == null ? 1 : 2;
    }

    private static String trimTicketSource(String value) {
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
