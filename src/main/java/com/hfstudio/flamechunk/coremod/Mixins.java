package com.hfstudio.flamechunk.coremod;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import com.hfstudio.flamechunk.common.integration.Mods;

public enum Mixins implements IMixins {

    MINECRAFT(Side.COMMON, "MixinWorld", "MixinWorldServer", "MixinSpawnerAnimals", "MixinChunk",
        "MixinMinecraftServer", "MixinNetworkManager", "MixinForgeChunkManager", "ForgeChunkManagerAccessor",
        "MixinEventBus", "ASMEventHandlerAccessor"),
    XAERO_WORLD_MAP(new MixinBuilder("Xaero World Map heatmap overlay").setPhase(Phase.LATE)
        .addRequiredMod(Mods.XaeroWorldMap)
        .addClientMixins("xaero.MixinGuiMap")),
    XAERO_MINIMAP(new MixinBuilder("Xaero Minimap heatmap overlay").setPhase(Phase.LATE)
        .addRequiredMod(Mods.XaeroMinimap)
        .addClientMixins("xaero.MixinMinimapRenderer", "xaero.MixinLwjgl3ifyCompat")),
    JOURNEYMAP_5(new MixinBuilder("JourneyMap 5 heatmap overlay").setPhase(Phase.LATE)
        .addRequiredMod(Mods.JourneyMap5)
        .addClientMixins("journeymap.MixinFullscreen", "journeymap.MixinMiniMap")),
    JOURNEYMAP_6(new MixinBuilder("JourneyMap 6 heatmap controls").setPhase(Phase.LATE)
        .addRequiredMod(Mods.JourneyMap6)
        .addClientMixins("journeymap6.MixinFullscreen", "journeymap6.MixinPopupMenuEventHandler")),
    APPLIED_ENERGISTICS_2(new MixinBuilder("Applied Energistics 2 ME network profiling").setPhase(Phase.LATE)
        .addRequiredMod(Mods.AppliedEnergistics2)
        .addCommonMixins("ae2.MixinTickManagerCache", "ae2.MixinGrid", "ae2.MixinTickHandler")),;

    public final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    Mixins(Side side, String... mixins) {
        this.builder = new MixinBuilder().addSidedMixins(side, mixins)
            .setPhase(Phase.EARLY);
    }

    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
