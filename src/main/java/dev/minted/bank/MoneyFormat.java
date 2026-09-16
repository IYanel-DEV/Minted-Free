package dev.minted.bank;

import org.bukkit.configuration.file.FileConfiguration;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Turns raw balances into the display strings used in chat, driven by the
 * {@code currency} section of the config (name, singular, symbol).
 */
public final class MoneyFormat {

    private final String name;
    private final String singular;
    private final String symbol;
    private final DecimalFormat number;

    private MoneyFormat(String name, String singular, String symbol) {
        this.name = name;
        this.singular = singular;
        this.symbol = symbol;
        this.number = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.ROOT));
    }

    public static MoneyFormat from(FileConfiguration config) {
        return new MoneyFormat(
                config.getString("currency.name", "Coins"),
                config.getString("currency.singular", "Coin"),
                config.getString("currency.symbol", "$"));
    }

    /** e.g. {@code $1,250 Coins}, or {@code $1 Coin} when the amount is one. */
    public String format(double amount) {
        String unit = amount == 1.0D ? singular : name;
        return symbol + number.format(amount) + " " + unit;
    }
}
