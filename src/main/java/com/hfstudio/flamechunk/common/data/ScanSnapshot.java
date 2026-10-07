package com.hfstudio.flamechunk.common.data;

import lombok.Getter;

public class ScanSnapshot {

    @Getter
    private final int durationSeconds;
    @Getter
    private final long sampledTicks;
    private final DimensionSnapshot[] dimensions;

    public ScanSnapshot(int durationSeconds, long sampledTicks, DimensionSnapshot[] dimensions) {
        if (durationSeconds < 0 || sampledTicks < 0L || dimensions == null) {
            throw new IllegalArgumentException("Invalid scan snapshot values");
        }
        this.durationSeconds = durationSeconds;
        this.sampledTicks = sampledTicks;
        this.dimensions = dimensions.clone();
    }

    public DimensionSnapshot[] getDimensions() {
        return dimensions.clone();
    }

    public int getChunkCount() {
        int count = 0;
        for (DimensionSnapshot dimension : dimensions) {
            count += dimension.getChunks()
                .size();
        }
        return count;
    }
}
