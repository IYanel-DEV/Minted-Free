package dev.minted.backend;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The uuid-to-name map that lets the leaderboard and sales feed show real names
 * instead of bare ids. Names are cosmetic, so callers never crash on a write
 * failure; the online list is always a fallback.
 */
public final class NamesDao {

    private static final String TABLE = "minted_names";

    private final DataSource dataSource;
    private final SqlDialect dialect;

    public NamesDao(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    public void createTable() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(dialect.createNames())) {
            statement.execute();
        } catch (SQLException e) {
            throw new StorageException("Could not create names table", e);
        }
    }

    /** Records a player's current name. Blocking; async callers only. */
    public void save(UUID uuid, String name) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(dialect.namesUpsert(TABLE))) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not record name for " + uuid, e);
        }
    }

    /** @return the display names for the given uuids that are known */
    public Map<UUID, String> names(Collection<UUID> uuids) {
        Map<UUID, String> result = new HashMap<UUID, String>();
        if (uuids.isEmpty()) {
            return result;
        }
        String sql = "SELECT uuid, name FROM " + TABLE + " WHERE uuid IN (" + placeholders(uuids.size()) + ")";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (UUID uuid : uuids) {
                statement.setString(index++, uuid.toString());
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(UUID.fromString(rows.getString(1)), rows.getString(2));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load player names", e);
        }
        return result;
    }

    /** The latest uuid that has been seen under the given name, or null. Blocking; async callers only. */
    public UUID uuidByName(String name) {
        String sql = "SELECT uuid FROM " + TABLE + " WHERE LOWER(name) = LOWER(?)"
                + " ORDER BY name = ? DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, name);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? UUID.fromString(rows.getString(1)) : null;
            }
        } catch (SQLException e) {
            throw new StorageException("Could not resolve name '" + name + "' to a uuid", e);
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