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
        public String upsert() {
            return "INSERT INTO minted_accounts(uuid, balance) VALUES(?, ?)"
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
        public String upsert() {
            // VALUES() is deprecated in MySQL 8.0.20+ (a log warning only) but
            // the row-alias alternative needs 8.0.19+; VALUES() still speaks to
            // every server the plugin promises to run on.
            return "INSERT INTO minted_accounts(uuid, balance) VALUES(?, ?)"
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
    public abstract String upsert();

    public String createTable() {
        return "CREATE TABLE IF NOT EXISTS minted_accounts ("
                + "uuid VARCHAR(36) PRIMARY KEY, balance DOUBLE NOT NULL)";
    }

    public String select() {
        return "SELECT balance FROM minted_accounts WHERE uuid = ?";
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
