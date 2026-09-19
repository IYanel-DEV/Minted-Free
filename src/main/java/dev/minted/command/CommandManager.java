package dev.minted.command;

import dev.minted.MintedPlugin;
import dev.minted.gui.GuiContext;
import dev.minted.gui.PersonalMenu;
import dev.minted.integration.IntegrationReport;
import dev.minted.integration.npc.BankNpc;
import dev.minted.integration.npc.NpcManager;
import dev.minted.request.RequestService;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Registers and dispatches {@code /minted}. Besides {@code reload} it opens the
 * personal GUI and carries the accept/decline actions behind the clickable
 * request message. The {@code npc} subtree manages the bank tellers when
 * ProtocolLib is installed; otherwise it explains why they are unavailable.
 */
public final class CommandManager implements CommandExecutor, TabCompleter {

    private final MintedPlugin plugin;
    private final GuiContext gui;
    private final RequestService requests;
    private final NpcManager npcs;

    public CommandManager(MintedPlugin plugin, GuiContext gui, RequestService requests, NpcManager npcs) {
        this.plugin = plugin;
        this.gui = gui;
        this.requests = requests;
        this.npcs = npcs;
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
            case "npc":
                return npc(sender, args);
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
        sender.sendMessage(npcsLine(r));
        return true;
    }

    private String npcsLine(IntegrationReport r) {
        if (!r.protocolLibInstalled) {
            return ChatColor.GRAY + "Bank tellers: " + ChatColor.RED + "not installed (no ProtocolLib)";
        }
        if (r.npcsActive) {
            return ChatColor.GREEN + "Bank tellers: ProtocolLib hooked, /minted npc available.";
        }
        return ChatColor.YELLOW + "Bank tellers: ProtocolLib present but the hook failed to start.";
    }

    /** {@code /minted npc create|remove|here|list}. */
    private boolean npc(CommandSender sender, String[] args) {
        if (npcs == null) {
            sender.sendMessage(ChatColor.RED + "Bank tellers are not available: install ProtocolLib "
                    + "and set integrations.npcs.enabled to true.");
            return true;
        }
        if (args.length == 1) {
            sender.sendMessage(ChatColor.GOLD + "Minted " + ChatColor.GRAY + "bank tellers");
            sender.sendMessage(ChatColor.GRAY + "Usage: /minted npc [create <name> [skinPlayer]|remove <name>|here <name>|list]");
            return true;
        }
        if (!sender.hasPermission("minted.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(sub)) {
            return npcList(sender);
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can place or move a teller.");
            return true;
        }
        Player player = (Player) sender;
        if ("create".equals(sub)) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Usage: /minted npc create <name> [skinPlayer]");
                return true;
            }
            String error = npcs.create(player, args[2], args.length > 3 ? args[3] : null);
            if (error != null) {
                player.sendMessage(ChatColor.RED + error);
            } else {
                player.sendMessage(ChatColor.GREEN + "Teller '" + args[2] + "' placed where you stand. "
                        + ChatColor.GRAY + "Right-click it to open the bank.");
                player.sendMessage(ChatColor.GRAY + "To use a real player's skin: "
                        + ChatColor.WHITE + "/minted npc create " + args[2] + " <playerName>");
            }
            return true;
        }
        if ("remove".equals(sub) || "here".equals(sub)) {
            if (args.length != 3) {
                player.sendMessage(ChatColor.RED + "Usage: /minted npc " + sub + " <name>");
                return true;
            }
            String error = "here".equals(sub)
                    ? npcs.here(player, args[2])
                    : npcs.remove(args[2]);
            if (error != null) {
                player.sendMessage(ChatColor.RED + error);
            } else {
                player.sendMessage(ChatColor.GREEN + "Teller '" + args[2] + "' "
                        + ("here".equals(sub) ? "moved here." : "removed."));
            }
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Usage: /minted npc [create <name> [skinPlayer]|remove <name>|here <name>|list]");
        return true;
    }

    private boolean npcList(CommandSender sender) {
        List<BankNpc> all = npcs.npcs();
        sender.sendMessage(ChatColor.GOLD + "Minted " + ChatColor.GRAY + "bank tellers ("
                + all.size() + ")");
        if (all.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "None placed yet. Stand where you want one and run "
                    + ChatColor.WHITE + "/minted npc create <name>");
            return true;
        }
        for (BankNpc npc : all) {
            sender.sendMessage(ChatColor.GRAY + npc.name() + ChatColor.DARK_GRAY + " | "
                    + ChatColor.WHITE + npc.world() + " " + Math.round(npc.x()) + " "
                    + Math.round(npc.y()) + " " + Math.round(npc.z()));
        }
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
            return Arrays.asList("gui", "reload", "report", "npc");
        }
        if (args.length == 2 && "npc".equalsIgnoreCase(args[0])) {
            return Arrays.asList("create", "remove", "here", "list");
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("here"))) {
            if (npcs == null) {
                return Collections.emptyList();
            }
            List<String> names = new ArrayList<String>();
            for (BankNpc npc : npcs.npcs()) {
                names.add(npc.name());
            }
            return names;
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
