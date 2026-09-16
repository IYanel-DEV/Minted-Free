package dev.minted.command;

import dev.minted.bank.MoneyFormat;
import dev.minted.bank.WalletService;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /balance [player]} - shows your own wallet, or another online player's
 * with the right permission. In physical mode this is the value of the notes
 * they are carrying, read straight from the inventory; in digital mode it is the
 * cached wallet balance. Either way it answers instantly.
 */
public final class BalanceCommand implements CommandExecutor {

    private final WalletService wallet;
    private final MoneyFormat format;

    public BalanceCommand(WalletService wallet, MoneyFormat format) {
        this.wallet = wallet;
        this.format = format;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minted.balance")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (!wallet.isReady()) {
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
        sender.sendMessage(ChatColor.GRAY + "Balance: " + ChatColor.WHITE
                + format.format(wallet.balance((Player) sender)));
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
        sender.sendMessage(ChatColor.GRAY + target.getName() + "'s balance: "
                + ChatColor.WHITE + format.format(wallet.balance(target)));
        return true;
    }
}
