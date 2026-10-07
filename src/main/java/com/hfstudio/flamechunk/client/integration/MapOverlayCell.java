package com.hfstudio.flamechunk.client.integration;

import lombok.Getter;

@Getter
public class MapOverlayCell {

    private final int dimensionId;
    private final int chunkX;
    private final int chunkZ;
    private final int color;
    private final float opacity;
    private final String label;
    private final long[] nanos;
    private final int[] counts;
    private final long sampledTicks;
    private final int entityCount;
    private final byte loadLevel;
    private final int ticketSourceCode;
    private final String ticketSource;

    public MapOverlayCell(int dimensionId, int chunkX, int chunkZ, int color, float opacity, String label) {
        this(dimensionId, chunkX, chunkZ, color, opacity, label, new long[7], new int[7], 0L, 0, (byte) 0, 0, "");
    }

    public MapOverlayCell(int dimensionId, int chunkX, int chunkZ, int color, float opacity, String label, long[] nanos,
        int[] counts, long sampledTicks, int entityCount, byte loadLevel, int ticketSourceCode, String ticketSource) {
        if (nanos == null || counts == null
            || nanos.length != 7
            || counts.length != 7
            || sampledTicks < 0L
            || entityCount < 0
            || ticketSourceCode < 0
            || ticketSource == null
            || ticketSource.length() > 64) {
            throw new IllegalArgumentException("Invalid overlay cell metadata");
        }
        this.dimensionId = dimensionId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.color = color;
        this.opacity = opacity;
        this.label = label;
        this.nanos = nanos.clone();
        this.counts = counts.clone();
        this.sampledTicks = sampledTicks;
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

}
