package com.hfstudio.flamechunk.server.integration;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.hfstudio.flamechunk.FlameChunk;

import cpw.mods.fml.common.Optional;
import serverutils.data.ClaimedChunk;
import serverutils.data.ClaimedChunks;
import serverutils.lib.math.ChunkDimPos;
import serverutils.lib.util.permission.DefaultPermissionLevel;
import serverutils.lib.util.permission.PermissionAPI;

public class ServerUtilitiesDirectBridge implements ServerUtilitiesBridge {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    @Optional.Method(modid = "serverutilities")
    public void registerPermissions() {
        PermissionAPI.registerNode("flamechunk.scan", DefaultPermissionLevel.OP, "Allow FlameChunk performance scans");
        PermissionAPI.registerNode(
            "flamechunk.weakclear",
            DefaultPermissionLevel.OP,
            "Allow clearing entities from weakly loaded chunks");
        PermissionAPI.registerNode(
            "flamechunk.loadercontrol",
            DefaultPermissionLevel.OP,
            "Allow inspection and control of chunk loader tickets");
    }

    @Override
    @Optional.Method(modid = "serverutilities")
    public boolean hasPermission(EntityPlayerMP player, String permission) {
        if (player == null) {
            return true;
        }
        try {
            return PermissionAPI.hasPermission(player, permission);
        } catch (RuntimeException exception) {
            FlameChunk.LOG.warn("ServerUtilities permission lookup failed", exception);
            return false;
        } catch (LinkageError error) {
            FlameChunk.LOG.warn("ServerUtilities permission API is incompatible", error);
            return false;
        }
    }

    @Override
    @Optional.Method(modid = "serverutilities")
    public String describeClaim(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return "unclaimed";
        }
        try {
            if (!ClaimedChunks.isActive() || ClaimedChunks.instance == null) {
                return "unclaimed";
            }
            ChunkDimPos position = new ChunkDimPos(chunkX, chunkZ, world.provider.dimensionId);
            ClaimedChunk claim = ClaimedChunks.instance.getChunk(position);
            if (claim == null) {
                return "unclaimed";
            }
            return claim.getTeam() == null ? "claimed" : "claimed by " + claim.getTeam();
        } catch (RuntimeException exception) {
            return "unknown";
        } catch (LinkageError error) {
            FlameChunk.LOG.warn("ServerUtilities claim API is incompatible", error);
            return "unknown";
        }
    }
}
