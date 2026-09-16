package dev.minted.command;

import dev.minted.MintedPlugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Registers and dispatches the plugin's commands.
 *
 * <p>The economy commands ({@code /bank}, {@code /eco}) are intentionally
 * absent for now: they open the GUI hub, which ships in a later milestone.
 */
public final class CommandManager implements CommandExecutor, TabCompleter {

    private final MintedPlugin plugin;

    public CommandManager(MintedPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        register("minted", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "Minted " + ChatColor.GRAY + plugin.getDescription().getVersion()
                    + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "server " + plugin.getServerVersion());
            sender.sendMessage(ChatColor.GRAY + "Tip: " + ChatColor.WHITE + "/minted reload");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload":
                if (!sender.hasPermission("minted.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " [reload]");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }

    private void register(String name, CommandExecutor executor) {
        if (plugin.getCommand(name) == null) {
            plugin.getLogger().warning("Command /" + name + " is missing from plugin.yml");
            return;
        }

        plugin.getCommand(name).setExecutor(executor);
        if (executor instanceof TabCompleter) {
            plugin.getCommand(name).setTabCompleter((TabCompleter) executor);
        }
    }
}