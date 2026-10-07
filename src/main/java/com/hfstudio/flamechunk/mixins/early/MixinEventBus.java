package com.hfstudio.flamechunk.mixins.early;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hfstudio.flamechunk.common.tick.TickCategory;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.eventhandler.IEventListener;
import cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent;

@Mixin(value = EventBus.class, remap = false)
public abstract class MixinEventBus {

    @Redirect(
        method = "post",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/eventhandler/IEventListener;invoke(Lcpw/mods/fml/common/eventhandler/Event;)V"))
    public void flamechunk$measureEventHandler(IEventListener listener, Event event) {
        if (!PerformanceSampler.isActive()) {
            listener.invoke(event);
            return;
        }
        World world = null;
        if (event instanceof WorldEvent worldEvent) {
            world = worldEvent.world;
        } else if (event instanceof WorldTickEvent worldTickEvent) {
            world = worldTickEvent.world;
        } else if (event instanceof EntityEvent entityEvent) {
            Entity entity = entityEvent.entity;
            world = entity == null ? null : entity.worldObj;
        }
        long start = world == null || world.isRemote ? 0L : PerformanceSampler.beginTiming();
        if (world != null && world.isRemote) {
            listener.invoke(event);
            return;
        }
        String handlerName = PerformanceSampler.workTypeName(listener.getClass());
        if (listener instanceof ASMEventHandlerAccessor accessor) {
            ModContainer owner = accessor.flamechunk$getOwner();
            if (owner != null && owner.getModId() != null
                && owner.getModId()
                    .length() > 0) {
                handlerName = owner.getModId();
            }
        }
        int work = PerformanceSampler.enterWork(TickCategory.HANDLER, world, handlerName);
        try {
            listener.invoke(event);
        } finally {
            PerformanceSampler.leaveWork(work);
            if (start != 0) {
                PerformanceSampler
                    .recordGlobalObjectTiming(TickCategory.HANDLER, world, handlerName, System.nanoTime() - start);
            }
        }
    }
}
