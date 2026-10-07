package com.hfstudio.flamechunk.common.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.Getter;

public class DimensionSnapshot {

    @Getter
    private final int dimensionId;
    private final ChunkSnapshot[] chunks;
    private final long[] globalNanos;
    private final int[] globalCounts;

    public DimensionSnapshot(int dimensionId, ChunkSnapshot[] chunks, long[] globalNanos, int[] globalCounts) {
        if (chunks == null || globalNanos == null
            || globalCounts == null
            || globalNanos.length != 7
            || globalCounts.length != 7) {
            throw new IllegalArgumentException("Dimension timing arrays must contain seven values");
        }
        this.dimensionId = dimensionId;
        this.chunks = chunks.clone();
        this.globalNanos = globalNanos.clone();
        this.globalCounts = globalCounts.clone();
    }

    public List<ChunkSnapshot> getChunks() {
        return Collections.unmodifiableList(Arrays.asList(chunks.clone()));
    }

    public long[] getGlobalNanos() {
        return globalNanos.clone();
    }

    public int[] getGlobalCounts() {
        return globalCounts.clone();
    }
}
