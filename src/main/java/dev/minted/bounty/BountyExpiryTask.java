package dev.minted.bounty;

import dev.minted.bank.MoneyFormat;
import dev.minted.lang.Messages;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Walks the open bounty board and refunds any posting that has sat unclaimed
 * longer than the configured window. Refunds reuse the normal refund path, so
 * the money lands back in the placer's bank and the row is marked refunded
 * off the main thread. Runs on the main thread; the heavy lifting stays async.
 */
public final class BountyExpiryTask implements Runnable {

    private final Plugin plugin;
    private final BountyService bounties;
    private final MoneyFormat format;
    private final Messages messages;

    public BountyExpiryTask(Plugin plugin, BountyService bounties, MoneyFormat format, Messages messages) {
        this.plugin = plugin;
        this.bounties = bounties;
        this.format = format;
        this.messages = messages;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Bounty bounty : bounties.expired(now)) {
            if (bounties.refund(bounty, bounty.placer(), true)) {
                Player placer = plugin.getServer().getPlayer(bounty.placer());
                if (placer != null) {
                    messages.send(placer, "bounty.expired", "amount", format.format(bounty.amount()));
                }
            }
        }
    }
}