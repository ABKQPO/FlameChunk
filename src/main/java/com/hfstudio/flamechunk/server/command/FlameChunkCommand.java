package com.hfstudio.flamechunk.server.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.server.sampler.PerformanceSampler;

public class FlameChunkCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "flamechunk";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "flamechunk.command.usage";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || !"scan".equals(args[0]) || args.length > 2) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        if (ServerConfig.requireOperator && !sender.canCommandSenderUseCommand(2, getCommandName())) {
            sender.addChatMessage(new ChatComponentTranslation("flamechunk.command.denied"));
            return;
        }
        int seconds = ServerConfig.scanSeconds;
        if (args.length == 2) {
            try {
                seconds = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                throw new NumberInvalidException("flamechunk.command.invalid_duration", args[1]);
            }
            if (seconds < 1 || seconds > 60) {
                throw new NumberInvalidException("flamechunk.command.invalid_duration", seconds);
            }
        }
        if (PerformanceSampler.isActive()) {
            sender.addChatMessage(new ChatComponentTranslation("flamechunk.command.active"));
            return;
        }
        EntityPlayerMP player = sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
        boolean started = player == null ? PerformanceSampler.requestConsoleScan(sender, seconds)
            : PerformanceSampler.requestScan(player, seconds);
        if (!started) {
            sender.addChatMessage(new ChatComponentTranslation("flamechunk.command.active"));
        }
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
