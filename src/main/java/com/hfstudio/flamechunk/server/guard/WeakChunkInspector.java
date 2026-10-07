package com.hfstudio.flamechunk.server.guard;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.server.integration.ServerUtilitiesBridge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent;

public class WeakChunkInspector {

    private static final int MAX_INSPECTED_ENTITIES = 100000;

    private final ServerUtilitiesBridge serverUtilities;
    private long tickCounter;

    public WeakChunkInspector(ServerUtilitiesBridge serverUtilities) {
        this.serverUtilities = serverUtilities;
    }

    @SubscribeEvent
    public void onWorldTick(WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ServerConfig.weakChunkDiagnostics) {
            return;
        }
        World world = event.world;
        if (world == null || world.isRemote || ++tickCounter % ServerConfig.weakChunkCheckIntervalTicks != 0L) {
            return;
        }
        int loadedChunks = world.getChunkProvider()
            .getLoadedChunkCount();
        int weakEntities = 0;
        int inspected = 0;
        for (Object value : world.loadedEntityList) {
            if (++inspected > MAX_INSPECTED_ENTITIES) {
                break;
            }
            if (!(value instanceof Entity entity)) {
                continue;
            }
            if (!entity.isDead && !world.getChunkProvider()
                .chunkExists(entity.chunkCoordX, entity.chunkCoordZ)) {
                weakEntities++;
                if (weakEntities <= 8) {
                    FlameChunk.LOG.warn(
                        "Entity references a weakly loaded chunk in dimension {} at ({}, {}) ({})",
                        world.provider.dimensionId,
                        entity.chunkCoordX,
                        entity.chunkCoordZ,
                        serverUtilities.describeClaim(world, entity.chunkCoordX, entity.chunkCoordZ));
                }
            }
        }
        if (loadedChunks <= ServerConfig.weakChunkMinimum || weakEntities > 0) {
            FlameChunk.LOG.info(
                "Weak chunk diagnostic for dimension {}: {} loaded chunks, {} weak entity references",
                world.provider.dimensionId,
                loadedChunks,
                weakEntities);
        }
    }
}
