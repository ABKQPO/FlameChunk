package com.hfstudio.flamechunk.mixins.late.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hfstudio.flamechunk.server.integration.AppliedEnergisticsBridge;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

import appeng.api.networking.IGridCache;
import appeng.api.networking.IGridNode;
import appeng.me.Grid;
import appeng.me.cache.TickManagerCache;

@Mixin(value = Grid.class, remap = false)
public abstract class MixinGrid {

    @Shadow(remap = false)
    public abstract IGridNode getPivot();

    @Redirect(
        method = "update",
        at = @At(value = "INVOKE", target = "Lappeng/api/networking/IGridCache;onUpdateTick()V"),
        remap = false)
    private void flamechunk$measureCacheTick(IGridCache cache) {
        if (cache instanceof TickManagerCache) {
            // Its machine ticks are already measured individually by MixinTickManagerCache.
            cache.onUpdateTick();
            return;
        }
        long start = AppliedEnergisticsBridge.begin();
        int work = start == 0L ? 0 : AppliedEnergisticsBridge.enterWork(getPivot(), cache);
        try {
            cache.onUpdateTick();
        } finally {
            AppliedEnergisticsBridge.leaveWork(work);
            if (start != 0L) {
                AppliedEnergisticsBridge
                    .recordGridTick(getPivot(), PerformanceSampler.workTypeName(cache.getClass()), start);
            }
        }
    }
}
