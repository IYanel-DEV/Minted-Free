package dev.minted.integration.vault;

import dev.minted.api.MintedEconomy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Makes Minted usable as the server's Vault economy, so every Vault-aware
 * plugin - EssentialsX, shops, Towny, mcMMO, Jobs and the rest - reads and
 * moves money through Minted without a line of integration code in either.
 *
 * <p>All player balances go through the public {@link MintedEconomy}, so Vault
 * and Minted always agree and the "which balance" choice lives in one config
 * option. Vault's <em>named banks</em> are deliberately not supported: Minted's
 * bank is one per-player account, not a set of shared named accounts, so
 * faking it would silently corrupt balances. Vault callers that need a bank
 * account get a clean {@code NOT_IMPLEMENTED}, which every well-behaved plugin
 * already handles.
 */
public final class VaultEconomy implements Economy {

    private final MintedEconomy economy;

    public VaultEconomy(MintedEconomy economy) {
        this.economy = economy;
    }

    @Override
    public boolean isEnabled() {
        return economy.isReady();
    }

    @Override
    public String getName() {
        return "Minted";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return economy.fractionalDigits();
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return economy.currencyNamePlural();
    }

    @Override
    public String currencyNameSingular() {
        return economy.currencyNameSingular();
    }

    // --- accounts -----------------------------------------------------------

    @Override
    public boolean hasAccount(String playerName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return true;
    }

    // --- balances -----------------------------------------------------------

    @Override
    public double getBalance(String playerName) {
        return economy.getBalance(id(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return economy.getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String world) {
        return economy.getBalance(id(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return economy.getBalance(player.getUniqueId());
    }

    @Override
    public boolean has(String playerName, double amount) {
        return economy.has(id(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player.getUniqueId(), amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return economy.has(id(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return economy.has(player.getUniqueId(), amount);
    }

    // --- transactions -------------------------------------------------------

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdraw(id(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdraw(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdraw(id(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdraw(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return deposit(id(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return deposit(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return deposit(id(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return deposit(player.getUniqueId(), amount);
    }

    // --- shared logic -------------------------------------------------------

    private EconomyResponse withdraw(UUID id, double amount) {
        if (amount <= 0) {
            return new EconomyResponse(0, economy.getBalance(id), ResponseType.FAILURE,
                    "Amount must be greater than zero");
        }
        if (!economy.withdraw(id, amount)) {
            return new EconomyResponse(0, economy.getBalance(id), ResponseType.FAILURE,
                    "Insufficient funds");
        }
        return new EconomyResponse(amount, economy.getBalance(id), ResponseType.SUCCESS, null);
    }

    private EconomyResponse deposit(UUID id, double amount) {
        if (amount <= 0) {
            return new EconomyResponse(0, economy.getBalance(id), ResponseType.FAILURE,
                    "Amount must be greater than zero");
        }
        if (!economy.deposit(id, amount)) {
            return new EconomyResponse(0, economy.getBalance(id), ResponseType.FAILURE,
                    "Balance limit reached");
        }
        return new EconomyResponse(amount, economy.getBalance(id), ResponseType.SUCCESS, null);
    }

    @SuppressWarnings("deprecation")
    private static UUID id(String playerName) {
        return Bukkit.getOfflinePlayer(playerName).getUniqueId();
    }

    // --- Vault bank accounts: not supported ---------------------------------

    @Override
    public EconomyResponse createBank(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED,
                "Minted uses per-player banks; Vault named bank accounts are not supported.");
    }
}
