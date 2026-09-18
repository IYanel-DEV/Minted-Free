package dev.minted.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired after any wallet or bank balance changes, whichever code path caused it
 * - a menu, a shop trade, interest, a loan pay-out or a third-party plugin
 * calling {@link dev.minted.api.MintedEconomy}. The event is informational: it
 * fires after the change has been applied and cannot be cancelled.
 *
 * <p>Listeners are always called on the main server thread, even for changes
 * that were applied off it (interest runs async), so it is safe to touch the
 * Bukkit API from a handler.
 */
public class MintedBalanceChangeEvent extends Event {

    /** Which of a player's two balances changed. */
    public enum Account {
        /** The wallet: physical banknotes in survival mode, a digital balance otherwise. */
        WALLET,
        /** The bank: always a digital account. */
        BANK
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final Account account;
    private final double oldBalance;
    private final double newBalance;

    public MintedBalanceChangeEvent(UUID player, Account account, double oldBalance, double newBalance) {
        this.player = player;
        this.account = account;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
    }

    public UUID getPlayer() {
        return player;
    }

    public Account getAccount() {
        return account;
    }

    public double getOldBalance() {
        return oldBalance;
    }

    public double getNewBalance() {
        return newBalance;
    }

    /** Positive for a deposit, negative for a withdrawal. */
    public double getDelta() {
        return newBalance - oldBalance;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
