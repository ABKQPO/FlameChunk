package com.hfstudio.flamechunk.common.data;

public class ChunkSnapshot {

    private final int dimensionId;
    private final int chunkX;
    private final int chunkZ;
    private final long[] nanos;
    private final int[] counts;

    public ChunkSnapshot(int dimensionId, int chunkX, int chunkZ, long[] nanos, int[] counts) {
        if (nanos == null || counts == null || nanos.length != 7 || counts.length != 7) {
            throw new IllegalArgumentException("Chunk timing arrays must contain seven values");
        }
        this.dimensionId = dimensionId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.nanos = nanos.clone();
        this.counts = counts.clone();
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public long[] getNanos() {
        return nanos.clone();
    }

    public int[] getCounts() {
        return counts.clone();
    }
}
