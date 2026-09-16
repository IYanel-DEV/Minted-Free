package dev.minted.gui;

import dev.minted.compat.InteractionHand;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Opens the payment menu when a player interacts with another player.
 *
 * <p>Only {@link PlayerInteractEntityEvent} is listened to: its sibling
 * {@code PlayerInteractAtEntityEvent} has a separate handler list, so binding
 * here alone already avoids that duplicate, and {@link InteractionHand} filters
 * the off-hand copy 1.9+ servers add. The event is cancelled once the click is
 * ours so the interaction cannot also mount or hit the target.
 */
public final class InteractionListener implements Listener {

    private final GuiContext ctx;
    private final boolean requireSneak;

    public InteractionListener(GuiContext ctx, boolean requireSneak) {
        this.ctx = ctx;
        this.requireSneak = requireSneak;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (InteractionHand.isOffHand(event)) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player)) {
            return;
        }
        Player opener = event.getPlayer();
        if (opener.isSneaking() != requireSneak) {
            return;
        }

        Player target = (Player) event.getRightClicked();
        event.setCancelled(true);

        if (!ctx.bank().isReady()) {
            opener.sendMessage(ChatColor.RED + "The economy is still starting up.");
            return;
        }
        if (!ctx.bank().isLoaded(opener.getUniqueId())) {
            opener.sendMessage(ChatColor.RED + "Your account is still loading, try again in a moment.");
            return;
        }
        new InteractionMenu(ctx, opener, target).open(opener);
    }
}
