package com.hfstudio.flamechunk.common.network.packet;

import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.network.NetworkHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

public class SnapshotPacket implements IMessage {

    @Getter
    public int originalSize;
    public byte[] compressedBytes;
    @Getter
    public boolean finalSnapshot = true;
    @Getter
    public long reportId;
    @Getter
    public boolean valid = true;

    public SnapshotPacket() {}

    public SnapshotPacket(int originalSize, byte[] compressedBytes) {
        this(originalSize, compressedBytes, true);
    }

    public SnapshotPacket(int originalSize, byte[] compressedBytes, boolean finalSnapshot) {
        this(originalSize, compressedBytes, finalSnapshot, 0L);
    }

    public SnapshotPacket(int originalSize, byte[] compressedBytes, boolean finalSnapshot, long reportId) {
        if (originalSize < 0 || originalSize > ServerConfig.maxPacketBytes
            || compressedBytes == null
            || compressedBytes.length > ServerConfig.maxPacketBytes
            || reportId < 0L) {
            throw new IllegalArgumentException("Invalid snapshot packet size");
        }
        this.originalSize = originalSize;
        this.compressedBytes = compressedBytes.clone();
        this.finalSnapshot = finalSnapshot;
        this.reportId = reportId;
    }

    public byte[] getCompressedBytes() {
        return compressedBytes == null ? new byte[0] : compressedBytes.clone();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        if (buffer.readableBytes() < 21) {
            return;
        }
        int magic = buffer.readInt();
        int finalSnapshotFlag = buffer.readUnsignedByte();
        long readReportId = buffer.readLong();
        int size = buffer.readInt();
        int length = buffer.readInt();
        if (magic != NetworkHandler.PROTOCOL_MAGIC || finalSnapshotFlag > 1
            || readReportId < 0L
            || size < 0
            || size > NetworkHandler.MAX_PACKET_BYTES
            || length < 0
            || length > NetworkHandler.MAX_PACKET_BYTES
            || length != buffer.readableBytes()) {
            return;
        }
        originalSize = size;
        finalSnapshot = finalSnapshotFlag != 0;
        reportId = readReportId;
        compressedBytes = new byte[length];
        buffer.readBytes(compressedBytes);
        valid = true;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        byte[] bytes = compressedBytes == null ? new byte[0] : compressedBytes;
        if (reportId < 0L || originalSize < 0
            || originalSize > ServerConfig.maxPacketBytes
            || bytes.length > ServerConfig.maxPacketBytes) {
            throw new IllegalArgumentException("Snapshot payload exceeds configured limit");
        }
        buffer.writeInt(NetworkHandler.PROTOCOL_MAGIC);
        buffer.writeByte(finalSnapshot ? 1 : 0);
        buffer.writeLong(reportId);
        buffer.writeInt(originalSize);
        buffer.writeInt(bytes.length);
        buffer.writeBytes(bytes);
    }
}
