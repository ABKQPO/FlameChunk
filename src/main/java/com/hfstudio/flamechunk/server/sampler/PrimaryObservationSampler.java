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
import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.WorkContextTracker.Observation;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class PrimaryObservationSampler implements AutoCloseable {

    public static final int RETAINED_TICKS = 20;
    public static final WorkKey UNKNOWN = new WorkKey(TickCategory.TASK, WorkContextTracker.NO_DIMENSION, "Unknown");
    public final WorkContextTracker tracker = new WorkContextTracker(Thread.currentThread());
    public final Long2ObjectOpenHashMap<TickSamples> ticks = new Long2ObjectOpenHashMap<>();
    public final Map<WorkKey, Aggregate> aggregates = new HashMap<>();
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
        worker = new Thread(this::observe, "FlameChunk primary observations");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void beginTick() {
        if (!running || tracker.tickId != 0) {
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
        if (id == 0) {
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
                if (tracker.read(observation)) {
                    record(observation);
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

    public synchronized void record(Observation observation) {
        sampleAttempts++;
        if (observation.consistent) {
            consistentSamples++;
        }
        TickSamples tick = ticks.get(observation.tickId);
        if (tick == null || tick.attempts == Integer.MAX_VALUE) {
            discardedSamples++;
            return;
        }
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
        int[] count = tick.counts.computeIfAbsent(key, ignored -> new int[1]);
        count[0]++;
        tick.attempts++;
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
        for (var entry : tick.counts.entrySet()) {
            int count = entry.getValue()[0];
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
            rows);
    }

    @Override
    public void close() {
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

        public final Map<WorkKey, int[]> counts = new HashMap<>();
        public long nanos;
        public int attempts;
        public boolean closed;
    }

    public static class Aggregate {

        public long nanos;
        public long peakNanos;
        public long samples;
    }
}
