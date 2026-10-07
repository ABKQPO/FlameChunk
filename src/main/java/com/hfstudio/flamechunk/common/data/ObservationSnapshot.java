package com.hfstudio.flamechunk.common.data;

import java.util.List;

import com.hfstudio.flamechunk.common.tick.TickCategory;

public record ObservationSnapshot(long tickNanos, long peakTickNanos, long completedTicks, long sampleAttempts,
    long consistentSamples, long discardedSamples, int intervalMicros, String degradationReason, List<Entry> entries) {

    public static final int MAX_ENTRIES = 256;
    public static final int MAX_REASON_LENGTH = 256;
    public static final ObservationSnapshot EMPTY = new ObservationSnapshot(0, 0, 0, 0, 0, 0, 1000, "", List.of());

    public ObservationSnapshot {
        if (tickNanos < 0 || peakTickNanos < 0
            || peakTickNanos > tickNanos
            || completedTicks < 0
            || sampleAttempts < 0
            || consistentSamples < 0
            || consistentSamples > sampleAttempts
            || discardedSamples < 0
            || discardedSamples > sampleAttempts
            || intervalMicros < 1000
            || intervalMicros > 100000
            || degradationReason == null
            || degradationReason.length() > MAX_REASON_LENGTH
            || entries == null
            || entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid primary observations");
        }
        entries = List.copyOf(entries);
        long attributedNanos = 0;
        for (Entry entry : entries) {
            if (entry.nanos() > tickNanos - attributedNanos) {
                throw new IllegalArgumentException("Observation weights exceed tick time");
            }
            attributedNanos += entry.nanos();
        }
        if (attributedNanos != tickNanos) {
            throw new IllegalArgumentException("Observation weights must conserve tick time");
        }
    }

    public double averageMspt() {
        return completedTicks == 0 ? 0 : tickNanos / 1000000.0D / completedTicks;
    }

    public record Entry(TickCategory category, int dimensionId, String typeName, long nanos, long peakNanos,
        long samples) {

        public Entry {
            if (category == null || typeName == null
                || typeName.isEmpty()
                || typeName.length() > ChunkTypeTiming.MAX_TYPE_NAME_LENGTH
                || nanos < 0
                || peakNanos < 0
                || peakNanos > nanos
                || samples < 0) {
                throw new IllegalArgumentException("Invalid primary observation entry");
            }
        }
    }
}
