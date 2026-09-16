package dev.minted.backend;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.Locale;

/** Immutable view of the {@code database} section of {@code config.yml}. */
public final class DatabaseSettings {

    private final String type;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int poolSize;
    private final File sqliteFile;

    private DatabaseSettings(String type, String host, int port, String database,
                            String username, String password, int poolSize, File sqliteFile) {
        this.type = type;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        this.sqliteFile = sqliteFile;
    }

    public static DatabaseSettings from(FileConfiguration config, File dataFolder) {
        String type = config.getString("database.type", "sqlite").toLowerCase(Locale.ROOT);
        return new DatabaseSettings(
                type,
                config.getString("database.host", "localhost"),
                config.getInt("database.port", 3306),
                config.getString("database.database", "minted"),
                config.getString("database.username", "root"),
                config.getString("database.password", ""),
                Math.max(1, config.getInt("database.pool-size", 8)),
                new File(dataFolder, "minted.db"));
    }

    public String getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public File getSqliteFile() {
        return sqliteFile;
    }
}
