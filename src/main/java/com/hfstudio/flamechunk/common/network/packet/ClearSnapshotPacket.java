package com.hfstudio.flamechunk.common.network.packet;

import com.hfstudio.flamechunk.common.network.NetworkHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

@Getter
public class ClearSnapshotPacket implements IMessage {

    public boolean valid = true;

    public ClearSnapshotPacket() {}

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = buffer.readableBytes() == 4 && buffer.readInt() == NetworkHandler.PROTOCOL_MAGIC;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(NetworkHandler.PROTOCOL_MAGIC);
    }
}
