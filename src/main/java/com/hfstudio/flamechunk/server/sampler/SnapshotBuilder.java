package com.hfstudio.flamechunk.server.sampler;

import java.util.ArrayList;
import java.util.List;

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
        dimensionValues.sort((left, right) -> Integer.compare(left.dimensionId, right.dimensionId));
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
                int ticketSource = forcedChunks.containsKey(new ChunkCoordIntPair(chunkX, chunkZ)) ? 1 : 0;
                chunkSnapshots[chunkIndex] = new ChunkSnapshot(
                    dimension.dimensionId,
                    chunkX,
                    chunkZ,
                    entry.getValue()
                        .copyNanos(),
                    entry.getValue()
                        .copyCounts(),
                    entityCounts.get(key),
                    (byte) 0,
                    ticketSource);
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
            if (!(value instanceof Entity entity) || ((Entity) value).isDead) {
                continue;
            }
            long key = ((long) entity.chunkCoordX << 32) ^ (entity.chunkCoordZ & 0xffffffffL);
            counts.addTo(key, 1);
        }
        return counts;
    }

}
