package com.hfstudio.flamechunk.server.sampler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.network.PeerChannels;
import com.hfstudio.flamechunk.common.network.packet.ScanProgressPacket;
import com.hfstudio.flamechunk.server.integration.ServerUtilitiesBridge;

public class SnapshotSubscriptions {

    public static final int MAX_SUBSCRIBERS = 256;
    public final Map<NetworkManager, Subscription> subscribers = new HashMap<>();
    public final ServerUtilitiesBridge permissions;

    public SnapshotSubscriptions(ServerUtilitiesBridge permissions) {
        this.permissions = permissions == null ? ServerUtilitiesBridge.NONE : permissions;
    }

    public Update update(EntityPlayerMP player, boolean enabled, boolean worldHotspots) {
        if (player == null || player.playerNetServerHandler == null) {
            return Update.DENIED;
        }
        NetworkManager manager = player.playerNetServerHandler.netManager;
        if (!enabled) {
            return subscribers.remove(manager) == null ? Update.UNCHANGED : Update.REMOVED;
        }
        if (!canSubscribe(player)) {
            return Update.DENIED;
        }
        Subscription subscription = subscribers.get(manager);
        if (subscription != null) {
            if (subscription.worldHotspots == worldHotspots) {
                return Update.UNCHANGED;
            }
            subscription.worldHotspots = worldHotspots;
            subscription.pendingSnapshot = true;
            return Update.CHANGED;
        }
        prune();
        if (subscribers.size() >= MAX_SUBSCRIBERS) {
            return Update.FULL;
        }
        subscribers.put(manager, new Subscription(manager, worldHotspots));
        return Update.ADDED;
    }

    public boolean canSubscribe(EntityPlayerMP player) {
        if (!PeerChannels.canSend(player)) {
            return false;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.isSinglePlayer()
            && player.getCommandSenderName()
                .equals(server.getServerOwner())) {
            return true;
        }
        if (ServerConfig.allowNonOperatorSubscriptions) {
            return true;
        }
        return (!ServerConfig.requireOperator || player.canCommandSenderUseCommand(2, "flamechunk"))
            && permissions.hasPermission(player, "flamechunk.scan");
    }

    public List<Subscription> current() {
        prune();
        return new ArrayList<>(subscribers.values());
    }

    public boolean contains(EntityPlayerMP player) {
        return player != null && player.playerNetServerHandler != null
            && subscribers.containsKey(player.playerNetServerHandler.netManager)
            && canSubscribe(player);
    }

    public void remove(EntityPlayerMP player) {
        if (player != null && player.playerNetServerHandler != null) {
            subscribers.remove(player.playerNetServerHandler.netManager);
        }
    }

    public void prune() {
        Iterator<Subscription> iterator = subscribers.values()
            .iterator();
        while (iterator.hasNext()) {
            Subscription subscription = iterator.next();
            EntityPlayerMP player = subscription.player();
            if (!canSubscribe(player)) {
                iterator.remove();
                if (PeerChannels.canSend(player)) {
                    FlameChunk.network
                        .sendTo(ScanProgressPacket.forStatus(ScanProgressPacket.SUBSCRIPTION_DENIED), player);
                }
            }
        }
    }

    public void clear() {
        subscribers.clear();
    }

    public enum Update {
        ADDED,
        CHANGED,
        REMOVED,
        UNCHANGED,
        DENIED,
        FULL
    }

    public static class Subscription {

        public final NetworkManager manager;
        public boolean worldHotspots;
        public boolean pendingSnapshot = true;

        public Subscription(NetworkManager manager, boolean worldHotspots) {
            this.manager = manager;
            this.worldHotspots = worldHotspots;
        }

        public EntityPlayerMP player() {
            return manager != null && manager.getNetHandler() instanceof NetHandlerPlayServer handler
                ? handler.playerEntity
                : null;
        }
    }
}
