package dev.minted.command;

import dev.minted.bank.BankAccount;
import dev.minted.bank.EconomyService;
import dev.minted.bank.MoneyFormat;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /balance [player]} - shows your own balance, or another online
 * player's with the right permission. Reads straight from the in-memory cache,
 * so it answers instantly.
 */
public final class BalanceCommand implements CommandExecutor {

    private final EconomyService economy;
    private final MoneyFormat format;

    public BalanceCommand(EconomyService economy, MoneyFormat format) {
        this.economy = economy;
        this.format = format;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minted.balance")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (!economy.isReady()) {
            sender.sendMessage(ChatColor.RED + "The economy is still starting up.");
            return true;
        }
        if (args.length >= 1) {
            return showOther(sender, args[0]);
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Console must specify a player: /" + label + " <player>");
            return true;
        }
        BankAccount account = economy.getCached(((Player) sender).getUniqueId());
        if (account == null) {
            sender.sendMessage(ChatColor.RED + "Your account is still loading, try again in a moment.");
            return true;
        }
        sender.sendMessage(ChatColor.GRAY + "Balance: " + ChatColor.WHITE + format.format(account.getBalance()));
        return true;
    }

    private boolean showOther(CommandSender sender, String name) {
        if (!sender.hasPermission("minted.balance.others")) {
            sender.sendMessage(ChatColor.RED + "You may only check your own balance.");
            return true;
        }
        Player target = sender.getServer().getPlayerExact(name);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player is not online.");
            return true;
        }
        BankAccount account = economy.getCached(target.getUniqueId());
        if (account == null) {
            sender.sendMessage(ChatColor.RED + "That player's account is still loading, try again in a moment.");
            return true;
        }
        sender.sendMessage(ChatColor.GRAY + target.getName() + "'s balance: "
                + ChatColor.WHITE + format.format(account.getBalance()));
        return true;
    }
}
