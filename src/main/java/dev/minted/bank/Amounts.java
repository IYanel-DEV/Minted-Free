package dev.minted.bank;

import java.util.Locale;

/** Parses the amount tokens shared by {@code /bank} and the bank menu. */
public final class Amounts {

    private Amounts() {
    }

    /**
     * Resolves a raw amount against what the source holds and how much room the
     * destination has left.
     *
     * @return the amount, or a non-positive value when the token is unusable
     */
    public static double resolve(String raw, double available, double headroom) {
        String token = raw.toLowerCase(Locale.ROOT);
        if ("all".equals(token)) {
            return available;
        }
        if ("half".equals(token)) {
            return available / 2;
        }
        if ("max".equals(token)) {
            return Math.min(available, headroom);
        }
        try {
            double value = Double.parseDouble(token);
            return Double.isFinite(value) ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
