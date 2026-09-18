package dev.minted.resourcepack;

import dev.minted.MintedPlugin;
import dev.minted.util.Reflection;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.lang.reflect.Method;

/**
 * Prompts players to download the Minted resource pack on join, if enabled.
 * Uses reflection to support the hash-aware setResourcePack call when available.
 */
public final class ResourcePackListener implements Listener {

    private final boolean enabled;
    private final String url;
    private final String hash;

    public ResourcePackListener(ConfigurationSection config) {
        this.enabled = config != null && config.getBoolean("enabled", true);
        this.url = config != null ? config.getString("url", "") : "";
        this.hash = config != null ? config.getString("hash", "") : "";
        MintedPlugin.get().getLogger().info("Resource pack listener: enabled=" + enabled
                + " url-set=" + !url.isEmpty() + " hash-len=" + hash.length());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled || url.isEmpty()) {
            return;
        }
        apply(event.getPlayer());
        MintedPlugin.get().getLogger().info("Sent resource pack prompt to " + event.getPlayer().getName()
                + " (url=" + url + ")");
    }

    private void apply(Player player) {
        // 1.10+ supports setResourcePack(url, hash); older only supports (url).
        // Reflection handles the switch safely without crashing 1.8.
        try {
            if (hash != null && !hash.isEmpty()) {
                try {
                    Method setWithHash = Reflection.getMethod(player.getClass(), "setResourcePack", String.class, String.class);
                    Reflection.invoke(setWithHash, player, url, hash);
                    return;
                } catch (Reflection.ReflectionException ignored) {
                    // fall back to url-only
                }
            }
            player.setResourcePack(url);
        } catch (Exception e) {
            // Some very old 1.8 builds might still fail if setResourcePack is missing or throws.
        }
    }
}
