package com.hfstudio.flamechunk.common.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.ChunkTypeTiming;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ObjectHotspot;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot.Entry;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot.StackDetail;
import com.hfstudio.flamechunk.common.data.ScanLimits;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.tick.TickCategory;

public class SnapshotCodec {

    public static final int CODEC_VERSION = 11;
    public static final TickCategory[] CATEGORIES = TickCategory.values();

    public byte[] encode(ScanSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot cannot be null");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(CODEC_VERSION);
            int duration = snapshot.getDurationSeconds();
            long ticks = snapshot.getSampledTicks();
            validateScanWindow(duration, ticks);
            output.writeInt(duration);
            output.writeLong(ticks);
            writeObservations(output, snapshot.observations);
            DimensionSnapshot[] dimensions = snapshot.getDimensions();
            checkCount(dimensions.length, ServerConfig.maxDimensions, "dimension");
            output.writeInt(dimensions.length);
            for (DimensionSnapshot dimension : dimensions) {
                output.writeInt(dimension.getDimensionId());
                ChunkSnapshot[] chunks = dimension.getChunks()
                    .toArray(new ChunkSnapshot[0]);
                checkCount(chunks.length, ServerConfig.maxChunksPerDimension, "chunk");
                output.writeInt(chunks.length);
                writeLongs(output, dimension.getGlobalNanos());
                writeInts(output, dimension.getGlobalCounts());
                writeTypeTimings(output, dimension.getGlobalTypeTimings());
                writeObjectHotspots(output, dimension.objectHotspots);
                for (ChunkSnapshot chunk : chunks) {
                    output.writeInt(chunk.getChunkX());
                    output.writeInt(chunk.getChunkZ());
                    writeLongs(output, chunk.getNanos());
                    writeInts(output, chunk.getCounts());
                    output.writeInt(chunk.getEntityCount());
                    output.writeByte(chunk.getLoadLevel());
                    output.writeInt(chunk.getTicketSourceCode());
                    output.writeUTF(chunk.getTicketSource());
                    writeTypeTimings(output, chunk.getTypeTimings());
                    if (bytes.size() > ServerConfig.maxPacketBytes) {
                        throw new IllegalArgumentException("Encoded snapshot exceeds configured packet limit");
                    }
                }
            }
            output.flush();
            byte[] result = bytes.toByteArray();
            if (result.length > ServerConfig.maxPacketBytes) {
                throw new IllegalArgumentException("Encoded snapshot exceeds configured packet limit");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to encode snapshot", exception);
        }
    }

    public ScanSnapshot decode(byte[] encoded, int expectedSize) {
        if (encoded == null || encoded.length == 0 || encoded.length > NetworkHandler.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Encoded snapshot exceeds protocol packet limit");
        }
        if (expectedSize < 0 || expectedSize > NetworkHandler.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Invalid snapshot size");
        }
        if (expectedSize != encoded.length) {
            throw new IllegalArgumentException("Snapshot size does not match payload");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            if (input.readInt() != CODEC_VERSION) {
                throw new IllegalArgumentException("Unsupported snapshot codec version");
            }
            int duration = input.readInt();
            long ticks = input.readLong();
            validateScanWindow(duration, ticks);
            ObservationSnapshot observations = readObservations(input);
            int dimensionCount = readCount(input, NetworkHandler.MAX_DIMENSIONS, "dimension");
            DimensionSnapshot[] dimensions = new DimensionSnapshot[dimensionCount];
            for (int dimensionIndex = 0; dimensionIndex < dimensionCount; dimensionIndex++) {
                int dimensionId = input.readInt();
                int chunkCount = readCount(input, NetworkHandler.MAX_CHUNKS_PER_DIMENSION, "chunk");
                long[] globalNanos = readLongs(input);
                int[] globalCounts = readInts(input);
                List<ChunkTypeTiming> globalTypeTimings = readTypeTimings(input);
                List<ObjectHotspot> objectHotspots = readObjectHotspots(input);
                ChunkSnapshot[] chunks = new ChunkSnapshot[chunkCount];
                for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                    int chunkX = input.readInt();
                    int chunkZ = input.readInt();
                    long[] nanos = readLongs(input);
                    int[] counts = readInts(input);
                    int entityCount = input.readInt();
                    byte loadLevel = input.readByte();
                    int ticketSourceCode = input.readInt();
                    String ticketSource = input.readUTF();
                    if (entityCount < 0 || ticketSourceCode < 0) {
                        throw new IllegalArgumentException("Chunk metadata cannot be negative");
                    }
                    List<ChunkTypeTiming> typeTimings = readTypeTimings(input);
                    chunks[chunkIndex] = new ChunkSnapshot(
                        dimensionId,
                        chunkX,
                        chunkZ,
                        nanos,
                        counts,
                        entityCount,
                        loadLevel,
                        ticketSourceCode,
                        ticketSource,
                        typeTimings);
                }
                dimensions[dimensionIndex] = new DimensionSnapshot(
                    dimensionId,
                    chunks,
                    globalNanos,
                    globalCounts,
                    globalTypeTimings,
                    objectHotspots);
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException("Trailing snapshot data");
            }
            return new ScanSnapshot(duration, ticks, dimensions, observations);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Truncated snapshot", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode snapshot", exception);
        }
    }

    public static void writeObservations(DataOutputStream output, ObservationSnapshot snapshot) throws IOException {
        output.writeLong(snapshot.tickNanos());
        output.writeLong(snapshot.peakTickNanos());
        output.writeLong(snapshot.completedTicks());
        output.writeLong(snapshot.sampleAttempts());
        output.writeLong(snapshot.consistentSamples());
        output.writeLong(snapshot.discardedSamples());
        output.writeInt(snapshot.intervalMicros());
        output.writeUTF(snapshot.degradationReason());
        output.writeInt(
            snapshot.entries()
                .size());
        for (Entry entry : snapshot.entries()) {
            output.writeByte(
                entry.category()
                    .ordinal());
            output.writeInt(entry.dimensionId());
            output.writeUTF(entry.typeName());
            output.writeLong(entry.nanos());
            output.writeLong(entry.peakNanos());
            output.writeLong(entry.samples());
        }
        output.writeByte(
            snapshot.unknownStacks()
                .size());
        for (StackDetail detail : snapshot.unknownStacks()) {
            output.writeUTF(detail.anchor());
            output.writeInt(detail.samples());
            output.writeByte(
                detail.frames()
                    .size());
            for (String frame : detail.frames()) {
                output.writeUTF(frame);
            }
        }
    }

    public static ObservationSnapshot readObservations(DataInputStream input) throws IOException {
        long nanos = input.readLong();
        long peak = input.readLong();
        long ticks = input.readLong();
        long attempts = input.readLong();
        long consistent = input.readLong();
        long discarded = input.readLong();
        int interval = input.readInt();
        String reason = input.readUTF();
        int count = readCount(input, ObservationSnapshot.MAX_ENTRIES, "observation entry");
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int category = input.readUnsignedByte();
            if (category >= CATEGORIES.length) {
                throw new IllegalArgumentException("Unknown observation category");
            }
            entries.add(
                new Entry(
                    CATEGORIES[category],
                    input.readInt(),
                    input.readUTF(),
                    input.readLong(),
                    input.readLong(),
                    input.readLong()));
        }
        int stackCount = input.readUnsignedByte();
        if (stackCount > ObservationSnapshot.MAX_UNKNOWN_STACKS) {
            throw new IllegalArgumentException("Unknown stack count exceeds the limit");
        }
        List<StackDetail> unknownStacks = new ArrayList<>(stackCount);
        for (int index = 0; index < stackCount; index++) {
            String anchor = input.readUTF();
            int samples = input.readInt();
            int frameCount = input.readUnsignedByte();
            if (frameCount > ObservationSnapshot.MAX_STACK_FRAMES) {
                throw new IllegalArgumentException("Unknown stack frame count exceeds the limit");
            }
            List<String> frames = new ArrayList<>(frameCount);
            for (int frame = 0; frame < frameCount; frame++) {
                frames.add(input.readUTF());
            }
            unknownStacks.add(new StackDetail(anchor, samples, frames));
        }
        return new ObservationSnapshot(
            nanos,
            peak,
            ticks,
            attempts,
            consistent,
            discarded,
            interval,
            reason,
            entries,
            unknownStacks);
    }

    public static void writeLongs(DataOutputStream output, long[] values) throws IOException {
        if (values.length != TickCategory.COUNT) {
            throw new IllegalArgumentException("Unexpected timing category count");
        }
        for (long value : values) {
            if (value < 0L) {
                throw new IllegalArgumentException("Timing values cannot be negative");
            }
            output.writeLong(value);
        }
    }

    public static void writeTypeTimings(DataOutputStream output, List<ChunkTypeTiming> timings) throws IOException {
        checkCount(timings.size(), 16, "type timing");
        output.writeByte(timings.size());
        for (ChunkTypeTiming timing : timings) {
            output.writeByte(
                timing.getCategory()
                    .ordinal());
            output.writeUTF(timing.getTypeName());
            output.writeLong(timing.getNanos());
            output.writeInt(timing.getCount());
            output.writeLong(timing.getPeakNanos());
        }
    }

    public static List<ChunkTypeTiming> readTypeTimings(DataInputStream input) throws IOException {
        int count = input.readUnsignedByte();
        if (count > 16) {
            throw new IllegalArgumentException("Type timing count exceeds the limit");
        }
        List<ChunkTypeTiming> timings = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int categoryIndex = input.readUnsignedByte();
            if (categoryIndex >= TickCategory.COUNT) {
                throw new IllegalArgumentException("Unknown type timing category");
            }
            timings.add(
                new ChunkTypeTiming(
                    CATEGORIES[categoryIndex],
                    input.readUTF(),
                    input.readLong(),
                    input.readInt(),
                    input.readLong()));
        }
        return timings;
    }

    public static void writeObjectHotspots(DataOutputStream output, List<ObjectHotspot> hotspots) throws IOException {
        checkCount(hotspots.size(), ObjectHotspot.MAX_PER_DIMENSION, "object hotspot");
        output.writeInt(hotspots.size());
        for (ObjectHotspot hotspot : hotspots) {
            output.writeByte(hotspot.category.ordinal());
            output.writeUTF(hotspot.typeName);
            output.writeInt(hotspot.entityId);
            output.writeLong(hotspot.identityMost);
            output.writeLong(hotspot.identityLeast);
            output.writeInt(hotspot.x);
            output.writeInt(hotspot.y);
            output.writeInt(hotspot.z);
            output.writeLong(hotspot.nanos);
            output.writeLong(hotspot.peakNanos);
            output.writeInt(hotspot.count);
        }
    }

    public static List<ObjectHotspot> readObjectHotspots(DataInputStream input) throws IOException {
        int count = readCount(input, ObjectHotspot.MAX_PER_DIMENSION, "object hotspot");
        List<ObjectHotspot> hotspots = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int categoryIndex = input.readUnsignedByte();
            if (categoryIndex >= CATEGORIES.length) {
                throw new IllegalArgumentException("Unknown object hotspot category");
            }
            hotspots.add(
                new ObjectHotspot(
                    CATEGORIES[categoryIndex],
                    input.readUTF(),
                    input.readInt(),
                    input.readLong(),
                    input.readLong(),
                    input.readInt(),
                    input.readInt(),
                    input.readInt(),
                    input.readLong(),
                    input.readLong(),
                    input.readInt()));
        }
        return hotspots;
    }

    public static void writeInts(DataOutputStream output, int[] values) throws IOException {
        if (values.length != TickCategory.COUNT) {
            throw new IllegalArgumentException("Unexpected timing category count");
        }
        for (int value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("Timing counts cannot be negative");
            }
            output.writeInt(value);
        }
    }

    public static long[] readLongs(DataInputStream input) throws IOException {
        long[] values = new long[TickCategory.COUNT];
        for (int index = 0; index < values.length; index++) {
            values[index] = input.readLong();
            if (values[index] < 0L) {
                throw new IllegalArgumentException("Timing values cannot be negative");
            }
        }
        return values;
    }

    public static int[] readInts(DataInputStream input) throws IOException {
        int[] values = new int[TickCategory.COUNT];
        for (int index = 0; index < values.length; index++) {
            values[index] = input.readInt();
            if (values[index] < 0) {
                throw new IllegalArgumentException("Timing counts cannot be negative");
            }
        }
        return values;
    }

    public static int readCount(DataInputStream input, int maximum, String label) throws IOException {
        int count = input.readInt();
        checkCount(count, maximum, label);
        return count;
    }

    public static void checkCount(int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + count);
        }
    }

    public static void validateScanWindow(int duration, long ticks) {
        if (!ScanLimits.isValidDuration(duration) || ticks < 0L || ticks > duration * 20L) {
            throw new IllegalArgumentException("Invalid scan window");
        }
    }
}
