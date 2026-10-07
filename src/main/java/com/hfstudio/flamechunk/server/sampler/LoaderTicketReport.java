package com.hfstudio.flamechunk.server.sampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;

import com.google.common.collect.ImmutableSetMultimap;

public class LoaderTicketReport {

    private static final int MAX_TICKET_ROWS_PER_DIMENSION = 32;
    private static final Comparator<TicketEntry> TICKET_ORDER = Comparator
        .comparing((TicketEntry entry) -> entry.source)
        .thenComparingInt(entry -> entry.chunkX)
        .thenComparingInt(entry -> entry.chunkZ)
        .thenComparingLong(entry -> entry.sequence);

    public static void send(ICommandSender sender) {
        MinecraftServer server = MinecraftServer.getServer();
        if (sender == null || server == null || server.worldServers == null) {
            return;
        }
        for (WorldServer world : server.worldServers) {
            if (world == null) {
                continue;
            }
            ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> tickets = ForgeChunkManager
                .getPersistentChunksFor(world);
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "flamechunk.command.tickets.dimension",
                    world.provider.dimensionId,
                    tickets.size()));
            List<TicketEntry> rows = new ArrayList<>(Math.min(MAX_TICKET_ROWS_PER_DIMENSION, tickets.size()));
            long sequence = 0L;
            for (Map.Entry<ChunkCoordIntPair, ForgeChunkManager.Ticket> entry : tickets.entries()) {
                ChunkCoordIntPair position = entry.getKey();
                TicketEntry row = new TicketEntry(
                    position.chunkXPos,
                    position.chunkZPos,
                    ticketSource(entry.getValue()),
                    sequence++);
                int index = Collections.binarySearch(rows, row, TICKET_ORDER);
                index = index < 0 ? -index - 1 : index;
                if (index < MAX_TICKET_ROWS_PER_DIMENSION) {
                    rows.add(index, row);
                    if (rows.size() > MAX_TICKET_ROWS_PER_DIMENSION) {
                        rows.remove(rows.size() - 1);
                    }
                }
            }
            int displayed = Math.min(MAX_TICKET_ROWS_PER_DIMENSION, tickets.size());
            for (int index = 0; index < displayed; index++) {
                TicketEntry entry = rows.get(index);
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "flamechunk.command.tickets.entry",
                        entry.chunkX,
                        entry.chunkZ,
                        entry.source));
            }
            if (rows.isEmpty()) {
                sender.addChatMessage(new ChatComponentTranslation("flamechunk.command.tickets.empty"));
            } else if (tickets.size() > displayed) {
                sender.addChatMessage(
                    new ChatComponentTranslation("flamechunk.command.tickets.truncated", tickets.size() - displayed));
            }
            List<ChunkCoordIntPair> frozen = LoaderTicketControlService.frozenChunks(world);
            sender.addChatMessage(
                new ChatComponentTranslation("flamechunk.command.tickets.frozen_header", frozen.size()));
            for (int index = 0; index < frozen.size() && index < MAX_TICKET_ROWS_PER_DIMENSION; index++) {
                ChunkCoordIntPair chunk = frozen.get(index);
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "flamechunk.command.tickets.frozen_entry",
                        chunk.chunkXPos,
                        chunk.chunkZPos));
            }
            if (frozen.size() > MAX_TICKET_ROWS_PER_DIMENSION) {
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "flamechunk.command.tickets.truncated",
                        frozen.size() - MAX_TICKET_ROWS_PER_DIMENSION));
            }
        }
    }

    public static String ticketSource(ForgeChunkManager.Ticket ticket) {
        if (ticket.isPlayerTicket()) {
            return "player:" + ticket.getPlayerName();
        }
        if (ticket.getEntity() != null) {
            return "entity:" + ticket.getEntity()
                .getClass()
                .getSimpleName();
        }
        String modId = ticket.getModId();
        String type = ticket.getType() == null ? "unknown"
            : ticket.getType()
                .name()
                .toLowerCase(Locale.ENGLISH);
        return (modId == null || modId.length() == 0 ? "unknown" : modId) + ":" + type;
    }

    public static class TicketEntry {

        public final int chunkX;
        public final int chunkZ;
        public final String source;
        public final long sequence;

        public TicketEntry(int chunkX, int chunkZ, String source, long sequence) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.source = source;
            this.sequence = sequence;
        }
    }
}
