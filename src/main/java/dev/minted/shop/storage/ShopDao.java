package dev.minted.shop.storage;

import dev.minted.backend.HikariPool;
import dev.minted.backend.SqlDialect;
import dev.minted.backend.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the shop tables, always through prepared statements and over the
 * pool the accounts layer already owns. Shop and item ids are supplied by the
 * caller, so writes are plain INSERT/UPDATE/DELETE; an item "save" is a delete
 * of its slot followed by an insert, which reads the same on both backends and
 * avoids a dialect-specific upsert.
 */
public final class ShopDao {

    private final HikariPool pool;
    private final SqlDialect dialect;

    public ShopDao(HikariPool pool, SqlDialect dialect) {
        this.pool = pool;
        this.dialect = dialect;
    }

    public void createTables() {
        try (Connection connection = pool.start().getConnection()) {
            exec(connection, dialect.createShops());
            exec(connection, dialect.createShopItems());
        } catch (SQLException e) {
            throw new StorageException("Could not create shop tables", e);
        }
    }

    public List<ShopRow> loadShops() {
        List<ShopRow> shops = new ArrayList<ShopRow>();
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, name, icon, currency FROM shops ORDER BY id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                shops.add(new ShopRow(rows.getInt(1), rows.getString(2), rows.getString(3), rows.getString(4)));
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load shops", e);
        }
        return shops;
    }

    public List<ShopItemRow> loadItems() {
        List<ShopItemRow> items = new ArrayList<ShopItemRow>();
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT shop_id, page, slot, item, buy_price, sell_price, category FROM shop_items");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                items.add(new ShopItemRow(rows.getInt(1), rows.getInt(2), rows.getInt(3), rows.getString(4),
                        rows.getDouble(5), rows.getDouble(6), rows.getString(7)));
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load shop items", e);
        }
        return items;
    }

    public void insertShop(int id, String name, String iconData, String currency) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO shops(id, name, icon, currency) VALUES(?, ?, ?, ?)")) {
            statement.setInt(1, id);
            statement.setString(2, name);
            statement.setString(3, iconData);
            statement.setString(4, currency);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not insert shop " + name, e);
        }
    }

    public void updateShop(int id, String name, String iconData, String currency) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE shops SET name = ?, icon = ?, currency = ? WHERE id = ?")) {
            statement.setString(1, name);
            statement.setString(2, iconData);
            statement.setString(3, currency);
            statement.setInt(4, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not update shop " + name, e);
        }
    }

    public void deleteShop(int id) {
        try (Connection connection = pool.start().getConnection()) {
            deleteShopItems(connection, id);
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM shops WHERE id = ?")) {
                statement.setInt(1, id);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("Could not delete shop " + id, e);
        }
    }

    public void saveItem(int shopId, int page, int slot, String itemData,
                         double buyPrice, double sellPrice, String category) {
        try (Connection connection = pool.start().getConnection()) {
            connection.setAutoCommit(false);
            deleteItemRow(connection, shopId, page, slot);
            insertItemRow(connection, shopId, page, slot, itemData, buyPrice, sellPrice, category);
            connection.commit();
        } catch (SQLException e) {
            throw new StorageException("Could not save shop item", e);
        }
    }

    public void deleteItem(int shopId, int page, int slot) {
        try (Connection connection = pool.start().getConnection()) {
            deleteItemRow(connection, shopId, page, slot);
        } catch (SQLException e) {
            throw new StorageException("Could not delete shop item", e);
        }
    }

    private void insertItemRow(Connection connection, int shopId, int page, int slot,
                               String itemData, double buyPrice, double sellPrice, String category)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO shop_items(shop_id, page, slot, item, buy_price, sell_price, category)"
                        + " VALUES(?, ?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, shopId);
            statement.setInt(2, page);
            statement.setInt(3, slot);
            statement.setString(4, itemData);
            statement.setDouble(5, buyPrice);
            statement.setDouble(6, sellPrice);
            statement.setString(7, category);
            statement.executeUpdate();
        }
    }

    private void deleteItemRow(Connection connection, int shopId, int page, int slot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM shop_items WHERE shop_id = ? AND page = ? AND slot = ?")) {
            statement.setInt(1, shopId);
            statement.setInt(2, page);
            statement.setInt(3, slot);
            statement.executeUpdate();
        }
    }

    private void deleteShopItems(Connection connection, int shopId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM shop_items WHERE shop_id = ?")) {
            statement.setInt(1, shopId);
            statement.executeUpdate();
        }
    }

    private void exec(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}
