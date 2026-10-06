package com.hfstudio.flamechunk.common.network;

import com.hfstudio.flamechunk.common.network.packet.ScanRequestPacket;
import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;

public class ScanRequestHandler implements IMessageHandler<ScanRequestPacket, IMessage> {

    @Override
    public IMessage onMessage(ScanRequestPacket message, MessageContext context) {
        if (context.side != Side.SERVER || context.getServerHandler() == null) {
            return null;
        }
        if (!message.isValid()) {
            FlameChunk.LOG.warn("Rejected invalid FlameChunk scan request packet");
            return null;
        }
        EntityPlayerMP player = context.getServerHandler().playerEntity;
        PerformanceSampler.requestScan(player, message.getSeconds());
        return null;
    }
}
