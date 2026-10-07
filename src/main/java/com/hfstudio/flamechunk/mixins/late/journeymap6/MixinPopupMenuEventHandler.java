package com.hfstudio.flamechunk.mixins.late.journeymap6;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.client.integration.JourneyMap6Adapter;

import journeymap.api.v2.client.event.PopupMenuEvent.FullscreenPopupMenuEvent;
import journeymap.client.event.handlers.PopupMenuEventHandler;

@Mixin(value = PopupMenuEventHandler.class, remap = false)
public abstract class MixinPopupMenuEventHandler {

    @Inject(method = "onFullscreenPopupMenu", at = @At("RETURN"), remap = false)
    private void flamechunk$addMapActions(FullscreenPopupMenuEvent event, CallbackInfo callbackInfo) {
        JourneyMap6Adapter.addPopupMenuItems(event);
    }
}
