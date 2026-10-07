package com.hfstudio.flamechunk.mixins.early;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.ASMEventHandler;

@Mixin(value = ASMEventHandler.class, remap = false)
public interface ASMEventHandlerAccessor {

    @Accessor(value = "owner", remap = false)
    ModContainer flamechunk$getOwner();
}
