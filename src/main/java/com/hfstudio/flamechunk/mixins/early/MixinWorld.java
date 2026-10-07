package com.hfstudio.flamechunk.mixins.early;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

@Mixin(World.class)
public abstract class MixinWorld {

    @Unique
    private static final ThreadLocal<Deque<Long>> flamechunk$neighborStarts = new ThreadLocal<>();

    @Redirect(
        method = "notifyBlockOfNeighborChange",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;onNeighborBlockChange(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;)V"))
    public void flamechunk$measureNeighborUpdate(Block block, World world, int x, int y, int z, Block neighbor) {
        long start = world == null || world.isRemote ? 0L : PerformanceSampler.beginTiming();
        Deque<Long> starts = null;
        if (start != 0L) {
            starts = flamechunk$neighborStarts.get();
            if (starts == null) {
                starts = new ArrayDeque<>();
                flamechunk$neighborStarts.set(starts);
            }
            starts.push(starts.isEmpty() ? start : 0L);
        }
        try {
            block.onNeighborBlockChange(world, x, y, z, neighbor);
        } finally {
            if (starts != null) {
                long outerStart = starts.pop();
                if (starts.isEmpty()) {
                    flamechunk$neighborStarts.remove();
                }
                if (outerStart != 0L) {
                    PerformanceSampler.recordBlockTiming(
                        TickCategory.BLOCK_UPDATE,
                        world,
                        x,
                        y,
                        z,
                        block.getClass()
                            .getSimpleName(),
                        System.nanoTime() - outerStart);
                }
            }
        }
    }

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
                PerformanceSampler.recordTileEntityTiming(world, tileEntity, System.nanoTime() - start);
            }
        }
    }

    @Redirect(
        method = "updateEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;updateEntity(Lnet/minecraft/entity/Entity;)V"))
    public void flamechunk$measureEntity(World world, Entity entity) {
        long start = PerformanceSampler.beginTiming();
        try {
            world.updateEntity(entity);
        } finally {
            if (start != 0L && entity != null) {
                PerformanceSampler.recordEntityTiming(world, entity, System.nanoTime() - start);
            }
        }
    }
}
