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

    private static final int CODEC_VERSION = 1;
    private static final int CATEGORY_COUNT = 7;

    public byte[] encode(ScanSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot cannot be null");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(CODEC_VERSION);
            output.writeInt(snapshot.getDurationSeconds());
            output.writeLong(snapshot.getSampledTicks());
            DimensionSnapshot[] dimensions = snapshot.getDimensions();
            checkCount(dimensions.length, ServerConfig.maxDimensions, "dimension");
            output.writeInt(dimensions.length);
            for (DimensionSnapshot dimension : dimensions) {
                output.writeInt(dimension.getDimensionId());
                ChunkSnapshot[] chunks = dimension.getChunks().toArray(new ChunkSnapshot[0]);
                checkCount(chunks.length, ServerConfig.maxChunksPerDimension, "chunk");
                output.writeInt(chunks.length);
                writeLongs(output, dimension.getGlobalNanos());
                writeInts(output, dimension.getGlobalCounts());
                for (ChunkSnapshot chunk : chunks) {
                    output.writeInt(chunk.getChunkX());
                    output.writeInt(chunk.getChunkZ());
                    writeLongs(output, chunk.getNanos());
                    writeInts(output, chunk.getCounts());
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
            int dimensionCount = readCount(input, ServerConfig.maxDimensions, "dimension");
            DimensionSnapshot[] dimensions = new DimensionSnapshot[dimensionCount];
            for (int dimensionIndex = 0; dimensionIndex < dimensionCount; dimensionIndex++) {
                int dimensionId = input.readInt();
                int chunkCount = readCount(input, ServerConfig.maxChunksPerDimension, "chunk");
                long[] globalNanos = readLongs(input);
                int[] globalCounts = readInts(input);
                ChunkSnapshot[] chunks = new ChunkSnapshot[chunkCount];
                for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                    chunks[chunkIndex] = new ChunkSnapshot(dimensionId, input.readInt(), input.readInt(), readLongs(input),
                            readInts(input));
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
            output.writeLong(value);
        }
    }

    private static void writeInts(DataOutputStream output, int[] values) throws IOException {
        if (values.length != CATEGORY_COUNT) {
            throw new IllegalArgumentException("Expected seven count values");
        }
        for (int value : values) {
            output.writeInt(value);
        }
    }

    private static long[] readLongs(DataInputStream input) throws IOException {
        long[] values = new long[CATEGORY_COUNT];
        for (int index = 0; index < values.length; index++) {
            values[index] = input.readLong();
        }
        return values;
    }

    private static int[] readInts(DataInputStream input) throws IOException {
        int[] values = new int[CATEGORY_COUNT];
        for (int index = 0; index < values.length; index++) {
            values[index] = input.readInt();
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
}
