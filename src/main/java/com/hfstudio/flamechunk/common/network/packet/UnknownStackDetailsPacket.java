package com.hfstudio.flamechunk.common.network.packet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.hfstudio.flamechunk.common.data.ObservationSnapshot;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot.StackDetail;
import com.hfstudio.flamechunk.common.network.NetworkHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

@Getter
public class UnknownStackDetailsPacket implements IMessage {

    public static final int OK = 0;
    public static final int EMPTY = 1;
    public static final int STALE = 2;
    public static final int DENIED = 3;
    public static final int BUSY = 4;
    public static final int MAX_PACKET_BYTES = 256 * 1024;
    public static final int MAX_TEXT_BYTES = ObservationSnapshot.MAX_STACK_TEXT_LENGTH * 4;

    public long requestId;
    public long reportId;
    public int status;
    public List<StackDetail> details = List.of();
    public boolean valid;

    public UnknownStackDetailsPacket() {}

    public UnknownStackDetailsPacket(long requestId, long reportId, int status, List<StackDetail> details) {
        if (requestId <= 0L || reportId <= 0L
            || status < OK
            || status > BUSY
            || details == null
            || details.size() > 3
            || status == OK && details.isEmpty()
            || status != OK && !details.isEmpty()) {
            throw new IllegalArgumentException("Invalid Unknown stack details response");
        }
        this.requestId = requestId;
        this.reportId = reportId;
        this.status = status;
        this.details = List.copyOf(details);
        valid = true;
    }

    public static UnknownStackDetailsPacket failed(long requestId, long reportId, int status) {
        return new UnknownStackDetailsPacket(requestId, reportId, status, List.of());
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            if (buffer.readableBytes() < 22 || buffer.readableBytes() > MAX_PACKET_BYTES
                || buffer.readInt() != NetworkHandler.PROTOCOL_MAGIC) {
                return;
            }
            long readRequestId = buffer.readLong();
            long readReportId = buffer.readLong();
            int readStatus = buffer.readUnsignedByte();
            int count = buffer.readUnsignedByte();
            if (readRequestId <= 0L || readReportId <= 0L
                || readStatus > BUSY
                || count > 3
                || readStatus == OK && count == 0
                || readStatus != OK && count != 0) {
                return;
            }
            ArrayList<StackDetail> readDetails = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                String anchor = readString(buffer);
                int samples = buffer.readInt();
                int frameCount = buffer.readUnsignedByte();
                if (samples <= 0 || frameCount < 1 || frameCount > 32) {
                    return;
                }
                ArrayList<String> frames = new ArrayList<>(frameCount);
                for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                    frames.add(readString(buffer));
                }
                readDetails.add(new StackDetail(anchor, samples, frames));
            }
            if (buffer.isReadable()) {
                return;
            }
            requestId = readRequestId;
            reportId = readReportId;
            status = readStatus;
            details = List.copyOf(readDetails);
            valid = true;
        } catch (RuntimeException exception) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid || requestId <= 0L
            || reportId <= 0L
            || status < OK
            || status > BUSY
            || details == null
            || details.size() > 3
            || status == OK && details.isEmpty()
            || status != OK && !details.isEmpty()) {
            throw new IllegalStateException("Unknown stack details response is unavailable");
        }
        buffer.writeInt(NetworkHandler.PROTOCOL_MAGIC);
        buffer.writeLong(requestId);
        buffer.writeLong(reportId);
        buffer.writeByte(status);
        buffer.writeByte(details.size());
        for (StackDetail detail : details) {
            writeString(buffer, detail.anchor());
            buffer.writeInt(detail.samples());
            buffer.writeByte(
                detail.frames()
                    .size());
            for (String frame : detail.frames()) {
                writeString(buffer, frame);
            }
        }
    }

    public static String readString(ByteBuf buffer) {
        if (buffer.readableBytes() < 2) {
            throw new IllegalArgumentException("Truncated Unknown stack text length");
        }
        int length = buffer.readUnsignedShort();
        if (length < 1 || length > MAX_TEXT_BYTES || buffer.readableBytes() < length) {
            throw new IllegalArgumentException("Invalid Unknown stack text length");
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 1 || bytes.length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException("Unknown stack text exceeds the packet limit");
        }
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }
}
