package com.hfstudio.flamechunk.common.data;

import com.hfstudio.flamechunk.common.tick.TickCategory;

public class ChunkTiming {

    private final long[] nanos = new long[TickCategory.values().length];
    private final int[] counts = new int[TickCategory.values().length];

    public void add(TickCategory category, long elapsedNanos) {
        if (elapsedNanos < 0L) {
            throw new IllegalArgumentException("Elapsed time cannot be negative");
        }
        int index = category.ordinal();
        nanos[index] += elapsedNanos;
        if (counts[index] < Integer.MAX_VALUE) {
            counts[index]++;
        }
    }

    public long getNanos(TickCategory category) {
        return nanos[category.ordinal()];
    }

    public int getCount(TickCategory category) {
        return counts[category.ordinal()];
    }

    public long[] copyNanos() {
        return nanos.clone();
    }

    public int[] copyCounts() {
        return counts.clone();
    }

    public long totalNanos() {
        long total = 0L;
        for (long value : nanos) {
            total += value;
        }
        return total;
    }

    public ChunkTiming copy() {
        ChunkTiming copy = new ChunkTiming();
        System.arraycopy(nanos, 0, copy.nanos, 0, nanos.length);
        System.arraycopy(counts, 0, copy.counts, 0, counts.length);
        return copy;
    }
}
