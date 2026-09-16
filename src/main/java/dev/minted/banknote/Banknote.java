package dev.minted.banknote;

/**
 * A physical banknote's data: how much it is worth and a per-note serial. The
 * serial exists so two notes of the same denomination still carry distinct,
 * signed payloads.
 */
public final class Banknote {

    private final double denomination;
    private final String serial;

    public Banknote(double denomination, String serial) {
        this.denomination = denomination;
        this.serial = serial;
    }

    public double getDenomination() {
        return denomination;
    }

    public String getSerial() {
        return serial;
    }
}
