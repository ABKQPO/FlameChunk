package com.hfstudio.flamechunk.server.guard;

import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.server.integration.ServerUtilitiesBridge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public class EntityLoadGuard {

    private static final int MAX_TRACKED_CHUNKS = 8192;

    private final ServerUtilitiesBridge serverUtilities;
    private long tickCounter;

    public EntityLoadGuard(ServerUtilitiesBridge serverUtilities) {
        this.serverUtilities = serverUtilities;
    }

    public static void checkChunk(Chunk chunk) {
        if (!ServerConfig.entityLoadProtection || chunk == null || chunk.worldObj == null || chunk.worldObj.isRemote) {
            return;
        }
        int entityCount = 0;
        int droppableCount = 0;
        for (List<?> section : chunk.entityLists) {
            for (Object value : section) {
                if (!(value instanceof Entity) || ((Entity) value).isDead) {
                    continue;
                }
                entityCount++;
                if (isDroppable((Entity) value)) {
                    droppableCount++;
                }
            }
        }
        if (entityCount <= ServerConfig.entityProtectionThreshold) {
            return;
        }
        int excess = entityCount - ServerConfig.entityProtectionThreshold;
        int removable = Math.max(0, droppableCount - ServerConfig.entityProtectionRetainedDrops);
        int target = Math.min(excess, removable);
        int removed = 0;
        if (target > 0) {
            for (List<?> section : chunk.entityLists) {
                Iterator<?> iterator = section.iterator();
                while (iterator.hasNext() && removed < target) {
                    Object value = iterator.next();
                    if (value instanceof Entity && isDroppable((Entity) value) && !((Entity) value).isDead) {
                        ((Entity) value).setDead();
                        iterator.remove();
                        removed++;
                    }
                }
                if (removed >= target) {
                    break;
                }
            }
        }
        FlameChunk.LOG.warn(
            "Entity load protection inspected dimension {} chunk ({}, {}): {} entities, removed {} droppable entities ({})",
            chunk.worldObj.provider.dimensionId,
            chunk.xPosition,
            chunk.zPosition,
            entityCount,
            removed,
            "item and experience entities only");
    }

    @SubscribeEvent
    public void onWorldTick(WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ServerConfig.entityLoadDiagnostics) {
            return;
        }
        World world = event.world;
        if (world == null || world.isRemote
            || ++tickCounter % Math.max(20, ServerConfig.entityWarningIntervalSeconds * 20L) != 0L) {
            return;
        }
        Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
        counts.defaultReturnValue(0);
        for (Object value : world.loadedEntityList) {
            if (!(value instanceof Entity entity)) {
                continue;
            }
            if (entity.isDead) {
                continue;
            }
            long key = pack(entity.chunkCoordX, entity.chunkCoordZ);
            int count = counts.get(key);
            if (count == 0 && counts.size() >= MAX_TRACKED_CHUNKS) {
                continue;
            }
            counts.put(key, count + 1);
        }
        int warnings = 0;
        for (Long2IntMap.Entry entry : counts.long2IntEntrySet()) {
            if (entry.getIntValue() < ServerConfig.entityWarningThreshold) {
                continue;
            }
            int chunkX = unpackX(entry.getLongKey());
            int chunkZ = unpackZ(entry.getLongKey());
            FlameChunk.LOG.warn(
                "High entity load in dimension {} chunk ({}, {}): {} entities ({})",
                world.provider.dimensionId,
                chunkX,
                chunkZ,
                entry.getIntValue(),
                serverUtilities.describeClaim(world, chunkX, chunkZ));
            if (++warnings >= 8) {
                break;
            }
        }
    }

    private static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }

    private static boolean isDroppable(Entity entity) {
        return entity instanceof EntityItem || entity instanceof EntityXPOrb;
    }
}
