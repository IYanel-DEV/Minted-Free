package dev.minted.command;

import dev.minted.MintedPlugin;
import dev.minted.gui.GuiContext;
import dev.minted.gui.PersonalMenu;
import dev.minted.integration.IntegrationReport;
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
            case "report":
                return report(sender);
            case "gui":
                return openGui(sender);
            case "accept":
                return resolveRequest(sender, args, true);
            case "decline":
                return resolveRequest(sender, args, false);
            default:
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " [gui|reload|report]");
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

    private boolean report(CommandSender sender) {
        if (!sender.hasPermission("minted.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        IntegrationReport r = plugin.integrationReport();
        sender.sendMessage(ChatColor.GOLD + "Minted " + ChatColor.GRAY + "integrations report");
        sender.sendMessage(line("API + events", r.apiReady));
        sender.sendMessage(ChatColor.GRAY + "Primary balance: " + ChatColor.WHITE + r.primary);
        sender.sendMessage(vaultLine(r));
        sender.sendMessage(papiLine(r));
        sender.sendMessage(essentialsLine(r));
        return true;
    }

    private String line(String label, boolean active) {
        return ChatColor.GRAY + label + ": " + (active ? ChatColor.GREEN + "ready" : ChatColor.RED + "off");
    }

    private String vaultLine(IntegrationReport r) {
        if (!r.vaultInstalled) {
            return ChatColor.GRAY + "Vault: " + ChatColor.RED + "not installed";
        }
        return ChatColor.GRAY + "Vault: " + (r.vaultRegistered ? ChatColor.GREEN + "Minted is the economy provider"
                : ChatColor.RED + "installed but not registered (Vault was unavailable)");
    }

    private String papiLine(IntegrationReport r) {
        if (!r.papiInstalled) {
            return ChatColor.GRAY + "PlaceholderAPI: " + ChatColor.RED + "not installed";
        }
        return ChatColor.GRAY + "PlaceholderAPI: " + (r.papiRegistered ? ChatColor.GREEN + "%minted_*% registered"
                : ChatColor.RED + "installed but not registered");
    }

    private String essentialsLine(IntegrationReport r) {
        if (!r.essentialsInstalled) {
            return ChatColor.GRAY + "Essentials: " + ChatColor.RED + "not installed";
        }
        if (r.essentialsEconomy) {
            return ChatColor.YELLOW + "Essentials: its own economy is still active - disable it ("
                    + "economy: disabled in Essentials' config.yml) to run it entirely on Minted.";
        }
        return ChatColor.GREEN + "Essentials: present, its built-in economy is off.";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("gui", "reload", "report");
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
