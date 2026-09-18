package dev.minted.bank;

import java.util.UUID;

/**
 * Notified whenever a {@link BankAccount} balance changes, after the mutation
 * has happened. {@link EconomyService} attaches one to every account it adopts
 * so the whole plugin - and any listener on the public
 * {@link dev.minted.api.event.MintedBalanceChangeEvent} - sees every change
 * regardless of which subsystem triggered it.
 */
public interface BalanceChangeSink {

    void changed(UUID uuid, double oldBalance, double newBalance);
}
