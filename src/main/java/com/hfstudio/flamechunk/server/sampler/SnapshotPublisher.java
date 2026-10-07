package com.hfstudio.flamechunk.server.sampler;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ObjectHotspot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.network.PeerChannels;
import com.hfstudio.flamechunk.common.network.SnapshotCodec;
import com.hfstudio.flamechunk.common.network.ZstdCompressionCodec;
import com.hfstudio.flamechunk.common.network.packet.SnapshotPacket;
import com.hfstudio.flamechunk.server.sampler.SnapshotSubscriptions.Subscription;

public class SnapshotPublisher {

    public final SnapshotCodec snapshotCodec = new SnapshotCodec();
    public final ZstdCompressionCodec compressionCodec = new ZstdCompressionCodec();

    public Set<NetworkManager> publish(ScanSnapshot snapshot, boolean finalSnapshot,
        List<Subscription> subscriptions, ObjectHotspotStore hotspots) {
        Set<NetworkManager> delivered = new HashSet<>();
        SnapshotPacket reportPacket = null;
        SnapshotPacket mapPacket = null;
        for (Subscription subscription : subscriptions) {
            EntityPlayerMP player = subscription.player();
            if (!PeerChannels.canSend(player)) {
                continue;
            }
            try {
                SnapshotPacket packet;
                if (finalSnapshot) {
                    if (reportPacket == null) {
                        reportPacket = encode(snapshot, true);
                    }
                    packet = reportPacket;
                } else if (!subscription.worldHotspots) {
                    if (mapPacket == null) {
                        mapPacket = encode(snapshot, false);
                    }
                    packet = mapPacket;
                } else {
                    packet = encode(forSubscriber(snapshot, player, subscription.worldHotspots, hotspots), false);
                }
                FlameChunk.network.sendTo(packet, player);
                subscription.pendingSnapshot = false;
                delivered.add(subscription.manager);
            } catch (RuntimeException exception) {
                FlameChunk.LOG.warn("Unable to publish FlameChunk snapshot to {}", player.getCommandSenderName(),
                    exception);
            }
        }
        return delivered;
    }

    public SnapshotPacket encode(ScanSnapshot snapshot, boolean finalSnapshot) {
        byte[] encoded = snapshotCodec.encode(snapshot);
        byte[] compressed = compressionCodec.compress(encoded);
        return new SnapshotPacket(encoded.length, compressed, finalSnapshot);
    }

    public ScanSnapshot forSubscriber(ScanSnapshot snapshot, EntityPlayerMP player, boolean includeHotspots,
        ObjectHotspotStore hotspots) {
        DimensionSnapshot[] dimensions = snapshot.getDimensions();
        for (int index = 0; index < dimensions.length; index++) {
            DimensionSnapshot dimension = dimensions[index];
            List<ObjectHotspot> nearby = includeHotspots && hotspots != null
                && player.worldObj != null && dimension.getDimensionId() == player.dimension
                ? hotspots.snapshotNear(dimension.getDimensionId(), player.posX, player.posY, player.posZ,
                    ServerConfig.worldHotspotRadius, ServerConfig.worldHotspotLimit)
                : Collections.emptyList();
            if (nearby.isEmpty() && dimension.objectHotspots.isEmpty()) {
                continue;
            }
            dimensions[index] = new DimensionSnapshot(dimension.getDimensionId(),
                dimension.getChunks().toArray(new ChunkSnapshot[0]), dimension.getGlobalNanos(),
                dimension.getGlobalCounts(), dimension.getGlobalTypeTimings(), nearby);
        }
        return new ScanSnapshot(snapshot.getDurationSeconds(), snapshot.getSampledTicks(), dimensions);
    }
}
