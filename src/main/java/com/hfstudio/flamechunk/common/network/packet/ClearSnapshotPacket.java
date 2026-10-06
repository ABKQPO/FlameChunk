package com.hfstudio.flamechunk.common.network.packet;

import com.hfstudio.flamechunk.common.network.NetworkHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public class ClearSnapshotPacket implements IMessage {

    private boolean valid = true;

    public ClearSnapshotPacket() {}

    public boolean isValid() {
        return valid;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = buffer.readableBytes() == 4 && buffer.readInt() == NetworkHandler.PROTOCOL_MAGIC;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(NetworkHandler.PROTOCOL_MAGIC);
    }
}
