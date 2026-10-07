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

    public MapOverlayCell(int dimensionId, int chunkX, int chunkZ, int color, float opacity, String label) {
        this.dimensionId = dimensionId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.color = color;
        this.opacity = opacity;
        this.label = label;
    }

}
