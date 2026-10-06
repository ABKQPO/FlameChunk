package com.hfstudio.flamechunk.mixins.early;

import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

import net.minecraft.world.WorldServer;
import net.minecraft.block.BlockEventData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldServer.class)
public abstract class MixinWorldServer {

    private long flamechunk$tickStart;
    private long flamechunk$scheduledStart;
    private long flamechunk$eventStart;

    @Inject(method = "tick", at = @At("HEAD"))
    public void flamechunk$startTick(CallbackInfo callbackInfo) {
        flamechunk$tickStart = System.nanoTime();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    public void flamechunk$finishTick(CallbackInfo callbackInfo) {
        PerformanceSampler.recordGlobal(TickCategory.MOB_SPAWNING, (WorldServer) (Object) this,
                System.nanoTime() - flamechunk$tickStart);
    }

    @Inject(method = "tickUpdates", at = @At("HEAD"))
    public void flamechunk$startScheduled(boolean runAll, CallbackInfo callbackInfo) {
        flamechunk$scheduledStart = System.nanoTime();
    }

    @Inject(method = "tickUpdates", at = @At("RETURN"))
    public void flamechunk$finishScheduled(boolean runAll, CallbackInfoReturnable<Boolean> callbackInfo) {
        PerformanceSampler.recordGlobal(TickCategory.SCHEDULED_TICK, (WorldServer) (Object) this,
                System.nanoTime() - flamechunk$scheduledStart);
    }

    @Inject(method = "func_147485_a", at = @At("HEAD"))
    public void flamechunk$startEvent(BlockEventData event, CallbackInfo callbackInfo) {
        flamechunk$eventStart = System.nanoTime();
    }

    @Inject(method = "func_147485_a", at = @At("RETURN"))
    public void flamechunk$finishEvent(BlockEventData event, CallbackInfoReturnable<Boolean> callbackInfo) {
        PerformanceSampler.recordGlobal(TickCategory.BLOCK_EVENT, (WorldServer) (Object) this,
                System.nanoTime() - flamechunk$eventStart);
    }
}
