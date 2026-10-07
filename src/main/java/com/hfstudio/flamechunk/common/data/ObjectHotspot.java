package com.hfstudio.flamechunk.common.data;

import com.hfstudio.flamechunk.common.tick.TickCategory;

public class ObjectHotspot {

    public static final int MAX_PER_DIMENSION = 128;
    public final TickCategory category;
    public final String typeName;
    public final int entityId;
    public final long identityMost;
    public final long identityLeast;
    public final int x;
    public final int y;
    public final int z;
    public final long nanos;
    public final long peakNanos;
    public final int count;

    public ObjectHotspot(TickCategory category, String typeName, int entityId, long identityMost, long identityLeast,
        int x, int y, int z, long nanos, long peakNanos, int count) {
        if (category == null || !category.supportsTypeTiming()
            || category == TickCategory.HANDLER
            || typeName == null
            || typeName.isEmpty()
            || typeName.length() > ChunkTypeTiming.MAX_TYPE_NAME_LENGTH
            || nanos < 0L
            || peakNanos < 0L
            || peakNanos > nanos
            || count < 1) {
            throw new IllegalArgumentException("Invalid object hotspot");
        }
        this.category = category;
        this.typeName = typeName;
        this.entityId = entityId;
        this.identityMost = identityMost;
        this.identityLeast = identityLeast;
        this.x = x;
        this.y = y;
        this.z = z;
        this.nanos = nanos;
        this.peakNanos = peakNanos;
        this.count = count;
    }

    public double calculateMspt(long sampledTicks) {
        return nanos / 1000000.0D / Math.max(1L, sampledTicks);
    }

    public static long blockKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }
}
