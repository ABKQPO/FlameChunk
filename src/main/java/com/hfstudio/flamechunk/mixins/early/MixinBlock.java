package com.hfstudio.flamechunk.mixins.early;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

@Mixin(Block.class)
public abstract class MixinBlock {

    @Unique
    private static final ThreadLocal<Deque<Long>> flamechunk$neighborStarts = new ThreadLocal<>();

    @Inject(method = "onNeighborBlockChange", at = @At("HEAD"))
    public void flamechunk$startNeighbor(World world, int x, int y, int z, Block neighbor, CallbackInfo callbackInfo) {
        long start = PerformanceSampler.beginTiming();
        if (start != 0L && world != null && !world.isRemote) {
            Deque<Long> starts = flamechunk$neighborStarts.get();
            if (starts == null) {
                starts = new ArrayDeque<>();
                flamechunk$neighborStarts.set(starts);
            }
            starts.push(start);
        }
    }

    @Inject(method = "onNeighborBlockChange", at = @At("RETURN"))
    public void flamechunk$finishNeighbor(World world, int x, int y, int z, Block neighbor, CallbackInfo callbackInfo) {
        Deque<Long> starts = flamechunk$neighborStarts.get();
        if (starts == null || starts.isEmpty()) {
            return;
        }
        long start = starts.pop();
        if (starts.isEmpty()) {
            flamechunk$neighborStarts.remove();
        }
        if (world != null && !world.isRemote) {
            PerformanceSampler.record(TickCategory.BLOCK_UPDATE, world, x >> 4, z >> 4, System.nanoTime() - start);
        }
    }
}
