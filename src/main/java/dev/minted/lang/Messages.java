package dev.minted.lang;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Looks up player-facing strings by key.
 *
 * <p>The English bundle ships inside the jar and is the fallback for every key,
 * so a missing or partial translation never leaves a blank line. On first run
 * {@code en.yml} is copied to {@code lang/} so it can be edited or used as a
 * template; the active language is chosen by {@code lang.code} in the config.
 * Values may use {@code &} colour codes and {@code {placeholder}} tokens.
 */
public final class Messages {

    private final YamlConfiguration defaults;
    private final FileConfiguration active;

    private Messages(YamlConfiguration defaults, FileConfiguration active) {
        this.defaults = defaults;
        this.active = active;
    }

    public static Messages load(Plugin plugin) {
        YamlConfiguration defaults = loadBundledDefaults(plugin);
        copyDefaultOut(plugin);
        String code = plugin.getConfig().getString("lang.code", "en");
        return new Messages(defaults, loadActive(plugin, code, defaults));
    }

    /** @param pairs alternating placeholder name and value, e.g. {@code "shop", "Spawn"} */
    public String get(String key, String... pairs) {
        String raw = active.getString(key, defaults.getString(key, key));
        return ChatColor.translateAlternateColorCodes('&', substitute(raw, pairs));
    }

    public void send(CommandSender to, String key, String... pairs) {
        to.sendMessage(get(key, pairs));
    }

    private String substitute(String message, String[] pairs) {
        String result = message;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return result;
    }

    private static YamlConfiguration loadBundledDefaults(Plugin plugin) {
        InputStream in = plugin.getResource("lang/en.yml");
        if (in == null) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static void copyDefaultOut(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "lang/en.yml");
        if (!file.exists()) {
            plugin.saveResource("lang/en.yml", false);
        }
    }

    private static FileConfiguration loadActive(Plugin plugin, String code, YamlConfiguration defaults) {
        File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        return defaults;
    }
}
