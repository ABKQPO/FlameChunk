package com.hfstudio.flamechunk.server.sampler;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.event.world.WorldEvent;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.hfstudio.flamechunk.mixins.early.ForgeChunkManagerAccessor;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class LoaderTicketControlService {

    public static final Map<World, Long2ObjectMap<Set<ForgeChunkManager.Ticket>>> BLOCKED_TICKETS = new IdentityHashMap<>();
    public static final Map<World, LoaderControlData> WORLD_DATA = new IdentityHashMap<>();

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        BLOCKED_TICKETS.remove(event.world);
        WORLD_DATA.remove(event.world);
    }

    public static void clearRuntimeState() {
        BLOCKED_TICKETS.clear();
        WORLD_DATA.clear();
    }

    public static boolean shouldBlock(ForgeChunkManager.Ticket ticket, ChunkCoordIntPair chunk) {
        if (ticket == null || chunk == null || !(ticket.world instanceof WorldServer)) {
            return false;
        }
        LoaderControlData data = getDataIfPresent(ticket.world);
        if (data == null || !data.isFrozen(chunk)) {
            return false;
        }
        Multimap<String, ForgeChunkManager.Ticket> tickets = ticketsForWorld(ticket.world);
        if (!tickets.containsEntry(ticket.getModId(), ticket)) {
            return false;
        }
        rememberTicket(ticket, chunk);
        return true;
    }

    public static int freeze(World world, int chunkX, int chunkZ) {
        if (!(world instanceof WorldServer)) {
            return 0;
        }
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(chunkX, chunkZ);
        ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> forced = ForgeChunkManager
            .getPersistentChunksFor(world);
        Set<ForgeChunkManager.Ticket> tickets = new LinkedHashSet<>(forced.get(chunk));
        if (tickets.isEmpty()) {
            return 0;
        }
        LoaderControlData data = getData(world);
        if (!data.isFrozen(chunk) && !data.setFrozen(chunk, true)) {
            return -1;
        }
        rememberTickets(tickets, chunk);
        for (ForgeChunkManager.Ticket ticket : tickets) {
            ForgeChunkManager.unforceChunk(ticket, chunk);
        }
        return tickets.size();
    }

    public static int unfreeze(World world, int chunkX, int chunkZ) {
        if (!(world instanceof WorldServer)) {
            return 0;
        }
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(chunkX, chunkZ);
        LoaderControlData data = getDataIfPresent(world);
        if (data != null) {
            data.setFrozen(chunk, false);
        }
        Set<ForgeChunkManager.Ticket> tickets = forgetTickets(world, chunk);
        Multimap<String, ForgeChunkManager.Ticket> worldTickets = ticketsForWorld(world);
        int restored = 0;
        for (ForgeChunkManager.Ticket ticket : tickets) {
            if (worldTickets.containsEntry(ticket.getModId(), ticket)) {
                ForgeChunkManager.forceChunk(ticket, chunk);
                restored++;
            }
        }
        return restored;
    }

    public static int toggle(World world, int chunkX, int chunkZ) {
        if (!(world instanceof WorldServer)) {
            return 0;
        }
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(chunkX, chunkZ);
        LoaderControlData data = getDataIfPresent(world);
        if (data != null && data.isFrozen(chunk)) {
            return -(unfreeze(world, chunkX, chunkZ) + 1);
        }
        int affected = freeze(world, chunkX, chunkZ);
        return affected == -1 ? Integer.MIN_VALUE : affected;
    }

    public static int clear(World world, int chunkX, int chunkZ) {
        if (!(world instanceof WorldServer)) {
            return 0;
        }
        ChunkCoordIntPair chunk = new ChunkCoordIntPair(chunkX, chunkZ);
        LoaderControlData data = getDataIfPresent(world);
        if (data != null) {
            data.setFrozen(chunk, false);
        }
        Set<ForgeChunkManager.Ticket> tickets = new LinkedHashSet<>();
        tickets.addAll(
            ForgeChunkManager.getPersistentChunksFor(world)
                .get(chunk));
        tickets.addAll(forgetTickets(world, chunk));
        Multimap<String, ForgeChunkManager.Ticket> worldTickets = ticketsForWorld(world);
        int cleared = 0;
        for (ForgeChunkManager.Ticket ticket : tickets) {
            if (worldTickets.containsEntry(ticket.getModId(), ticket) && ticket.getChunkList()
                .contains(chunk)) {
                ForgeChunkManager.unforceChunk(ticket, chunk);
                cleared++;
            }
        }
        return cleared;
    }

    public static List<ChunkCoordIntPair> frozenChunks(World world) {
        if (!(world instanceof WorldServer)) {
            return Collections.emptyList();
        }
        LoaderControlData data = getDataIfPresent(world);
        return data == null ? Collections.emptyList() : data.frozenChunks();
    }

    public static int clearOrphans(World world) {
        if (!(world instanceof WorldServer)) {
            return 0;
        }
        LoaderControlData data = getDataIfPresent(world);
        if (data == null) {
            return 0;
        }
        Multimap<String, ForgeChunkManager.Ticket> liveTickets = ticketsForWorld(world);
        Long2ObjectMap<Set<ForgeChunkManager.Ticket>> blocked = BLOCKED_TICKETS.get(world);
        int cleared = 0;
        for (ChunkCoordIntPair chunk : data.frozenChunks()) {
            Set<ForgeChunkManager.Ticket> tickets = blocked == null ? null
                : blocked.get(ChunkCoordIntPair.chunkXZ2Int(chunk.chunkXPos, chunk.chunkZPos));
            boolean live = false;
            if (tickets != null) {
                for (ForgeChunkManager.Ticket ticket : tickets) {
                    if (liveTickets.containsEntry(ticket.getModId(), ticket)) {
                        live = true;
                        break;
                    }
                }
            }
            if (!live && ForgeChunkManager.getPersistentChunksFor(world)
                .get(chunk)
                .isEmpty()) {
                data.setFrozen(chunk, false);
                forgetTickets(world, chunk);
                cleared++;
            }
        }
        return cleared;
    }

    public static LoaderControlData getData(World world) {
        if (WORLD_DATA.containsKey(world)) {
            LoaderControlData cached = WORLD_DATA.get(world);
            if (cached != null) {
                return cached;
            }
        }
        LoaderControlData cached = getDataIfPresent(world);
        if (cached != null) {
            return cached;
        }
        LoaderControlData data = new LoaderControlData(LoaderControlData.DATA_NAME);
        world.setItemData(LoaderControlData.DATA_NAME, data);
        WORLD_DATA.put(world, data);
        return data;
    }

    public static LoaderControlData getDataIfPresent(World world) {
        if (WORLD_DATA.containsKey(world)) {
            return WORLD_DATA.get(world);
        }
        WorldSavedData loaded = world.loadItemData(LoaderControlData.class, LoaderControlData.DATA_NAME);
        if (loaded instanceof LoaderControlData data) {
            WORLD_DATA.put(world, data);
            return data;
        }
        WORLD_DATA.put(world, null);
        return null;
    }

    public static void rememberTickets(Iterable<ForgeChunkManager.Ticket> tickets, ChunkCoordIntPair chunk) {
        for (ForgeChunkManager.Ticket ticket : tickets) {
            rememberTicket(ticket, chunk);
        }
    }

    public static void rememberTicket(ForgeChunkManager.Ticket ticket, ChunkCoordIntPair chunk) {
        long key = ChunkCoordIntPair.chunkXZ2Int(chunk.chunkXPos, chunk.chunkZPos);
        Long2ObjectMap<Set<ForgeChunkManager.Ticket>> byChunk = BLOCKED_TICKETS.get(ticket.world);
        if (byChunk == null) {
            byChunk = new Long2ObjectOpenHashMap<>();
            BLOCKED_TICKETS.put(ticket.world, byChunk);
        }
        Set<ForgeChunkManager.Ticket> tickets = byChunk.get(key);
        if (tickets == null) {
            tickets = Collections.newSetFromMap(new IdentityHashMap<>());
            byChunk.put(key, tickets);
        }
        tickets.add(ticket);
    }

    public static Set<ForgeChunkManager.Ticket> forgetTickets(World world, ChunkCoordIntPair chunk) {
        Long2ObjectMap<Set<ForgeChunkManager.Ticket>> byChunk = BLOCKED_TICKETS.get(world);
        if (byChunk == null) {
            return Collections.emptySet();
        }
        Set<ForgeChunkManager.Ticket> tickets = byChunk
            .remove(ChunkCoordIntPair.chunkXZ2Int(chunk.chunkXPos, chunk.chunkZPos));
        if (byChunk.isEmpty()) {
            BLOCKED_TICKETS.remove(world);
        }
        return tickets == null ? Collections.emptySet() : tickets;
    }

    public static Multimap<String, ForgeChunkManager.Ticket> ticketsForWorld(World world) {
        Map<World, Multimap<String, ForgeChunkManager.Ticket>> tickets = ForgeChunkManagerAccessor
            .flamechunk$getTickets();
        Multimap<String, ForgeChunkManager.Ticket> worldTickets = tickets.get(world);
        return worldTickets == null ? ImmutableMultimap.of() : worldTickets;
    }
}
