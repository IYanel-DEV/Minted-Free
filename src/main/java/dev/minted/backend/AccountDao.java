package dev.minted.backend;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * All SQL for the accounts table lives here, always through prepared
 * statements. No caller ever concatenates a value into a query.
 */
final class AccountDao {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final String table;

    AccountDao(DataSource dataSource, SqlDialect dialect, String table) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.table = table;
    }

    void createTable() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(dialect.createTable(table))) {
            statement.execute();
        } catch (SQLException e) {
            throw new StorageException("Could not create accounts table", e);
        }
    }

    Double load(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(dialect.select(table))) {
            statement.setString(1, uuid.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getDouble(1) : null;
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load balance for " + uuid, e);
        }
    }

    Map<UUID, Double> loadAll(Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return new HashMap<UUID, Double>();
        }
        String sql = "SELECT uuid, balance FROM " + table + " WHERE uuid IN (" + placeholders(uuids.size()) + ")";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindUuids(statement, uuids);
            return readBalances(statement);
        } catch (SQLException e) {
            throw new StorageException("Could not batch-load balances", e);
        }
    }

    void save(UUID uuid, double balance) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(dialect.upsert(table))) {
            statement.setString(1, uuid.toString());
            statement.setDouble(2, balance);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not save balance for " + uuid, e);
        }
    }

    void saveAll(Map<UUID, Double> balances) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(dialect.upsert(table))) {
            connection.setAutoCommit(false);
            for (Entry<UUID, Double> entry : balances.entrySet()) {
                statement.setString(1, entry.getKey().toString());
                statement.setDouble(2, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            throw new StorageException("Could not batch-save balances", e);
        }
    }

    private Map<UUID, Double> readBalances(PreparedStatement statement) throws SQLException {
        Map<UUID, Double> result = new HashMap<UUID, Double>();
        try (ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.put(UUID.fromString(rows.getString(1)), rows.getDouble(2));
            }
        }
        return result;
    }

    private void bindUuids(PreparedStatement statement, Collection<UUID> uuids) throws SQLException {
        int index = 1;
        for (UUID uuid : uuids) {
            statement.setString(index++, uuid.toString());
        }
    }

    private String placeholders(int count) {
        StringBuilder builder = new StringBuilder(count * 2);
        for (int i = 0; i < count; i++) {
            builder.append(i == 0 ? "?" : ", ?");
        }
        return builder.toString();
    }
}
