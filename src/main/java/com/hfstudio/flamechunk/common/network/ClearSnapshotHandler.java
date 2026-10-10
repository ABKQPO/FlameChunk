package com.hfstudio.flamechunk.common.network;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.network.packet.ClearSnapshotPacket;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class ClearSnapshotHandler implements IMessageHandler<ClearSnapshotPacket, IMessage> {

    @Override
    public IMessage onMessage(ClearSnapshotPacket message, MessageContext context) {
        if (context.side.isClient() && message.isValid()) {
            FlameChunk.proxy.handleClear(message);
        }
        return null;
    }
}
