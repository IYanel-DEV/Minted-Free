package dev.minted.banknote;

import dev.minted.bank.BankAccount;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Bridges banknotes and balances. Paper is the material: it is the one item
 * that has existed under the same name from 1.8 all the way to 1.26, so a note
 * minted on any supported server is recognisable on any other.
 */
public final class BanknoteManager {

    private final BanknoteParser parser;

    public BanknoteManager(BanknoteParser parser) {
        this.parser = parser;
    }

    /**
     * Pulls the amount out of the account and mints a note item for it.
     *
     * @return the note, or null if the balance could not cover the amount
     */
    public ItemStack withdraw(BankAccount account, double amount) {
        if (!account.withdraw(amount)) {
            return null;
        }
        ItemStack item = new ItemStack(Material.PAPER);
        parser.write(item, new Banknote(amount, UUID.randomUUID().toString()));
        return item;
    }

    /**
     * Verifies the note and, if genuine, credits its value to the account. The
     * caller consumes the item only when this returns true.
     *
     * @return true if the note was valid and its value was banked
     */
    public boolean redeem(BankAccount account, ItemStack item) {
        Banknote note = parser.read(item);
        return note != null && account.deposit(note.getDenomination());
    }

    public boolean isBanknote(ItemStack item) {
        return parser.read(item) != null;
    }
}
