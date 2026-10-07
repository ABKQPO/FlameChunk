package com.hfstudio.flamechunk.client.integration;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.network.packet.ClearSnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanRequestPacket;

public class MapOverlayControls {

    public static void requestScan() {
        if (FlameChunk.network != null) {
            FlameChunk.network.sendToServer(new ScanRequestPacket(ServerConfig.scanSeconds));
        }
    }

    public static void clear() {
        if (FlameChunk.proxy != null) {
            FlameChunk.proxy.handleClear(new ClearSnapshotPacket());
        }
    }
}
