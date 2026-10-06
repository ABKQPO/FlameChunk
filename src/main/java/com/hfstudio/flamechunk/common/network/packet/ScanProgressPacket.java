package com.hfstudio.flamechunk.common.network.packet;

import com.hfstudio.flamechunk.common.network.NetworkHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class ScanProgressPacket implements IMessage {

    private long elapsedTicks;
    private long totalTicks;
    private boolean valid = true;

    public ScanProgressPacket() {}

    public ScanProgressPacket(long elapsedTicks, long totalTicks) {
        this.elapsedTicks = elapsedTicks;
        this.totalTicks = totalTicks;
    }

    public long getElapsedTicks() {
        return elapsedTicks;
    }

    public long getTotalTicks() {
        return totalTicks;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        if (buffer.readableBytes() != 20) {
            return;
        }
        int magic = buffer.readInt();
        long elapsed = buffer.readLong();
        long total = buffer.readLong();
        if (magic == NetworkHandler.PROTOCOL_MAGIC && elapsed >= 0L && total >= 1L && elapsed <= total) {
            elapsedTicks = elapsed;
            totalTicks = total;
            valid = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(NetworkHandler.PROTOCOL_MAGIC);
        buffer.writeLong(elapsedTicks);
        buffer.writeLong(totalTicks);
    }
}
