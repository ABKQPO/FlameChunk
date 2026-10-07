package com.hfstudio.flamechunk.common.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;
import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.network.packet.ScanProgressPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanRequestPacket;
import com.hfstudio.flamechunk.server.guard.WeakChunkInspector;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

public class ScanRequestHandler implements IMessageHandler<ScanRequestPacket, IMessage> {

    @Override
    public IMessage onMessage(ScanRequestPacket message, MessageContext context) {
        if (context.side != Side.SERVER || context.getServerHandler() == null) {
            return null;
        }
        if (!message.isValid()) {
            FlameChunk.LOG.warn("Rejected invalid FlameChunk scan request packet");
            if (message.isProtocolMismatch()) {
                FlameChunk.network.sendTo(
                    ScanProgressPacket.forStatus(ScanProgressPacket.PROTOCOL_MISMATCH),
                    context.getServerHandler().playerEntity);
            }
            return null;
        }
        EntityPlayerMP player = context.getServerHandler().playerEntity;
        if (message.isStopScanRequest()) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null && player != null) {
                ServerThreadUtil.addScheduledTask(() -> {
                    if (!PerformanceSampler.stopScan(player)) {
                        FlameChunk.network.sendTo(ScanProgressPacket.forStatus(ScanProgressPacket.DENIED), player);
                    }
                });
            }
            return null;
        }
        if (message.isWeakSnapshotRequest()) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null && player != null) {
                ServerThreadUtil.addScheduledTask(() -> processWeakSnapshotRequest(player));
            }
            return null;
        }
        if (!PerformanceSampler.enqueueScan(player, message.getSeconds())) {
            FlameChunk.network.sendTo(ScanProgressPacket.forStatus(ScanProgressPacket.QUEUE_FULL), player);
        } else {
            FlameChunk.network.sendTo(ScanProgressPacket.forStatus(ScanProgressPacket.QUEUED), player);
        }
        return null;
    }

    public static void processWeakSnapshotRequest(EntityPlayerMP player) {
        if (player.isDead || player.worldObj == null || player.playerNetServerHandler == null) {
            return;
        }
        if (!ServerConfig.weakChunkDiagnostics) {
            FlameChunk.network.sendTo(ScanProgressPacket.forStatus(ScanProgressPacket.WEAK_SCAN_DISABLED), player);
        } else if ((ServerConfig.requireOperator && !player.canCommandSenderUseCommand(2, "flamechunk"))
            || FlameChunk.serverUtilities == null
            || !FlameChunk.serverUtilities.hasPermission(player, "flamechunk.scan")) {
                FlameChunk.network.sendTo(ScanProgressPacket.forStatus(ScanProgressPacket.DENIED), player);
            } else if (!WeakChunkInspector.enqueueSnapshotRequest(player)) {
                FlameChunk.network.sendTo(ScanProgressPacket.forStatus(ScanProgressPacket.QUEUE_FULL), player);
            }
    }
}
