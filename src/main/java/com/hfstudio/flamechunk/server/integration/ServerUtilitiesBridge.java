package com.hfstudio.flamechunk.server.integration;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.integration.Mods;

public interface ServerUtilitiesBridge {

    ServerUtilitiesBridge NONE = new NoOpServerUtilitiesBridge();

    boolean isAvailable();

    default void registerPermissions() {}

    boolean hasPermission(EntityPlayerMP player, String permission);

    String describeClaim(World world, int chunkX, int chunkZ);

    default String describeTeam(String teamId) {
        return null;
    }

    static ServerUtilitiesBridge create() {
        if (!Mods.ServerUtilities.isModLoaded()) {
            return NONE;
        }
        try {
            return new ServerUtilitiesDirectBridge();
        } catch (LinkageError error) {
            FlameChunk.LOG.warn("ServerUtilities was found but its compatibility API is unavailable", error);
            return NONE;
        }
    }

    public static class NoOpServerUtilitiesBridge implements ServerUtilitiesBridge {

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public boolean hasPermission(EntityPlayerMP player, String permission) {
            return true;
        }

        @Override
        public String describeClaim(World world, int chunkX, int chunkZ) {
            return "unclaimed";
        }
    }
}
