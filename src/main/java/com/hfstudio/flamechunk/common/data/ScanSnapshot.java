package com.hfstudio.flamechunk.common.data;

import lombok.Getter;

public class ScanSnapshot {

    @Getter
    public final int durationSeconds;
    @Getter
    public final long sampledTicks;
    public final DimensionSnapshot[] dimensions;
    public final ObservationSnapshot observations;

    public ScanSnapshot(int durationSeconds, long sampledTicks, DimensionSnapshot[] dimensions) {
        this(durationSeconds, sampledTicks, dimensions, ObservationSnapshot.EMPTY);
    }

    public ScanSnapshot(int durationSeconds, long sampledTicks, DimensionSnapshot[] dimensions,
        ObservationSnapshot observations) {
        if (durationSeconds < 0 || sampledTicks < 0L || dimensions == null || observations == null) {
            throw new IllegalArgumentException("Invalid scan snapshot values");
        }
        this.durationSeconds = durationSeconds;
        this.sampledTicks = sampledTicks;
        this.dimensions = dimensions.clone();
        this.observations = observations;
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
