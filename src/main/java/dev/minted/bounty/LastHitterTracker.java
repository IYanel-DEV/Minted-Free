package dev.minted.bounty;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks the most recent damager for each player, enabling bounty claims when
 * a player disconnects in combat.
 */
public final class LastHitterTracker implements Listener {

    private final Map<UUID, UUID> lastHitter = new HashMap<UUID, UUID>();

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            lastHitter.put(event.getEntity().getUniqueId(), event.getDamager().getUniqueId());
        }
    }

    public UUID get(UUID victim) {
        return lastHitter.get(victim);
    }

    public void remove(UUID victim) {
        lastHitter.remove(victim);
    }
}
