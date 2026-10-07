package com.hfstudio.flamechunk.common.network;

import com.hfstudio.flamechunk.common.network.packet.ClearSnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.MapContextActionPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanProgressPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanRequestPacket;
import com.hfstudio.flamechunk.common.network.packet.SnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.WeakChunkSnapshotPacket;

import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class NetworkHandler {

    public static final int PROTOCOL_MAGIC = 0x46434B34;

    public static void register(SimpleNetworkWrapper network) {
        network.registerMessage(ScanRequestHandler.class, ScanRequestPacket.class, 0, Side.SERVER);
        network.registerMessage(ScanProgressHandler.class, ScanProgressPacket.class, 1, Side.CLIENT);
        network.registerMessage(SnapshotHandler.class, SnapshotPacket.class, 2, Side.CLIENT);
        network.registerMessage(ClearSnapshotHandler.class, ClearSnapshotPacket.class, 3, Side.CLIENT);
        network.registerMessage(WeakChunkSnapshotHandler.class, WeakChunkSnapshotPacket.class, 5, Side.CLIENT);
        network.registerMessage(MapContextActionHandler.class, MapContextActionPacket.class, 6, Side.SERVER);
    }
}
