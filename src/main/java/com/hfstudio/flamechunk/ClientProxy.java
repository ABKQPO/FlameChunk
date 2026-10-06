package com.hfstudio.flamechunk;

import com.hfstudio.flamechunk.client.ClientController;
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
import net.minecraft.client.Minecraft;

public class ClientProxy extends CommonProxy {

    private final SnapshotCodec snapshotCodec = new SnapshotCodec();
    private final ZstdCompressionCodec compressionCodec = new ZstdCompressionCodec();
    private ClientSnapshotStorage snapshotStorage;
    private ClientController controller;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        snapshotStorage = new ClientSnapshotStorage();
        controller = new ClientController(snapshotStorage);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        ClientRegistry.registerKeyBinding(controller.getDiagnosticKey());
        FMLCommonHandler.instance().bus().register(controller);
    }

    @Override
    public void handleProgress(final ScanProgressPacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                snapshotStorage.updateProgress(packet.getElapsedTicks(), packet.getTotalTicks());
            }
        });
    }

    @Override
    public void handleSnapshot(SnapshotPacket packet) {
        try {
            byte[] compressed = packet.getCompressedBytes();
            byte[] encoded = compressionCodec.decompress(compressed, packet.getOriginalSize());
            final ScanSnapshot snapshot = snapshotCodec.decode(encoded, packet.getOriginalSize());
            Minecraft.getMinecraft().func_152344_a(new Runnable() {
                @Override
                public void run() {
                    snapshotStorage.publish(snapshot);
                }
            });
        } catch (RuntimeException exception) {
            FlameChunk.LOG.warn("Rejected FlameChunk snapshot payload", exception);
        }
    }

    @Override
    public void handleClear(final ClearSnapshotPacket packet) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {
            @Override
            public void run() {
                snapshotStorage.clear();
            }
        });
    }

    public ClientSnapshotStorage getSnapshotStorage() {
        return snapshotStorage;
    }
}
