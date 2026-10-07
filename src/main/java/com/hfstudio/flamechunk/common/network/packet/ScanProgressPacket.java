package com.hfstudio.flamechunk.common.network.packet;

import com.hfstudio.flamechunk.common.network.NetworkHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

@Getter
public class ScanProgressPacket implements IMessage {

    public static final int QUEUED = 0;
    public static final int STARTED = 1;
    public static final int BUSY = 2;
    public static final int DENIED = 3;
    public static final int PROTOCOL_MISMATCH = 4;
    public static final int QUEUE_FULL = 5;
    public static final int WEAK_SCAN_DISABLED = 6;
    public static final int LOCAL_GAME_PAUSED = 7;

    private long elapsedTicks;
    private long totalTicks;
    private int status = -1;
    private boolean statusMessage;
    private boolean valid = true;

    public ScanProgressPacket() {}

    public ScanProgressPacket(long elapsedTicks, long totalTicks) {
        this.elapsedTicks = elapsedTicks;
        this.totalTicks = totalTicks;
    }

    public static ScanProgressPacket forStatus(int status) {
        if (status < QUEUED || status > WEAK_SCAN_DISABLED) {
            throw new IllegalArgumentException("Invalid scan status");
        }
        ScanProgressPacket packet = new ScanProgressPacket(status, 0L);
        packet.status = status;
        packet.statusMessage = true;
        return packet;
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
        if (magic != NetworkHandler.PROTOCOL_MAGIC) {
            return;
        }
        if (total == 0L && elapsed >= QUEUED && elapsed <= WEAK_SCAN_DISABLED) {
            status = (int) elapsed;
            statusMessage = true;
            valid = true;
        } else if (elapsed >= 0L && total >= 1L && elapsed <= total) {
            elapsedTicks = elapsed;
            totalTicks = total;
            valid = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(NetworkHandler.PROTOCOL_MAGIC);
        buffer.writeLong(statusMessage ? status : elapsedTicks);
        buffer.writeLong(statusMessage ? 0L : totalTicks);
    }
}
