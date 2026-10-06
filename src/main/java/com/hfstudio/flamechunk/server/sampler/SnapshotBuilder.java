package com.hfstudio.flamechunk.server.sampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.ChunkTiming;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class SnapshotBuilder {

    public ScanSnapshot build(int durationSeconds, long sampledTicks,
            Int2ObjectOpenHashMap<PerformanceSampler.DimensionTimings> dimensions) {
        List<PerformanceSampler.DimensionTimings> dimensionValues = new ArrayList<PerformanceSampler.DimensionTimings>(
                dimensions.values());
        Collections.sort(dimensionValues, new Comparator<PerformanceSampler.DimensionTimings>() {
            @Override
            public int compare(PerformanceSampler.DimensionTimings left, PerformanceSampler.DimensionTimings right) {
                return Integer.compare(left.dimensionId, right.dimensionId);
            }
        });
        DimensionSnapshot[] snapshots = new DimensionSnapshot[dimensionValues.size()];
        for (int index = 0; index < dimensionValues.size(); index++) {
            PerformanceSampler.DimensionTimings dimension = dimensionValues.get(index);
            List<Long2ObjectMap.Entry<ChunkTiming>> chunks = new ArrayList<Long2ObjectMap.Entry<ChunkTiming>>(
                    dimension.chunks.long2ObjectEntrySet());
            Collections.sort(chunks, new Comparator<Long2ObjectMap.Entry<ChunkTiming>>() {
                @Override
                public int compare(Long2ObjectMap.Entry<ChunkTiming> left, Long2ObjectMap.Entry<ChunkTiming> right) {
                    int leftX = (int) (left.getLongKey() >> 32);
                    int rightX = (int) (right.getLongKey() >> 32);
                    int xCompare = Integer.compare(leftX, rightX);
                    if (xCompare != 0) {
                        return xCompare;
                    }
                    return Integer.compare((int) left.getLongKey(), (int) right.getLongKey());
                }
            });
            ChunkSnapshot[] chunkSnapshots = new ChunkSnapshot[chunks.size()];
            for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
                Long2ObjectMap.Entry<ChunkTiming> entry = chunks.get(chunkIndex);
                long key = entry.getLongKey();
                chunkSnapshots[chunkIndex] = new ChunkSnapshot(dimension.dimensionId, (int) (key >> 32),
                        (int) key, entry.getValue().copyNanos(), entry.getValue().copyCounts());
            }
            snapshots[index] = new DimensionSnapshot(dimension.dimensionId, chunkSnapshots,
                    dimension.global.copyNanos(), dimension.global.copyCounts());
        }
        return new ScanSnapshot(durationSeconds, sampledTicks, snapshots);
    }

}
