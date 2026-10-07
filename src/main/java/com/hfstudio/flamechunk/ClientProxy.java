package com.hfstudio.flamechunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.hfstudio.flamechunk.client.ClientController;
import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.config.ReportOutputMode;
import com.hfstudio.flamechunk.client.integration.ClientMapIntegrations;
import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.integration.MapOverlayModel;
import com.hfstudio.flamechunk.client.render.WorldPerformanceOverlay;
import com.hfstudio.flamechunk.client.storage.ClientSnapshotStorage;
import com.hfstudio.flamechunk.client.ui.DiagnosticScreen;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.ChunkTypeTiming;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ObjectHotspot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;
import com.hfstudio.flamechunk.common.network.SnapshotCodec;
import com.hfstudio.flamechunk.common.network.ZstdCompressionCodec;
import com.hfstudio.flamechunk.common.network.packet.ClearSnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanProgressPacket;
import com.hfstudio.flamechunk.common.network.packet.SnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.WeakChunkSnapshotPacket;
import com.hfstudio.flamechunk.common.tick.TickCategory;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import lombok.Getter;

public class ClientProxy extends CommonProxy {

    public final SnapshotCodec snapshotCodec = new SnapshotCodec();
    public final ZstdCompressionCodec compressionCodec = new ZstdCompressionCodec();
    @Getter
    public ClientSnapshotStorage snapshotStorage;
    public ClientController controller;
    public ClientMapIntegrations mapIntegrations;
    public WorldPerformanceOverlay worldPerformanceOverlay;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        try {
            ClientConfig.register();
        } catch (ConfigException exception) {
            throw new RuntimeException("Unable to register FlameChunk client configuration", exception);
        }
        snapshotStorage = new ClientSnapshotStorage();
        controller = new ClientController(snapshotStorage);
        mapIntegrations = new ClientMapIntegrations();
        mapIntegrations.initialize();
        worldPerformanceOverlay = new WorldPerformanceOverlay(snapshotStorage);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        ClientRegistry.registerKeyBinding(controller.getDiagnosticKey());
        FMLCommonHandler.instance()
            .bus()
            .register(controller);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(worldPerformanceOverlay);
    }

    @Override
    public void handleProgress(final ScanProgressPacket packet) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> snapshotStorage.updateProgress(packet.getElapsedTicks(), packet.getTotalTicks()));
    }

    @Override
    public void handleSnapshot(SnapshotPacket packet) {
        try {
            byte[] compressed = packet.getCompressedBytes();
            byte[] encoded = compressionCodec.decompress(compressed, packet.getOriginalSize());
            final ScanSnapshot snapshot = snapshotCodec.decode(encoded, packet.getOriginalSize());
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    snapshotStorage.publish(snapshot, packet.isFinalSnapshot());
                    mapIntegrations.publish(MapOverlayModel.from(snapshot, snapshotStorage.getWeakSnapshots()));
                    showReport(snapshot, packet.isFinalSnapshot());
                });
        } catch (RuntimeException exception) {
            FlameChunk.LOG.warn("Rejected FlameChunk snapshot payload", exception);
        }
    }

    @Override
    public void handleScanStatus(final int status) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                if (status >= ScanProgressPacket.SUBSCRIBED && status <= ScanProgressPacket.SUBSCRIPTION_DENIED) {
                    MapOverlayControls.handleSubscriptionStatus(status);
                    if (status != ScanProgressPacket.SUBSCRIPTION_DENIED) {
                        return;
                    }
                    snapshotStorage.setScanStatus(-1);
                } else {
                    snapshotStorage.setScanStatus(status);
                    if (status == ScanProgressPacket.PROTOCOL_MISMATCH) {
                        MapOverlayControls.subscriptionDenied = true;
                    } else if (status == ScanProgressPacket.QUEUE_FULL) {
                        MapOverlayControls.retrySubscription();
                    }
                }
                Minecraft.getMinecraft().ingameGUI.getChatGUI()
                    .printChatMessage(new ChatComponentTranslation("flamechunk.client.scanStatus." + status));
            });
    }

    @Override
    public void handleWeakChunkSnapshot(final WeakChunkSnapshotPacket packet) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                WeakChunkSnapshot snapshot = packet.getSnapshot();
                snapshotStorage.publishWeakSnapshot(snapshot);
                mapIntegrations
                    .publish(MapOverlayModel.from(snapshotStorage.getSnapshot(), snapshotStorage.getWeakSnapshots()));
            });
    }

    public void refreshOverlay(ScanSnapshot snapshot) {
        if (mapIntegrations != null) {
            mapIntegrations.publish(MapOverlayModel.from(snapshot, snapshotStorage.getWeakSnapshots()));
        }
    }

    @Override
    public void handleClear(final ClearSnapshotPacket packet) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                snapshotStorage.clear();
                mapIntegrations.publish(MapOverlayModel.from(null, snapshotStorage.getWeakSnapshots()));
            });
    }

    @SubscribeEvent
    public void onClientConnect(ClientConnectedToServerEvent event) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> MapOverlayControls.setConnection(event.manager, event.isLocal));
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MapOverlayControls.updateSubscription();
        }
    }

    @SubscribeEvent
    public void onClientDisconnect(ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                if (MapOverlayControls.connection == event.manager) {
                    MapOverlayControls.setConnection(null, false);
                    clearClientState();
                }
            });
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world != null && event.world.isRemote) {
            clearClientState();
        }
    }

    public void clearClientState() {
        MapOverlayControls.resetRequestState();
        if (snapshotStorage != null) {
            snapshotStorage.reset();
        }
        if (worldPerformanceOverlay != null) {
            worldPerformanceOverlay.clear();
        }
        if (mapIntegrations != null) {
            mapIntegrations.clear();
        }
    }

    public void showReport(ScanSnapshot snapshot, boolean finalSnapshot) {
        if (!finalSnapshot) {
            return;
        }
        ReportOutputMode outputMode = ClientConfig.reportOutputMode;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (outputMode != ReportOutputMode.CHAT && minecraft.currentScreen == null) {
            minecraft.displayGuiScreen(new DiagnosticScreen(snapshotStorage));
        }
        if (finalSnapshot && (outputMode == ReportOutputMode.CHAT || outputMode == ReportOutputMode.BOTH)) {
            printReport(snapshot);
        }
    }

    public void printReport(ScanSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.ingameGUI.getChatGUI()
            .printChatMessage(
                new ChatComponentTranslation(
                    "flamechunk.command.report.header",
                    snapshot.getDurationSeconds(),
                    snapshot.getSampledTicks()));
        DimensionSnapshot[] dimensions = snapshot.getDimensions();
        int dimensionLimit = Math.min(4, dimensions.length);
        for (int dimensionIndex = 0; dimensionIndex < dimensionLimit; dimensionIndex++) {
            DimensionSnapshot dimension = dimensions[dimensionIndex];
            minecraft.ingameGUI.getChatGUI()
                .printChatMessage(
                    new ChatComponentTranslation("flamechunk.command.report.dimension", dimension.getDimensionId()));
            long[] globalNanos = dimension.getGlobalNanos();
            for (TickCategory category : TickCategory.values()) {
                long nanos = globalNanos[category.ordinal()];
                if (nanos > 0L) {
                    minecraft.ingameGUI.getChatGUI()
                        .printChatMessage(
                            new ChatComponentTranslation(
                                "flamechunk.command.report.category",
                                StatCollector.translateToLocal(
                                    "flamechunk.category." + category.name()
                                        .toLowerCase(Locale.ENGLISH)),
                                nanos / 1000000.0D / Math.max(1L, snapshot.getSampledTicks())));
                }
            }
            List<ChunkTypeTiming> globalTypeTimings = dimension.getGlobalTypeTimings();
            int globalTypeLimit = Math.min(4, globalTypeTimings.size());
            for (int typeIndex = 0; typeIndex < globalTypeLimit; typeIndex++) {
                ChunkTypeTiming timing = globalTypeTimings.get(typeIndex);
                minecraft.ingameGUI.getChatGUI()
                    .printChatMessage(
                        new ChatComponentTranslation(
                            "flamechunk.command.report.hotspot",
                            StatCollector.translateToLocal(
                                "flamechunk.category." + timing.getCategory()
                                    .name()
                                    .toLowerCase(Locale.ENGLISH)),
                            timing.getTypeName(),
                            timing.getNanos() / 1000000.0D / Math.max(1L, snapshot.getSampledTicks()),
                            timing.getCount(),
                            timing.getPeakNanos() / 1000000.0D));
            }
            List<ChunkSnapshot> chunks = new ArrayList<>(dimension.getChunks());
            chunks.sort(
                Comparator.comparingLong(ChunkSnapshot::totalNanos)
                    .reversed());
            int shown = 0;
            for (ChunkSnapshot chunk : chunks) {
                if (chunk.totalNanos() <= 0L) {
                    continue;
                }
                minecraft.ingameGUI.getChatGUI()
                    .printChatMessage(
                        new ChatComponentTranslation(
                            "flamechunk.command.report.chunk",
                            chunk.getChunkX(),
                            chunk.getChunkZ(),
                            chunk.calculateMspt(snapshot.getSampledTicks()),
                            chunk.getTicketSource()
                                .length() == 0 ? "none" : chunk.getTicketSource()));
                int typeCount = 0;
                for (ChunkTypeTiming timing : chunk.getTypeTimings()) {
                    minecraft.ingameGUI.getChatGUI()
                        .printChatMessage(
                            new ChatComponentTranslation(
                                "flamechunk.command.report.hotspot",
                                StatCollector.translateToLocal(
                                    "flamechunk.category." + timing.getCategory()
                                        .name()
                                        .toLowerCase(Locale.ENGLISH)),
                                timing.getTypeName(),
                                timing.getNanos() / 1000000.0D / Math.max(1L, snapshot.getSampledTicks()),
                                timing.getCount(),
                                timing.getPeakNanos() / 1000000.0D));
                    if (++typeCount >= 2) {
                        break;
                    }
                }
                if (++shown >= 5) {
                    break;
                }
            }
            int objectLimit = Math.min(5, dimension.objectHotspots.size());
            for (int index = 0; index < objectLimit; index++) {
                ObjectHotspot hotspot = dimension.objectHotspots.get(index);
                minecraft.ingameGUI.getChatGUI()
                    .printChatMessage(
                        new ChatComponentTranslation(
                            "flamechunk.command.report.object",
                            StatCollector.translateToLocal(
                                "flamechunk.category." + hotspot.category.name()
                                    .toLowerCase(Locale.ENGLISH)),
                            hotspot.typeName,
                            hotspot.x,
                            hotspot.y,
                            hotspot.z,
                            hotspot.calculateMspt(snapshot.getSampledTicks()),
                            hotspot.peakNanos / 1000000.0D,
                            hotspot.count));
            }
        }
        if (dimensions.length > dimensionLimit) {
            minecraft.ingameGUI.getChatGUI()
                .printChatMessage(
                    new ChatComponentTranslation(
                        "flamechunk.client.report.truncatedDimensions",
                        dimensions.length - dimensionLimit));
        }
    }
}
