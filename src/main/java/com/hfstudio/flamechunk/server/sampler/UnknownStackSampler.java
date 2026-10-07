package com.hfstudio.flamechunk.server.sampler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongPredicate;

import com.hfstudio.flamechunk.common.data.ObservationSnapshot;

import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;

public class UnknownStackSampler implements AutoCloseable {

    public static final String EXECUTION_SAMPLE_EVENT = "jdk.ExecutionSample";
    public static final String NATIVE_SAMPLE_EVENT = "jdk.NativeMethodSample";
    public static final int MAX_STACK_DEPTH = 96;
    public static final int MAX_SAMPLES = ObservationSnapshot.MAX_UNKNOWN_STACK_SAMPLES;
    public static final long SAMPLE_INTERVAL_NANOS = Duration.ofMillis(100L)
        .toNanos();

    public final Thread target;
    public final Consumer<StackTraceElement[]> sink;
    public final LongPredicate contextFilter;
    public final AtomicLong lastSampleNanos = new AtomicLong(Long.MIN_VALUE);
    public final AtomicInteger acceptedSamples = new AtomicInteger();
    public volatile RecordingStream stream;
    public volatile String degradationReason = "";
    public long baseNanos;
    public Instant baseInstant;

    public UnknownStackSampler(Thread target, Consumer<StackTraceElement[]> sink, LongPredicate contextFilter) {
        this.target = target;
        this.sink = sink;
        this.contextFilter = contextFilter;
    }

    public void start() {
        try {
            if (target == null || sink == null || contextFilter == null || !FlightRecorder.isAvailable()) {
                degradationReason = "flamechunk.observation.degraded.stack";
                return;
            }
            calibrateClock();
            RecordingStream recording = new RecordingStream();
            Duration period = Duration.ofNanos(SAMPLE_INTERVAL_NANOS);
            recording.enable(EXECUTION_SAMPLE_EVENT)
                .withPeriod(period)
                .withStackTrace();
            recording.onEvent(EXECUTION_SAMPLE_EVENT, this::onSample);
            if (hasEvent(NATIVE_SAMPLE_EVENT)) {
                recording.enable(NATIVE_SAMPLE_EVENT)
                    .withPeriod(period)
                    .withStackTrace();
                recording.onEvent(NATIVE_SAMPLE_EVENT, this::onSample);
            }
            recording.onError(error -> degradationReason = "flamechunk.observation.degraded.stack");
            stream = recording;
            recording.startAsync();
        } catch (RuntimeException | LinkageError exception) {
            degradationReason = "flamechunk.observation.degraded.stack";
            close();
        }
    }

    public void onSample(RecordedEvent event) {
        try {
            if (acceptedSamples.get() >= MAX_SAMPLES || !isTargetThread(event)) {
                return;
            }
            long now = toNanos(event.getStartTime());
            if (!contextFilter.test(now)) {
                return;
            }
            long previous = lastSampleNanos.get();
            if ((previous != Long.MIN_VALUE && now - previous < SAMPLE_INTERVAL_NANOS)
                || !lastSampleNanos.compareAndSet(previous, now)) {
                return;
            }
            RecordedStackTrace trace = event.getStackTrace();
            if (trace == null) {
                return;
            }
            StackTraceElement[] stack = stack(trace);
            if (stack.length == 0) {
                return;
            }
            sink.accept(stack);
            acceptedSamples.incrementAndGet();
        } catch (RuntimeException | LinkageError exception) {
            degradationReason = "flamechunk.observation.degraded.stack";
        }
    }

    public boolean isTargetThread(RecordedEvent event) {
        RecordedThread sampled = event.hasField("sampledThread") ? event.getThread("sampledThread") : event.getThread();
        return sampled != null && sampled.getJavaThreadId() == target.threadId();
    }

    public StackTraceElement[] stack(RecordedStackTrace trace) {
        List<RecordedFrame> frames = trace.getFrames();
        int size = Math.min(frames.size(), MAX_STACK_DEPTH);
        StackTraceElement[] stack = new StackTraceElement[size];
        for (int index = 0; index < size; index++) {
            RecordedFrame frame = frames.get(index);
            stack[index] = new StackTraceElement(
                frame.getMethod()
                    .getType()
                    .getName(),
                frame.getMethod()
                    .getName(),
                null,
                frame.getLineNumber());
        }
        return stack;
    }

    public boolean hasEvent(String eventName) {
        try {
            return FlightRecorder.getFlightRecorder()
                .getEventTypes()
                .stream()
                .anyMatch(event -> eventName.equals(event.getName()));
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    public void calibrateClock() {
        long before = System.nanoTime();
        baseInstant = Instant.now();
        long after = System.nanoTime();
        baseNanos = before + (after - before) / 2L;
    }

    public long toNanos(Instant timestamp) {
        return baseNanos + Duration.between(baseInstant, timestamp)
            .toNanos();
    }

    @Override
    public void close() {
        RecordingStream current = stream;
        stream = null;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException | LinkageError exception) {
                degradationReason = "flamechunk.observation.degraded.stack";
            }
        }
    }
}
