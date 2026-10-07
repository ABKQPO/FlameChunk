package com.hfstudio.flamechunk.mixins.early;

import java.util.Map;

import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.google.common.collect.Multimap;

@Mixin(value = ForgeChunkManager.class, remap = false)
public interface ForgeChunkManagerAccessor {

    @Accessor(value = "tickets", remap = false)
    static Map<World, Multimap<String, ForgeChunkManager.Ticket>> flamechunk$getTickets() {
        throw new AssertionError();
    }
}
