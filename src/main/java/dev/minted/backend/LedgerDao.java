package dev.minted.backend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQL for the per-player transaction history, always through prepared statements
 * and over the pool the accounts layer already owns. Ids are assigned in Java (a
 * boot-time max + 1, like the sales feed), so writes are plain INSERT; the
 * prune keeps the table bounded forever.
 */
public final class LedgerDao {

    private final HikariPool pool;
    private final SqlDialect dialect;

    public LedgerDao(HikariPool pool, SqlDialect dialect) {
        this.pool = pool;
        this.dialect = dialect;
    }

    public void createTable() {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(dialect.createLedger())) {
            statement.execute();
        } catch (SQLException e) {
            throw new StorageException("Could not create ledger table", e);
        }
    }

    /** Highest id currently stored, used to seed the next insert id after a restart. */
    public int maxId() {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(MAX(id), 0) FROM minted_ledger");
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getInt(1) : 0;
        } catch (SQLException e) {
            throw new StorageException("Could not read the ledger id", e);
        }
    }

    public void insert(int id, long ts, UUID uuid, double delta, String kind, String detail) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO minted_ledger(id, ts, uuid, delta, kind, detail)"
                             + " VALUES(?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, id);
            statement.setLong(2, ts);
            statement.setString(3, uuid.toString());
            statement.setDouble(4, delta);
            statement.setString(5, kind);
            statement.setString(6, detail == null ? "" : detail);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not record a ledger entry", e);
        }
    }

    /** The player's newest movements first, limited to the request. */
    public List<LedgerEntry> recent(UUID uuid, int limit) {
        List<LedgerEntry> entries = new ArrayList<LedgerEntry>();
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT ts, delta, kind, detail FROM minted_ledger"
                             + " WHERE uuid = ? ORDER BY id DESC LIMIT ?")) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(new LedgerEntry(rows.getLong(1), rows.getDouble(2),
                            rows.getString(3), rows.getString(4)));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load the transaction history", e);
        }
        return entries;
    }

    /** Drops everything but the newest {@code keep} rows so the table never grows forever. */
    public void prune(int keep) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM minted_ledger WHERE id NOT IN"
                             + " (SELECT id FROM minted_ledger ORDER BY id DESC LIMIT ?)")) {
            statement.setInt(1, keep);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not prune old ledger rows", e);
        }
    }
}