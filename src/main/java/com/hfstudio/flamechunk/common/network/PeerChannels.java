package com.hfstudio.flamechunk.common.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;

import com.hfstudio.flamechunk.FlameChunk;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.CustomPacketRegistrationEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ServerConnectionFromClientEvent;
import io.netty.util.AttributeKey;

public class PeerChannels {

    public static final AttributeKey<Boolean> FLAMECHUNK_AVAILABLE = new AttributeKey<>("flamechunk:remote-channel");

    @SubscribeEvent
    public void onChannelRegistration(CustomPacketRegistrationEvent<?> event) {
        if (event.registrations.contains(FlameChunk.MODID)) {
            setAvailable(event.manager, "REGISTER".equals(event.operation));
        }
    }

    @SubscribeEvent
    public void onLocalConnection(ServerConnectionFromClientEvent event) {
        if (event.isLocal) {
            setAvailable(event.manager, true);
        }
    }

    public static void setAvailable(NetworkManager manager, boolean available) {
        if (manager != null && manager.channel() != null) {
            manager.channel()
                .attr(FLAMECHUNK_AVAILABLE)
                .set(available);
        }
    }

    public static boolean isAvailable(NetworkManager manager) {
        return manager != null && manager.channel() != null
            && manager.isChannelOpen()
            && Boolean.TRUE.equals(
                manager.channel()
                    .attr(FLAMECHUNK_AVAILABLE)
                    .get());
    }

    public static boolean canSend(EntityPlayerMP player) {
        return player != null && player.playerNetServerHandler != null
            && isAvailable(player.playerNetServerHandler.netManager);
    }
}
