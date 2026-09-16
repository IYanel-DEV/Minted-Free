package dev.minted.bank;

import dev.minted.banknote.NoteInventory;

import org.bukkit.entity.Player;

/**
 * One side of a trade's money: something you can read a balance from, charge, or
 * pay into. A digital purse is a {@link BankAccount}; a physical purse is the
 * signed notes a player carries. Every shop and wallet money movement goes
 * through this one interface, so there is exactly one money-in and one money-out
 * path regardless of which economy is in play.
 */
public interface Purse {

    double balance();

    /** @return true if the amount was taken; false if the purse is short */
    boolean charge(double amount);

    /** @return true if the amount was paid in; false only if a digital cap refused it */
    boolean credit(double amount);

    /** A digital purse backed by a bank/wallet account, honouring its cap. */
    static Purse digital(final BankAccount account) {
        return new Purse() {
            @Override
            public double balance() {
                return account.getBalance();
            }

            @Override
            public boolean charge(double amount) {
                return account.withdraw(amount);
            }

            @Override
            public boolean credit(double amount) {
                return account.deposit(amount);
            }
        };
    }

    /** A physical purse: the value is whatever signed notes the player holds. */
    static Purse physical(final Player player, final NoteInventory notes) {
        return new Purse() {
            @Override
            public double balance() {
                return notes.value(player);
            }

            @Override
            public boolean charge(double amount) {
                return notes.charge(player, amount);
            }

            @Override
            public boolean credit(double amount) {
                notes.credit(player, amount);
                return true;
            }
        };
    }
}
