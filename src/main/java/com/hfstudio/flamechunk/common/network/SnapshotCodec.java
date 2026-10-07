package com.hfstudio.flamechunk.common.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;

public class SnapshotCodec {

    private static final int CODEC_VERSION = 2;
    private static final int CATEGORY_COUNT = 7;

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
                for (ChunkSnapshot chunk : chunks) {
                    output.writeInt(chunk.getChunkX());
                    output.writeInt(chunk.getChunkZ());
                    writeLongs(output, chunk.getNanos());
                    writeInts(output, chunk.getCounts());
                    output.writeInt(chunk.getEntityCount());
                    output.writeByte(chunk.getLoadLevel());
                    output.writeInt(chunk.getTicketSourceCode());
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
        if (encoded == null || encoded.length == 0 || encoded.length > ServerConfig.maxPacketBytes) {
            throw new IllegalArgumentException("Encoded snapshot exceeds configured packet limit");
        }
        if (expectedSize < 0 || expectedSize > ServerConfig.maxPacketBytes) {
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
            int dimensionCount = readCount(input, ServerConfig.maxDimensions, "dimension");
            DimensionSnapshot[] dimensions = new DimensionSnapshot[dimensionCount];
            for (int dimensionIndex = 0; dimensionIndex < dimensionCount; dimensionIndex++) {
                int dimensionId = input.readInt();
                int chunkCount = readCount(input, ServerConfig.maxChunksPerDimension, "chunk");
                long[] globalNanos = readLongs(input);
                int[] globalCounts = readInts(input);
                ChunkSnapshot[] chunks = new ChunkSnapshot[chunkCount];
                for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                    int chunkX = input.readInt();
                    int chunkZ = input.readInt();
                    long[] nanos = readLongs(input);
                    int[] counts = readInts(input);
                    int entityCount = input.readInt();
                    byte loadLevel = input.readByte();
                    int ticketSourceCode = input.readInt();
                    if (entityCount < 0 || ticketSourceCode < 0) {
                        throw new IllegalArgumentException("Chunk metadata cannot be negative");
                    }
                    chunks[chunkIndex] = new ChunkSnapshot(
                        dimensionId,
                        chunkX,
                        chunkZ,
                        nanos,
                        counts,
                        entityCount,
                        loadLevel,
                        ticketSourceCode);
                }
                dimensions[dimensionIndex] = new DimensionSnapshot(dimensionId, chunks, globalNanos, globalCounts);
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException("Trailing snapshot data");
            }
            return new ScanSnapshot(duration, ticks, dimensions);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Truncated snapshot", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to decode snapshot", exception);
        }
    }

    private static void writeLongs(DataOutputStream output, long[] values) throws IOException {
        if (values.length != CATEGORY_COUNT) {
            throw new IllegalArgumentException("Expected seven timing values");
        }
        for (long value : values) {
            if (value < 0L) {
                throw new IllegalArgumentException("Timing values cannot be negative");
            }
            output.writeLong(value);
        }
    }

    private static void writeInts(DataOutputStream output, int[] values) throws IOException {
        if (values.length != CATEGORY_COUNT) {
            throw new IllegalArgumentException("Expected seven count values");
        }
        for (int value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("Timing counts cannot be negative");
            }
            output.writeInt(value);
        }
    }

    private static long[] readLongs(DataInputStream input) throws IOException {
        long[] values = new long[CATEGORY_COUNT];
        for (int index = 0; index < values.length; index++) {
            values[index] = input.readLong();
            if (values[index] < 0L) {
                throw new IllegalArgumentException("Timing values cannot be negative");
            }
        }
        return values;
    }

    private static int[] readInts(DataInputStream input) throws IOException {
        int[] values = new int[CATEGORY_COUNT];
        for (int index = 0; index < values.length; index++) {
            values[index] = input.readInt();
            if (values[index] < 0) {
                throw new IllegalArgumentException("Timing counts cannot be negative");
            }
        }
        return values;
    }

    private static int readCount(DataInputStream input, int maximum, String label) throws IOException {
        int count = input.readInt();
        checkCount(count, maximum, label);
        return count;
    }

    private static void checkCount(int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + count);
        }
    }

    private static void validateScanWindow(int duration, long ticks) {
        if (duration < 1 || duration > 60 || ticks < 0L || ticks > duration * 20L) {
            throw new IllegalArgumentException("Invalid scan window");
        }
    }
}
