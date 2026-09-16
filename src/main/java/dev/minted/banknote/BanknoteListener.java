package dev.minted.banknote;

import dev.minted.bank.BankAccount;
import dev.minted.bank.EconomyService;
import dev.minted.bank.MoneyFormat;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Redeems a banknote when a player right-clicks holding one. The interaction is
 * handled on the main thread, but the balance change only touches the in-memory
 * account; the actual disk write is left to the async batch saver.
 */
public final class BanknoteListener implements Listener {

    private final EconomyService economy;
    private final BanknoteManager banknotes;
    private final MoneyFormat format;

    public BanknoteListener(EconomyService economy, BanknoteManager banknotes, MoneyFormat format) {
        this.economy = economy;
        this.banknotes = banknotes;
        this.format = format;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isRightClick(event.getAction())) {
            return;
        }
        ItemStack held = event.getItem();
        if (!banknotes.isBanknote(held)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        BankAccount account = economy.getCached(player.getUniqueId());
        if (account == null) {
            player.sendMessage(ChatColor.RED + "Your account is still loading, try again in a moment.");
            return;
        }

        double before = account.getBalance();
        if (!banknotes.redeem(account, held)) {
            player.sendMessage(ChatColor.RED + "That banknote could not be redeemed.");
            return;
        }

        consumeOne(player, held);
        player.sendMessage(ChatColor.GREEN + "Redeemed " + ChatColor.WHITE
                + format.format(account.getBalance() - before) + ChatColor.GREEN + ".");
    }

    private void consumeOne(Player player, ItemStack held) {
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInHand(held);
        } else {
            player.getInventory().setItemInHand(null);
        }
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }
}
