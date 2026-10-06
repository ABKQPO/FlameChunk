package com.hfstudio.flamechunk.coremod;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Mixins implements IMixins {

    MINECRAFT(Side.COMMON, "MixinWorld", "MixinWorldServer", "MixinBlock"),
    ;

    @Getter
    private final MixinBuilder builder;

    Mixins(Side side, String... mixins) {
        this.builder = new MixinBuilder().addSidedMixins(side, mixins).setPhase(Phase.EARLY);
    }
}
