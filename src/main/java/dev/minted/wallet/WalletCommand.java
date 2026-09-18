package dev.minted.wallet;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /wallet} - get a wallet if you do not own one yet, and open its menu.
 * When you already hold a wallet it just opens it; when you own one elsewhere in
 * the inventory it is moved to your hand first. A fresh wallet is only given
 * out when both of those are false, so spamming the command cannot flood an
 * inventory with empty bags.
 */
public final class WalletCommand implements CommandExecutor {

    private final WalletManager wallets;

    public WalletCommand(WalletManager wallets) {
        this.wallets = wallets;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players have a wallet.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("minted.wallet")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use a wallet.");
            return true;
        }
        wallets.open(player);
        return true;
    }
}