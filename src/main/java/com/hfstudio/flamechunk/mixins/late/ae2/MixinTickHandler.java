package com.hfstudio.flamechunk.mixins.late.ae2;

import java.util.Collection;
import java.util.function.Predicate;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

import appeng.api.networking.crafting.ICraftingJob;
import appeng.hooks.TickHandler;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent;

@Mixin(value = TickHandler.class, remap = false)
public abstract class MixinTickHandler {

    @Redirect(
        method = "onTick",
        at = @At(value = "INVOKE", target = "Ljava/util/Collection;removeIf(Ljava/util/function/Predicate;)Z"),
        remap = false)
    private boolean flamechunk$measureCraftingSimulation(Collection<ICraftingJob> jobs,
        Predicate<ICraftingJob> finished, TickEvent event) {
        long start = PerformanceSampler.beginTiming();
        try {
            return jobs.removeIf(finished);
        } finally {
            if (start != 0L && event instanceof WorldTickEvent worldTickEvent) {
                World world = worldTickEvent.world;
                if (world != null && !world.isRemote) {
                    PerformanceSampler.recordGlobalObjectTiming(
                        TickCategory.AE2_NETWORK,
                        world,
                        "CraftingSimulation",
                        System.nanoTime() - start);
                }
            }
        }
    }
}
