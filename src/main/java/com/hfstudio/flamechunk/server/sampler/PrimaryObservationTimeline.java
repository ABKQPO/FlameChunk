package com.hfstudio.flamechunk.server.sampler;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class PrimaryObservationTimeline {

    public static final int CAPACITY = 4096;
    public static final VarHandle SEQUENCE_HANDLE = MethodHandles.arrayElementVarHandle(long[].class);
    public final long[] sequences = new long[CAPACITY];
    public final long[] timestamps = new long[CAPACITY];
    public final long[] tickIds = new long[CAPACITY];
    public final boolean[] unknownStates = new boolean[CAPACITY];
    public final int indexMask = CAPACITY - 1;
    public final long maximumGapNanos;
    public volatile long publishedSequence;
    public long nextSequence = 1L;

    public PrimaryObservationTimeline(int intervalMicros) {
        maximumGapNanos = Math.max(1L, intervalMicros) * 4_000L;
    }

    public void record(long timestampNanos, long tickId, boolean unknown) {
        if (tickId <= 0L) {
            return;
        }
        long sequence = nextSequence++;
        int index = (int) sequence & indexMask;
        SEQUENCE_HANDLE.setRelease(sequences, index, 0L);
        timestamps[index] = timestampNanos;
        tickIds[index] = tickId;
        unknownStates[index] = unknown;
        SEQUENCE_HANDLE.setRelease(sequences, index, sequence);
        publishedSequence = sequence;
    }

    public boolean isUnknownAt(long timestampNanos) {
        long latest = publishedSequence;
        long earliest = Math.max(1L, latest - CAPACITY + 1L);
        long afterSequence = 0L;
        long afterTimestamp = 0L;
        long afterTickId = 0L;
        boolean afterUnknown = false;
        for (long sequence = latest; sequence >= earliest; sequence--) {
            int index = (int) sequence & indexMask;
            if (sequenceAt(index) != sequence) {
                return false;
            }
            long observedTimestamp = timestamps[index];
            long observedTickId = tickIds[index];
            boolean observedUnknown = unknownStates[index];
            if (sequenceAt(index) != sequence) {
                return false;
            }
            if (observedTimestamp >= timestampNanos) {
                afterSequence = sequence;
                afterTimestamp = observedTimestamp;
                afterTickId = observedTickId;
                afterUnknown = observedUnknown;
                continue;
            }
            return afterSequence == sequence + 1L && observedTickId > 0L
                && observedTickId == afterTickId
                && observedUnknown
                && afterUnknown
                && timestampNanos - observedTimestamp <= maximumGapNanos
                && afterTimestamp - timestampNanos <= maximumGapNanos;
        }
        return false;
    }

    public long sequenceAt(int index) {
        return (long) SEQUENCE_HANDLE.getAcquire(sequences, index);
    }
}
