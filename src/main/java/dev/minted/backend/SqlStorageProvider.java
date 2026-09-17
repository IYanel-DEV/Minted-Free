package dev.minted.backend;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** SQL-backed {@link StorageProvider} wiring a Hikari pool to the DAO. */
public final class SqlStorageProvider implements StorageProvider {

    private final HikariPool pool;
    private final SqlDialect dialect;
    private final String table;

    private volatile AccountDao dao;

    public SqlStorageProvider(HikariPool pool, SqlDialect dialect, String table) {
        this.pool = pool;
        this.dialect = dialect;
        this.table = table;
    }

    @Override
    public void open() {
        this.dao = new AccountDao(pool.start(), dialect, table);
        dao.createTable();
    }

    private AccountDao dao() {
        AccountDao value = dao;
        if (value == null) {
            // Guards against callers that slip in before open() completes; the
            // join listener filters for this, this is the second line.
            throw new StorageException("Storage is not open yet");
        }
        return value;
    }

    @Override
    public void close() {
        pool.close();
    }

    @Override
    public Double loadBalance(UUID uuid) {
        return dao().load(uuid);
    }

    @Override
    public Map<UUID, Double> batchLoad(Collection<UUID> uuids) {
        return dao().loadAll(uuids);
    }

    @Override
    public void saveBalance(UUID uuid, double balance) {
        dao().save(uuid, balance);
    }

    @Override
    public void saveBalances(Map<UUID, Double> balances) {
        dao().saveAll(balances);
    }

    @Override
    public double sumBalances() {
        return dao().sum();
    }

    @Override
    public int countAccounts() {
        return dao().count();
    }

    @Override
    public java.util.List<RankedAccount> topAccounts(int limit) {
        return dao().top(limit);
    }

    @Override
    public Map<UUID, Double> allBalances() {
        return dao().all();
    }
}
