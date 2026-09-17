package dev.minted.backend;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence contract for player balances.
 *
 * <p>Every method here talks to disk or the network and is therefore
 * <em>blocking</em>. Callers are responsible for running them off the main
 * thread; the economy layer only ever reaches storage from async tasks.
 */
public interface StorageProvider {

    /** Opens the backing store and makes sure the schema exists. */
    void open();

    void close();

    /**
     * @return the stored balance, or {@code null} if the account has never
     *         been saved
     */
    Double loadBalance(UUID uuid);

    Map<UUID, Double> batchLoad(Collection<UUID> uuids);

    void saveBalance(UUID uuid, double balance);

    void saveBalances(Map<UUID, Double> balances);

    /** Sum of every stored balance in this table. */
    double sumBalances();

    /** Number of accounts that hold a stored balance. */
    int countAccounts();

    /** The richest accounts, richest first, up to {@code limit} rows. */
    java.util.List<RankedAccount> topAccounts(int limit);

    /** Snapshot of every stored (uuid, balance) pair. */
    Map<UUID, Double> allBalances();
}
