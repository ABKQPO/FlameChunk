package com.hfstudio.flamechunk.coremod;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;
import com.hfstudio.flamechunk.common.integration.Mods;

public enum Mixins implements IMixins {

    MINECRAFT(Side.COMMON, "MixinWorld", "MixinWorldServer", "MixinChunk", "MixinMinecraftServer",
        "MixinForgeChunkManager", "ForgeChunkManagerAccessor", "MixinEventBus", "ASMEventHandlerAccessor"),
    XAERO_WORLD_MAP(new MixinBuilder("Xaero World Map heatmap overlay").setPhase(Phase.LATE)
        .addRequiredMod(Mods.XaeroWorldMap)
        .addClientMixins("xaero.MixinGuiMap")),
    XAERO_MINIMAP(new MixinBuilder("Xaero Minimap heatmap overlay").setPhase(Phase.LATE)
        .addRequiredMod(Mods.XaeroMinimap)
        .addClientMixins("xaero.MixinMinimapProcessor")),
    JOURNEYMAP_5(new MixinBuilder("JourneyMap 5 heatmap overlay").setPhase(Phase.LATE)
        .addRequiredMod(Mods.JourneyMap5)
        .setApplyIf(Mods.JourneyMap5::isModLoaded)
        .addClientMixins("journeymap.MixinFullscreen")),;

    private final MixinBuilder builder;

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
