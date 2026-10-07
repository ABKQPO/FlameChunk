package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.ClientProxy;
import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.storage.ClientSnapshotStorage;
import com.hfstudio.flamechunk.client.ui.WeakEntitySelectionScreen;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.ChunkEntry;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;
import com.hfstudio.flamechunk.common.integration.Mods;
import com.hfstudio.flamechunk.common.network.PeerChannels;
import com.hfstudio.flamechunk.common.network.packet.ClearSnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.MapContextActionPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanProgressPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanRequestPacket;

public class MapOverlayControls {

    public static int lastWeakSnapshotDimension = Integer.MIN_VALUE;
    public static long lastWeakSnapshotRequestMillis;
    public static volatile NetworkManager connection;
    public static boolean subscribed;
    public static boolean subscriptionDenied;
    public static boolean subscribedWorldHotspots;
    public static long nextSubscriptionAttemptNanos;

    public static void setConnection(NetworkManager manager, boolean local) {
        connection = manager;
        subscribed = false;
        subscriptionDenied = false;
        subscribedWorldHotspots = false;
        nextSubscriptionAttemptNanos = 0L;
        if (local) {
            PeerChannels.setAvailable(manager, true);
        }
    }

    public static boolean isServerAvailable() {
        return FlameChunk.network != null && Mods.hasRemoteFlameChunk(connection);
    }

    public static void updateSubscription() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || !isServerAvailable()) {
            return;
        }
        boolean hasMapIntegration = ClientMapIntegrations.hasMapIntegration();
        boolean requested = ClientConfig.liveUpdates && hasMapIntegration && !subscriptionDenied;
        boolean worldHotspots = ClientConfig.worldOverlayEnabled && hasMapIntegration;
        if (System.nanoTime() < nextSubscriptionAttemptNanos) {
            return;
        }
        if (requested != subscribed || requested && worldHotspots != subscribedWorldHotspots) {
            FlameChunk.network.sendToServer(
                requested ? ScanRequestPacket.subscribeRequest(worldHotspots) : ScanRequestPacket.unsubscribeRequest());
            subscribed = requested;
            subscribedWorldHotspots = worldHotspots;
            nextSubscriptionAttemptNanos = System.nanoTime() + 1000000000L;
        }
    }

    public static void handleSubscriptionStatus(int status) {
        if (status == ScanProgressPacket.SUBSCRIPTION_DENIED) {
            subscribed = false;
            subscriptionDenied = true;
        } else if (status == ScanProgressPacket.SUBSCRIBED) {
            subscribed = true;
            subscriptionDenied = false;
        } else if (status == ScanProgressPacket.UNSUBSCRIBED) {
            subscribed = false;
        }
    }

    public static void retrySubscription() {
        subscribed = false;
        nextSubscriptionAttemptNanos = System.nanoTime() + 5000000000L;
    }

    public static void requestScan() {
        ClientSnapshotStorage storage = clientStorage();
        if (storage == null || storage.hasPendingScan() || FlameChunk.network == null) {
            return;
        }
        if (!isServerAvailable()) {
            storage.setScanStatus(ScanProgressPacket.SERVER_UNAVAILABLE);
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.isSingleplayer() && minecraft.currentScreen != null
            && minecraft.currentScreen.doesGuiPauseGame()) {
            storage.setScanStatus(ScanProgressPacket.LOCAL_GAME_PAUSED);
            return;
        }
        storage.setScanStatus(0);
        subscriptionDenied = false;
        try {
            FlameChunk.network.sendToServer(new ScanRequestPacket(ClientConfig.scanSeconds));
        } catch (RuntimeException exception) {
            storage.setScanStatus(-1);
            FlameChunk.LOG.warn("Unable to request a FlameChunk scan from the map screen", exception);
        }
    }

    public static void clear() {
        if (FlameChunk.proxy != null) {
            FlameChunk.proxy.handleClear(new ClearSnapshotPacket());
        }
    }

    public static void requestStop() {
        if (isServerAvailable() && Minecraft.getMinecraft().theWorld != null) {
            try {
                FlameChunk.network.sendToServer(ScanRequestPacket.stopScanRequest());
            } catch (RuntimeException exception) {
                FlameChunk.LOG.warn("Unable to stop a FlameChunk scan", exception);
            }
        }
    }

    public static void resetRequestState() {
        lastWeakSnapshotDimension = Integer.MIN_VALUE;
        lastWeakSnapshotRequestMillis = 0L;
    }

    public static void requestWeakSnapshot() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!isServerAvailable() || minecraft.theWorld == null) {
            return;
        }
        int dimensionId = minecraft.theWorld.provider.dimensionId;
        long now = System.currentTimeMillis();
        if (dimensionId == lastWeakSnapshotDimension && now - lastWeakSnapshotRequestMillis < 15000L) {
            return;
        }
        lastWeakSnapshotDimension = dimensionId;
        lastWeakSnapshotRequestMillis = now;
        try {
            FlameChunk.network.sendToServer(ScanRequestPacket.weakSnapshotRequest());
        } catch (RuntimeException exception) {
            lastWeakSnapshotDimension = Integer.MIN_VALUE;
            lastWeakSnapshotRequestMillis = 0L;
            FlameChunk.LOG.debug("Unable to request a FlameChunk weak chunk snapshot", exception);
        }
    }

    public static List<EntityTypeCount> weakClearTargets(int dimensionId, int chunkX, int chunkZ) {
        ClientSnapshotStorage storage = clientStorage();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (storage == null || minecraft.theWorld == null || minecraft.theWorld.provider.dimensionId != dimensionId) {
            return Collections.emptyList();
        }
        WeakChunkSnapshot snapshot = storage.getWeakSnapshot(dimensionId);
        if (snapshot == null) {
            return Collections.emptyList();
        }
        for (ChunkEntry chunk : snapshot.getChunks()) {
            if (chunk.getChunkX() == chunkX && chunk.getChunkZ() == chunkZ
                && chunk.getEntityCount() >= WeakChunkSnapshot.ENTITY_CLEAR_THRESHOLD) {
                ArrayList<EntityTypeCount> targets = new ArrayList<>();
                for (EntityTypeCount entityType : chunk.getEntityTypes()) {
                    if (entityType.getCount() >= WeakChunkSnapshot.ENTITY_CLEAR_THRESHOLD) {
                        targets.add(entityType);
                    }
                }
                return targets;
            }
        }
        return Collections.emptyList();
    }

    public static boolean hasLoaderControlTarget(int dimensionId, int chunkX, int chunkZ) {
        ClientSnapshotStorage storage = clientStorage();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (storage == null || minecraft.theWorld == null || minecraft.theWorld.provider.dimensionId != dimensionId) {
            return false;
        }
        ScanSnapshot snapshot = storage.getSnapshot();
        if (snapshot == null) {
            return false;
        }
        for (DimensionSnapshot dimension : snapshot.getDimensions()) {
            if (dimension.getDimensionId() != dimensionId) {
                continue;
            }
            for (ChunkSnapshot chunk : dimension.getChunks()) {
                if (chunk.getChunkX() == chunkX && chunk.getChunkZ() == chunkZ) {
                    return chunk.getTicketSourceCode() > 0;
                }
            }
        }
        return false;
    }

    public static void openWeakClearSelection(int dimensionId, int chunkX, int chunkZ) {
        openWeakClearSelection(dimensionId, chunkX, chunkZ, false);
    }

    public static void openWeakClearSelection(int dimensionId, int chunkX, int chunkZ, boolean showLoaderControl) {
        List<EntityTypeCount> targets = weakClearTargets(dimensionId, chunkX, chunkZ);
        if (!targets.isEmpty()) {
            Minecraft.getMinecraft()
                .displayGuiScreen(
                    new WeakEntitySelectionScreen(
                        Minecraft.getMinecraft().currentScreen,
                        dimensionId,
                        chunkX,
                        chunkZ,
                        targets,
                        showLoaderControl));
        }
    }

    public static void toggleScan() {
        if (isScanning()) {
            requestStop();
        } else if (!hasPendingScan()) {
            requestScan();
            reportMapScanFailure();
        }
    }

    private static void reportMapScanFailure() {
        ClientSnapshotStorage storage = clientStorage();
        if (storage == null) {
            return;
        }
        int status = storage.getStatus();
        if (status != ScanProgressPacket.LOCAL_GAME_PAUSED && status != ScanProgressPacket.SERVER_UNAVAILABLE) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.ingameGUI != null) {
            minecraft.ingameGUI.getChatGUI()
                .printChatMessage(new ChatComponentTranslation("flamechunk.client.scanStatus." + status));
        }
    }

    public static String scanMenuLabel() {
        if (isScanning()) {
            int percent = Math.round(scanProgress() * 100.0F);
            return StatCollector.translateToLocalFormatted("flamechunk.client.stop.progress", percent);
        }
        return StatCollector
            .translateToLocal(hasPendingScan() ? "flamechunk.client.scan.pending" : "flamechunk.client.scan");
    }

    public static void updateScanButton(Iterable<GuiButton> buttons, int buttonId) {
        if (buttons == null) {
            return;
        }
        boolean scanning = isScanning();
        boolean pending = hasPendingScan();
        String label = StatCollector.translateToLocal(
            scanning ? "flamechunk.client.stop"
                : pending ? "flamechunk.client.scan.pending" : "flamechunk.client.scan");
        for (GuiButton button : buttons) {
            if (button.id == buttonId) {
                button.displayString = label;
                button.enabled = scanning || !pending;
                return;
            }
        }
    }

    public static void confirmWeakClear(final GuiScreen parent, final int dimensionId, final int chunkX,
        final int chunkZ, final String entityType) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        String title = new ChatComponentTranslation("flamechunk.client.map.weakclear.title").getFormattedText();
        String message = new ChatComponentTranslation(
            "flamechunk.client.map.weakclear.confirm",
            entityType,
            chunkX,
            chunkZ).getFormattedText();
        minecraft.displayGuiScreen(new GuiYesNo((confirmed, id) -> {
            if (confirmed) {
                sendMapContextAction(MapContextActionPacket.WEAK_ENTITY_CLEAR, dimensionId, chunkX, chunkZ, entityType);
            }
            minecraft.displayGuiScreen(parent);
        }, title, message, 0));
    }

    public static void confirmLoaderToggle(final GuiScreen parent, final int dimensionId, final int chunkX,
        final int chunkZ) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        String title = new ChatComponentTranslation("flamechunk.client.map.loader.title").getFormattedText();
        String message = new ChatComponentTranslation("flamechunk.client.map.loader.confirm", chunkX, chunkZ)
            .getFormattedText();
        minecraft.displayGuiScreen(new GuiYesNo((confirmed, id) -> {
            if (confirmed) {
                sendMapContextAction(MapContextActionPacket.LOADER_TOGGLE, dimensionId, chunkX, chunkZ, "");
            }
            minecraft.displayGuiScreen(parent);
        }, title, message, 0));
    }

    public static boolean isScanning() {
        ClientSnapshotStorage storage = clientStorage();
        return storage != null && storage.isScanning();
    }

    public static boolean hasPendingScan() {
        ClientSnapshotStorage storage = clientStorage();
        return storage != null && storage.hasPendingScan();
    }

    public static float scanProgress() {
        ClientSnapshotStorage storage = clientStorage();
        return storage != null && storage.isScanning() ? storage.getProgress() : 0.0F;
    }

    public static ClientSnapshotStorage clientStorage() {
        return FlameChunk.proxy instanceof ClientProxy clientProxy ? clientProxy.getSnapshotStorage() : null;
    }

    public static void sendMapContextAction(int action, int dimensionId, int chunkX, int chunkZ, String entityType) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!isServerAvailable() || minecraft.theWorld == null
            || minecraft.theWorld.provider.dimensionId != dimensionId) {
            return;
        }
        try {
            FlameChunk.network
                .sendToServer(new MapContextActionPacket(action, dimensionId, chunkX, chunkZ, entityType));
        } catch (RuntimeException exception) {
            FlameChunk.LOG.debug("Unable to send a FlameChunk map context action", exception);
        }
    }
}
