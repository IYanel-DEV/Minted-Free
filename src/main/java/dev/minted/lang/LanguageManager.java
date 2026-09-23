package dev.minted.lang;

import dev.minted.backend.HikariPool;
import dev.minted.backend.SqlDialect;
import dev.minted.backend.StorageException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple languages with per-player overrides.
 * 
 * Features:
 * - Global default language (config: lang.code)
 * - Per-player language preference (stored in database)
 * - Auto-detects available languages from lang/ folder
 * - Falls back to English for missing keys
 * - Easy to add new languages: just drop .yml file in lang/ folder
 */
public final class LanguageManager {

    private final Plugin plugin;
    private final HikariPool pool;
    private final SqlDialect dialect;

    // Fallback/default language (always English)
    private final YamlConfiguration englishDefaults;
    
    // Currently loaded global language
    private String globalLanguageCode = "en";
    private FileConfiguration globalLanguage;
    
    // Cache of all available languages (code -> display name)
    private final Map<String, String> availableLanguages = new HashMap<>();
    
    // Player language preferences (UUID -> language code)
    private final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();
    
    // Loaded language files (code -> FileConfiguration)
    private final Map<String, FileConfiguration> loadedLanguages = new ConcurrentHashMap<>();

    public LanguageManager(Plugin plugin, HikariPool pool, SqlDialect dialect) {
        this.plugin = plugin;
        this.pool = pool;
        this.dialect = dialect;
        
        // Load English as fallback
        this.englishDefaults = loadBundledDefaults();
        
        // Discover available languages
        discoverLanguages();
    }

    public void initialize() {
        // Create languages table first - preferences cannot load without it
        createTables();

        // Load global language from config
        this.globalLanguageCode = plugin.getConfig().getString("lang.code", "en").toLowerCase(Locale.ROOT);
        this.globalLanguage = loadLanguageFile(globalLanguageCode);

        // Load player preferences from database
        loadPlayerPreferences();
    }

    public void reload() {
        globalLanguageCode = plugin.getConfig().getString("lang.code", "en").toLowerCase(Locale.ROOT);
        globalLanguage = loadLanguageFile(globalLanguageCode);
        discoverLanguages();
    }

    // =========================================================================
    // LANGUAGE DISCOVERY
    // =========================================================================

    private void discoverLanguages() {
        availableLanguages.clear();
        loadedLanguages.clear();
        
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // Extract every bundled pack first so the scan below sees them
        copyBundledLanguages();

        // Always have English as fallback
        availableLanguages.put("en", "English");
        loadedLanguages.put("en", englishDefaults);

        // Scan for .yml files
        File[] files = langFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String code = file.getName().replace(".yml", "").toLowerCase(Locale.ROOT);
                if (!code.equals("en")) {
                    // Try to read the language name from the file
                    FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                    String displayName = config.getString("__meta.name", code.substring(0, 1).toUpperCase() + code.substring(1));
                    availableLanguages.put(code, displayName);
                    loadedLanguages.put(code, config);
                }
            }
        }
    }

    private void copyBundledLanguages() {
        String[] bundled = {"ar", "de", "en", "es", "fr", "it", "ja", "ko", "pl", "pt", "ru", "zh"};
        for (String code : bundled) {
            File out = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
            if (!out.exists()) {
                try {
                    plugin.saveResource("lang/" + code + ".yml", false);
                } catch (IllegalArgumentException missingFromJar) {
                    // Older jar without this pack - skip
                }
            }
        }
    }

    // =========================================================================
    // PLAYER LANGUAGE PREFERENCES
    // =========================================================================

    private void createTables() {
        try (Connection connection = pool.start().getConnection()) {
            exec(connection, dialect.createLanguages());
        } catch (SQLException e) {
            throw new StorageException("Could not create languages table", e);
        }
    }

    private void loadPlayerPreferences() {
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT uuid, language FROM player_languages");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                UUID uuid = UUID.fromString(rows.getString(1));
                String lang = rows.getString(2);
                if (availableLanguages.containsKey(lang)) {
                    playerLanguages.put(uuid, lang);
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load player languages", e);
        }
    }

    public void setPlayerLanguage(UUID uuid, String languageCode) {
        languageCode = languageCode.toLowerCase(Locale.ROOT);
        if (!availableLanguages.containsKey(languageCode)) {
            throw new IllegalArgumentException("Language not available: " + languageCode);
        }
        
        playerLanguages.put(uuid, languageCode);
        
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO player_languages(uuid, language) VALUES(?, ?) " + dialect.languageUpsertSuffix())) {
            statement.setString(1, uuid.toString());
            statement.setString(2, languageCode);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not save player language", e);
        }
    }

    public String getPlayerLanguage(UUID uuid) {
        return playerLanguages.getOrDefault(uuid, globalLanguageCode);
    }

    public void resetPlayerLanguage(UUID uuid) {
        playerLanguages.remove(uuid);
        try (Connection connection = pool.start().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM player_languages WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not reset player language", e);
        }
    }

    // =========================================================================
    // GLOBAL LANGUAGE
    // =========================================================================

    public void setGlobalLanguage(String languageCode) {
        languageCode = languageCode.toLowerCase(Locale.ROOT);
        if (!availableLanguages.containsKey(languageCode)) {
            throw new IllegalArgumentException("Language not available: " + languageCode);
        }
        
        globalLanguageCode = languageCode;
        globalLanguage = loadLanguageFile(languageCode);

        // Update config so /language global survives a restart (harmless if
        // the server runs /language reload, which re-reads lang.code).
        plugin.getConfig().set("lang.code", languageCode);
        plugin.saveConfig();
    }

    public String getGlobalLanguage() {
        return globalLanguageCode;
    }

    // =========================================================================
    // MESSAGE LOOKUP
    // =========================================================================

    /**
     * Gets a message for a specific player (uses their preferred language).
     */
    public String get(Player player, String key, String... pairs) {
        String langCode = getPlayerLanguage(player.getUniqueId());
        FileConfiguration lang = loadedLanguages.get(langCode);
        if (lang == null) lang = globalLanguage;
        if (lang == null) lang = englishDefaults;
        
        String raw = lang.getString(key, englishDefaults.getString(key, key));
        return ChatColor.translateAlternateColorCodes('&', substitute(raw, pairs));
    }

    /**
     * Gets a message for a specific language code (for admin commands, etc.).
     */
    public String get(String languageCode, String key, String... pairs) {
        languageCode = languageCode.toLowerCase(Locale.ROOT);
        FileConfiguration lang = loadedLanguages.get(languageCode);
        if (lang == null) lang = englishDefaults;
        
        String raw = lang.getString(key, englishDefaults.getString(key, key));
        return ChatColor.translateAlternateColorCodes('&', substitute(raw, pairs));
    }

    /**
     * Gets a message using the global server language.
     */
    public String getGlobal(String key, String... pairs) {
        String raw = globalLanguage.getString(key, englishDefaults.getString(key, key));
        return ChatColor.translateAlternateColorCodes('&', substitute(raw, pairs));
    }

    /**
     * Sends a message to a player in their preferred language.
     */
    public void send(Player player, String key, String... pairs) {
        player.sendMessage(get(player, key, pairs));
    }

    /**
     * Sends a message to any CommandSender in the global language.
     */
    public void sendGlobal(CommandSender sender, String key, String... pairs) {
        sender.sendMessage(getGlobal(key, pairs));
    }

    private String substitute(String message, String[] pairs) {
        String result = message;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return result;
    }

    // =========================================================================
    // AVAILABLE LANGUAGES
    // =========================================================================

    public Map<String, String> getAvailableLanguages() {
        return new HashMap<>(availableLanguages);
    }

    public List<String> getAvailableLanguageCodes() {
        return new ArrayList<>(availableLanguages.keySet());
    }

    public boolean isLanguageAvailable(String code) {
        return availableLanguages.containsKey(code.toLowerCase(Locale.ROOT));
    }

    // =========================================================================
    // INTERNAL
    // =========================================================================

    private YamlConfiguration loadBundledDefaults() {
        InputStream in = plugin.getResource("lang/en.yml");
        if (in == null) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private FileConfiguration loadLanguageFile(String code) {
        File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        return englishDefaults;
    }

    private void exec(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }
}