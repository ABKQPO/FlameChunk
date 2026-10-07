package com.hfstudio.flamechunk.common.network;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.network.packet.UnknownStackDetailsPacket;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

public class UnknownStackDetailsHandler implements IMessageHandler<UnknownStackDetailsPacket, IMessage> {

    @Override
    public IMessage onMessage(UnknownStackDetailsPacket message, MessageContext context) {
        if (context.side == Side.CLIENT && message.isValid()) {
            try {
                FlameChunk.proxy.handleUnknownStackDetails(message);
            } catch (RuntimeException exception) {
                FlameChunk.LOG.warn("Rejected Unknown stack details response", exception);
            }
        } else if (context.side == Side.CLIENT) {
            FlameChunk.LOG.warn("Rejected invalid Unknown stack details response");
        }
        return null;
    }
}
