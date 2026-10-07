package com.hfstudio.flamechunk;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.hfstudio.flamechunk.client.ClientController;
import com.hfstudio.flamechunk.client.integration.ClientMapIntegrations;
import com.hfstudio.flamechunk.client.integration.MapOverlayModel;
import com.hfstudio.flamechunk.client.storage.ClientSnapshotStorage;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.network.SnapshotCodec;
import com.hfstudio.flamechunk.common.network.ZstdCompressionCodec;
import com.hfstudio.flamechunk.common.network.packet.ClearSnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanProgressPacket;
import com.hfstudio.flamechunk.common.network.packet.SnapshotPacket;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;
import lombok.Getter;

public class ClientProxy extends CommonProxy {

    private final SnapshotCodec snapshotCodec = new SnapshotCodec();
    private final ZstdCompressionCodec compressionCodec = new ZstdCompressionCodec();
    @Getter
    private ClientSnapshotStorage snapshotStorage;
    private ClientController controller;
    private ClientMapIntegrations mapIntegrations;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        snapshotStorage = new ClientSnapshotStorage();
        controller = new ClientController(snapshotStorage);
        mapIntegrations = new ClientMapIntegrations();
        mapIntegrations.initialize();
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
                    snapshotStorage.publish(snapshot);
                    mapIntegrations.publish(MapOverlayModel.from(snapshot));
                });
        } catch (RuntimeException exception) {
            FlameChunk.LOG.warn("Rejected FlameChunk snapshot payload", exception);
        }
    }

    @Override
    public void handleClear(final ClearSnapshotPacket packet) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> {
                snapshotStorage.clear();
                mapIntegrations.clear();
            });
    }

    @SubscribeEvent
    public void onClientDisconnect(ClientDisconnectionFromServerEvent event) {
        clearClientState();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world != null && event.world.isRemote) {
            clearClientState();
        }
    }

    private void clearClientState() {
        if (snapshotStorage != null) {
            snapshotStorage.reset();
        }
        if (mapIntegrations != null) {
            mapIntegrations.clear();
        }
    }
}
