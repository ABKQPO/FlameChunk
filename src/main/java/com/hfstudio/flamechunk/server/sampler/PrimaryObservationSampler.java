package com.hfstudio.flamechunk.server.sampler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

import com.hfstudio.flamechunk.common.data.ChunkTypeTiming;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot.Entry;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot.StackDetail;
import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.WorkContextTracker.Observation;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class PrimaryObservationSampler implements AutoCloseable {

    public static final int RETAINED_TICKS = 20;
    public static final int MAX_UNKNOWN_STACK_SAMPLES = ObservationSnapshot.MAX_UNKNOWN_STACK_SAMPLES;
    public static final WorkKey UNKNOWN = new WorkKey(TickCategory.TASK, WorkContextTracker.NO_DIMENSION, "Unknown");
    public final WorkContextTracker tracker = new WorkContextTracker(Thread.currentThread());
    public final PrimaryObservationTimeline timeline;
    public final Long2ObjectOpenHashMap<TickSamples> ticks = new Long2ObjectOpenHashMap<>();
    public final Map<WorkKey, Aggregate> aggregates = new HashMap<>();
    public final List<List<String>> unknownStackSamples = new ArrayList<>(MAX_UNKNOWN_STACK_SAMPLES);
    public final UnknownStackSampler unknownStackSampler;
    public final int intervalMicros;
    public final Thread worker;
    public final WindowsTimerLease timer = new WindowsTimerLease();
    public volatile boolean running = true;
    public long nextTickId;
    public long currentTickStarted;
    public long tickNanos;
    public long peakTickNanos;
    public long completedTicks;
    public long sampleAttempts;
    public long consistentSamples;
    public long discardedSamples;
    public long unsampledTicks;
    public String degradationReason = "";

    public PrimaryObservationSampler(int intervalMicros) {
        this.intervalMicros = Math.max(1000, Math.min(100000, intervalMicros));
        aggregates.put(UNKNOWN, new Aggregate());
        if (!timer.acquire()) {
            degradationReason = "flamechunk.observation.degraded.timer";
        }
        timeline = new PrimaryObservationTimeline(this.intervalMicros);
        worker = new Thread(this::observe, "FlameChunk primary observations");
        worker.setDaemon(true);
        worker.start();
        unknownStackSampler = new UnknownStackSampler(tracker.owner, this::recordUnknownStack, timeline::isUnknownAt);
        unknownStackSampler.start();
    }

    public synchronized void beginTick() {
        if (!running || tracker.tickId != 0 || Thread.currentThread() != tracker.owner) {
            return;
        }
        finalizeOldTicks(false);
        currentTickStarted = System.nanoTime();
        long id = ++nextTickId;
        ticks.put(id, new TickSamples());
        tracker.beginTick(id);
    }

    public synchronized void endTick() {
        long id = tracker.tickId;
        if (id == 0 || Thread.currentThread() != tracker.owner) {
            return;
        }
        long elapsed = Math.max(0, System.nanoTime() - currentTickStarted);
        tracker.endTick();
        TickSamples tick = ticks.get(id);
        tick.nanos = elapsed;
        tick.closed = true;
    }

    public void observe() {
        Observation observation = new Observation();
        try {
            while (running) {
                long sampleStartedNanos = System.nanoTime();
                if (tracker.read(observation)) {
                    long sampledAtNanos = sampleStartedNanos + (System.nanoTime() - sampleStartedNanos) / 2L;
                    record(observation, sampledAtNanos);
                }
                LockSupport.parkNanos(this, intervalMicros * 1000L);
            }
        } catch (RuntimeException | LinkageError exception) {
            synchronized (this) {
                degradationReason = "flamechunk.observation.degraded.worker";
            }
        } finally {
            timer.close();
        }
    }

    public void record(Observation observation) {
        record(observation, System.nanoTime());
    }

    public void record(Observation observation, long sampledAtNanos) {
        recordObservation(observation, sampledAtNanos);
    }

    public synchronized void recordObservation(Observation observation, long sampledAtNanos) {
        sampleAttempts++;
        if (observation.consistent) {
            consistentSamples++;
        }
        TickSamples tick = ticks.get(observation.tickId);
        if (tick == null || tick.attempts == Integer.MAX_VALUE) {
            discardedSamples++;
            return;
        }
        timeline.record(
            sampledAtNanos,
            observation.tickId,
            observation.consistent && observation.category == TickCategory.TASK
                && "Unknown".equals(observation.typeName));
        String typeName = observation.typeName;
        if (typeName.length() > ChunkTypeTiming.MAX_TYPE_NAME_LENGTH) {
            typeName = typeName.substring(0, ChunkTypeTiming.MAX_TYPE_NAME_LENGTH);
        }
        WorkKey key = new WorkKey(observation.category, observation.dimensionId, typeName);
        if (!aggregates.containsKey(key)) {
            if (aggregates.size() == ObservationSnapshot.MAX_ENTRIES) {
                key = UNKNOWN;
            } else {
                aggregates.put(key, new Aggregate());
            }
        }
        tick.counts.addTo(key, 1);
        tick.attempts++;
    }

    public void recordUnknownStack(StackTraceElement[] stack) {
        List<String> frames = visibleFrames(stack);
        if (frames.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (unknownStackSamples.size() < MAX_UNKNOWN_STACK_SAMPLES) {
                unknownStackSamples.add(frames);
            }
        }
    }

    public synchronized void finalizeOldTicks(boolean all) {
        long oldestRetained = nextTickId - RETAINED_TICKS;
        var entries = ticks.long2ObjectEntrySet()
            .fastIterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            TickSamples tick = entry.getValue();
            if (tick.closed && (all || entry.getLongKey() <= oldestRetained)) {
                accumulate(tick, aggregates);
                tickNanos += tick.nanos;
                peakTickNanos = Math.max(peakTickNanos, tick.nanos);
                completedTicks++;
                if (tick.attempts == 0) {
                    unsampledTicks++;
                }
                entries.remove();
            }
        }
    }

    public static void accumulate(TickSamples tick, Map<WorkKey, Aggregate> destination) {
        if (tick.attempts == 0) {
            Aggregate unknown = destination.computeIfAbsent(UNKNOWN, ignored -> new Aggregate());
            unknown.nanos += tick.nanos;
            unknown.peakNanos = Math.max(unknown.peakNanos, tick.nanos);
            return;
        }
        long remaining = tick.nanos;
        int left = tick.counts.size();
        var counts = tick.counts.object2IntEntrySet()
            .fastIterator();
        while (counts.hasNext()) {
            var entry = counts.next();
            int count = entry.getIntValue();
            long weight = --left == 0 ? remaining
                : tick.nanos / tick.attempts * count + tick.nanos % tick.attempts * count / tick.attempts;
            remaining -= weight;
            Aggregate aggregate = destination.computeIfAbsent(entry.getKey(), ignored -> new Aggregate());
            aggregate.nanos += weight;
            aggregate.peakNanos = Math.max(aggregate.peakNanos, weight);
            aggregate.samples += count;
        }
    }

    public synchronized ObservationSnapshot snapshot() {
        return snapshot(true);
    }

    public synchronized ObservationSnapshot snapshot(boolean includeUnknownStacks) {
        Map<WorkKey, Aggregate> combined = new HashMap<>();
        for (var entry : aggregates.entrySet()) {
            Aggregate source = entry.getValue();
            Aggregate copy = new Aggregate();
            copy.nanos = source.nanos;
            copy.peakNanos = source.peakNanos;
            copy.samples = source.samples;
            combined.put(entry.getKey(), copy);
        }
        long total = tickNanos;
        long peak = peakTickNanos;
        long count = completedTicks;
        boolean unsampledTick = unsampledTicks > 0;
        for (TickSamples tick : ticks.values()) {
            if (tick.closed) {
                accumulate(tick, combined);
                total += tick.nanos;
                peak = Math.max(peak, tick.nanos);
                count++;
                unsampledTick |= tick.attempts == 0;
            }
        }
        List<Entry> rows = new ArrayList<>(combined.size());
        for (var entry : combined.entrySet()) {
            WorkKey key = entry.getKey();
            Aggregate value = entry.getValue();
            if (value.nanos > 0 || value.samples > 0) {
                rows.add(
                    new Entry(
                        key.category(),
                        key.dimensionId(),
                        key.typeName(),
                        value.nanos,
                        value.peakNanos,
                        value.samples));
            }
        }
        rows.sort(
            Comparator.comparingLong(Entry::nanos)
                .reversed()
                .thenComparingInt(Entry::dimensionId)
                .thenComparing(Entry::typeName));
        String reason = degradationReason;
        if (reason.isEmpty() && discardedSamples > 0) {
            reason = "flamechunk.observation.degraded.discarded";
        } else if (reason.isEmpty() && (unsampledTick || count > 0 && sampleAttempts == 0)) {
            reason = "flamechunk.observation.degraded.density";
        } else if (reason.isEmpty() && !unknownStackSampler.degradationReason.isEmpty()) {
            reason = unknownStackSampler.degradationReason;
        }
        return new ObservationSnapshot(
            total,
            peak,
            count,
            sampleAttempts,
            consistentSamples,
            discardedSamples,
            intervalMicros,
            reason,
            rows,
            includeUnknownStacks ? clusterUnknownStacks() : List.of());
    }

    private List<StackDetail> clusterUnknownStacks() {
        Map<String, StackAggregate> groups = new HashMap<>();
        for (List<String> frames : unknownStackSamples) {
            if (frames.isEmpty()) {
                continue;
            }
            String anchor = frames.get(0);
            if (frames.size() > 1) {
                anchor += " -> " + frames.get(1);
            }
            if (anchor.length() > ObservationSnapshot.MAX_STACK_TEXT_LENGTH) {
                anchor = anchor.substring(0, ObservationSnapshot.MAX_STACK_TEXT_LENGTH);
            }
            StackAggregate aggregate = groups.get(anchor);
            if (aggregate == null) {
                aggregate = new StackAggregate(anchor, frames);
                groups.put(anchor, aggregate);
            }
            aggregate.samples++;
        }
        List<StackAggregate> sorted = new ArrayList<>(groups.values());
        sorted.sort(
            Comparator.comparingInt((StackAggregate value) -> value.samples)
                .reversed()
                .thenComparing(value -> value.anchor));
        List<StackDetail> details = new ArrayList<>(Math.min(sorted.size(), ObservationSnapshot.MAX_UNKNOWN_STACKS));
        for (int index = 0; index < Math.min(sorted.size(), ObservationSnapshot.MAX_UNKNOWN_STACKS); index++) {
            StackAggregate aggregate = sorted.get(index);
            details.add(new StackDetail(aggregate.anchor, aggregate.samples, aggregate.frames));
        }
        return details;
    }

    private List<String> visibleFrames(StackTraceElement[] stack) {
        List<String> frames = new ArrayList<>(ObservationSnapshot.MAX_STACK_FRAMES);
        for (StackTraceElement frame : stack) {
            if (frame == null) {
                continue;
            }
            String className = frame.getClassName();
            if (className.startsWith("java.") || className.startsWith("sun.")
                || className.equals(PrimaryObservationSampler.class.getName())
                || className.equals(WorkContextTracker.class.getName())) {
                continue;
            }
            String value = frame.toString();
            if (value.length() > ObservationSnapshot.MAX_STACK_TEXT_LENGTH) {
                value = value.substring(0, ObservationSnapshot.MAX_STACK_TEXT_LENGTH);
            }
            frames.add(value);
            if (frames.size() == ObservationSnapshot.MAX_STACK_FRAMES) {
                break;
            }
        }
        return frames;
    }

    @Override
    public void close() {
        unknownStackSampler.close();
        endTick();
        running = false;
        LockSupport.unpark(worker);
        boolean interrupted = false;
        while (worker.isAlive()) {
            try {
                worker.join();
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread()
                .interrupt();
        }
        timer.close();
    }

    public record WorkKey(TickCategory category, int dimensionId, String typeName) {}

    public static class TickSamples {

        public final Object2IntOpenHashMap<WorkKey> counts = new Object2IntOpenHashMap<>();
        public long nanos;
        public int attempts;
        public boolean closed;
    }

    public static class Aggregate {

        public long nanos;
        public long peakNanos;
        public long samples;
    }

    public static class StackAggregate {

        public final String anchor;
        public final List<String> frames;
        public int samples = 1;

        public StackAggregate(String anchor, List<String> frames) {
            this.anchor = anchor;
            this.frames = frames;
        }
    }
}
