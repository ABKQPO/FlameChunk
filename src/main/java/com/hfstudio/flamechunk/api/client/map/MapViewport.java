package com.hfstudio.flamechunk.api.client.map;

public record MapViewport(int dimensionId, double centerX, double centerZ, double pixelsPerBlock, int width,
    int height) {

    public MapViewport {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)
            || !Double.isFinite(pixelsPerBlock)
            || pixelsPerBlock <= 0
            || width <= 0
            || height <= 0) {
            throw new IllegalArgumentException("Invalid map viewport");
        }
    }
}
