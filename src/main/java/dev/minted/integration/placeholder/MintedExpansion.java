package dev.minted.integration.placeholder;

import dev.minted.api.MintedEconomy;
import dev.minted.bank.EconomyStats;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.OfflinePlayer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * PlaceholderAPI expansion exposing Minted's numbers to chat, scoreboards,
 * TAB, holograms and anything else that parses placeholders.
 *
 * <p>Supported identifiers (all prefixed with the {@code %minted_...%} player
 * context):
 *
 * <ul>
 *   <li>{@code balance} / {@code balance_formatted} - the primary balance</li>
 *   <li>{@code wallet} / {@code wallet_formatted} - the wallet balance</li>
 *   <li>{@code bank} / {@code bank_formatted} - the bank balance</li>
 *   <li>{@code currency_name} / {@code currency_singular}</li>
 *   <li>{@code banked} / {@code burned} / {@code accounts} - server totals</li>
 * </ul>
 */
public final class MintedExpansion extends PlaceholderExpansion {

    private static final List<String> PLACEHOLDERS = Arrays.asList(
            "%minted_balance%", "%minted_balance_formatted%",
            "%minted_wallet%", "%minted_wallet_formatted%",
            "%minted_bank%", "%minted_bank_formatted%",
            "%minted_currency_name%", "%minted_currency_singular%",
            "%minted_banked%", "%minted_burned%", "%minted_accounts%");

    private final MintedEconomy economy;
    private final EconomyStats stats;
    private final String version;

    public MintedExpansion(MintedEconomy economy, EconomyStats stats, String version) {
        this.economy = economy;
        this.stats = stats;
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "minted";
    }

    @Override
    public String getAuthor() {
        return "iyanel-dev";
    }

    @Override
    public String getVersion() {
        return version;
    }

    /** The expansion lives inside Minted, so /papi reload must not drop it. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public List<String> getPlaceholders() {
        return PLACEHOLDERS;
    }

    @Override
    public String onRequest(final OfflinePlayer player, final String params) {
        // A placeholder that throws must degrade to nothing, never break the
        // scoreboard or chat line it lives in.
        try {
            return resolve(player, params);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String resolve(OfflinePlayer player, String params) {
        if (player == null) {
            return null;
        }
        UUID id = player.getUniqueId();
        String key = params == null ? "" : params.toLowerCase(Locale.ROOT);
        if (key.equals("balance")) {
            return plain(economy.getBalance(id));
        }
        if (key.equals("balance_formatted")) {
            return economy.format(economy.getBalance(id));
        }
        if (key.equals("wallet")) {
            return plain(economy.getWallet(id));
        }
        if (key.equals("wallet_formatted")) {
            return economy.format(economy.getWallet(id));
        }
        if (key.equals("bank")) {
            return plain(economy.getBank(id));
        }
        if (key.equals("bank_formatted")) {
            return economy.format(economy.getBank(id));
        }
        if (key.equals("currency_name")) {
            return economy.currencyNamePlural();
        }
        if (key.equals("currency_singular")) {
            return economy.currencyNameSingular();
        }
        if (key.equals("banked")) {
            return economy.format(stats.banked());
        }
        if (key.equals("burned")) {
            return economy.format(stats.burned());
        }
        if (key.equals("accounts")) {
            return String.valueOf(stats.accountCount());
        }
        return null;
    }

    private String plain(double value) {
        return String.format(Locale.ROOT, "%." + economy.fractionalDigits() + "f", value);
    }
}