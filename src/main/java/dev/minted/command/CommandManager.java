package dev.minted.command;

import dev.minted.MintedPlugin;
import dev.minted.gui.GuiContext;
import dev.minted.gui.PersonalMenu;
import dev.minted.request.RequestService;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Registers and dispatches {@code /minted}. Besides {@code reload} it opens the
 * personal GUI and carries the accept/decline actions behind the clickable
 * request message.
 */
public final class CommandManager implements CommandExecutor, TabCompleter {

    private final MintedPlugin plugin;
    private final GuiContext gui;
    private final RequestService requests;

    public CommandManager(MintedPlugin plugin, GuiContext gui, RequestService requests) {
        this.plugin = plugin;
        this.gui = gui;
        this.requests = requests;
    }

    public void register() {
        register("minted", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "Minted " + ChatColor.GRAY + plugin.getDescription().getVersion()
                    + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "server " + plugin.getServerVersion());
            sender.sendMessage(ChatColor.GRAY + "Tip: " + ChatColor.WHITE + "/minted gui" + ChatColor.GRAY
                    + " or " + ChatColor.WHITE + "/minted reload");
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
            case "gui":
                return openGui(sender);
            case "accept":
                return resolveRequest(sender, args, true);
            case "decline":
                return resolveRequest(sender, args, false);
            default:
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " [gui|reload]");
                return true;
        }
    }

    private boolean openGui(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open the menu.");
            return true;
        }
        Player player = (Player) sender;
        if (!gui.ready(player.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "Your account is still loading, try again in a moment.");
            return true;
        }
        new PersonalMenu(gui, player).open(player);
        return true;
    }

    private boolean resolveRequest(CommandSender sender, String[] args, boolean accept) {
        if (!(sender instanceof Player) || args.length != 2) {
            return true;
        }
        UUID id;
        try {
            id = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            return true;
        }
        if (accept) {
            requests.accept(id, (Player) sender);
        } else {
            requests.decline(id, (Player) sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("gui", "reload");
        }
        return java.util.Collections.emptyList();
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
