package dev.minted.bank;

/**
 * Bank sinks: the small percentage taken out of a withdrawal or a payment,
 * money that leaves the economy for good. Kept in cents so stored whole
 * hundredths never accumulate float dust.
 */
public final class Fees {

    private Fees() {
    }

    /**
     * The fee for {@code amount} at {@code percent} - e.g. 2 {%} of $100 is $2.
     *
     * @return 0 when the fee is disabled or the amount is not positive
     */
    public static double of(double percent, double amount) {
        if (percent <= 0 || amount <= 0) {
            return 0;
        }
        return Math.round(amount * percent) / 100.0;
    }
}