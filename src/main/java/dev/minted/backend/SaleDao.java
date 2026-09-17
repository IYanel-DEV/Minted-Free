package dev.minted.backend;

import dev.minted.shop.log.SaleEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL for the server-wide sales feed, always through prepared statements and
 * over the pool the accounts layer already owns. Ids are assigned in Java (a
 * boot-time max + 1, like the shop tables), so writes are plain INSERT.
 */
public final class SaleDao {

    private final HikariPool pool;
    private final SqlDialect dialect;

    public SaleDao(HikariPool pool, SqlDialect dialect) {
        this.pool = pool;
        this.dialect = dialect;
    }

    public void createTable() {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(dialect.createSales())) {
            statement.execute();
        } catch (SQLException e) {
            throw new StorageException("Could not create sales table", e);
        }
    }

    /** Highest id currently stored, used to seed the next insert id after a restart. */
    public int maxId() {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(MAX(id), 0) FROM minted_sales");
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getInt(1) : 0;
        } catch (SQLException e) {
            throw new StorageException("Could not read the sales table id", e);
        }
    }

    public void insert(int id, long ts, String seller, String buyer, String item, int qty, double price, String kind) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO minted_sales(id, ts, seller, buyer, item, qty, price, kind)"
                             + " VALUES(?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, id);
            statement.setLong(2, ts);
            statement.setString(3, seller);
            statement.setString(4, buyer);
            statement.setString(5, item);
            statement.setInt(6, qty);
            statement.setDouble(7, price);
            statement.setString(8, kind);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not record a sale", e);
        }
    }

    /** @return the newest sales first, limited to the request. */
    public List<SaleEntry> recent(int limit) {
        List<SaleEntry> entries = new ArrayList<SaleEntry>();
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT ts, seller, buyer, item, qty, price, kind FROM minted_sales"
                             + " ORDER BY id DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(new SaleEntry(rows.getLong(1), rows.getString(2), rows.getString(3),
                            rows.getString(4), rows.getInt(5), rows.getDouble(6), rows.getString(7)));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load recent sales", e);
        }
        return entries;
    }

    /** Drops all but the newest {@code keep} sales so the table never grows forever. */
    public void prune(int keep) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM minted_sales WHERE id NOT IN"
                             + " (SELECT id FROM minted_sales ORDER BY id DESC LIMIT ?)")) {
            statement.setInt(1, keep);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not prune old sales", e);
        }
    }
}