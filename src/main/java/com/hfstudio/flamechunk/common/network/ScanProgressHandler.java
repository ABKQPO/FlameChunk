package com.hfstudio.flamechunk.common.network;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.network.packet.ScanProgressPacket;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public class ScanProgressHandler implements IMessageHandler<ScanProgressPacket, IMessage> {

    @Override
    public IMessage onMessage(ScanProgressPacket message, MessageContext context) {
        if (context.side.isClient() && message.isValid()) {
            if (message.isStatusMessage()) {
                FlameChunk.proxy.handleScanStatus(message.getStatus());
            } else {
                FlameChunk.proxy.handleProgress(message);
            }
        }
        return null;
    }
}
