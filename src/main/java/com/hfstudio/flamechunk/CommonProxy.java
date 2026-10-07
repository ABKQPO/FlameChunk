package com.hfstudio.flamechunk;

import com.hfstudio.flamechunk.common.network.packet.ClearSnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanProgressPacket;
import com.hfstudio.flamechunk.common.network.packet.SnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.WeakChunkSnapshotPacket;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {}

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void completeInit(FMLLoadCompleteEvent event) {}

    public void handleProgress(ScanProgressPacket packet) {}

    public void handleScanStatus(int status) {}

    public void handleWeakChunkSnapshot(WeakChunkSnapshotPacket packet) {}

    public void handleSnapshot(SnapshotPacket packet) {}

    public void handleClear(ClearSnapshotPacket packet) {}
}
