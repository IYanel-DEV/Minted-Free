package dev.minted.backend;

import java.util.Locale;

/**
 * The two supported SQL backends. They differ only in how a connection URL is
 * built and how an "insert or update" is spelled, so those are the only things
 * that vary per dialect.
 */
public enum SqlDialect {

    SQLITE("org.sqlite.JDBC") {
        @Override
        public String jdbcUrl(DatabaseSettings settings) {
            return "jdbc:sqlite:" + settings.getSqliteFile().getAbsolutePath();
        }

        @Override
        public String upsert(String table) {
            return "INSERT INTO " + table + "(uuid, balance) VALUES(?, ?)"
                    + " ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance";
        }
    },

    MYSQL("dev.minted.libs.mysql.cj.jdbc.Driver") {
        @Override
        public String jdbcUrl(DatabaseSettings settings) {
            return "jdbc:mysql://" + settings.getHost() + ":" + settings.getPort()
                    + "/" + settings.getDatabase()
                    + "?useSSL=false&autoReconnect=true&serverTimezone=UTC";
        }

        @Override
        public String upsert(String table) {
            // VALUES() is deprecated in MySQL 8.0.20+ (a log warning only) but
            // the row-alias alternative needs 8.0.19+; VALUES() still speaks to
            // every server the plugin promises to run on.
            return "INSERT INTO " + table + "(uuid, balance) VALUES(?, ?)"
                    + " ON DUPLICATE KEY UPDATE balance = VALUES(balance)";
        }
    };

    private final String driverClass;

    SqlDialect(String driverClass) {
        this.driverClass = driverClass;
    }

    /**
     * Relocated driver FQCN. Strings are used deliberately: a driver picked by
     * {@link java.sql.DriverManager} auto-discovery would resolve against the
     * server's own (possibly unrelated) copy, and it is a wart on the promise
     * that the jar works unchanged from 1.8 to 1.26.
     */
    public String driverClassName() {
        return driverClass;
    }

    public abstract String jdbcUrl(DatabaseSettings settings);

    /** Statement that writes a balance, creating the row if it is missing. */
    public abstract String upsert(String table);

    public String createTable(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "uuid VARCHAR(36) PRIMARY KEY, balance DOUBLE NOT NULL)";
    }

    // The shop schema is identical on both backends: ids are assigned in Java
    // (not AUTO_INCREMENT), and the column types used here spell the same on
    // sqlite and mysql, so no per-dialect branch is needed. It lives here so all
    // of the plugin's DDL stays in one place, as the accounts table does above.
    public String createShops() {
        return "CREATE TABLE IF NOT EXISTS shops ("
                + "id INTEGER PRIMARY KEY, "
                + "name VARCHAR(64) NOT NULL UNIQUE, "
                + "icon TEXT NOT NULL, "
                + "currency VARCHAR(16) NOT NULL)";
    }

    public String createShopItems() {
        return "CREATE TABLE IF NOT EXISTS shop_items ("
                + "shop_id INTEGER NOT NULL, "
                + "page INT NOT NULL, "
                + "slot INT NOT NULL, "
                + "item TEXT NOT NULL, "
                + "buy_price DOUBLE NOT NULL, "
                + "sell_price DOUBLE NOT NULL, "
                + "category VARCHAR(64), "
                + "PRIMARY KEY (shop_id, page, slot), "
                + "FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE)";
    }

    public String select(String table) {
        return "SELECT balance FROM " + table + " WHERE uuid = ?";
    }

    /** MySQL only needs credentials passed to the pool; SQLite is file-based. */
    public boolean usesCredentials() {
        return this == MYSQL;
    }

    public static SqlDialect fromId(String id) {
        if ("mysql".equals(id.toLowerCase(Locale.ROOT))) {
            return MYSQL;
        }
        return SQLITE;
    }
}
