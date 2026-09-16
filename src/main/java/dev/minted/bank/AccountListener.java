package dev.minted.bank;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Loads a player's account on join and flushes it on quit.
 *
 * <p>Loads are skipped while the storage layer is still opening: {@code
 * MintedPlugin} reloads every already-online player the moment it becomes
 * ready, so a first-minute joiner is covered either way.
 */
public final class AccountListener implements Listener {

    private final EconomyService economy;

    public AccountListener(EconomyService economy) {
        this.economy = economy;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (economy.isReady()) {
            economy.load(event.getPlayer().getUniqueId(), null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        economy.unload(event.getPlayer().getUniqueId());
    }
}
