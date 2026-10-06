package com.hfstudio.flamechunk.mixins.early;

import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

import net.minecraft.entity.Entity;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorld {

    private long flamechunk$randomStart;
    private long flamechunk$entityStart;
    private long flamechunk$updateStart;

    @Inject(method = "func_147467_a", at = @At("HEAD"))
    public void flamechunk$startRandom(int chunkX, int chunkZ, Chunk chunk, CallbackInfo callbackInfo) {
        flamechunk$randomStart = System.nanoTime();
    }

    @Inject(method = "func_147467_a", at = @At("RETURN"))
    public void flamechunk$finishRandom(int chunkX, int chunkZ, Chunk chunk, CallbackInfo callbackInfo) {
        PerformanceSampler.record(TickCategory.RANDOM_TICK, (World) (Object) this, chunkX, chunkZ,
                System.nanoTime() - flamechunk$randomStart);
    }

    @Inject(method = "updateEntities", at = @At("HEAD"))
    public void flamechunk$startUpdates(CallbackInfo callbackInfo) {
        flamechunk$updateStart = System.nanoTime();
    }

    @Inject(method = "updateEntities", at = @At("RETURN"))
    public void flamechunk$finishUpdates(CallbackInfo callbackInfo) {
        PerformanceSampler.recordGlobal(TickCategory.BLOCK_ENTITY, (World) (Object) this,
                System.nanoTime() - flamechunk$updateStart);
    }

    @Inject(method = "updateEntityWithOptionalForce", at = @At("HEAD"))
    public void flamechunk$startEntity(Entity entity, boolean force, CallbackInfo callbackInfo) {
        flamechunk$entityStart = System.nanoTime();
    }

    @Inject(method = "updateEntityWithOptionalForce", at = @At("RETURN"))
    public void flamechunk$finishEntity(Entity entity, boolean force, CallbackInfo callbackInfo) {
        if (entity != null) {
            PerformanceSampler.record(TickCategory.ENTITY, (World) (Object) this, entity.chunkCoordX,
                    entity.chunkCoordZ, System.nanoTime() - flamechunk$entityStart);
        }
    }
}
