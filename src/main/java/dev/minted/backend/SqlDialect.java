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

        @Override
        public String statsUpsert(String table) {
            return "INSERT INTO " + table + "(k, value) VALUES(?, ?)"
                    + " ON CONFLICT(k) DO UPDATE SET value = excluded.value";
        }

        @Override
        public String namesUpsert(String table) {
            return "INSERT INTO " + table + "(uuid, name) VALUES(?, ?)"
                    + " ON CONFLICT(uuid) DO UPDATE SET name = excluded.name";
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

        @Override
        public String statsUpsert(String table) {
            return "INSERT INTO " + table + "(k, value) VALUES(?, ?)"
                    + " ON DUPLICATE KEY UPDATE value = VALUES(value)";
        }

        @Override
        public String namesUpsert(String table) {
            return "INSERT INTO " + table + "(uuid, name) VALUES(?, ?)"
                    + " ON DUPLICATE KEY UPDATE name = VALUES(name)";
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

    /** Statement that writes a stats counter, creating the row if missing. */
    public abstract String statsUpsert(String table);

    /** Statement that writes a player's display name, creating the row if missing. */
    public abstract String namesUpsert(String table);

    public String createTable(String table) {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "uuid VARCHAR(36) PRIMARY KEY, balance DOUBLE NOT NULL)";
    }

    // The economy-stats counters (e.g. money burned by shops) are a tiny
    // key/value table; a VARCHAR key spells the same on sqlite and mysql.
    public String createStats() {
        return "CREATE TABLE IF NOT EXISTS minted_stats ("
                + "k VARCHAR(32) PRIMARY KEY, value DOUBLE NOT NULL)";
    }

    // Display names for the leaderboard and sales feed. Cosmetic, so a write is
    // best-effort; REPLACE avoids needing a per-dialect upsert.
    public String createNames() {
        return "CREATE TABLE IF NOT EXISTS minted_names ("
                + "uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16) NOT NULL)";
    }

    public String createSales() {
        return "CREATE TABLE IF NOT EXISTS minted_sales ("
                + "id INTEGER PRIMARY KEY, "
                + "ts BIGINT NOT NULL, "
                + "seller VARCHAR(36), "
                + "buyer VARCHAR(36), "
                + "item VARCHAR(64) NOT NULL, "
                + "qty INT NOT NULL, "
                + "price DOUBLE NOT NULL, "
                + "kind VARCHAR(16) NOT NULL)";
    }

    public String createLoans() {
        return "CREATE TABLE IF NOT EXISTS minted_loans ("
                + "id INTEGER PRIMARY KEY, "
                + "borrower VARCHAR(36) NOT NULL, "
                + "amount DOUBLE NOT NULL, "
                + "owed DOUBLE NOT NULL, "
                + "taken_at BIGINT NOT NULL, "
                + "due_at BIGINT NOT NULL, "
                + "repaid INT NOT NULL DEFAULT 0)";
    }

    public String createBounties() {
        return "CREATE TABLE IF NOT EXISTS minted_bounties ("
                + "id INTEGER PRIMARY KEY, "
                + "target VARCHAR(36) NOT NULL, "
                + "placer VARCHAR(36) NOT NULL, "
                + "amount DOUBLE NOT NULL, "
                + "note VARCHAR(64), "
                + "placed_at BIGINT NOT NULL, "
                + "claimed_by VARCHAR(36), "
                + "claimed_at BIGINT, "
                + "status INT NOT NULL DEFAULT 0)";
    }

    /** Whole-table total, the global view used by the economy stats menu. */
    public String sum(String table) {
        return "SELECT COALESCE(SUM(balance), 0) FROM " + table;
    }

    /** How many rows the table holds, the number of accounts in use. */
    public String count(String table) {
        return "SELECT COUNT(*) FROM " + table;
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
                + "currency VARCHAR(16) NOT NULL, "
                + "type VARCHAR(16) NOT NULL DEFAULT 'global')";
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
                + "owner VARCHAR(36), "
                + "stock BIGINT NOT NULL DEFAULT 0, "
                + "buy_back DOUBLE NOT NULL DEFAULT -1, "
                + "earnings DOUBLE NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (shop_id, page, slot), "
                + "FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE)";
    }

    /**
     * In-place upgrades for a database created before v0.10.0. Each is wrapped
     * by the DAO in a "duplicate column is fine" guard, so re-running is safe.
     */
    public String[] migrateShopColumns() {
        return new String[] {
                "ALTER TABLE shops ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'global'",
                "ALTER TABLE shop_items ADD COLUMN owner VARCHAR(36)",
                "ALTER TABLE shop_items ADD COLUMN stock BIGINT NOT NULL DEFAULT 0",
                "ALTER TABLE shop_items ADD COLUMN buy_back DOUBLE NOT NULL DEFAULT -1",
                "ALTER TABLE shop_items ADD COLUMN earnings DOUBLE NOT NULL DEFAULT 0",
        };
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
