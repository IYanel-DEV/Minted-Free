package dev.minted.api;

import java.util.Map;
import java.util.UUID;

/**
 * Minted's public economy, the stable surface other plugins compile against.
 *
 * <p>Obtain it once and keep the reference:
 * <pre>
 * MintedEconomy economy = MintedAPI.economy();
 * if (economy != null) {
 *     economy.deposit(player.getUniqueId(), 250);
 * }
 * </pre>
 *
 * <p>Minted keeps two balances per player: the {@linkplain #getBank(UUID) bank}
 * and the {@linkplain #getWallet(UUID) wallet}. {@link #getBalance(UUID)} /
 * {@link #deposit(UUID, double)} / {@link #withdraw(UUID, double)} act on
 * whichever one the server selected as primary (the bank by default), which is
 * what Vault-based plugins see; the wallet/bank accessors are for callers that
 * need a specific silo.
 *
 * <p>Never cache a balance: read it when you need it. The primary balance is the
 * one to move money through so plugins agree on a single economy.
 */
public interface MintedEconomy {

    /** False until storage has opened; treat all reads as 0 until then. */
    boolean isReady();

    /** The balance third-party plugins treat as "the" player balance. */
    double getBalance(UUID player);

    /** The player's wallet balance. */
    double getWallet(UUID player);

    /** The player's bank balance. */
    double getBank(UUID player);

    /** @return true if the player's primary balance is at least {@code amount} */
    boolean has(UUID player, double amount);

    /**
     * Adds to the primary balance.
     *
     * @return true if applied; false if the amount is not positive or would
     *         breach the configured maximum
     */
    boolean deposit(UUID player, double amount);

    /**
     * Removes from the primary balance.
     *
     * @return true if applied; false if the amount is not positive or funds are short
     */
    boolean withdraw(UUID player, double amount);

    /**
     * Sets the primary balance outright, for admin tooling.
     *
     * @return true if applied; false if the amount is negative or over the cap
     */
    boolean setBalance(UUID player, double amount);

    /**
     * All stored primary balances, uuid -> balance, overlaid with the live
     * balances of any currently cached accounts so a migration export never
     * loses an unsaved deposit. Blocking; call from an async task only.
     */
    Map<UUID, Double> snapshot();

    /** Formats an amount the way Minted shows it, e.g. {@code $1,250 Coins}. */
    String format(double amount);

    int fractionalDigits();

    String currencyNameSingular();

    String currencyNamePlural();
}
