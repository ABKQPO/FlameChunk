package com.hfstudio.flamechunk.server.sampler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.ForgeChunkManager;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

public class LoaderControlData extends WorldSavedData {

    public static final String DATA_NAME = "flamechunk_loader_control";
    public static final int MAX_FROZEN_CHUNKS = 4096;
    public static final String FROZEN_CHUNKS_TAG = "FrozenChunks";
    public static final String TICKET_REFERENCES_TAG = "TicketReferences";
    public static final String CHUNK_X_TAG = "X";
    public static final String CHUNK_Z_TAG = "Z";
    public static final String TICKETS_TAG = "Tickets";
    public static final String MOD_ID_TAG = "ModId";
    public static final String TICKET_TYPE_TAG = "TicketType";
    public static final String PLAYER_NAME_TAG = "PlayerName";
    public static final String ENTITY_UUID_MOST_TAG = "EntityUuidMost";
    public static final String ENTITY_UUID_LEAST_TAG = "EntityUuidLeast";
    public static final String HAS_ENTITY_UUID_TAG = "HasEntityUuid";
    public static final String MOD_DATA_TAG = "ModData";

    public final LongSet frozenChunks = new LongOpenHashSet();
    public final Long2ObjectOpenHashMap<List<TicketReference>> ticketReferences = new Long2ObjectOpenHashMap<>();

    public LoaderControlData(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        frozenChunks.clear();
        ticketReferences.clear();
        NBTTagList chunks = tag.getTagList(FROZEN_CHUNKS_TAG, 10);
        for (int index = 0; index < chunks.tagCount(); index++) {
            if (frozenChunks.size() >= MAX_FROZEN_CHUNKS) {
                break;
            }
            NBTTagCompound chunk = chunks.getCompoundTagAt(index);
            if (!chunk.hasKey(CHUNK_X_TAG, 3) || !chunk.hasKey(CHUNK_Z_TAG, 3)) {
                continue;
            }
            frozenChunks
                .add(ChunkCoordIntPair.chunkXZ2Int(chunk.getInteger(CHUNK_X_TAG), chunk.getInteger(CHUNK_Z_TAG)));
        }
        NBTTagList references = tag.getTagList(TICKET_REFERENCES_TAG, 10);
        for (int index = 0; index < references.tagCount(); index++) {
            NBTTagCompound reference = references.getCompoundTagAt(index);
            if (!reference.hasKey(CHUNK_X_TAG, 3) || !reference.hasKey(CHUNK_Z_TAG, 3)) {
                continue;
            }
            long key = ChunkCoordIntPair
                .chunkXZ2Int(reference.getInteger(CHUNK_X_TAG), reference.getInteger(CHUNK_Z_TAG));
            if (!frozenChunks.contains(key)) {
                continue;
            }
            NBTTagList tickets = reference.getTagList(TICKETS_TAG, 10);
            List<TicketReference> chunkReferences = new ArrayList<>(tickets.tagCount());
            for (int ticketIndex = 0; ticketIndex < tickets.tagCount(); ticketIndex++) {
                NBTTagCompound ticket = tickets.getCompoundTagAt(ticketIndex);
                String modId = ticket.getString(MOD_ID_TAG);
                int type = ticket.getInteger(TICKET_TYPE_TAG);
                if (modId.isEmpty() || type < 0 || type >= ForgeChunkManager.Type.values().length) {
                    continue;
                }
                UUID entityId = ticket.getBoolean(HAS_ENTITY_UUID_TAG)
                    ? new UUID(ticket.getLong(ENTITY_UUID_MOST_TAG), ticket.getLong(ENTITY_UUID_LEAST_TAG))
                    : null;
                NBTTagCompound modData = ticket.hasKey(MOD_DATA_TAG, 10) ? ticket.getCompoundTag(MOD_DATA_TAG)
                    : new NBTTagCompound();
                chunkReferences.add(
                    new TicketReference(
                        modId,
                        type,
                        ticket.getString(PLAYER_NAME_TAG),
                        entityId,
                        (NBTTagCompound) modData.copy()));
            }
            if (!chunkReferences.isEmpty()) {
                ticketReferences.put(key, chunkReferences);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList chunks = new NBTTagList();
        for (long key : frozenChunks) {
            NBTTagCompound chunk = new NBTTagCompound();
            chunk.setInteger(CHUNK_X_TAG, (int) key);
            chunk.setInteger(CHUNK_Z_TAG, (int) (key >> 32));
            chunks.appendTag(chunk);
        }
        tag.setTag(FROZEN_CHUNKS_TAG, chunks);
        NBTTagList references = new NBTTagList();
        for (var entry : ticketReferences.long2ObjectEntrySet()) {
            if (!frozenChunks.contains(entry.getLongKey())) {
                continue;
            }
            NBTTagCompound reference = new NBTTagCompound();
            reference.setInteger(CHUNK_X_TAG, (int) entry.getLongKey());
            reference.setInteger(CHUNK_Z_TAG, (int) (entry.getLongKey() >> 32));
            NBTTagList tickets = new NBTTagList();
            for (TicketReference ticketReference : entry.getValue()) {
                NBTTagCompound ticket = new NBTTagCompound();
                ticket.setString(MOD_ID_TAG, ticketReference.modId());
                ticket.setInteger(TICKET_TYPE_TAG, ticketReference.ticketType());
                ticket.setString(PLAYER_NAME_TAG, ticketReference.playerName());
                if (ticketReference.entityId() != null) {
                    ticket.setBoolean(HAS_ENTITY_UUID_TAG, true);
                    ticket.setLong(
                        ENTITY_UUID_MOST_TAG,
                        ticketReference.entityId()
                            .getMostSignificantBits());
                    ticket.setLong(
                        ENTITY_UUID_LEAST_TAG,
                        ticketReference.entityId()
                            .getLeastSignificantBits());
                }
                ticket.setTag(
                    MOD_DATA_TAG,
                    (NBTTagCompound) ticketReference.modData()
                        .copy());
                tickets.appendTag(ticket);
            }
            reference.setTag(TICKETS_TAG, tickets);
            references.appendTag(reference);
        }
        tag.setTag(TICKET_REFERENCES_TAG, references);
    }

    public boolean isFrozen(ChunkCoordIntPair chunk) {
        return frozenChunks.contains(key(chunk));
    }

    public boolean setFrozen(ChunkCoordIntPair chunk, boolean frozen) {
        if (frozen && !frozenChunks.contains(key(chunk)) && frozenChunks.size() >= MAX_FROZEN_CHUNKS) {
            return false;
        }
        long chunkKey = key(chunk);
        boolean changed = frozen ? frozenChunks.add(chunkKey) : frozenChunks.remove(chunkKey);
        if (!frozen) {
            changed |= ticketReferences.remove(chunkKey) != null;
        }
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public void storeTicketReferences(ChunkCoordIntPair chunk, Iterable<ForgeChunkManager.Ticket> tickets) {
        List<TicketReference> references = new ArrayList<>();
        for (ForgeChunkManager.Ticket ticket : tickets) {
            references.add(reference(ticket));
        }
        if (!references.isEmpty()) {
            ticketReferences.put(key(chunk), references);
            markDirty();
        }
    }

    public void storeTicketReference(ChunkCoordIntPair chunk, ForgeChunkManager.Ticket ticket) {
        long chunkKey = key(chunk);
        List<TicketReference> references = ticketReferences.get(chunkKey);
        if (references == null) {
            references = new ArrayList<>();
            ticketReferences.put(chunkKey, references);
        }
        references.add(reference(ticket));
        markDirty();
    }

    public TicketReference reference(ForgeChunkManager.Ticket ticket) {
        UUID entityId = ticket.getEntity() == null ? null
            : ticket.getEntity()
                .getPersistentID();
        return new TicketReference(
            ticket.getModId(),
            ticket.getType()
                .ordinal(),
            ticket.isPlayerTicket() ? ticket.getPlayerName() : "",
            entityId,
            (NBTTagCompound) ticket.getModData()
                .copy());
    }

    public List<TicketReference> ticketReferences(ChunkCoordIntPair chunk) {
        List<TicketReference> references = ticketReferences.get(key(chunk));
        return references == null ? List.of() : references;
    }

    public List<ChunkCoordIntPair> frozenChunks() {
        List<ChunkCoordIntPair> chunks = new ArrayList<>(frozenChunks.size());
        for (long key : frozenChunks) {
            chunks.add(new ChunkCoordIntPair((int) key, (int) (key >> 32)));
        }
        chunks.sort(
            Comparator.comparingInt((ChunkCoordIntPair left) -> left.chunkXPos)
                .thenComparingInt(left -> left.chunkZPos));
        return chunks;
    }

    public static long key(ChunkCoordIntPair chunk) {
        return ChunkCoordIntPair.chunkXZ2Int(chunk.chunkXPos, chunk.chunkZPos);
    }

    public record TicketReference(String modId, int ticketType, String playerName, UUID entityId,
        NBTTagCompound modData) {

        public TicketReference {
            modData = (NBTTagCompound) modData.copy();
        }
    }
}
