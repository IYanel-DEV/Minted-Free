package dev.minted.banknote;

import dev.minted.bank.EconomyStats;

import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

/**
 * Counts physical cash that is genuinely lost. Money leaves the economy for
 * good whenever a dropped note is actually destroyed - its despawn timer runs
 * out, it burns up in fire or lava, a cactus or anvil or explosion or the void
 * breaks it, or someone straight out whacks it - and this listener adds the
 * face value to the burned/lost total in every one of those lanes. Notes
 * picked back up, banked, or still held never count.
 *
 * <p>Servers differ in which event they fire for a destroyed item, so all the
 * lanes are handled (timeout despawn, ignition, and generic item damage). A
 * per-entity dedupe keyed by entity id makes sure the same note can never be
 * counted twice even when a server fires several events for one loss - a note
 * sitting in lava takes tick after tick of fire damage, but it is only worth
 * counting once.
 */
public final class LostCashListener implements Listener {

    private final JavaPlugin plugin;
    private final BanknoteManager banknotes;
    private final EconomyStats stats;
    private final Set<Integer> counted = new HashSet<Integer>();

    public LostCashListener(JavaPlugin plugin, BanknoteManager banknotes, EconomyStats stats) {
        this.plugin = plugin;
        this.banknotes = banknotes;
        this.stats = stats;
    }

    // Despawn rule: the note's take-it-or-lose-it timer expired.
    @EventHandler
    public void onDespawn(ItemDespawnEvent event) {
        burnFrom(event.getEntity(), event.getEntity().getItemStack(), "timeout");
    }

    // Set alight by lava or a fire block: it will burn out and vanish.
    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Item) {
            Item item = (Item) event.getEntity();
            burnFrom(item, item.getItemStack(), "fire");
        }
    }

    // Any other lane that deletes the money: lava / fire-tick damage, cactus,
    // anvil or falling-block crush, TNT / block explosions, lightning, player
    // attack, and the void all reach here as item damage.
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item) {
            Item item = (Item) event.getEntity();
            burnFrom(item, item.getItemStack(), event.getCause().name().toLowerCase());
        }
    }

    private void burnFrom(Item entity, ItemStack stack, String lane) {
        double value = banknotes.faceValue(stack) * stack.getAmount();
        if (value <= 0) {
            return;
        }
        int id = entity.getEntityId();
        if (counted.contains(id)) {
            return;
        }
        counted.add(id);
        stats.burn(value);
        plugin.getLogger().info("Lost cash (" + lane + "): " + stack.getAmount()
                + "x note worth " + value + ", burned total " + stats.burned());
    }
}