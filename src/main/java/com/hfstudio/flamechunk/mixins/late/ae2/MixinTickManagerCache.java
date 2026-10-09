package com.hfstudio.flamechunk.mixins.late.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hfstudio.flamechunk.server.integration.AppliedEnergisticsBridge;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.me.cache.TickManagerCache;

@Mixin(value = TickManagerCache.class, remap = false)
public abstract class MixinTickManagerCache {

    @Redirect(
        method = "onUpdateTick",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/networking/ticking/IGridTickable;tickingRequest(Lappeng/api/networking/IGridNode;I)Lappeng/api/networking/ticking/TickRateModulation;"),
        remap = false)
    private TickRateModulation flamechunk$measureMachineTick(IGridTickable tickable, IGridNode node,
        int ticksSinceLastCall) {
        long start = AppliedEnergisticsBridge.begin();
        int work = start == 0L ? 0 : AppliedEnergisticsBridge.enterWork(node, tickable);
        try {
            return tickable.tickingRequest(node, ticksSinceLastCall);
        } finally {
            AppliedEnergisticsBridge.leaveWork(work);
            AppliedEnergisticsBridge.recordMachineTick(node, tickable, start);
        }
    }
}
