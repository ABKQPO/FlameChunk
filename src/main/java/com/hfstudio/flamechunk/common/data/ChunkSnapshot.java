package com.hfstudio.flamechunk.common.data;

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
    private final int entityCount;
    @Getter
    private final byte loadLevel;
    @Getter
    private final int ticketSourceCode;
    @Getter
    private final String ticketSource;

    public ChunkSnapshot(int dimensionId, int chunkX, int chunkZ, long[] nanos, int[] counts) {
        this(dimensionId, chunkX, chunkZ, nanos, counts, 0, (byte) 0, 0);
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
            ticketSourceCode == 0 ? "" : "forced");
    }

    public ChunkSnapshot(int dimensionId, int chunkX, int chunkZ, long[] nanos, int[] counts, int entityCount,
        byte loadLevel, int ticketSourceCode, String ticketSource) {
        if (nanos == null || counts == null || nanos.length != 7 || counts.length != 7) {
            throw new IllegalArgumentException("Chunk timing arrays must contain seven values");
        }
        if (entityCount < 0 || ticketSourceCode < 0 || ticketSource == null || ticketSource.length() > 64) {
            throw new IllegalArgumentException("Chunk metadata cannot be negative");
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

    public float calculateMspt(long sampledTicks) {
        if (sampledTicks <= 0L) {
            return 0.0F;
        }
        return totalNanos() / 1000000.0F / sampledTicks;
    }
}
