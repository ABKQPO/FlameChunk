package com.hfstudio.flamechunk.common.network;

import com.github.luben.zstd.Zstd;
import com.hfstudio.flamechunk.common.config.ServerConfig;

public class ZstdCompressionCodec implements CompressionCodec {

    public static final int COMPRESSION_LEVEL = 3;

    @Override
    public byte[] compress(byte[] input) {
        if (input == null || input.length == 0) {
            return new byte[0];
        }
        return Zstd.compress(input, COMPRESSION_LEVEL);
    }

    @Override
    public byte[] decompress(byte[] input, int originalSize) {
        if (originalSize < 0 || originalSize > ServerConfig.maxPacketBytes) {
            throw new IllegalArgumentException("Original size exceeds configured packet limit");
        }
        if (input != null && input.length > ServerConfig.maxPacketBytes) {
            throw new IllegalArgumentException("Compressed size exceeds configured packet limit");
        }
        if (originalSize == 0) {
            if (input == null || input.length != 0) {
                throw new IllegalArgumentException("Unexpected compressed data for empty payload");
            }
            return new byte[0];
        }
        if (input == null || input.length == 0) {
            throw new IllegalArgumentException("Compressed payload is empty");
        }
        byte[] result = new byte[originalSize];
        long decoded = Zstd.decompress(result, input);
        if (Zstd.isError(decoded) || decoded != originalSize) {
            throw new IllegalArgumentException("Zstd decompression failed: " + Zstd.getErrorName(decoded));
        }
        return result;
    }
}
