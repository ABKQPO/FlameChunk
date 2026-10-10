package com.hfstudio.flamechunk.common.network;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.network.packet.UnknownStackDetailsPacket;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class UnknownStackDetailsHandler implements IMessageHandler<UnknownStackDetailsPacket, IMessage> {

    @Override
    public IMessage onMessage(UnknownStackDetailsPacket message, MessageContext context) {
        if (context.side.isClient() && message.isValid()) {
            try {
                FlameChunk.proxy.handleUnknownStackDetails(message);
            } catch (RuntimeException exception) {
                FlameChunk.LOG.warn("Rejected Unknown stack details response", exception);
            }
        }
        return null;
    }
}
