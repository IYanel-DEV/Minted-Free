package dev.minted.backend;

import java.util.UUID;

/** One account's standing in the riches ranking, fetched richest-first. */
public final class RankedAccount {

    private final UUID uuid;
    private final double balance;

    public RankedAccount(UUID uuid, double balance) {
        this.uuid = uuid;
        this.balance = balance;
    }

    public UUID getUuid() {
        return uuid;
    }

    public double getBalance() {
        return balance;
    }
}