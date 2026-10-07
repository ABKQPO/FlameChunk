package com.hfstudio.flamechunk.server.guard;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;
import com.hfstudio.flamechunk.server.command.ServerMessages;
import com.hfstudio.flamechunk.server.integration.ServerUtilitiesBridge;

import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;

public class WeakChunkClearService {

    public static final int ACCEPTED = 0;
    public static final int DENIED = 1;
    public static final int STALE_TARGET = 2;
    public static final int ALREADY_QUEUED = 3;
    public static final int QUEUE_FULL = 4;

    public static final int MAX_JOBS = 32;
    public static final int MAX_QUEUED_ENTITIES = 16384;
    public static final int MAX_INSPECTED_PER_TICK = 4096;
    public static final int MAX_REMOVED_PER_TICK = 512;

    public final ServerUtilitiesBridge serverUtilities;
    public final ArrayDeque<ClearJob> jobs = new ArrayDeque<>();
    public final Set<String> activeJobs = new HashSet<>();
    public int queuedEntities;

    public WeakChunkClearService(ServerUtilitiesBridge serverUtilities) {
        this.serverUtilities = serverUtilities == null ? ServerUtilitiesBridge.NONE : serverUtilities;
    }

    public int submit(EntityPlayerMP player, int chunkX, int chunkZ, String typeId) {
        if (player == null || player.isDead
            || typeId == null
            || typeId.length() == 0
            || typeId.length() > WeakChunkSnapshot.MAX_TYPE_ID_LENGTH
            || (ServerConfig.requireOperator && !player.canCommandSenderUseCommand(2, "flamechunk"))
            || !serverUtilities.hasPermission(player, "flamechunk.weakclear")) {
            return DENIED;
        }
        World world = player.worldObj;
        if (world == null || world.isRemote
            || world.getChunkProvider() == null
            || world.getChunkProvider()
                .chunkExists(chunkX, chunkZ)) {
            return STALE_TARGET;
        }
        String key = key(world.provider.dimensionId, chunkX, chunkZ, typeId);
        if (activeJobs.contains(key)) {
            return ALREADY_QUEUED;
        }
        if (jobs.size() >= MAX_JOBS || queuedEntities >= MAX_QUEUED_ENTITIES) {
            return QUEUE_FULL;
        }
        activeJobs.add(key);
        jobs.addLast(new ClearJob(this, key, player, world, chunkX, chunkZ, typeId));
        return ACCEPTED;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || jobs.isEmpty()) {
            return;
        }
        int inspectedBudget = MAX_INSPECTED_PER_TICK;
        int removedBudget = MAX_REMOVED_PER_TICK;
        int jobsToVisit = jobs.size();
        while (jobsToVisit-- > 0 && inspectedBudget > 0 && removedBudget > 0) {
            ClearJob job = jobs.removeFirst();
            int inspectedBefore = job.inspected;
            int removedBefore = job.removed;
            try {
                job.process(inspectedBudget, removedBudget);
            } catch (RuntimeException exception) {
                FlameChunk.LOG.error("Unable to clear weak chunk entities", exception);
                job.complete = true;
            }
            inspectedBudget -= job.inspected - inspectedBefore;
            removedBudget -= job.removed - removedBefore;
            if (job.complete()) {
                complete(job, job.removed > 0, job.queueFull);
            } else if (activeJobs.contains(job.key)) {
                jobs.addLast(job);
            }
        }
    }

    public void onServerStopping(FMLServerStoppingEvent event) {
        for (ClearJob job : jobs) {
            job.releaseEntitySnapshot();
        }
        jobs.clear();
        activeJobs.clear();
        queuedEntities = 0;
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        Iterator<ClearJob> iterator = jobs.iterator();
        while (iterator.hasNext()) {
            ClearJob job = iterator.next();
            if (job.world == event.world) {
                iterator.remove();
                job.releaseEntitySnapshot();
                activeJobs.remove(job.key);
                queuedEntities -= job.targetCount;
            }
        }
    }

    public void complete(ClearJob job, boolean removedAny, boolean queueFull) {
        if (!activeJobs.remove(job.key)) {
            return;
        }
        job.releaseEntitySnapshot();
        queuedEntities -= job.targetCount;
        String message = queueFull ? "flamechunk.command.weakclear.queue_full"
            : removedAny ? "flamechunk.command.weakclear.completed" : "flamechunk.command.weakclear.stale";
        if (job.player.playerNetServerHandler != null) {
            if (removedAny && !queueFull) {
                ServerMessages.send(job.player, message, job.removed, job.typeId);
            } else if (queueFull) {
                ServerMessages.send(job.player, message);
            } else {
                ServerMessages.send(job.player, message, 0, job.typeId);
            }
        }
    }

    public static String key(int dimensionId, int chunkX, int chunkZ, String typeId) {
        return dimensionId + ":" + chunkX + ":" + chunkZ + ":" + typeId;
    }

    public static class ClearJob {

        public final WeakChunkClearService service;
        public final String key;
        public final EntityPlayerMP player;
        public final World world;
        public final int chunkX;
        public final int chunkZ;
        public final String typeId;
        public final ArrayDeque<Entity> targets;
        public final Set<Entity> selectedEntities = Collections.newSetFromMap(new IdentityHashMap<>());
        private List<Entity> entitySnapshot;
        public int retainedEntityCount;
        public int targetCount;
        public int inspected;
        public int removed;
        public boolean complete;
        public boolean scanComplete;
        public boolean queueFull;

        public ClearJob(WeakChunkClearService service, String key, EntityPlayerMP player, World world, int chunkX,
            int chunkZ, String typeId) {
            this.service = service;
            this.key = key;
            this.player = player;
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.typeId = typeId;
            this.targets = new ArrayDeque<>();
        }

        public void process(int inspectionBudget, int removalBudget) {
            int inspectedBefore = inspected;
            if (player.isDead || player.worldObj != world
                || player.playerNetServerHandler == null
                || (ServerConfig.requireOperator && !player.canCommandSenderUseCommand(2, "flamechunk"))
                || !service.serverUtilities.hasPermission(player, "flamechunk.weakclear")
                || world.getChunkProvider() == null
                || world.getChunkProvider()
                    .chunkExists(chunkX, chunkZ)) {
                complete = true;
                return;
            }
            if (!scanComplete) {
                scanEntities(inspectionBudget);
                if (!scanComplete || complete) {
                    return;
                }
                if (targetCount
                    < Math.max(ServerConfig.weakChunkEntityThreshold, WeakChunkSnapshot.ENTITY_CLEAR_THRESHOLD)) {
                    complete = true;
                    return;
                }
            }
            int remainingInspectionBudget = inspectionBudget - (inspected - inspectedBefore);
            if (remainingInspectionBudget <= 0) {
                return;
            }
            int inspectedThisTick = 0;
            int removedThisTick = 0;
            while (!targets.isEmpty() && inspectedThisTick < remainingInspectionBudget
                && removedThisTick < removalBudget) {
                Entity entity = targets.removeFirst();
                inspected++;
                inspectedThisTick++;
                if (entity == null || entity.isDead
                    || entity.worldObj != world
                    || entity instanceof EntityPlayer
                    || entity.chunkCoordX != chunkX
                    || entity.chunkCoordZ != chunkZ
                    || !typeId.equals(WeakChunkInspector.entityType(entity))) {
                    continue;
                }
                entity.setDead();
                removed++;
                removedThisTick++;
            }
            complete = targets.isEmpty();
        }

        public void scanEntities(int inspectionBudget) {
            int inspectionLimit = Math.max(0, ServerConfig.weakChunkMaximumScannedEntities);
            if (entitySnapshot == null) {
                List<Entity> loadedEntities = world.loadedEntityList;
                entitySnapshot = WeakEntitySnapshotBudget.capture(loadedEntities, inspectionLimit);
                retainedEntityCount = entitySnapshot.size();
                if (loadedEntities.size() > entitySnapshot.size()) {
                    complete = true;
                    return;
                }
            }
            List<Entity> entities = entitySnapshot;
            int inspectedThisTick = 0;
            while (inspectedThisTick < inspectionBudget && inspected < inspectionLimit && inspected < entities.size()) {
                Entity entity = entities.get(inspected++);
                inspectedThisTick++;
                if (entity == null || entity.isDead
                    || entity instanceof EntityPlayer
                    || entity.worldObj != world
                    || selectedEntities.contains(entity)
                    || entity.chunkCoordX != chunkX
                    || entity.chunkCoordZ != chunkZ
                    || !typeId.equals(WeakChunkInspector.entityType(entity))) {
                    continue;
                }
                if (service.queuedEntities >= MAX_QUEUED_ENTITIES) {
                    queueFull = true;
                    complete = true;
                    return;
                }
                selectedEntities.add(entity);
                targets.addLast(entity);
                targetCount++;
                service.queuedEntities++;
            }
            if (inspected >= entities.size()) {
                scanComplete = true;
            } else if (inspected >= inspectionLimit) {
                complete = true;
            }
        }

        public boolean complete() {
            return complete;
        }

        public void releaseEntitySnapshot() {
            WeakEntitySnapshotBudget.release(retainedEntityCount);
            retainedEntityCount = 0;
            entitySnapshot = null;
        }
    }
}
