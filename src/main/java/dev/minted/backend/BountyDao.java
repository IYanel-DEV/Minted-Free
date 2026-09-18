package dev.minted.backend;

import dev.minted.bounty.Bounty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQL for the bounties table, always through prepared statements. A bounty is a
 * plain row keyed by its own id, with a status that moves it from open to
 * claimed or refunded; the open set is mirrored in memory by the service.
 */
public final class BountyDao {

    private final HikariPool pool;
    private final SqlDialect dialect;

    public BountyDao(HikariPool pool, SqlDialect dialect) {
        this.pool = pool;
        this.dialect = dialect;
    }

    public void createTable() {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(dialect.createBounties())) {
            statement.execute();
        } catch (SQLException e) {
            throw new StorageException("Could not create bounties table", e);
        }
    }

    /** Highest id currently stored, used to resume the id counter after a restart. */
    public int maxId() {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(MAX(id), 0) FROM minted_bounties");
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getInt(1) : 0;
        } catch (SQLException e) {
            throw new StorageException("Could not read the bounties table id", e);
        }
    }

    public void insert(int id, UUID target, UUID placer, double amount, String note, long placedAt) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO minted_bounties(id, target, placer, amount, note, placed_at, status)"
                             + " VALUES(?, ?, ?, ?, ?, ?, 0)")) {
            statement.setInt(1, id);
            statement.setString(2, target.toString());
            statement.setString(3, placer.toString());
            statement.setDouble(4, amount);
            if (note != null && !note.isEmpty()) {
                statement.setString(5, note);
            } else {
                statement.setNull(5, java.sql.Types.VARCHAR);
            }
            statement.setLong(6, placedAt);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not record a bounty", e);
        }
    }

    /** Every still-open bounty, in posting order. */
    public List<Bounty> openBounties() {
        List<Bounty> bounties = new ArrayList<Bounty>();
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, target, placer, amount, note, placed_at FROM minted_bounties"
                             + " WHERE status = 0 ORDER BY id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                bounties.add(new Bounty(rows.getInt(1), UUID.fromString(rows.getString(2)),
                        UUID.fromString(rows.getString(3)), rows.getDouble(4),
                        rows.getString(5), rows.getLong(6)));
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load open bounties", e);
        }
        return bounties;
    }

    public void markClaimed(int id, UUID killer, long claimedAt) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE minted_bounties SET status = 1, claimed_by = ?, claimed_at = ? WHERE id = ?")) {
            statement.setString(1, killer.toString());
            statement.setLong(2, claimedAt);
            statement.setInt(3, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not mark a bounty claimed", e);
        }
    }

    public void markRefunded(int id) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE minted_bounties SET status = 2 WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not mark a bounty refunded", e);
        }
    }
}