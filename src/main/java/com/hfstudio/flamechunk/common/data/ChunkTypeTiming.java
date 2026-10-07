package com.hfstudio.flamechunk.common.data;

import com.hfstudio.flamechunk.common.tick.TickCategory;

import lombok.Getter;

@Getter
public class ChunkTypeTiming {

    public static final int MAX_TYPE_NAME_LENGTH = 64;

    public final TickCategory category;
    public final String typeName;
    public final long nanos;
    public final int count;
    public final long peakNanos;

    public ChunkTypeTiming(TickCategory category, String typeName, long nanos, int count) {
        this(category, typeName, nanos, count, 0L);
    }

    public ChunkTypeTiming(TickCategory category, String typeName, long nanos, int count, long peakNanos) {
        if (category == null || !category.supportsTypeTiming()
            || typeName == null
            || typeName.length() == 0
            || typeName.length() > MAX_TYPE_NAME_LENGTH
            || nanos < 0L
            || count < 1
            || peakNanos < 0L
            || peakNanos > nanos) {
            throw new IllegalArgumentException("Invalid type timing");
        }
        this.category = category;
        this.typeName = typeName;
        this.nanos = nanos;
        this.count = count;
        this.peakNanos = peakNanos;
    }

}
