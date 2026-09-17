package dev.minted.banknote;

/**
 * A physical banknote's data: how much it is worth and a serial. The serial is
 * derived from the denomination, so every note of the same face value shares an
 * identical signed payload and the notes stack in the player's inventory like
 * real change.
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
