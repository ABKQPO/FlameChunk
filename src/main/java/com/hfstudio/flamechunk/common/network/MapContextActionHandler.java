package com.hfstudio.flamechunk.common.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;
import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.network.packet.MapContextActionPacket;
import com.hfstudio.flamechunk.server.guard.MapContextActionService;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

public class MapContextActionHandler implements IMessageHandler<MapContextActionPacket, IMessage> {

    @Override
    public IMessage onMessage(final MapContextActionPacket message, MessageContext context) {
        if (context.side != Side.SERVER || context.getServerHandler() == null) {
            return null;
        }
        if (!message.isValid()) {
            FlameChunk.LOG.warn("Rejected invalid FlameChunk map context action");
            return null;
        }
        final EntityPlayerMP player = context.getServerHandler().playerEntity;
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && player != null) {
            ServerThreadUtil.addScheduledTask(() -> MapContextActionService.process(player, message));
        }
        return null;
    }
}
