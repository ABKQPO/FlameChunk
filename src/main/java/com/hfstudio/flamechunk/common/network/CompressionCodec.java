package com.hfstudio.flamechunk.common.network;

public interface CompressionCodec {

    byte[] compress(byte[] input);

    byte[] decompress(byte[] input, int originalSize);
}
