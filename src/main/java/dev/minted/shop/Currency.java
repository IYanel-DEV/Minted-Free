package dev.minted.shop;

import java.util.Locale;

/**
 * Which account pair a shop trades against. {@code WALLET} spends and pays into
 * the player's wallet; {@code BANK} uses the bank. Both resolve to an ordinary
 * {@code EconomyService} account, so the max-balance cap and async saving apply
 * either way.
 */
public enum Currency {

    WALLET("Wallet"),
    BANK("Bank");

    private final String display;

    Currency(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    /** Stored form is the lower-cased enum name. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Falls back to {@link #WALLET} for anything unrecognised. */
    public static Currency fromId(String id) {
        if (id != null && "bank".equals(id.toLowerCase(Locale.ROOT))) {
            return BANK;
        }
        return WALLET;
    }
}
