package dev.minted.wallet;

import dev.minted.compat.InteractionHand;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Right-clicking the wallet in your hand opens its menu, the same way /wallet
 * does. Sneaking is left alone - that is the trigger for the player payment
 * menu, and a wallet in your hand should never shadow it.
 */
public final class WalletListener implements Listener {

    private final WalletManager wallets;

    public WalletListener(WalletManager wallets) {
        this.wallets = wallets;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (InteractionHand.isOffHand(event)) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!wallets.isWallet(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        wallets.open(player);
    }
}