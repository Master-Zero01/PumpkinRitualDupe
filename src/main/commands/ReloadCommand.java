package org.First.pumpkinRitualDupe.commands;

import org.First.pumpkinRitualDupe.PumpkinRitualDupe;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Handles the plugin's administrative commands.
 */
public final class ReloadCommand implements CommandExecutor, TabCompleter {

    private final PumpkinRitualDupe plugin;

    public ReloadCommand(@NotNull PumpkinRitualDupe plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            String @NotNull [] args
    ) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(plugin.getMessage("command-header"));
            sender.sendMessage(plugin.getMessage("command-reload"));
            return true;
        }

        if (!sender.hasPermission("pumpkindupe.reload") && !sender.isOp()) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return true;
        }

        plugin.reloadConfig();
        plugin.loadConfigSettings();
        sender.sendMessage(plugin.getMessage("config-reloaded"));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            String @NotNull [] args
    ) {
        return args.length == 1 && sender.hasPermission("pumpkindupe.reload")
                ? Collections.singletonList("reload")
                : Collections.emptyList();
    }
}
