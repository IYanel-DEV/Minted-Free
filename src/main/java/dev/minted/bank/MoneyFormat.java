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

    // Suffix ladder for compact display, largest first. Kept tiny on purpose.
    private static final double[] COMPACT_STEPS = {
            1.0E15, 1.0E12, 1.0E9, 1.0E6, 1.0E3};
    private static final String[] COMPACT_SUFFIXES = {"q", "t", "b", "m", "k"};

    private final String name;
    private final String singular;
    private final String symbol;
    private final boolean compact;
    private final DecimalFormat number;

    private MoneyFormat(String name, String singular, String symbol, boolean compact) {
        this.name = name;
        this.singular = singular;
        this.symbol = symbol;
        this.compact = compact;
        this.number = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.ROOT));
    }

    public static MoneyFormat from(FileConfiguration config) {
        return new MoneyFormat(
                config.getString("currency.name", "Coins"),
                config.getString("currency.singular", "Coin"),
                config.getString("currency.symbol", "$"),
                config.getBoolean("currency.compact", false));
    }

    /** e.g. {@code $1,250 Coins}, or {@code $1 Coin} when the amount is one. */
    public String format(double amount) {
        String unit = amount == 1.0D ? singular : name;
        return symbol + digits(amount) + " " + unit;
    }

    /**
     * One short line regardless of the {@code currency.compact} config flag:
     * {@code $200}, {@code $1m}, {@code $1.5t}. Used for headline numbers like
     * the economy stats page, where the long form would be unreadable.
     */
    public String brief(double amount) {
        return symbol + digitsBrief(amount);
    }

    // Full digits, or a short suffixed form (10,000 -> 10k) when compact is on.
    // Only the display changes; the value itself is never rounded away here.
    private String digits(double amount) {
        if (compact) {
            return digitsBrief(amount);
        }
        return number.format(amount);
    }

    // The suffix ladder, always applied: 1,000 -> 1k, 1,000,000 -> 1m, and so
    // on, largest first. Small amounts keep their full digits.
    private String digitsBrief(double amount) {
        double abs = Math.abs(amount);
        for (int i = 0; i < COMPACT_STEPS.length; i++) {
            if (abs >= COMPACT_STEPS[i]) {
                return number.format(amount / COMPACT_STEPS[i]) + COMPACT_SUFFIXES[i];
            }
        }
        return number.format(amount);
    }
}
