package com.hfstudio.flamechunk.common.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.hfstudio.flamechunk.common.tick.TickCategory;

import lombok.Getter;

public class DimensionSnapshot {

    @Getter
    private final int dimensionId;
    private final ChunkSnapshot[] chunks;
    private final long[] globalNanos;
    private final int[] globalCounts;
    @Getter
    private final List<ChunkTypeTiming> globalTypeTimings;
    public final List<ObjectHotspot> objectHotspots;

    public DimensionSnapshot(int dimensionId, ChunkSnapshot[] chunks, long[] globalNanos, int[] globalCounts) {
        this(dimensionId, chunks, globalNanos, globalCounts, Collections.emptyList());
    }

    public DimensionSnapshot(int dimensionId, ChunkSnapshot[] chunks, long[] globalNanos, int[] globalCounts,
        List<ChunkTypeTiming> globalTypeTimings) {
        this(dimensionId, chunks, globalNanos, globalCounts, globalTypeTimings, Collections.emptyList());
    }

    public DimensionSnapshot(int dimensionId, ChunkSnapshot[] chunks, long[] globalNanos, int[] globalCounts,
        List<ChunkTypeTiming> globalTypeTimings, List<ObjectHotspot> objectHotspots) {
        if (chunks == null || globalNanos == null
            || globalCounts == null
            || globalTypeTimings == null
            || globalNanos.length != TickCategory.COUNT
            || globalCounts.length != TickCategory.COUNT) {
            throw new IllegalArgumentException("Dimension timing arrays must contain one value per category");
        }
        if (globalTypeTimings.size() > 16) {
            throw new IllegalArgumentException("Dimension global type timings exceed the limit");
        }
        for (ChunkTypeTiming timing : globalTypeTimings) {
            if (timing == null || timing.getCategory() != TickCategory.HANDLER) {
                throw new IllegalArgumentException("Dimension global timings must contain event handlers");
            }
        }
        if (objectHotspots == null || objectHotspots.size() > ObjectHotspot.MAX_PER_DIMENSION
            || objectHotspots.contains(null)) {
            throw new IllegalArgumentException("Invalid dimension object hotspots");
        }
        this.objectHotspots = List.copyOf(objectHotspots);
        this.dimensionId = dimensionId;
        this.chunks = chunks.clone();
        this.globalNanos = globalNanos.clone();
        this.globalCounts = globalCounts.clone();
        this.globalTypeTimings = List.copyOf(globalTypeTimings);
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
