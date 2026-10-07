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
    private void flamechunk$measureRandomTick(Block block, World world, int x, int y, int z, Random random) {
        long start = PerformanceSampler.beginTiming();
        int work = start == 0 ? 0
            : PerformanceSampler
                .enterWork(TickCategory.RANDOM_TICK, world, PerformanceSampler.workTypeName(block.getClass()));
        try {
            block.updateTick(world, x, y, z, random);
        } finally {
            PerformanceSampler.leaveWork(work);
            if (start != 0L) {
                PerformanceSampler.recordBlockTiming(
                    TickCategory.RANDOM_TICK,
                    world,
                    x,
                    y,
                    z,
                    block.getClass()
                        .getSimpleName(),
                    System.nanoTime() - start);
            }
        }
    }

    @Redirect(
        method = "tickUpdates",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;updateTick(Lnet/minecraft/world/World;IIILjava/util/Random;)V"))
    private void flamechunk$measureScheduledTick(Block block, World world, int x, int y, int z, Random random) {
        long start = PerformanceSampler.beginTiming();
        int work = start == 0 ? 0
            : PerformanceSampler
                .enterWork(TickCategory.SCHEDULED_TICK, world, PerformanceSampler.workTypeName(block.getClass()));
        try {
            block.updateTick(world, x, y, z, random);
        } finally {
            PerformanceSampler.leaveWork(work);
            if (start != 0L) {
                PerformanceSampler.recordBlockTiming(
                    TickCategory.SCHEDULED_TICK,
                    world,
                    x,
                    y,
                    z,
                    block.getClass()
                        .getSimpleName(),
                    System.nanoTime() - start);
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
        int work = start == 0 ? 0
            : PerformanceSampler
                .enterWork(TickCategory.BLOCK_EVENT, world, PerformanceSampler.workTypeName(block.getClass()));
        try {
            return block.onBlockEventReceived(world, x, y, z, eventId, eventData);
        } finally {
            PerformanceSampler.leaveWork(work);
            if (start != 0L) {
                PerformanceSampler.recordBlockTiming(
                    TickCategory.BLOCK_EVENT,
                    world,
                    x,
                    y,
                    z,
                    block.getClass()
                        .getSimpleName(),
                    System.nanoTime() - start);
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
        int work = start == 0 ? 0
            : PerformanceSampler
                .enterWork(TickCategory.MOB_SPAWNING, world, PerformanceSampler.workTypeName(spawner.getClass()));
        try {
            return spawner.findChunksForSpawning(world, hostile, peaceful, animals);
        } finally {
            PerformanceSampler.leaveWork(work);
            if (start != 0L) {
                PerformanceSampler.recordGlobal(TickCategory.MOB_SPAWNING, world, System.nanoTime() - start);
            }
        }
    }
}
