package dev.minted.currency;

import dev.minted.backend.HikariPool;
import dev.minted.backend.SqlDialect;
import dev.minted.backend.StorageException;
import dev.minted.lang.Messages;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manages multiple currencies and handles conversion between them.
 */
public final class CurrencyManager {

    private final Map<String, Currency> currencies = new HashMap<>();
    private final HikariPool pool;
    private final SqlDialect dialect;
    private final Messages messages;
    private Currency baseCurrency;

    public CurrencyManager(HikariPool pool, SqlDialect dialect, Messages messages) {
        this.pool = pool;
        this.dialect = dialect;
        this.messages = messages;
    }

    public void initialize() {
        loadCurrencies();
        ensureBaseCurrency();
    }

    public void reload() {
        currencies.clear();
        loadCurrencies();
        ensureBaseCurrency();
    }

    private void loadCurrencies() {
        try {
            createTables();
            List<Currency> loaded = loadFromDatabase();
            for (Currency c : loaded) {
                currencies.put(c.getId(), c);
                if (c.isBase()) {
                    baseCurrency = c;
                }
            }
        } catch (RuntimeException e) {
            // Logger would be here
            e.printStackTrace();
        }
    }

    private void createTables() {
        try (Connection connection = pool.start().getConnection()) {
            exec(connection, dialect.createCurrencies());
        } catch (SQLException e) {
            throw new StorageException("Could not create currency tables", e);
        }
    }

    private List<Currency> loadFromDatabase() {
        List<Currency> list = new ArrayList<>();
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, name, singular, symbol, exchange_rate, is_base, sort_order FROM currencies ORDER BY sort_order");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                list.add(new Currency(
                        rows.getString(1),
                        rows.getString(2),
                        rows.getString(3),
                        rows.getString(4),
                        rows.getDouble(5),
                        rows.getBoolean(6),
                        rows.getInt(7)
                ));
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load currencies", e);
        }
        return list;
    }

    private void ensureBaseCurrency() {
        if (baseCurrency == null) {
            // Create default base currency if none exists
            baseCurrency = new Currency("coins", "Coins", "Coin", "$", 1.0, true, 0);
            currencies.put("coins", baseCurrency);
            saveCurrency(baseCurrency);
        }
    }

    public void saveCurrency(Currency currency) {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO currencies(id, name, singular, symbol, exchange_rate, is_base, sort_order) "
                     + "VALUES(?, ?, ?, ?, ?, ?, ?) "
                     + dialect.upsertSuffix())) {
            statement.setString(1, currency.getId());
            statement.setString(2, currency.getName());
            statement.setString(3, currency.getSingular());
            statement.setString(4, currency.getSymbol());
            statement.setDouble(5, currency.getExchangeRate());
            statement.setBoolean(6, currency.isBase());
            statement.setInt(7, currency.getSortOrder());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not save currency " + currency.getId(), e);
        }
    }

    public void deleteCurrency(String id) {
        if (id.equals(baseCurrency.getId())) {
            throw new IllegalArgumentException("Cannot delete base currency");
        }
        currencies.remove(id);
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM currencies WHERE id = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not delete currency " + id, e);
        }
    }

    public Currency getCurrency(String id) {
        return currencies.get(id.toLowerCase(Locale.ROOT));
    }

    public Currency getBaseCurrency() {
        return baseCurrency;
    }

    public List<Currency> getAllCurrencies() {
        List<Currency> list = new ArrayList<>(currencies.values());
        list.sort(Comparator.comparingInt(Currency::getSortOrder));
        return list;
    }

    public double convert(double amount, String fromId, String toId) {
        Currency from = getCurrency(fromId);
        Currency to = getCurrency(toId);
        if (from == null || to == null) return amount;
        return from.convertTo(amount, to);
    }

    public String format(double amount, String currencyId) {
        Currency c = getCurrency(currencyId);
        if (c == null) c = baseCurrency;
        return c.getSymbol() + amount + " " + c.getName();
    }

    private void exec(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}