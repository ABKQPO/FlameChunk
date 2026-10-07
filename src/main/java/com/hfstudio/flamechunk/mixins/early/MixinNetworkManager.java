package com.hfstudio.flamechunk.mixins.early;

import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

@Mixin(NetworkManager.class)
public abstract class MixinNetworkManager {

    @Redirect(
        method = "processReceivedPackets",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/Packet;processPacket(Lnet/minecraft/network/INetHandler;)V"))
    private void flamechunk$attributePacketTask(Packet packet, INetHandler handler) {
        int work = PerformanceSampler.enterTask(packet.getClass());
        try {
            packet.processPacket(handler);
        } finally {
            PerformanceSampler.leaveWork(work);
        }
    }
}
