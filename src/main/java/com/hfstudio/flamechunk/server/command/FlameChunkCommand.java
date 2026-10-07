package com.hfstudio.flamechunk.server.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.data.ScanLimits;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.ChunkEntry;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;
import com.hfstudio.flamechunk.server.guard.WeakChunkClearService;
import com.hfstudio.flamechunk.server.guard.WeakChunkInspector;
import com.hfstudio.flamechunk.server.sampler.LoaderTicketControlService;
import com.hfstudio.flamechunk.server.sampler.LoaderTicketReport;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

public class FlameChunkCommand extends CommandBase {

    private final WeakChunkClearService weakChunkClearService;
    public final WeakChunkInspector weakChunkInspector;

    public FlameChunkCommand(WeakChunkClearService weakChunkClearService) {
        this(weakChunkClearService, null);
    }

    public FlameChunkCommand(WeakChunkClearService weakChunkClearService, WeakChunkInspector weakChunkInspector) {
        this.weakChunkClearService = weakChunkClearService;
        this.weakChunkInspector = weakChunkInspector;
    }

    @Override
    public String getCommandName() {
        return "flamechunk";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "flamechunk.command.usage";
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> commands = new ArrayList<>();
            if (authorized(sender, "flamechunk.scan")) {
                Collections.addAll(commands, "scan", "stop", "report", "tickets");
            }
            if (authorized(sender, "flamechunk.loadercontrol")) {
                commands.add("loader");
            }
            if (sender instanceof EntityPlayerMP && authorized(sender, "flamechunk.weakclear")) {
                commands.add("weakclear");
            }
            return getListOfStringsMatchingLastWord(args, commands.toArray(new String[0]));
        }
        if ("scan".equals(args[0]) && args.length == 2 && authorized(sender, "flamechunk.scan")) {
            return getListOfStringsMatchingLastWord(
                args,
                Integer.toString(ServerConfig.scanSeconds),
                "10",
                "30",
                "60",
                "300",
                "900",
                "3600",
                "86400");
        }
        if ("loader".equals(args[0]) && authorized(sender, "flamechunk.loadercontrol")) {
            if (args.length == 2) {
                return getListOfStringsMatchingLastWord(args, "freeze", "unfreeze", "clear", "frozen");
            }
            if ("frozen".equalsIgnoreCase(args[1])) {
                return args.length == 3 ? getListOfStringsMatchingLastWord(args, "list", "clear-orphans")
                    : Collections.emptyList();
            }
            if ("freeze".equalsIgnoreCase(args[1]) || "unfreeze".equalsIgnoreCase(args[1])
                || "clear".equalsIgnoreCase(args[1])) {
                if (args.length == 3 || args.length == 4) {
                    int coordinate = args.length == 3 ? sender.getPlayerCoordinates().posX >> 4
                        : sender.getPlayerCoordinates().posZ >> 4;
                    return getListOfStringsMatchingLastWord(args, Integer.toString(coordinate));
                }
                if (args.length == 5) {
                    return getListOfStringsMatchingLastWord(args, "confirm");
                }
            }
        }
        if ("weakclear".equals(args[0]) && sender instanceof EntityPlayerMP
            && authorized(sender, "flamechunk.weakclear")) {
            if (args.length == 5) {
                return getListOfStringsMatchingLastWord(args, "confirm");
            }
            if (args.length >= 2 && args.length <= 4) {
                return completeWeakTarget(sender, args);
            }
        }
        return Collections.emptyList();
    }

    public List<String> completeWeakTarget(ICommandSender sender, String[] args) {
        Set<String> candidates = new TreeSet<>();
        WeakChunkSnapshot snapshot = weakChunkInspector == null ? null
            : weakChunkInspector.getLatestSnapshot(sender.getEntityWorld());
        if (snapshot != null) {
            for (ChunkEntry chunk : snapshot.getChunks()) {
                if (args.length == 2) {
                    candidates.add(Integer.toString(chunk.getChunkX()));
                } else if (Integer.toString(chunk.getChunkX())
                    .equals(args[1])) {
                        if (args.length == 3) {
                            candidates.add(Integer.toString(chunk.getChunkZ()));
                        } else if (Integer.toString(chunk.getChunkZ())
                            .equals(args[2])) {
                                for (EntityTypeCount type : chunk.getEntityTypes()) {
                                    candidates.add(type.getTypeId());
                                }
                            }
                    }
            }
        }
        if (args.length == 2 || args.length == 3) {
            int coordinate = args.length == 2 ? sender.getPlayerCoordinates().posX >> 4
                : sender.getPlayerCoordinates().posZ >> 4;
            candidates.add(Integer.toString(coordinate));
        }
        return getListOfStringsMatchingLastWord(args, candidates.toArray(new String[0]));
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length > 0 && "scan".equals(args[0])) {
            processScan(sender, args);
            return;
        }
        if (args.length == 1 && "stop".equals(args[0])) {
            if (PerformanceSampler.stopScan(sender)) {
                sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.stopped"));
            } else {
                sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.stop.denied_or_inactive"));
            }
            return;
        }
        if (args.length > 0 && "weakclear".equals(args[0])) {
            processWeakClear(sender, args);
            return;
        }
        if (args.length == 1 && "report".equals(args[0])) {
            if (!PerformanceSampler.sendLastReport(sender)) {
                sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.report.empty_or_denied"));
            }
            return;
        }
        if (args.length == 1 && "tickets".equals(args[0])) {
            if (authorized(sender, "flamechunk.scan")) {
                LoaderTicketReport.send(sender);
            } else {
                sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.denied"));
            }
            return;
        }
        if (args.length > 0 && "loader".equals(args[0])) {
            processLoaderControl(sender, args);
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private void processScan(ICommandSender sender, String[] args) throws CommandException {
        if (args.length > 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        if (ServerConfig.requireOperator && !sender.canCommandSenderUseCommand(2, getCommandName())) {
            sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.denied"));
            return;
        }
        int seconds = ServerConfig.scanSeconds;
        if (args.length == 2) {
            try {
                seconds = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                throw new NumberInvalidException("flamechunk.command.invalid_duration", args[1]);
            }
            if (!ScanLimits.isValidDuration(seconds)) {
                throw new NumberInvalidException("flamechunk.command.invalid_duration", seconds);
            }
        }
        if (PerformanceSampler.isActive()) {
            sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.active"));
            return;
        }
        EntityPlayerMP player = sender instanceof EntityPlayerMP playerSender ? playerSender : null;
        boolean started = player == null ? PerformanceSampler.requestConsoleScan(sender, seconds)
            : PerformanceSampler.requestScan(player, seconds);
        if (!started) {
            sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.active"));
        }
    }

    private void processWeakClear(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 5 || !"confirm".equalsIgnoreCase(args[4])) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        if (!(sender instanceof EntityPlayerMP player)) {
            sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.weakclear.player_only"));
            return;
        }
        int chunkX = parseCoordinate(args[1]);
        int chunkZ = parseCoordinate(args[2]);
        String typeId = args[3];
        if (typeId.length() > 64) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        int result = weakChunkClearService.submit(player, chunkX, chunkZ, typeId);
        if (result == WeakChunkClearService.ACCEPTED) {
            sender.addChatMessage(
                ServerMessages.translated(sender, "flamechunk.command.weakclear.queued", chunkX, chunkZ, typeId));
        } else if (result == WeakChunkClearService.DENIED) {
            sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.denied"));
        } else if (result == WeakChunkClearService.STALE_TARGET) {
            sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.weakclear.stale", 0, typeId));
        } else if (result == WeakChunkClearService.ALREADY_QUEUED) {
            sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.weakclear.already_queued"));
        } else {
            sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.weakclear.queue_full"));
        }
    }

    private int parseCoordinate(String value) throws NumberInvalidException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new NumberInvalidException("flamechunk.command.invalid_coordinate", value);
        }
    }

    private void processLoaderControl(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 3 && "frozen".equalsIgnoreCase(args[1])) {
            if (!authorized(sender, "flamechunk.loadercontrol")) {
                sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.denied"));
                return;
            }
            World world = sender.getEntityWorld();
            if ("list".equalsIgnoreCase(args[2])) {
                List<ChunkCoordIntPair> frozen = LoaderTicketControlService.frozenChunks(world);
                sender
                    .addChatMessage(ServerMessages.translated(sender, "flamechunk.command.loader.list", frozen.size()));
                for (int index = 0; index < frozen.size() && index < 100; index++) {
                    ChunkCoordIntPair chunk = frozen.get(index);
                    sender.addChatMessage(
                        ServerMessages.translated(
                            sender,
                            "flamechunk.command.loader.entry",
                            world.provider.dimensionId,
                            chunk.chunkXPos,
                            chunk.chunkZPos));
                }
                return;
            }
            if ("clear-orphans".equalsIgnoreCase(args[2])) {
                sender.addChatMessage(
                    ServerMessages.translated(
                        sender,
                        "flamechunk.command.loader.orphans",
                        LoaderTicketControlService.clearOrphans(world)));
                return;
            }
        }
        if (args.length != 5
            || (!"freeze".equalsIgnoreCase(args[1]) && !"unfreeze".equalsIgnoreCase(args[1])
                && !"clear".equalsIgnoreCase(args[1]))
            || !"confirm".equalsIgnoreCase(args[4])) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        int chunkX = parseCoordinate(args[2]);
        int chunkZ = parseCoordinate(args[3]);
        if (!authorized(sender, "flamechunk.loadercontrol")) {
            sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.denied"));
            return;
        }
        World world = sender.getEntityWorld();
        int affected;
        String action = args[1].toLowerCase(Locale.ENGLISH);
        if ("freeze".equals(action)) {
            affected = LoaderTicketControlService.freeze(world, chunkX, chunkZ);
            if (affected < 0) {
                sender.addChatMessage(ServerMessages.translated(sender, "flamechunk.command.loader.limit"));
            } else if (affected == 0) {
                sender.addChatMessage(
                    ServerMessages.translated(sender, "flamechunk.command.loader.missing", chunkX, chunkZ));
            } else {
                sender.addChatMessage(
                    ServerMessages.translated(sender, "flamechunk.command.loader.frozen", chunkX, chunkZ, affected));
            }
        } else if ("unfreeze".equals(action)) {
            affected = LoaderTicketControlService.unfreeze(world, chunkX, chunkZ);
            sender.addChatMessage(
                ServerMessages.translated(sender, "flamechunk.command.loader.unfrozen", chunkX, chunkZ, affected));
        } else {
            affected = LoaderTicketControlService.clear(world, chunkX, chunkZ);
            sender.addChatMessage(
                ServerMessages.translated(sender, "flamechunk.command.loader.cleared", chunkX, chunkZ, affected));
        }
    }

    private boolean authorized(ICommandSender sender, String permission) {
        if (sender instanceof EntityPlayerMP player) {
            if (ServerConfig.requireOperator && !player.canCommandSenderUseCommand(2, getCommandName())) {
                return false;
            }
            return FlameChunk.serverUtilities == null || FlameChunk.serverUtilities.hasPermission(player, permission);
        }
        return true;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
