package dev.minted.backend;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Keeps the display-name map fresh for the leaderboard and sales feed. The write
 * is best-effort and off-thread; a name is purely cosmetic, so a failure is
 * swallowed and the online player list covers the gap anyway.
 */
public final class NamesListener implements Listener {

    private final Plugin plugin;
    private final NamesDao dao;

    public NamesListener(Plugin plugin, NamesDao dao) {
        this.plugin = plugin;
        this.dao = dao;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    dao.save(uuid, name);
                } catch (RuntimeException ignored) {
                    // Cosmetic only; never take a join down for a name.
                }
            }
        });
    }
}