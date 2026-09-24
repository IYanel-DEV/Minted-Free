package dev.minted.command;

import dev.minted.gui.theme.Design;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Renders the {@code /minted help} page: one bordered chat list of every Minted
 * command, filtered to what the sender may actually run. It borrows the crafting
 * menus' palette instead of inventing a second look - gold money, green player
 * actions, red admin powers, gray hints.
 */
public final class CommandHelp {

    private static final String BORDER = ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH
            + "--------------------------------------------------";
    private static final ChatColor USAGE = ChatColor.GOLD;

    private static final class Entry {
        final String usage;
        final String description;
        final String permission;

        Entry(String usage, String description, String permission) {
            this.usage = usage;
            this.description = description;
            this.permission = permission;
        }

        boolean available(CommandSender sender) {
            return permission == null || sender.hasPermission(permission);
        }
    }

    private static final List<Entry> PLAYER = Arrays.asList(
            new Entry("/minted gui", "Open your personal economy hub", "minted.use"),
            new Entry("/balance [player]", "Check your balance", "minted.balance"),
            new Entry("/pay <player> <amount>", "Send money to another player", "minted.pay"),
            new Entry("/bank", "Deposit and withdraw from your bank", "minted.bank"),
            new Entry("/wallet", "Get and open your wallet", "minted.wallet"),
            new Entry("/sell [amount|all]", "Sell the item in your hand", "minted.sell"),
            new Entry("/eshop [shop]", "Browse every shop", "minted.shop.use"),
            new Entry("/ah", "Auction house - bid, buy and sell", "minted.use"),
            new Entry("/pshop [create|my|browse]", "Open and run your player shop", "minted.shop.own"),
            new Entry("/bounty <player> <amount> [note]", "Put a bounty on a player", "minted.bounty"),
            new Entry("/mhistory", "View your transaction history", "minted.history"),
            new Entry("/mstats", "Server-wide economy statistics", "minted.stats"),
            new Entry("/language [set <code>|list]", "Change your language", "minted.use")
    );

    private static final List<Entry> ADMIN = Arrays.asList(
            new Entry("/minted dashboard", "Open the admin overview", "minted.admin"),
            new Entry("/minted vip [add|remove|list]", "Manage VIPs and their shop commands", "minted.admin"),
            new Entry("/minted reload", "Reload Minted's configuration", "minted.admin"),
            new Entry("/minted report", "Show the integrations report", "minted.admin"),
            new Entry("/minted npc [create|remove|here|list]", "Place and manage bank tellers", "minted.admin"),
            new Entry("/eshop create|delete|edit|reload", "Create and manage the shops", "minted.shop.admin"),
            new Entry("/language global <code>|reload", "Set the server-wide language", "minted.admin"),
            new Entry("/eco give|take|set|reset <player> <amount>", "Edit balances (console only)", "minted.eco")
    );

    private CommandHelp() {
    }

    public static void show(CommandSender sender, String version) {
        List<String> lines = new ArrayList<String>();
        lines.add(BORDER);
        lines.add(Design.MONEY + "" + ChatColor.BOLD + "Minted " + ChatColor.WHITE + "" + ChatColor.BOLD + "Help"
                + Design.LABEL + "  v" + version);
        lines.add(BORDER);
        lines.add(Design.HINT + "Commands below are filtered to what you may run.");

        if (sender instanceof Player) {
            render(sender, lines, PLAYER, Design.IN, "Player commands");
            render(sender, lines, ADMIN, ChatColor.RED, "Admin commands");
        } else {
            render(sender, lines, ADMIN, ChatColor.RED, "Admin commands");
        }

        lines.add("");
        if (sender instanceof Player) {
            lines.add(Design.HINT + "Press " + ChatColor.WHITE + "Tab" + Design.HINT
                    + " to autocomplete while you type.");
            lines.add(Design.HINT + "A command without arguments opens its menu.");
        } else {
            lines.add(Design.HINT + "Run " + ChatColor.WHITE + "/minted reload" + Design.HINT
                    + " or " + ChatColor.WHITE + "/eco ..." + Design.HINT + " from here.");
        }
        lines.add(BORDER);

        sender.sendMessage(lines.toArray(new String[lines.size()]));
    }

    private static void render(CommandSender sender, List<String> lines, List<Entry> entries,
                               ChatColor accent, String title) {
        List<String> shown = new ArrayList<String>();
        int count = 0;
        for (Entry entry : entries) {
            if (!entry.available(sender)) {
                continue;
            }
            count++;
            shown.add(USAGE + "" + ChatColor.BOLD + entry.usage);
            shown.add(Design.LABEL + "   " + Design.HINT + entry.description);
        }
        if (count == 0) {
            return;
        }
        lines.add("");
        lines.add(accent + "" + ChatColor.BOLD + "» " + title + Design.HINT + "  (" + count + ")");
        lines.addAll(shown);
    }
}