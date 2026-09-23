package dev.minted.command;

import dev.minted.gui.GuiContext;
import dev.minted.gui.HistoryMenu;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /mhistory} - opens the player's own transaction history screen. The
 * ledger rows are read off the main thread inside {@link HistoryMenu}, so this
 * command never blocks on the database.
 */
public final class HistoryCommand implements CommandExecutor {

    private final GuiContext gui;

    public HistoryCommand(GuiContext gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players have a history.");
            return true;
        }
        if (!sender.hasPermission("minted.history")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        HistoryMenu.open(gui, (Player) sender);
        return true;
    }
}