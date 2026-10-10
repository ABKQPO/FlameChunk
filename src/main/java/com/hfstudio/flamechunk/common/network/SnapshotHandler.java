package com.hfstudio.flamechunk.common.network;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.network.packet.SnapshotPacket;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class SnapshotHandler implements IMessageHandler<SnapshotPacket, IMessage> {

    @Override
    public IMessage onMessage(SnapshotPacket message, MessageContext context) {
        if (context.side.isClient() && message.isValid()) {
            try {
                FlameChunk.proxy.handleSnapshot(message);
            } catch (RuntimeException exception) {
                FlameChunk.LOG.warn("Rejected snapshot packet", exception);
            }
        }
        return null;
    }
}
