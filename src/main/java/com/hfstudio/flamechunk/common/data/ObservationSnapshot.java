package com.hfstudio.flamechunk.common.data;

import java.util.List;

import com.hfstudio.flamechunk.common.tick.TickCategory;

public record ObservationSnapshot(long tickNanos, long peakTickNanos, long completedTicks, long sampleAttempts,
    long consistentSamples, long discardedSamples, int intervalMicros, String degradationReason, List<Entry> entries,
    List<StackDetail> unknownStacks) {

    public static final int MAX_ENTRIES = 256;
    public static final int MAX_REASON_LENGTH = 256;
    public static final int MAX_UNKNOWN_STACKS = 3;
    public static final int MAX_UNKNOWN_STACK_SAMPLES = 128;
    public static final int MAX_STACK_FRAMES = 32;
    public static final int MAX_STACK_TEXT_LENGTH = 512;
    public static final ObservationSnapshot EMPTY = new ObservationSnapshot(
        0,
        0,
        0,
        0,
        0,
        0,
        1000,
        "",
        List.of(),
        List.of());

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
            || entries.size() > MAX_ENTRIES
            || unknownStacks == null
            || unknownStacks.size() > MAX_UNKNOWN_STACKS) {
            throw new IllegalArgumentException("Invalid primary observations");
        }
        entries = List.copyOf(entries);
        unknownStacks = List.copyOf(unknownStacks);
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

    public ObservationSnapshot withoutUnknownStacks() {
        return unknownStacks.isEmpty() ? this
            : new ObservationSnapshot(
                tickNanos,
                peakTickNanos,
                completedTicks,
                sampleAttempts,
                consistentSamples,
                discardedSamples,
                intervalMicros,
                degradationReason,
                entries,
                List.of());
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

    public record StackDetail(String anchor, int samples, List<String> frames) {

        public StackDetail {
            if (anchor == null || anchor.isEmpty()
                || anchor.length() > MAX_STACK_TEXT_LENGTH
                || samples <= 0
                || samples > MAX_UNKNOWN_STACK_SAMPLES
                || frames == null
                || frames.isEmpty()
                || frames.size() > MAX_STACK_FRAMES) {
                throw new IllegalArgumentException("Invalid unknown stack detail");
            }
            for (String frame : frames) {
                if (frame == null || frame.isEmpty() || frame.length() > MAX_STACK_TEXT_LENGTH) {
                    throw new IllegalArgumentException("Invalid unknown stack frame");
                }
            }
            frames = List.copyOf(frames);
        }
    }
}
