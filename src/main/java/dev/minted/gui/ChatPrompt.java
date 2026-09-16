package dev.minted.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Captures a single line of chat from a player, used by the "custom amount"
 * buttons. The chat event fires off the main thread, so the handler is bounced
 * back onto it before it touches any account.
 */
public final class ChatPrompt implements Listener {

    private final Plugin plugin;
    private final ConcurrentHashMap<UUID, Consumer<String>> pending =
            new ConcurrentHashMap<UUID, Consumer<String>>();

    public ChatPrompt(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The next thing {@code player} types is swallowed and passed to {@code handler}. */
    public void await(Player player, Consumer<String> handler) {
        pending.put(player.getUniqueId(), handler);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        final Consumer<String> handler = pending.remove(event.getPlayer().getUniqueId());
        if (handler == null) {
            return;
        }
        event.setCancelled(true);
        final String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                handler.accept(message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
