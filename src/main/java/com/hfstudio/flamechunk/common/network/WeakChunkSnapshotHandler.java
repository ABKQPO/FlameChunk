package com.hfstudio.flamechunk.common.network;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.network.packet.WeakChunkSnapshotPacket;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class WeakChunkSnapshotHandler implements IMessageHandler<WeakChunkSnapshotPacket, IMessage> {

    @Override
    public IMessage onMessage(WeakChunkSnapshotPacket message, MessageContext context) {
        if (context.side.isClient() && message.isValid()) {
            FlameChunk.proxy.handleWeakChunkSnapshot(message);
        }
        return null;
    }
}
