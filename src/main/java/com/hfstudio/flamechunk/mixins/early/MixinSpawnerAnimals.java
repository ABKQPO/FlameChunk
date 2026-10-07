package com.hfstudio.flamechunk.mixins.early;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

@Mixin(SpawnerAnimals.class)
public abstract class MixinSpawnerAnimals {

    @Shadow
    protected static ChunkPosition func_151350_a(World world, int chunkX, int chunkZ) {
        throw new AssertionError();
    }

    @Unique
    private long flamechunk$spawnChunkStart;
    @Unique
    private int flamechunk$spawnChunkX;
    @Unique
    private int flamechunk$spawnChunkZ;

    @Inject(method = "findChunksForSpawning", at = @At("HEAD"))
    private void flamechunk$resetSpawnChunkTiming(WorldServer world, boolean hostile, boolean peaceful, boolean animals,
        CallbackInfoReturnable<Integer> cir) {
        flamechunk$spawnChunkStart = 0L;
    }

    @Redirect(
        method = "findChunksForSpawning",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/SpawnerAnimals;func_151350_a(Lnet/minecraft/world/World;II)Lnet/minecraft/world/ChunkPosition;"))
    private ChunkPosition flamechunk$startSpawnChunkTiming(World world, int chunkX, int chunkZ) {
        flamechunk$recordSpawnChunkTiming(world);
        flamechunk$spawnChunkStart = PerformanceSampler.beginTiming();
        flamechunk$spawnChunkX = chunkX;
        flamechunk$spawnChunkZ = chunkZ;
        return func_151350_a(world, chunkX, chunkZ);
    }

    @Inject(method = "findChunksForSpawning", at = @At("RETURN"))
    private void flamechunk$finishSpawnChunkTiming(WorldServer world, boolean hostile, boolean peaceful,
        boolean animals, CallbackInfoReturnable<Integer> cir) {
        flamechunk$recordSpawnChunkTiming(world);
    }

    @Unique
    private void flamechunk$recordSpawnChunkTiming(World world) {
        long start = flamechunk$spawnChunkStart;
        if (start == 0L) {
            return;
        }
        flamechunk$spawnChunkStart = 0L;
        long elapsedNanos = System.nanoTime() - start;
        PerformanceSampler
            .record(TickCategory.MOB_SPAWNING, world, flamechunk$spawnChunkX, flamechunk$spawnChunkZ, elapsedNanos);
        PerformanceSampler.recordGlobal(TickCategory.MOB_SPAWNING, world, elapsedNanos);
    }
}
