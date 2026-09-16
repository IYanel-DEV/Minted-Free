package dev.minted.request;

import dev.minted.bank.MoneyFormat;
import dev.minted.bank.WalletService;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds pending money requests and settles the accepted ones. A request is
 * removed from the map the instant it is acted on, so it can pay out at most
 * once; the periodic {@link #purgeExpired()} sweep and the quit handler drop
 * the rest.
 */
public final class RequestService implements Listener {

    private final Plugin plugin;
    private final WalletService wallet;
    private final MoneyFormat format;
    private final long expiryMillis;

    private final ConcurrentHashMap<UUID, PaymentRequest> byId =
            new ConcurrentHashMap<UUID, PaymentRequest>();

    public RequestService(Plugin plugin, WalletService wallet, MoneyFormat format, long expirySeconds) {
        this.plugin = plugin;
        this.wallet = wallet;
        this.format = format;
        this.expiryMillis = expirySeconds * 1000L;
    }

    public PaymentRequest create(UUID requester, UUID payer, double amount) {
        PaymentRequest request = new PaymentRequest(
                UUID.randomUUID(), requester, payer, amount, System.currentTimeMillis() + expiryMillis);
        byId.put(request.getId(), request);
        return request;
    }

    /** Settles the request if it is still valid and {@code acceptor} is its payer. */
    public void accept(UUID id, Player acceptor) {
        PaymentRequest request = byId.remove(id);
        if (request == null || !request.getPayer().equals(acceptor.getUniqueId())) {
            acceptor.sendMessage(ChatColor.RED + "That request is no longer available.");
            return;
        }
        if (request.isExpired(System.currentTimeMillis())) {
            acceptor.sendMessage(ChatColor.RED + "That request has expired.");
            return;
        }

        Player requester = plugin.getServer().getPlayer(request.getRequester());
        if (!wallet.transfer(acceptor, requester, request.getAmount())) {
            acceptor.sendMessage(ChatColor.RED + "Payment failed - the recipient must be online and you need the funds.");
            return;
        }

        acceptor.sendMessage(ChatColor.GREEN + "Paid " + ChatColor.WHITE + format.format(request.getAmount())
                + ChatColor.GREEN + ".");
        if (requester != null) {
            requester.sendMessage(ChatColor.GREEN + acceptor.getName() + " paid your request for "
                    + ChatColor.WHITE + format.format(request.getAmount()) + ChatColor.GREEN + ".");
        }
    }

    public void decline(UUID id, Player decliner) {
        PaymentRequest request = byId.remove(id);
        if (request == null || !request.getPayer().equals(decliner.getUniqueId())) {
            return;
        }
        decliner.sendMessage(ChatColor.GRAY + "Request declined.");
        Player requester = plugin.getServer().getPlayer(request.getRequester());
        if (requester != null) {
            requester.sendMessage(ChatColor.GRAY + decliner.getName() + " declined your request.");
        }
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<PaymentRequest> it = byId.values().iterator();
        while (it.hasNext()) {
            if (it.next().isExpired(now)) {
                it.remove();
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Iterator<PaymentRequest> it = byId.values().iterator();
        while (it.hasNext()) {
            if (it.next().involves(uuid)) {
                it.remove();
            }
        }
    }
}
