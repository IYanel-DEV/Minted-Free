package dev.minted.banknote;

import dev.minted.bank.BankAccount;
import dev.minted.bank.CombatLock;
import dev.minted.bank.EconomyService;
import dev.minted.bank.MoneyFormat;
import dev.minted.compat.InteractionHand;
import dev.minted.lang.Messages;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Right-clicking a banknote in hand. In physical mode the whole held stack is
 * banked; in digital mode a single note is redeemed into the wallet balance, the
 * classic behaviour. Either way the balance change only touches the in-memory
 * account and the async batch saver writes it out later.
 */
public final class BanknoteListener implements Listener {

    private final EconomyService walletEconomy;
    private final EconomyService bankEconomy;
    private final BanknoteManager banknotes;
    private final NoteInventory notes;
    private final MoneyFormat format;
    private final Messages messages;
    private final boolean physical;
    private final CombatLock combatLock;

    public BanknoteListener(EconomyService walletEconomy, EconomyService bankEconomy, BanknoteManager banknotes,
                            NoteInventory notes, MoneyFormat format, Messages messages, boolean physical,
                            CombatLock combatLock) {
        this.walletEconomy = walletEconomy;
        this.bankEconomy = bankEconomy;
        this.banknotes = banknotes;
        this.notes = notes;
        this.format = format;
        this.messages = messages;
        this.physical = physical;
        this.combatLock = combatLock;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isRightClick(event.getAction()) || InteractionHand.isOffHand(event)) {
            return;
        }
        ItemStack held = event.getItem();
        if (!banknotes.isBanknote(held)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (physical) {
            bankHeld(player);
        } else {
            redeem(player, held);
        }
    }

    // Physical mode: bank the whole stack in the main hand at once.
    private void bankHeld(Player player) {
        int locked = combatLock.secondsLeft(player);
        if (locked > 0) {
            messages.send(player, "bank.combat-lock", "seconds", String.valueOf(locked));
            return;
        }
        BankAccount account = bankEconomy.getCached(player.getUniqueId());
        if (account == null) {
            player.sendMessage(ChatColor.RED + "Your account is still loading, try again in a moment.");
            return;
        }
        double[] result = notes.depositHeld(player, account);
        double banked = result[0];
        double left = result[1];
        if (banked <= 0) {
            messages.send(player, "bank.full");
            return;
        }
        if (left > 0) {
            messages.send(player, "bank.deposited-partial", "banked", format.format(banked), "left", format.format(left));
        } else {
            messages.send(player, "bank.deposited-notes", "amount", format.format(banked));
        }
    }

    // Digital mode: redeem one note into the wallet balance.
    private void redeem(Player player, ItemStack held) {
        BankAccount account = walletEconomy.getCached(player.getUniqueId());
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
