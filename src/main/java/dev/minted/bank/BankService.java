package dev.minted.bank;

import java.util.UUID;

/**
 * Moves money between a player's wallet and their bank. Both sides are ordinary
 * {@link EconomyService} accounts, so the wallet's caching, async loading and
 * batch saving apply to the bank unchanged; this class only owns the
 * wallet-to-bank direction and the cap check that goes with it.
 */
public final class BankService {

    private final EconomyService wallet;
    private final EconomyService bank;
    private final double maxBalance;

    public BankService(EconomyService wallet, EconomyService bank, double maxBalance) {
        this.wallet = wallet;
        this.bank = bank;
        this.maxBalance = maxBalance;
    }

    /** Both stores must be loaded before the bank can be used. */
    public boolean isReady() {
        return wallet.isReady() && bank.isReady();
    }

    /** @return the account pair, or null while either side is still loading */
    public double walletBalance(UUID uuid) {
        BankAccount account = wallet.getCached(uuid);
        return account != null ? account.getBalance() : 0;
    }

    public double bankBalance(UUID uuid) {
        BankAccount account = bank.getCached(uuid);
        return account != null ? account.getBalance() : 0;
    }

    public boolean isLoaded(UUID uuid) {
        return wallet.getCached(uuid) != null && bank.getCached(uuid) != null;
    }

    /** How much more the bank can hold before hitting the cap. */
    public double bankHeadroom(UUID uuid) {
        return maxBalance - bankBalance(uuid);
    }

    /** How much more the wallet can hold before hitting the cap. */
    public double walletHeadroom(UUID uuid) {
        return maxBalance - walletBalance(uuid);
    }

    /** wallet -> bank. False if funds are short or the bank cap would be passed. */
    public boolean deposit(UUID uuid, double amount) {
        return wallet.transfer(wallet.getCached(uuid), bank.getCached(uuid), amount);
    }

    /** bank -> wallet. False if the bank is short or the wallet cap would be passed. */
    public boolean withdraw(UUID uuid, double amount) {
        return wallet.transfer(bank.getCached(uuid), wallet.getCached(uuid), amount);
    }
}
