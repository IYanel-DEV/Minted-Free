package dev.minted.bounty;

import dev.minted.bank.CombatLock;
import dev.minted.bank.MoneyFormat;
import dev.minted.lang.Messages;
import dev.minted.sound.SoundFX;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Hands every open bounty on a killed player to whoever dealt the kill. Only a
 * player kill pays out; PvE deaths leave the posting in place.
 */
public final class BountyListener implements Listener {

    private final BountyService bounties;
    private final MoneyFormat format;
    private final Messages messages;
    private final SoundFX sounds;
    private final CombatLock combat;
    private final LastHitterTracker tracker;

    public BountyListener(BountyService bounties, MoneyFormat format, Messages messages,
                          SoundFX sounds, CombatLock combat, LastHitterTracker tracker) {
        this.bounties = bounties;
        this.format = format;
        this.messages = messages;
        this.sounds = sounds;
        this.combat = combat;
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (combat.secondsLeft(player) > 0) {
            UUID killerId = tracker.get(player.getUniqueId());
            if (killerId != null) {
                bounties.claimByKill(player.getUniqueId(), killerId);
            }
        }
        tracker.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = getKiller(victim);
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        double paid = bounties.claimByKill(victim.getUniqueId(), killer.getUniqueId());
        handleClaim(killer, victim, paid);
        tracker.remove(victim.getUniqueId());
    }

    private Player getKiller(Player victim) {
        if (victim.getKiller() != null) {
            return victim.getKiller();
        }
        UUID killerId = tracker.get(victim.getUniqueId());
        return killerId == null ? null : victim.getServer().getPlayer(killerId);
    }

    private void handleClaim(Player killer, Player victim, double paid) {
        if (paid > 0) {
            sounds.bountyCollected(killer);
            messages.send(killer, "bounty.collected", "amount", format.format(paid), "target", victim.getName());
            killer.getServer().broadcastMessage(messages.get("bounty.claimed-broadcast",
                    "target", victim.getName(), "killer", killer.getName(), "amount", format.format(paid)));
        } else if (paid < 0) {
            messages.send(killer, "bounty.cap");
        }
    }
}