package com.hfstudio.flamechunk.mixins.early;

import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.server.guard.EntityLoadGuard;

@Mixin(Chunk.class)
public abstract class MixinChunk {

    @Inject(method = "onChunkLoad", at = @At("RETURN"))
    public void flamechunk$checkEntityLoad(CallbackInfo callbackInfo) {
        EntityLoadGuard.checkChunk((Chunk) (Object) this);
    }
}
