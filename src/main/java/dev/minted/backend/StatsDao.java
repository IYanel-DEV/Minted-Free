package dev.minted.backend;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Persistence for the small set of economy counters (currently just money
 * burned by shop purchases) kept in the {@code minted_stats} key/value table.
 * Always through prepared statements; blocking, so async callers only.
 */
public final class StatsDao {

    private static final String TABLE = "minted_stats";

    private final DataSource dataSource;
    private final SqlDialect dialect;

    public StatsDao(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    public void createTable() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(dialect.createStats())) {
            statement.execute();
        } catch (SQLException e) {
            throw new StorageException("Could not create stats table", e);
        }
    }

    /** @return the stored counter, or null when no row exists yet */
    public Double load(String key) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT value FROM " + TABLE + " WHERE k = ?")) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getDouble(1) : null;
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load stat " + key, e);
        }
    }

    /** Writes the counter, creating the row if it is missing. */
    public void save(String key, double value) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(dialect.statsUpsert(TABLE))) {
            statement.setString(1, key);
            statement.setDouble(2, value);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not save stat " + key, e);
        }
    }
}