package dev.minted.api;

import dev.minted.bank.BankAccount;
import dev.minted.bank.EconomyService;
import dev.minted.bank.MoneyFormat;

import java.util.UUID;

/**
 * The concrete economy behind {@link MintedAPI}. Registered as a Bukkit service
 * once storage is opening; every call reads through the live account cache and
 * falls back to a storage read for players who are not loaded, so it is correct
 * for offline players too.
 */
public final class MintedEconomyImpl implements MintedEconomy {

    /** Which balance the primary accessors operate on. */
    public enum Primary {
        WALLET,
        BANK
    }

    private final EconomyService wallet;
    private final EconomyService bank;
    private final MoneyFormat format;
    private final Primary primary;

    public MintedEconomyImpl(EconomyService wallet, EconomyService bank, MoneyFormat format, Primary primary) {
        this.wallet = wallet;
        this.bank = bank;
        this.format = format;
        this.primary = primary;
    }

    @Override
    public boolean isReady() {
        return wallet.isReady() && bank.isReady();
    }

    @Override
    public double getBalance(UUID player) {
        return account(player).getBalance();
    }

    @Override
    public double getWallet(UUID player) {
        return wallet.account(player).getBalance();
    }

    @Override
    public double getBank(UUID player) {
        return bank.account(player).getBalance();
    }

    @Override
    public boolean has(UUID player, double amount) {
        return account(player).getBalance() >= amount;
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        return account(player).deposit(amount);
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        return account(player).withdraw(amount);
    }

    @Override
    public boolean setBalance(UUID player, double amount) {
        return account(player).setBalance(amount);
    }

    @Override
    public String format(double amount) {
        return format.format(amount);
    }

    @Override
    public int fractionalDigits() {
        return format.fractionalDigits();
    }

    @Override
    public String currencyNameSingular() {
        return format.singular();
    }

    @Override
    public String currencyNamePlural() {
        return format.name();
    }

    private BankAccount account(UUID player) {
        return (primary == Primary.BANK ? bank : wallet).account(player);
    }
}
