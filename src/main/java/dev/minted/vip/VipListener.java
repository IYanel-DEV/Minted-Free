package dev.minted.vip;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps a VIP's stored name - and with it their {@code /<username>} shop
 * command - pointing at the account they are currently playing under, and
 * observes the {@code minted.vip} permission on join and quit, the two
 * moments a permission plugin's answer is trustworthy for that player.
 */
public final class VipListener implements Listener {

    private final VipService vips;

    public VipListener(VipService vips) {
        this.vips = vips;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        vips.refreshName(event.getPlayer());
        vips.observePermission(event.getPlayer());
    }

    /** Catches permission changes made while the player was online. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        vips.observePermission(event.getPlayer());
    }
}