package dev.minted.command;

import dev.minted.bank.MoneyFormat;
import dev.minted.bank.WalletService;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /pay <player> <amount>} - moves wallet money to another online player.
 * In physical mode the sender's notes are consumed and the same value is paid to
 * the receiver as cash; in digital mode it is an in-memory balance transfer. The
 * sender is told at once and the batch saver persists any digital change later.
 */
public final class PayCommand implements CommandExecutor {

    private final WalletService wallet;
    private final MoneyFormat format;

    public PayCommand(WalletService wallet, MoneyFormat format) {
        this.wallet = wallet;
        this.format = format;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can pay.");
            return true;
        }
        if (!sender.hasPermission("minted.pay")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <player> <amount>");
            return true;
        }
        if (!wallet.isReady()) {
            sender.sendMessage(ChatColor.RED + "The economy is still starting up.");
            return true;
        }

        Player from = (Player) sender;
        Player target = from.getServer().getPlayerExact(args[0]);
        if (target == null || target.equals(from)) {
            sender.sendMessage(ChatColor.RED + "Pick a different online player.");
            return true;
        }

        double amount = parseAmount(args[1]);
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Enter an amount greater than zero.");
            return true;
        }

        if (!wallet.transfer(from, target, amount)) {
            sender.sendMessage(ChatColor.RED + "Transfer failed - check your balance and their limit.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "Paid " + ChatColor.WHITE + format.format(amount)
                + ChatColor.GREEN + " to " + target.getName() + ".");
        target.sendMessage(ChatColor.GREEN + "Received " + ChatColor.WHITE + format.format(amount)
                + ChatColor.GREEN + " from " + from.getName() + ".");
        return true;
    }

    private double parseAmount(String raw) {
        try {
            double value = Double.parseDouble(raw);
            return Double.isFinite(value) ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
