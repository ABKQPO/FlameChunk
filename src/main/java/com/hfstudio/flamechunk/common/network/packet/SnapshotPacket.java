package com.hfstudio.flamechunk.common.network.packet;

import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.network.NetworkHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class SnapshotPacket implements IMessage {

    private int originalSize;
    private byte[] compressedBytes;
    private boolean valid = true;

    public SnapshotPacket() {}

    public SnapshotPacket(int originalSize, byte[] compressedBytes) {
        if (originalSize < 0 || originalSize > ServerConfig.maxPacketBytes || compressedBytes == null
                || compressedBytes.length > ServerConfig.maxPacketBytes) {
            throw new IllegalArgumentException("Invalid snapshot packet size");
        }
        this.originalSize = originalSize;
        this.compressedBytes = compressedBytes.clone();
    }

    public int getOriginalSize() {
        return originalSize;
    }

    public byte[] getCompressedBytes() {
        return compressedBytes == null ? new byte[0] : compressedBytes.clone();
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        if (buffer.readableBytes() < 12) {
            return;
        }
        int magic = buffer.readInt();
        int size = buffer.readInt();
        int length = buffer.readInt();
        if (magic != NetworkHandler.PROTOCOL_MAGIC || size < 0 || size > ServerConfig.maxPacketBytes || length < 0
                || length > ServerConfig.maxPacketBytes || length != buffer.readableBytes()) {
            return;
        }
        originalSize = size;
        compressedBytes = new byte[length];
        buffer.readBytes(compressedBytes);
        valid = true;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        byte[] bytes = compressedBytes == null ? new byte[0] : compressedBytes;
        if (bytes.length > ServerConfig.maxPacketBytes) {
            throw new IllegalArgumentException("Snapshot payload exceeds configured limit");
        }
        buffer.writeInt(NetworkHandler.PROTOCOL_MAGIC);
        buffer.writeInt(originalSize);
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }
}
