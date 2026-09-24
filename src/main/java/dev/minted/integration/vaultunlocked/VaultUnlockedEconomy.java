package dev.minted.integration.vaultunlocked;

import dev.minted.api.MintedEconomy;

import net.milkbowl.vault2.economy.AccountPermission;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import net.milkbowl.vault2.economy.EconomyResponse.ResponseType;

import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Makes Minted usable as the server's VaultUnlocked economy - the {@code vault2}
 * API behind the VaultUnlocked plugin - alongside the classic Vault provider.
 * Same contract as {@code VaultEconomy}: every balance goes through the public
 * {@link MintedEconomy}, so all APIs always agree with Minted.
 *
 * <p>Minted keeps one balance per player, so shared accounts, named bank
 * accounts, account deletion/renaming and second currencies answer honestly
 * ({@code false} / {@code NOT_IMPLEMENTED}) instead of faking them. World and
 * currency parameters collapse onto Minted's primary balance - a call in a
 * currency Minted does not have fails with a clear message rather than
 * silently touching the wrong pot.
 *
 * <p>This class is only ever loaded once the caller has confirmed the
 * VaultUnlocked API classes exist (see {@link VaultUnlockedHook}); on a server
 * without VaultUnlocked it is never referenced.
 */
public final class VaultUnlockedEconomy implements Economy {

    private final MintedEconomy economy;

    public VaultUnlockedEconomy(MintedEconomy economy) {
        this.economy = economy;
    }

    // --- economy plugin info ---------------------------------------------------

    @Override
    public boolean isEnabled() {
        return economy.isReady();
    }

    @Override
    public String getName() {
        return "Minted";
    }

    @Override
    public boolean hasSharedAccountSupport() {
        return false;
    }

    @Override
    public boolean hasMultiCurrencySupport() {
        return false;
    }

    // --- currency --------------------------------------------------------------

    @Override
    public int fractionalDigits(String pluginName) {
        return economy.fractionalDigits();
    }

    @Override
    public String format(BigDecimal amount) {
        return economy.format(amount.doubleValue());
    }

    @Override
    public String format(String pluginName, BigDecimal amount) {
        return format(amount);
    }

    @Override
    public String format(BigDecimal amount, String currency) {
        // One currency: the configured one, whatever the caller asked for.
        return format(amount);
    }

    @Override
    public String format(String pluginName, BigDecimal amount, String currency) {
        return format(amount);
    }

    @Override
    public boolean hasCurrency(String currency) {
        return currency != null && (currency.equalsIgnoreCase(economy.currencyNamePlural())
                || currency.equalsIgnoreCase(economy.currencyNameSingular()));
    }

    @Override
    public String getDefaultCurrency(String pluginName) {
        return economy.currencyNamePlural();
    }

    @Override
    public String defaultCurrencyNamePlural(String pluginName) {
        return economy.currencyNamePlural();
    }

    @Override
    public String defaultCurrencyNameSingular(String pluginName) {
        return economy.currencyNameSingular();
    }

    @Override
    public Collection<String> currencies() {
        return Collections.singletonList(economy.currencyNamePlural());
    }

    // --- accounts ---------------------------------------------------------------
    // Minted accounts are implicit: one per player, created when they join.
    // Every create reports success so callers proceed, and hasAccount is always
    // true - the same stance the classic Vault adapter takes.

    @Override
    public boolean createAccount(UUID accountID, String name) {
        return true;
    }

    @Override
    public boolean createAccount(UUID accountID, String name, boolean player) {
        return true;
    }

    @Override
    public boolean createAccount(UUID accountID, String name, String worldName) {
        return true;
    }

    @Override
    public boolean createAccount(UUID accountID, String name, String worldName, boolean player) {
        return true;
    }

    @Override
    public Map<UUID, String> getUUIDNameMap() {
        // Names live in Minted's name history, not on MintedEconomy; callers
        // that need display names should read them from their own source.
        return Collections.emptyMap();
    }

    @Override
    public Optional<String> getAccountName(UUID accountID) {
        return Optional.ofNullable(Bukkit.getOfflinePlayer(accountID).getName());
    }

    @Override
    public boolean hasAccount(UUID accountID) {
        return true;
    }

    @Override
    public boolean hasAccount(UUID accountID, String worldName) {
        return true;
    }

    @Override
    public boolean renameAccount(UUID accountID, String name) {
        return false;
    }

    @Override
    public boolean renameAccount(String pluginName, UUID accountID, String name) {
        return false;
    }

    @Override
    public boolean deleteAccount(String pluginName, UUID accountID) {
        return false;
    }

    @Override
    public boolean accountSupportsCurrency(String pluginName, UUID accountID, String currency) {
        return hasCurrency(currency);
    }

    @Override
    public boolean accountSupportsCurrency(String pluginName, UUID accountID, String currency, String world) {
        return hasCurrency(currency);
    }

    // --- balances ----------------------------------------------------------------

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID) {
        return BigDecimal.valueOf(economy.getBalance(accountID));
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID, String world) {
        // One balance per player; worlds are not a separate silo in Minted.
        return getBalance(pluginName, accountID);
    }

    @Override
    public BigDecimal getBalance(String pluginName, UUID accountID, String world, String currency) {
        return hasCurrency(currency) ? getBalance(pluginName, accountID) : BigDecimal.ZERO;
    }

    @Override
    public boolean has(String pluginName, UUID accountID, BigDecimal amount) {
        return economy.has(accountID, amount.doubleValue());
    }

    @Override
    public boolean has(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return has(pluginName, accountID, amount);
    }

    @Override
    public boolean has(String pluginName, UUID accountID, String worldName, String currency, BigDecimal amount) {
        return hasCurrency(currency) && has(pluginName, accountID, amount);
    }

    // --- money movement ----------------------------------------------------------

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, BigDecimal amount) {
        return withdraw(accountID, amount);
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return withdraw(accountID, amount);
    }

    @Override
    public EconomyResponse withdraw(String pluginName, UUID accountID, String worldName,
                                    String currency, BigDecimal amount) {
        return currencyOk(currency) ? withdraw(accountID, amount) : wrongCurrency(currency);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, BigDecimal amount) {
        return deposit(accountID, amount);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, String worldName, BigDecimal amount) {
        return deposit(accountID, amount);
    }

    @Override
    public EconomyResponse deposit(String pluginName, UUID accountID, String worldName,
                                   String currency, BigDecimal amount) {
        return currencyOk(currency) ? deposit(accountID, amount) : wrongCurrency(currency);
    }

    private EconomyResponse withdraw(UUID id, BigDecimal amount) {
        double value = amount.doubleValue();
        if (value <= 0) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.valueOf(economy.getBalance(id)),
                    ResponseType.FAILURE, "Amount must be greater than zero");
        }
        if (!economy.withdraw(id, value)) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.valueOf(economy.getBalance(id)),
                    ResponseType.FAILURE, "Insufficient funds");
        }
        return new EconomyResponse(amount, BigDecimal.valueOf(economy.getBalance(id)),
                ResponseType.SUCCESS, "");
    }

    private EconomyResponse deposit(UUID id, BigDecimal amount) {
        double value = amount.doubleValue();
        if (value <= 0) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.valueOf(economy.getBalance(id)),
                    ResponseType.FAILURE, "Amount must be greater than zero");
        }
        if (!economy.deposit(id, value)) {
            return new EconomyResponse(BigDecimal.ZERO, BigDecimal.valueOf(economy.getBalance(id)),
                    ResponseType.FAILURE, "Balance limit reached");
        }
        return new EconomyResponse(amount, BigDecimal.valueOf(economy.getBalance(id)),
                ResponseType.SUCCESS, "");
    }

    /** Null/empty means "the default currency" per the vault2 contract. */
    private boolean currencyOk(String currency) {
        return currency == null || currency.isEmpty() || hasCurrency(currency);
    }

    private EconomyResponse wrongCurrency(String currency) {
        return new EconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, ResponseType.NOT_IMPLEMENTED,
                "Minted uses a single currency: " + economy.currencyNamePlural()
                        + " (asked for '" + currency + "')");
    }

    // --- shared accounts: not supported -------------------------------------------

    @Override
    public boolean createSharedAccount(String pluginName, UUID accountID, String name, UUID owner) {
        return false;
    }

    @Override
    public boolean isAccountOwner(String pluginName, UUID accountID, UUID uuid) {
        // Nobody owns anything but their own account.
        return accountID != null && accountID.equals(uuid);
    }

    @Override
    public boolean setOwner(String pluginName, UUID accountID, UUID uuid) {
        return false;
    }

    @Override
    public boolean isAccountMember(String pluginName, UUID accountID, UUID uuid) {
        return accountID != null && accountID.equals(uuid);
    }

    @Override
    public boolean addAccountMember(String pluginName, UUID accountID, UUID uuid) {
        return false;
    }

    @Override
    public boolean addAccountMember(String pluginName, UUID accountID, UUID uuid,
                                    AccountPermission... initialPermissions) {
        return false;
    }

    @Override
    public boolean removeAccountMember(String pluginName, UUID accountID, UUID uuid) {
        return false;
    }

    @Override
    public boolean hasAccountPermission(String pluginName, UUID accountID, UUID uuid,
                                        AccountPermission permission) {
        // The owner holds every permission on their own account; there are no others.
        return accountID != null && accountID.equals(uuid);
    }

    @Override
    public boolean updateAccountPermission(String pluginName, UUID accountID, UUID uuid,
                                           AccountPermission permission, boolean value) {
        return false;
    }
}