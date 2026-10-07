package com.hfstudio.flamechunk.common.network.packet;

import com.hfstudio.flamechunk.common.network.NetworkHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

@Getter
public class UnknownStackDetailsRequestPacket implements IMessage {

    public long requestId;
    public long reportId;
    public boolean valid;

    public UnknownStackDetailsRequestPacket() {}

    public UnknownStackDetailsRequestPacket(long requestId, long reportId) {
        if (requestId <= 0L || reportId <= 0L) {
            throw new IllegalArgumentException("Invalid Unknown stack details request");
        }
        this.requestId = requestId;
        this.reportId = reportId;
        valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        if (buffer.readableBytes() != 20 || buffer.readInt() != NetworkHandler.PROTOCOL_MAGIC) {
            return;
        }
        long readRequestId = buffer.readLong();
        long readReportId = buffer.readLong();
        if (readRequestId <= 0L || readReportId <= 0L) {
            return;
        }
        requestId = readRequestId;
        reportId = readReportId;
        valid = true;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid || requestId <= 0L || reportId <= 0L) {
            throw new IllegalStateException("Unknown stack details request is unavailable");
        }
        buffer.writeInt(NetworkHandler.PROTOCOL_MAGIC);
        buffer.writeLong(requestId);
        buffer.writeLong(reportId);
    }
}
