package com.hfstudio.flamechunk.common.network.packet;

import com.hfstudio.flamechunk.common.network.NetworkHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

@Getter
public class ScanRequestPacket implements IMessage {

    private int seconds;
    private boolean valid = true;

    public ScanRequestPacket() {}

    public ScanRequestPacket(int seconds) {
        this.seconds = seconds;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        if (buffer.readableBytes() != 8) {
            return;
        }
        int magic = buffer.readInt();
        int value = buffer.readInt();
        if (magic == NetworkHandler.PROTOCOL_MAGIC && value >= 1 && value <= 60) {
            seconds = value;
            valid = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(NetworkHandler.PROTOCOL_MAGIC);
        buffer.writeInt(seconds);
    }
}
