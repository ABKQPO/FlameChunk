package com.hfstudio.flamechunk.common.network.packet;

import com.hfstudio.flamechunk.common.data.ScanLimits;
import com.hfstudio.flamechunk.common.network.NetworkHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

@Getter
public class ScanRequestPacket implements IMessage {

    public static final int WEAK_SNAPSHOT_REQUEST = -1;
    public static final int STOP_SCAN_REQUEST = -2;
    public static final int SUBSCRIBE_REQUEST = -3;
    public static final int UNSUBSCRIBE_REQUEST = -4;
    public static final int SUBSCRIBE_WORLD_REQUEST = -5;

    private int seconds;
    private boolean valid = true;
    private boolean protocolMismatch;
    private boolean weakSnapshotRequest;

    public ScanRequestPacket() {}

    public ScanRequestPacket(int seconds) {
        this.seconds = seconds;
    }

    public static ScanRequestPacket weakSnapshotRequest() {
        ScanRequestPacket packet = new ScanRequestPacket(WEAK_SNAPSHOT_REQUEST);
        packet.weakSnapshotRequest = true;
        return packet;
    }

    public static ScanRequestPacket stopScanRequest() {
        return new ScanRequestPacket(STOP_SCAN_REQUEST);
    }

    public static ScanRequestPacket subscribeRequest() {
        return new ScanRequestPacket(SUBSCRIBE_REQUEST);
    }

    public static ScanRequestPacket subscribeRequest(boolean worldHotspots) {
        return new ScanRequestPacket(worldHotspots ? SUBSCRIBE_WORLD_REQUEST : SUBSCRIBE_REQUEST);
    }

    public static ScanRequestPacket unsubscribeRequest() {
        return new ScanRequestPacket(UNSUBSCRIBE_REQUEST);
    }

    public boolean isStopScanRequest() {
        return seconds == STOP_SCAN_REQUEST;
    }

    public boolean isSubscribeRequest() {
        return seconds == SUBSCRIBE_REQUEST || seconds == SUBSCRIBE_WORLD_REQUEST;
    }

    public boolean requestsWorldHotspots() {
        return seconds == SUBSCRIBE_WORLD_REQUEST;
    }

    public boolean isUnsubscribeRequest() {
        return seconds == UNSUBSCRIBE_REQUEST;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        if (buffer.readableBytes() != 8) {
            return;
        }
        int magic = buffer.readInt();
        int value = buffer.readInt();
        protocolMismatch = magic != NetworkHandler.PROTOCOL_MAGIC;
        if (!protocolMismatch) {
            if (value == WEAK_SNAPSHOT_REQUEST) {
                seconds = value;
                weakSnapshotRequest = true;
                valid = true;
            } else if (value == STOP_SCAN_REQUEST || value == SUBSCRIBE_REQUEST || value == UNSUBSCRIBE_REQUEST
                || value == SUBSCRIBE_WORLD_REQUEST
                || ScanLimits.isValidDuration(value)) {
                seconds = value;
                valid = true;
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(NetworkHandler.PROTOCOL_MAGIC);
        buffer.writeInt(weakSnapshotRequest ? WEAK_SNAPSHOT_REQUEST : seconds);
    }
}
