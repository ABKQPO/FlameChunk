package com.hfstudio.flamechunk.common.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;
import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.network.packet.UnknownStackDetailsPacket;
import com.hfstudio.flamechunk.common.network.packet.UnknownStackDetailsRequestPacket;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

public class UnknownStackDetailsRequestHandler implements IMessageHandler<UnknownStackDetailsRequestPacket, IMessage> {

    @Override
    public IMessage onMessage(UnknownStackDetailsRequestPacket message, MessageContext context) {
        if (context.side != Side.SERVER || context.getServerHandler() == null) {
            return null;
        }
        if (!message.isValid()) {
            FlameChunk.LOG.warn("Rejected invalid Unknown stack details request");
            return null;
        }
        EntityPlayerMP player = context.getServerHandler().playerEntity;
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && player != null) {
            ServerThreadUtil.addScheduledTask(() -> {
                PerformanceSampler sampler = PerformanceSampler.activeSampler;
                if (sampler == null) {
                    if (PeerChannels.canSend(player)) {
                        FlameChunk.network.sendTo(
                            UnknownStackDetailsPacket
                                .failed(message.getRequestId(), message.getReportId(), UnknownStackDetailsPacket.STALE),
                            player);
                    }
                } else {
                    sampler.sendUnknownStackDetails(player, message.getRequestId(), message.getReportId());
                }
            });
        }
        return null;
    }
}
