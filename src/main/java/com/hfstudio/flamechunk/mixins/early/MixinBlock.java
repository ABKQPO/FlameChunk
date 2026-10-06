package com.hfstudio.flamechunk.mixins.early;

import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class MixinBlock {

    private final ThreadLocal<Long> flamechunk$neighborStart = new ThreadLocal<Long>();

    @Inject(method = "onNeighborBlockChange", at = @At("HEAD"))
    public void flamechunk$startNeighbor(World world, int x, int y, int z, Block neighbor, CallbackInfo callbackInfo) {
        flamechunk$neighborStart.set(System.nanoTime());
    }

    @Inject(method = "onNeighborBlockChange", at = @At("RETURN"))
    public void flamechunk$finishNeighbor(World world, int x, int y, int z, Block neighbor, CallbackInfo callbackInfo) {
        Long start = flamechunk$neighborStart.get();
        flamechunk$neighborStart.remove();
        if (start != null) {
            PerformanceSampler.record(TickCategory.BLOCK_UPDATE, world, x >> 4, z >> 4, System.nanoTime() - start);
        }
    }
}
