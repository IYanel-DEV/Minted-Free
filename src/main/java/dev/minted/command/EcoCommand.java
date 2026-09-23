package dev.minted.command;

import dev.minted.api.MintedEconomy;
import dev.minted.bank.MoneyFormat;
import dev.minted.ledger.LedgerService;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
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
 * {@code /eco give|take|set|reset [player] [amount]} edits the primary balance
 * (the one Vault and placeholders read, the bank by default). Online players are
 * edited through their live account; offline ones are resolved by name and read
 * from storage, so an admin can fix money while the player is away.
 *
 * <p>The balance write is blocking only when the target is not cached, so the
 * whole command runs off the main thread; the feedback comes back on it.
 */
public final class EcoCommand implements CommandExecutor, TabCompleter {

    private final MintedEconomy economy;
    private final MoneyFormat format;
    private final org.bukkit.plugin.Plugin plugin;
    private final LedgerService ledger;
    private final BalanceTransfer transfer;

    public EcoCommand(org.bukkit.plugin.Plugin plugin, MintedEconomy economy, MoneyFormat format,
                      LedgerService ledger, BalanceTransfer transfer) {
        this.plugin = plugin;
        this.economy = economy;
        this.format = format;
        this.ledger = ledger;
        this.transfer = transfer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minted.eco")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.RED + "Balance administration is console-only - "
                    + "give, take, set and reset can only be run from the server console.");
            return true;
        }
        if (!economy.isReady()) {
            sender.sendMessage(ChatColor.RED + "The economy is still starting up.");
            return true;
        }
        if (args.length < 2) {
            usage(sender, label);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if ("export".equals(action) || "import".equals(action)) {
            return transfer.onCommand(sender, action, Arrays.copyOfRange(args, 1, args.length), label);
        }
        if (!isAction(action)) {
            usage(sender, label);
            return true;
        }

        final UUID target = resolve(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Could not resolve player '" + args[1] + "'.");
            return true;
        }

        double amount = -1;
        if ("give".equals(action) || "take".equals(action) || "set".equals(action)) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " " + action + " <player> <amount>");
                return true;
            }
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException notANumber) {
                sender.sendMessage(ChatColor.RED + "That is not a valid amount.");
                return true;
            }
            if (amount <= 0) {
                sender.sendMessage(ChatColor.RED + "Enter an amount greater than zero.");
                return true;
            }
        }

        final double requested = amount;
        final String actionName = action;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final String outcome = apply(actionName, target, requested);
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        sender.sendMessage(outcome);
                    }
                });
            }
        });
        return true;
    }

    private String apply(String action, UUID target, double amount) {
        if ("give".equals(action)) {
            if (!economy.deposit(target, amount)) {
                return ChatColor.RED + "Give failed - " + format.brief(amount)
                        + " would pass the balance limit.";
            }
            ledger.record(target, amount, "admin", null);
            return ChatColor.GREEN + "Gave " + ChatColor.WHITE + format.format(amount)
                    + ChatColor.GREEN + ". New balance: " + ChatColor.WHITE + format.format(economy.getBalance(target));
        }
        if ("take".equals(action)) {
            if (!economy.withdraw(target, amount)) {
                return ChatColor.RED + "Take failed - the player does not have " + format.brief(amount) + ".";
            }
            ledger.record(target, -amount, "admin", null);
            return ChatColor.GREEN + "Took " + ChatColor.WHITE + format.format(amount)
                    + ChatColor.GREEN + ". New balance: " + ChatColor.WHITE + format.format(economy.getBalance(target));
        }
        if ("set".equals(action)) {
            double before = economy.getBalance(target);
            if (!economy.setBalance(target, amount)) {
                return ChatColor.RED + "Set failed - " + format.brief(amount) + " is out of range.";
            }
            ledger.record(target, amount - before, "admin", null);
            return ChatColor.GREEN + "Set " + ChatColor.WHITE + display(target)
                    + ChatColor.GREEN + "'s balance to " + ChatColor.WHITE + format.format(amount) + ChatColor.GREEN + ".";
        }
        double before = economy.getBalance(target);
        if (economy.setBalance(target, 0)) {
            ledger.record(target, -before, "admin", null);
            return ChatColor.GREEN + "Reset " + ChatColor.WHITE + display(target)
                    + ChatColor.GREEN + "'s balance to zero.";
        }
        return ChatColor.RED + "Reset failed.";
    }

    private static boolean isAction(String action) {
        return "give".equals(action) || "take".equals(action)
                || "set".equals(action) || "reset".equals(action);
    }

    private UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        // Offline players: prefer a player we know the real UUID of. On an
        // online-mode server getOfflinePlayer maps a name to its real UUID;
        // on a cracked server the mapping is derived from the name, which only
        // matches accounts this name has actually joined with.
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.getUniqueId();
    }

    private String display(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        return uuid.toString().substring(0, 8);
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "Minted " + ChatColor.GRAY + "economy administration (console only)");
        sender.sendMessage(ChatColor.GRAY + "Balances: /" + label + " [give|take|set|reset] <player> [amount]");
        sender.sendMessage(ChatColor.GRAY + "Migration: /" + label + " [export|import] <file> [confirm]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player || !sender.hasPermission("minted.eco")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return Arrays.asList("give", "take", "set", "reset", "export", "import");
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        return Collections.emptyList();
    }
}