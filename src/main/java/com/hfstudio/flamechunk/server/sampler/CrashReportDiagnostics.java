package com.hfstudio.flamechunk.server.sampler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;

import com.google.common.collect.ImmutableSetMultimap;
import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public class CrashReportDiagnostics {

    public static final int MAX_TICKETS_INSPECTED = 8192;
    public static final int MAX_ENTITIES_INSPECTED = 100000;
    public static final int MAX_OWNERS_REPORTED = 64;

    public static void append(CrashReport report, MinecraftServer server) {
        if (!ServerConfig.watchdogTicketDiagnostics || report == null
            || server == null
            || server.worldServers == null) {
            return;
        }
        try {
            CrashReportCategory category = report.makeCategory("FlameChunk Ticket Diagnostics");
            List<String> dimensions = new ArrayList<>();
            for (WorldServer world : server.worldServers) {
                if (world == null) {
                    continue;
                }
                try {
                    dimensions.add(formatDimension(world));
                } catch (RuntimeException exception) {
                    dimensions.add(
                        "Dimension " + dimensionId(world)
                            + " unavailable: "
                            + exception.getClass()
                                .getSimpleName());
                } catch (LinkageError error) {
                    dimensions.add("Dimension " + dimensionId(world) + " unavailable: incompatible API");
                }
            }
            category.addCrashSection("Active ticket owners", join(dimensions));
        } catch (RuntimeException exception) {
            FlameChunk.LOG.warn("Unable to append ticket diagnostics to the server crash report", exception);
        } catch (LinkageError error) {
            FlameChunk.LOG.warn("Ticket diagnostics are incompatible with this server", error);
        }
    }

    public static String dimensionId(WorldServer world) {
        return world == null || world.provider == null ? "unknown" : Integer.toString(world.provider.dimensionId);
    }

    public static String formatDimension(WorldServer world) {
        ImmutableSetMultimap<ChunkCoordIntPair, ForgeChunkManager.Ticket> tickets = ForgeChunkManager
            .getPersistentChunksFor(world);
        Long2IntOpenHashMap entityCounts = entityCounts(world);
        TreeMap<String, OwnerSummary> owners = new TreeMap<>();
        int inspected = 0;
        boolean truncated = false;
        for (Map.Entry<ChunkCoordIntPair, ForgeChunkManager.Ticket> entry : tickets.entries()) {
            if (++inspected > MAX_TICKETS_INSPECTED) {
                truncated = true;
                break;
            }
            ForgeChunkManager.Ticket ticket = entry.getValue();
            String source = ticketSource(ticket);
            OwnerSummary summary = owners.get(source);
            if (summary == null) {
                if (owners.size() >= MAX_OWNERS_REPORTED) {
                    truncated = true;
                    continue;
                }
                summary = new OwnerSummary(source);
                owners.put(source, summary);
            }
            ChunkCoordIntPair position = entry.getKey();
            summary.addChunk(
                position.chunkXPos,
                position.chunkZPos,
                entityCounts.get(pack(position.chunkXPos, position.chunkZPos)));
            if (world.getChunkProvider() != null && world.getChunkProvider()
                .chunkExists(position.chunkXPos, position.chunkZPos)) {
                Chunk chunk = world.getChunkFromChunkCoords(position.chunkXPos, position.chunkZPos);
                if (chunk != null && chunk.chunkTileEntityMap != null) {
                    summary.blockEntities += chunk.chunkTileEntityMap.size();
                }
            }
        }
        List<OwnerSummary> ranked = new ArrayList<>(owners.values());
        ranked.sort(
            Comparator.comparingInt((OwnerSummary owner) -> owner.blockEntities + owner.entities)
                .reversed()
                .thenComparing(owner -> owner.source));
        StringBuilder result = new StringBuilder("Dimension ").append(world.provider.dimensionId)
            .append(':');
        for (OwnerSummary owner : ranked) {
            result.append("\n")
                .append(owner.source)
                .append(" chunks=")
                .append(owner.chunks)
                .append(" blockEntities=")
                .append(owner.blockEntities)
                .append(" entities=")
                .append(owner.entities)
                .append(" sample=(")
                .append(owner.sampleX)
                .append(',')
                .append(owner.sampleZ)
                .append(')');
        }
        if (ranked.isEmpty()) {
            result.append(" no active Forge tickets");
        }
        if (truncated) {
            result.append("\nresults truncated by the diagnostic limit");
        }
        return result.toString();
    }

    public static Long2IntOpenHashMap entityCounts(WorldServer world) {
        Long2IntOpenHashMap counts = new Long2IntOpenHashMap();
        counts.defaultReturnValue(0);
        int inspected = 0;
        for (Entity entity : world.loadedEntityList) {
            if (++inspected > MAX_ENTITIES_INSPECTED) {
                break;
            }
            if (entity != null && !entity.isDead) {
                counts.addTo(pack(entity.chunkCoordX, entity.chunkCoordZ), 1);
            }
        }
        return counts;
    }

    public static String ticketSource(ForgeChunkManager.Ticket ticket) {
        if (ticket.isPlayerTicket()) {
            return "player:" + ticket.getPlayerName();
        }
        Entity entity = ticket.getEntity();
        if (entity != null) {
            return "entity:" + entity.getClass()
                .getSimpleName();
        }
        String modId = ticket.getModId();
        String ticketType = ticket.getType() == null ? "unknown"
            : ticket.getType()
                .name()
                .toLowerCase(Locale.ENGLISH);
        return (modId == null || modId.length() == 0 ? "unknown" : modId) + ":" + ticketType;
    }

    public static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(value);
        }
        return result.toString();
    }

    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    public static class OwnerSummary {

        public final String source;
        public int chunks;
        public int blockEntities;
        public int entities;
        public int sampleX;
        public int sampleZ;

        public OwnerSummary(String source) {
            this.source = source;
        }

        public void addChunk(int chunkX, int chunkZ, int entityCount) {
            if (chunks == 0) {
                sampleX = chunkX;
                sampleZ = chunkZ;
            }
            chunks++;
            entities += entityCount;
        }
    }
}
