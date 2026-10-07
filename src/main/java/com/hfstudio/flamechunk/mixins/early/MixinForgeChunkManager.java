package com.hfstudio.flamechunk.mixins.early;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.ForgeChunkManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.server.sampler.LoaderTicketControlService;

@Mixin(value = ForgeChunkManager.class, remap = false)
public class MixinForgeChunkManager {

    @Inject(method = "forceChunk", at = @At("HEAD"), cancellable = true, remap = false)
    private static void flamechunk$interceptFrozenChunk(ForgeChunkManager.Ticket ticket, ChunkCoordIntPair chunk,
        CallbackInfo callback) {
        if (LoaderTicketControlService.shouldBlock(ticket, chunk)) {
            callback.cancel();
        }
    }
}
