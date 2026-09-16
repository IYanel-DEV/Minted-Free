package dev.minted.request;

import java.util.UUID;

/**
 * A pending "pay me" request: the {@code requester} asks the {@code payer} for
 * an amount, and it is only good until {@code expiresAt}. Instances are
 * immutable; single-use is enforced by {@link RequestService} removing the
 * request from its map before any money moves.
 */
public final class PaymentRequest {

    private final UUID id;
    private final UUID requester;
    private final UUID payer;
    private final double amount;
    private final long expiresAt;

    PaymentRequest(UUID id, UUID requester, UUID payer, double amount, long expiresAt) {
        this.id = id;
        this.requester = requester;
        this.payer = payer;
        this.amount = amount;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequester() {
        return requester;
    }

    public UUID getPayer() {
        return payer;
    }

    public double getAmount() {
        return amount;
    }

    boolean isExpired(long now) {
        return now >= expiresAt;
    }

    boolean involves(UUID uuid) {
        return requester.equals(uuid) || payer.equals(uuid);
    }
}
