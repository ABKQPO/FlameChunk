package com.hfstudio.flamechunk.mixins.early;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

@Mixin(World.class)
public abstract class MixinWorld {

    @Unique
    private static final ThreadLocal<Deque<Long>> flamechunk$entityStarts = new ThreadLocal<>();

    @Redirect(
        method = "updateEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;updateEntity()V"))
    public void flamechunk$measureTileEntity(TileEntity tileEntity) {
        long start = PerformanceSampler.beginTiming();
        try {
            tileEntity.updateEntity();
        } finally {
            if (start != 0L) {
                World world = (World) (Object) this;
                PerformanceSampler.record(
                    TickCategory.BLOCK_ENTITY,
                    world,
                    tileEntity.xCoord >> 4,
                    tileEntity.zCoord >> 4,
                    System.nanoTime() - start);
            }
        }
    }

    @Inject(method = "updateEntityWithOptionalForce", at = @At("HEAD"))
    public void flamechunk$startEntity(Entity entity, boolean force, CallbackInfo callbackInfo) {
        long start = PerformanceSampler.beginTiming();
        if (start != 0L) {
            Deque<Long> starts = flamechunk$entityStarts.get();
            if (starts == null) {
                starts = new ArrayDeque<>();
                flamechunk$entityStarts.set(starts);
            }
            starts.push(start);
        }
    }

    @Inject(method = "updateEntityWithOptionalForce", at = @At("RETURN"))
    public void flamechunk$finishEntity(Entity entity, boolean force, CallbackInfo callbackInfo) {
        Deque<Long> starts = flamechunk$entityStarts.get();
        if (starts == null || starts.isEmpty()) {
            return;
        }
        long start = starts.pop();
        if (starts.isEmpty()) {
            flamechunk$entityStarts.remove();
        }
        if (entity != null) {
            PerformanceSampler.record(
                TickCategory.ENTITY,
                (World) (Object) this,
                entity.chunkCoordX,
                entity.chunkCoordZ,
                System.nanoTime() - start);
        }
    }
}
