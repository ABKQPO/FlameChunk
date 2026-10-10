package com.hfstudio.flamechunk.server.guard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.server.command.ServerMessages;
import com.hfstudio.flamechunk.server.integration.ServerUtilitiesBridge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class EntityLoadGuard {

    public static final int MAX_TRACKED_CHUNKS = 8192;
    public static final int MAX_DIAGNOSTIC_ENTITIES = 100000;

    public final ServerUtilitiesBridge serverUtilities;
    public long tickCounter;

    public EntityLoadGuard(ServerUtilitiesBridge serverUtilities) {
        this.serverUtilities = serverUtilities;
    }

    public static void checkChunk(Chunk chunk) {
        if (!ServerConfig.entityLoadProtection || chunk == null || chunk.worldObj == null || chunk.worldObj.isRemote) {
            return;
        }
        Object2IntOpenHashMap<String> typeCounts = new Object2IntOpenHashMap<>();
        int entityCount = 0;
        int inspectedEntities = 0;
        for (List<?> section : chunk.entityLists) {
            for (Entity entity : entities(section)) {
                if (++inspectedEntities > ServerConfig.entityProtectionMaximumInspected) {
                    FlameChunk.LOG.debug(
                        "Entity load protection skipped dimension {} chunk ({}, {}): inspection limit {} exceeded",
                        chunk.worldObj.provider.dimensionId,
                        chunk.xPosition,
                        chunk.zPosition,
                        ServerConfig.entityProtectionMaximumInspected);
                    return;
                }
                if (entity == null || entity.isDead) {
                    continue;
                }
                entityCount++;
                if (!(entity instanceof EntityPlayer)) {
                    typeCounts.addTo(WeakChunkInspector.entityType(entity), 1);
                }
            }
        }
        if (entityCount <= ServerConfig.entityProtectionThreshold) {
            return;
        }
        List<Object2IntMap.Entry<String>> rankedTypes = new ArrayList<>(typeCounts.object2IntEntrySet());
        rankedTypes.sort((left, right) -> {
            int countOrder = Integer.compare(right.getIntValue(), left.getIntValue());
            return countOrder != 0 ? countOrder
                : left.getKey()
                    .compareTo(right.getKey());
        });
        Object2IntOpenHashMap<String> removalAllowances = new Object2IntOpenHashMap<>();
        int typeLimit = Math.min(ServerConfig.entityProtectionTopTypes, rankedTypes.size());
        int target = 0;
        for (int index = 0; index < typeLimit; index++) {
            Object2IntMap.Entry<String> entry = rankedTypes.get(index);
            int excess = entry.getIntValue() - ServerConfig.entityProtectionRetainedPerType;
            if (excess > 0) {
                removalAllowances.put(entry.getKey(), excess);
                target += excess;
            }
        }
        target = Math.min(target, ServerConfig.entityProtectionMaximumRemoved);
        int removed = 0;
        if (target > 0) {
            for (List<?> section : chunk.entityLists) {
                Iterator<Entity> iterator = entities(section).iterator();
                while (iterator.hasNext() && removed < target) {
                    Entity entity = iterator.next();
                    if (entity == null || entity.isDead || entity instanceof EntityPlayer) {
                        continue;
                    }
                    String type = WeakChunkInspector.entityType(entity);
                    int allowance = removalAllowances.getInt(type);
                    if (allowance <= 0) {
                        continue;
                    }
                    entity.setDead();
                    iterator.remove();
                    removalAllowances.put(type, allowance - 1);
                    removed++;
                }
                if (removed >= target) {
                    break;
                }
            }
        }
        FlameChunk.LOG.debug(
            "Entity load protection inspected dimension {} chunk ({}, {}): {} entities across {} selected types, removed {} entities ({})",
            chunk.worldObj.provider.dimensionId,
            chunk.xPosition,
            chunk.zPosition,
            entityCount,
            removalAllowances.size(),
            removed,
            "players and unselected entity types were preserved");
        if (removed > 0 && ServerConfig.entityProtectionBroadcast) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null && server.getConfigurationManager() != null) {
                for (EntityPlayerMP player : server.getConfigurationManager().playerEntityList) {
                    ServerMessages.send(
                        player,
                        "flamechunk.entity.protection.broadcast",
                        chunk.worldObj.provider.dimensionId,
                        chunk.xPosition,
                        chunk.zPosition,
                        removed);
                }
            }
        }
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
        int inspectedEntities = 0;
        for (Entity entity : world.loadedEntityList) {
            if (++inspectedEntities > MAX_DIAGNOSTIC_ENTITIES) {
                break;
            }
            if (entity == null || entity.isDead) {
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
            FlameChunk.LOG.debug(
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

    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    public static int unpackX(long key) {
        return (int) (key >> 32);
    }

    public static int unpackZ(long key) {
        return (int) key;
    }

    @SuppressWarnings("unchecked")
    public static List<Entity> entities(List<?> section) {
        return (List<Entity>) section;
    }

}
