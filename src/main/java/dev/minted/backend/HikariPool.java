package dev.minted.backend;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * Builds and owns the connection pool.
 *
 * <p>SQLite is a single-writer file database, so its pool is pinned to one
 * connection; MySQL uses the configured size. Drivers are loaded from the host
 * server's classpath via the JDBC URL rather than a hard-coded driver class,
 * which keeps this working across the connector versions shipped over the
 * supported server range.
 */
public final class HikariPool {

    private final DatabaseSettings settings;
    private final SqlDialect dialect;

    private HikariDataSource dataSource;

    public HikariPool(DatabaseSettings settings, SqlDialect dialect) {
        this.settings = settings;
        this.dialect = dialect;
    }

    public DataSource start() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("Minted");
        config.setJdbcUrl(dialect.jdbcUrl(settings));
        // Trivial local ops (sqlite) or a dead remote (mysql) must not hang
        // startup past a few seconds; connection errors surface as failures.
        config.setConnectionTimeout(5000);
        config.setMaximumPoolSize(dialect == SqlDialect.SQLITE ? 1 : settings.getPoolSize());

        if (dialect.usesCredentials()) {
            config.setUsername(settings.getUsername());
            config.setPassword(settings.getPassword());
        }

        // Hikari 2.x validates the driver class through the thread context loader
        // before the pool's connection path can see it. Point that loader at our
        // jar (child-first), then restore it; the pool itself finds the driver
        // through its own classloader.
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        try {
            config.setDriverClassName(dialect.driverClassName());
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }

        this.dataSource = new HikariDataSource(config);
        return dataSource;
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
