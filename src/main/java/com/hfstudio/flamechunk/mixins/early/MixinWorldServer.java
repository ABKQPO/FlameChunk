package com.hfstudio.flamechunk.mixins.early;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

@Mixin(WorldServer.class)
public abstract class MixinWorldServer {

    @Redirect(
        method = "func_147456_g",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;updateTick(Lnet/minecraft/world/World;IIILjava/util/Random;)V"))
    public void flamechunk$measureRandomTick(Block block, World world, int x, int y, int z, Random random) {
        long start = PerformanceSampler.beginTiming();
        try {
            block.updateTick(world, x, y, z, random);
        } finally {
            if (start != 0L) {
                PerformanceSampler.record(TickCategory.RANDOM_TICK, world, x >> 4, z >> 4, System.nanoTime() - start);
            }
        }
    }

    @Redirect(
        method = "tickUpdates",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;updateTick(Lnet/minecraft/world/World;IIILjava/util/Random;)V"))
    public void flamechunk$measureScheduledTick(Block block, World world, int x, int y, int z, Random random) {
        long start = PerformanceSampler.beginTiming();
        try {
            block.updateTick(world, x, y, z, random);
        } finally {
            if (start != 0L) {
                PerformanceSampler
                    .record(TickCategory.SCHEDULED_TICK, world, x >> 4, z >> 4, System.nanoTime() - start);
            }
        }
    }

    @Redirect(
        method = "func_147485_a",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;onBlockEventReceived(Lnet/minecraft/world/World;IIIII)Z"))
    public boolean flamechunk$measureBlockEvent(Block block, World world, int x, int y, int z, int eventId,
        int eventData) {
        long start = PerformanceSampler.beginTiming();
        try {
            return block.onBlockEventReceived(world, x, y, z, eventId, eventData);
        } finally {
            if (start != 0L) {
                PerformanceSampler.record(TickCategory.BLOCK_EVENT, world, x >> 4, z >> 4, System.nanoTime() - start);
            }
        }
    }

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/SpawnerAnimals;findChunksForSpawning(Lnet/minecraft/world/WorldServer;ZZZ)I"))
    public int flamechunk$measureMobSpawning(SpawnerAnimals spawner, WorldServer world, boolean hostile,
        boolean peaceful, boolean animals) {
        long start = PerformanceSampler.beginTiming();
        try {
            return spawner.findChunksForSpawning(world, hostile, peaceful, animals);
        } finally {
            if (start != 0L) {
                PerformanceSampler.recordGlobal(TickCategory.MOB_SPAWNING, world, System.nanoTime() - start);
            }
        }
    }
}
