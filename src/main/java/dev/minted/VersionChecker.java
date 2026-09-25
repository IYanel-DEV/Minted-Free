package dev.minted;

import dev.minted.MintedPlugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks for plugin updates on GitHub and notifies admins.
 * Runs asynchronously on startup to avoid blocking server startup.
 */
public final class VersionChecker {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/IYanel-DEV/Minted-Free/releases/latest";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-.*)?$");

    private final MintedPlugin plugin;
    private final String currentVersion;

    public VersionChecker(MintedPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /** Starts the async version check. */
    public void checkForUpdates() {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) {
            plugin.getLogger().info("Update checker disabled in config.");
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                checkVersion();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to check for updates", e);
            }
        });
    }

    private void checkVersion() throws Exception {
        URL url = new URL(GITHUB_API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Minted-Plugin/" + plugin.getDescription().getVersion());
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            // Parse JSON for tag_name
            String json = response.toString();
            String latestVersion = extractVersionFromJson(json);
            
            if (latestVersion == null) {
                plugin.getLogger().warning("Could not parse latest version from GitHub API response");
                return;
            }

            int comparison = compareVersions(currentVersion, latestVersion);
            
            if (comparison < 0) {
                // New version available
                String downloadUrl = "https://github.com/IYanel-DEV/Minted-Free/releases/tag/v" + latestVersion;
                String downloadLink = "https://github.com/IYanel-DEV/Minted-Free/releases/tag/v" + latestVersion;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String msg = "§e[Minted] §6New version available: §ev" + latestVersion + " §6(current: v" + currentVersion + ")";
                    String urlMsg = "§bDownload: §b" + downloadLink;
                    plugin.getLogger().info("[Minted] New version available: v" + latestVersion + " (current: v" + currentVersion + ")");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.hasPermission("minted.admin") || p.isOp()) {
                            p.sendMessage(msg);
                            p.sendMessage(urlMsg);
                        }
                    }
                });
            } else if (comparison == 0) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLogger().info("[Minted] You are running the latest version (v" + currentVersion + ").");
                });
            } else {
                // Current is newer (dev build)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLogger().info("[Minted] You are running a development build (v" + currentVersion + "). Latest release: v" + latestVersion);
                });
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to check version: " + e.getMessage(), e);
        }
    }

    private String extractVersionFromJson(String json) {
        // Simple JSON parsing for tag_name
        int tagIndex = json.indexOf("\"tag_name\"");
        if (tagIndex == -1) return null;
        
        int colonIndex = json.indexOf(':', tagIndex);
        if (colonIndex == -1) return null;
        
        int quoteStart = json.indexOf('"', colonIndex);
        if (quoteStart == -1) return null;
        
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd == -1) return null;
        
        return json.substring(quoteStart + 1, quoteEnd);
    }

    private int compareVersions(String v1, String v2) {
        Matcher m1 = VERSION_PATTERN.matcher(v1);
        Matcher m2 = VERSION_PATTERN.matcher(v2);
        
        if (!m1.matches() || !m2.matches()) {
            return v1.compareTo(v2); // Fallback to string comparison
        }
        
        int major1 = Integer.parseInt(m1.group(1));
        int minor1 = Integer.parseInt(m1.group(2));
        int patch1 = Integer.parseInt(m1.group(3));
        
        int major2 = Integer.parseInt(m2.group(1));
        int minor2 = Integer.parseInt(m2.group(2));
        int patch2 = Integer.parseInt(m2.group(3));
        
        if (major1 != major2) return Integer.compare(major1, major2);
        if (minor1 != minor2) return Integer.compare(minor1, minor2);
        return Integer.compare(patch1, patch2);
    }
}