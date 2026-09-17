package dev.minted.command;

import dev.minted.gui.EconomyMenu;
import dev.minted.gui.GuiContext;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /mstats} opens the server-wide economy stats page: total money in the
 * bank and how much has been burned out of the economy.
 */
public final class StatsCommand implements CommandExecutor {

    private final GuiContext gui;

    public StatsCommand(GuiContext gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open the economy stats.");
            return true;
        }
        if (!sender.hasPermission("minted.stats")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        Player player = (Player) sender;
        if (!gui.ready(player.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "Your account is still loading, try again in a moment.");
            return true;
        }
        new EconomyMenu(gui, player).open(player);
        return true;
    }
}