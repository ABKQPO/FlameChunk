package com.hfstudio.flamechunk.common.data;

import java.util.Collections;
import java.util.List;

import com.hfstudio.flamechunk.common.tick.TickCategory;

import lombok.Getter;

public class ChunkSnapshot {

    @Getter
    private final int dimensionId;
    @Getter
    private final int chunkX;
    @Getter
    private final int chunkZ;
    private final long[] nanos;
    private final int[] counts;
    @Getter
    private final List<ChunkTypeTiming> typeTimings;
    @Getter
    private final int entityCount;
    @Getter
    private final byte loadLevel;
    @Getter
    private final int ticketSourceCode;
    @Getter
    private final String ticketSource;

    public ChunkSnapshot(int dimensionId, int chunkX, int chunkZ, long[] nanos, int[] counts) {
        this(dimensionId, chunkX, chunkZ, nanos, counts, 0, (byte) 0, 0, "", Collections.emptyList());
    }

    public ChunkSnapshot(int dimensionId, int chunkX, int chunkZ, long[] nanos, int[] counts, int entityCount,
        byte loadLevel, int ticketSourceCode) {
        this(
            dimensionId,
            chunkX,
            chunkZ,
            nanos,
            counts,
            entityCount,
            loadLevel,
            ticketSourceCode,
            ticketSourceCode == 0 ? "" : "forced",
            Collections.emptyList());
    }

    public ChunkSnapshot(int dimensionId, int chunkX, int chunkZ, long[] nanos, int[] counts, int entityCount,
        byte loadLevel, int ticketSourceCode, String ticketSource) {
        this(
            dimensionId,
            chunkX,
            chunkZ,
            nanos,
            counts,
            entityCount,
            loadLevel,
            ticketSourceCode,
            ticketSource,
            Collections.emptyList());
    }

    public ChunkSnapshot(int dimensionId, int chunkX, int chunkZ, long[] nanos, int[] counts, int entityCount,
        byte loadLevel, int ticketSourceCode, String ticketSource, List<ChunkTypeTiming> typeTimings) {
        if (nanos == null || counts == null
            || nanos.length != TickCategory.COUNT
            || counts.length != TickCategory.COUNT) {
            throw new IllegalArgumentException("Chunk timing arrays must contain one value per category");
        }
        if (entityCount < 0 || ticketSourceCode < 0
            || ticketSource == null
            || ticketSource.length() > 64
            || typeTimings == null
            || typeTimings.size() > 16) {
            throw new IllegalArgumentException("Chunk metadata cannot be negative");
        }
        for (ChunkTypeTiming typeTiming : typeTimings) {
            if (typeTiming == null || !typeTiming.getCategory()
                .supportsTypeTiming()) {
                throw new IllegalArgumentException("Unsupported chunk type timing category");
            }
        }
        this.dimensionId = dimensionId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.nanos = nanos.clone();
        this.counts = counts.clone();
        this.entityCount = entityCount;
        this.loadLevel = loadLevel;
        this.ticketSourceCode = ticketSourceCode;
        this.ticketSource = ticketSource;
        this.typeTimings = List.copyOf(typeTimings);
    }

    public long[] getNanos() {
        return nanos.clone();
    }

    public int[] getCounts() {
        return counts.clone();
    }

    public boolean isWeakLoaded() {
        return loadLevel >= 32;
    }

    public long totalNanos() {
        long total = 0L;
        for (int index = 0; index < nanos.length; index++) {
            if (index == TickCategory.BLOCK_UPDATE.ordinal()) {
                continue;
            }
            long value = nanos[index];
            if (value > 0L && Long.MAX_VALUE - total < value) {
                return Long.MAX_VALUE;
            }
            total += value;
        }
        return total;
    }

    public long getCategoryNanos(TickCategory category) {
        return nanos[category.ordinal()];
    }

    public float calculateMspt(long sampledTicks) {
        if (sampledTicks <= 0L) {
            return 0.0F;
        }
        return totalNanos() / 1000000.0F / sampledTicks;
    }
}
