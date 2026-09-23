package dev.minted.auction;

import dev.minted.auction.storage.AuctionDao;
import dev.minted.auction.storage.AuctionBidRow;
import dev.minted.bank.EconomyService;
import dev.minted.bank.MoneyFormat;
import dev.minted.bank.Purse;
import dev.minted.bank.WalletService;
import dev.minted.lang.Messages;
import dev.minted.shop.ItemCodec;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs the auction house: create, bid, buyout, expire, collect. All money
 * moves through the wallet purse so the physical/digital economy and balance
 * cap are honoured. The database is written through a single writer thread
 * (borrowed from ShopService's pattern) so the main thread never blocks.
 */
public final class AuctionService {

    public static boolean isBanknote(ItemStack item) {
        // Banknotes have a special NBT tag or can be identified by their type/name
        // This is a simple check - actual implementation would check NBT
        if (item == null) return false;
        return item.getType() == org.bukkit.Material.PAPER
                && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains("Banknote");
    }

    private final AuctionDao dao;
    private final WalletService wallet;
    private final EconomyService walletEconomy;
    private final MoneyFormat format;
    private final Messages messages;
    private final dev.minted.ledger.LedgerService ledger;

    private final double listingFeePercent;
    private final double minStartPrice;
    private final int maxDurationHours;
    private final int minDurationMinutes;
    private final int maxItemsPerPlayer;

    private final Map<Long, AuctionItem> auctions = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private volatile boolean ready;

    public AuctionService(AuctionDao dao, WalletService wallet, EconomyService walletEconomy,
                          MoneyFormat format, Messages messages, dev.minted.ledger.LedgerService ledger,
                          double listingFeePercent, double minStartPrice, int maxDurationHours,
                          int minDurationMinutes, int maxItemsPerPlayer) {
        this.dao = dao;
        this.wallet = wallet;
        this.walletEconomy = walletEconomy;
        this.format = format;
        this.messages = messages;
        this.ledger = ledger;
        this.listingFeePercent = listingFeePercent;
        this.minStartPrice = minStartPrice;
        this.maxDurationHours = maxDurationHours;
        this.minDurationMinutes = minDurationMinutes;
        this.maxItemsPerPlayer = maxItemsPerPlayer;
    }

    public double getListingFeePercent() {
        return listingFeePercent;
    }

    public double getMinStartPrice() {
        return minStartPrice;
    }

    public int getMaxDurationHours() {
        return maxDurationHours;
    }

    public int getMinDurationMinutes() {
        return minDurationMinutes;
    }

    public int getMaxItemsPerPlayer() {
        return maxItemsPerPlayer;
    }

    public void initialize() {
        loadAsync();
    }

    public void reload() {
        loadAsync();
    }

    private void loadAsync() {
        new Thread(() -> {
            try {
                dao.createTables();
                List<AuctionItem> loaded = dao.loadAuctions();
                long maxId = 0;
                for (AuctionItem a : loaded) {
                    if (a.getState() == AuctionState.ACTIVE && !a.isExpired()) {
                        auctions.put(a.getId(), a);
                    }
                    if (a.getId() > maxId) maxId = a.getId();
                }
                nextId.set(maxId + 1);
                ready = true;
                // Clean up expired auctions on startup
                cleanupExpired();
            } catch (RuntimeException e) {
                // Logger would be here but we don't have plugin reference
                e.printStackTrace();
            }
        }, "Minted-Auction-Loader").start();
    }

    public boolean isReady() {
        return ready;
    }

    public AuctionItem createAuction(Player seller, ItemStack item, int amount, double startPrice,
                                     double buyoutPrice, int durationHours) {
        if (!ready) return null;

        Purse purse = wallet.purseFor(seller);
        if (purse == null) return null;

        int have = dev.minted.shop.Inventories.count(seller, item);
        if (have < amount) return null;

        double fee = startPrice * (listingFeePercent / 100.0);
        if (!purse.charge(fee)) return null;

        dev.minted.shop.Inventories.remove(seller, item, amount);
        ItemStack listingItem = item.clone();
        listingItem.setAmount(amount);

        long id = nextId.getAndIncrement();
        long now = System.currentTimeMillis();
        long endsAt = now + (durationHours * 3600L * 1000L);

        AuctionItem auction = new AuctionItem(id, seller.getUniqueId(), listingItem, startPrice,
                buyoutPrice, endsAt, now);
        auctions.put(id, auction);

        dao.insertAuction(auction);
        ledger.record(seller.getUniqueId(), -fee, "auction_listing_fee",
                "Listed " + amount + "x " + item.getType().name() + " for " + format.format(startPrice));

        return auction;
    }

    public boolean placeBid(Player bidder, long auctionId, double amount) {
        AuctionItem auction = auctions.get(auctionId);
        if (auction == null || !auction.isActive()) return false;
        if (bidder.getUniqueId().equals(auction.getSeller())) return false;
        if (amount <= auction.getEffectivePrice()) return false;
        if (auction.hasBuyout() && amount >= auction.getBuyoutPrice()) return false;

        Purse purse = wallet.purseFor(bidder);
        if (purse == null) return false;

        double required = amount;
        if (auction.getHighestBidder() != null && auction.getHighestBidder().equals(bidder.getUniqueId())) {
            required = amount - auction.getCurrentBid();
        }

        if (!purse.charge(required)) return false;

        // Refund previous highest bidder
        if (auction.getHighestBidder() != null && !auction.getHighestBidder().equals(bidder.getUniqueId())) {
            UUID oldBidder = auction.getHighestBidder();
            Player oldPlayer = Bukkit.getPlayer(oldBidder);
            if (oldPlayer != null) {
                Purse oldPurse = wallet.purseFor(oldPlayer);
                if (oldPurse != null) {
                    oldPurse.credit(auction.getCurrentBid());
                    messages.send(oldPlayer, "auction.bid.outbid",
                            "item", itemName(auction.getRawItem()),
                            "price", format.format(amount));
                } else {
                    walletEconomy.deposit(oldBidder, auction.getCurrentBid());
                }
            } else {
                walletEconomy.deposit(oldBidder, auction.getCurrentBid());
            }
        }

        auction.setCurrentBid(amount);
        auction.setHighestBidder(bidder.getUniqueId());
        auction.incrementBidCount();
        dao.updateAuction(auction);
        dao.insertBid(auctionId, bidder.getUniqueId(), amount, System.currentTimeMillis());

        messages.send(bidder, "auction.bid.placed",
                "item", itemName(auction.getRawItem()),
                "price", format.format(amount));

        // Notify seller
        Player seller = Bukkit.getPlayer(auction.getSeller());
        if (seller != null) {
            messages.send(seller, "auction.bid.received",
                    "item", itemName(auction.getRawItem()),
                    "price", format.format(amount),
                    "bidder", bidder.getName());
        }

        return true;
    }

    public boolean buyout(Player buyer, long auctionId) {
        AuctionItem auction = auctions.get(auctionId);
        if (auction == null || !auction.isActive() || !auction.hasBuyout()) return false;
        if (buyer.getUniqueId().equals(auction.getSeller())) return false;

        Purse purse = wallet.purseFor(buyer);
        if (purse == null) return false;
        if (!purse.charge(auction.getBuyoutPrice())) return false;

        // Refund any existing bidder
        if (auction.getHighestBidder() != null && !auction.getHighestBidder().equals(buyer.getUniqueId())) {
            UUID oldBidder = auction.getHighestBidder();
            Player oldPlayer = Bukkit.getPlayer(oldBidder);
            if (oldPlayer != null) {
                Purse oldPurse = wallet.purseFor(oldPlayer);
                if (oldPurse != null) {
                    oldPurse.credit(auction.getCurrentBid());
                } else {
                    walletEconomy.deposit(oldBidder, auction.getCurrentBid());
                }
                messages.send(oldPlayer, "auction.bid.buyout",
                        "item", itemName(auction.getRawItem()));
            } else {
                walletEconomy.deposit(oldBidder, auction.getCurrentBid());
            }
        }

        completeAuction(auction, buyer.getUniqueId(), auction.getBuyoutPrice());
        return true;
    }

    public void expireAuction(long auctionId) {
        AuctionItem auction = auctions.get(auctionId);
        if (auction == null || auction.getState() != AuctionState.ACTIVE) return;
        if (!auction.isExpired()) return;

        if (auction.getHighestBidder() != null) {
            // Has a winner
            UUID winner = auction.getHighestBidder();
            Player winnerPlayer = Bukkit.getPlayer(winner);
            ItemStack item = auction.getItem();

            if (winnerPlayer != null) {
                boolean dropped = dev.minted.shop.Inventories.giveOrDrop(winnerPlayer, item, item.getAmount());
                if (dropped) {
                    messages.send(winnerPlayer, "auction.win.overflow");
                }
                messages.send(winnerPlayer, "auction.won",
                        "item", itemName(auction.getRawItem()),
                        "price", format.format(auction.getCurrentBid()));
            } else {
                // Offline - we'd need to store the item for later collection
                // For now, mail it or store in a pending collection table
                // TODO: Implement offline collection
            }

            completeAuction(auction, winner, auction.getCurrentBid());
        } else {
            // No bids - return item to seller
            returnItemToSeller(auction);
            auction.setState(AuctionState.EXPIRED);
            dao.updateAuction(auction);
        }
    }

    private void completeAuction(AuctionItem auction, UUID winner, double finalPrice) {
        auction.setState(AuctionState.SOLD);
        dao.updateAuction(auction);

        UUID seller = auction.getSeller();
        Player sellerPlayer = Bukkit.getPlayer(seller);
        Purse sellerPurse = sellerPlayer != null ? wallet.purseFor(sellerPlayer) : null;

        if (sellerPurse != null) {
            sellerPurse.credit(finalPrice);
        } else {
            walletEconomy.deposit(seller, finalPrice);
        }

        ledger.record(seller, finalPrice, "auction_sale",
                "Sold " + auction.getRawItem().getAmount() + "x " + auction.getRawItem().getType().name()
                        + " to " + (Bukkit.getOfflinePlayer(winner).getName() != null
                        ? Bukkit.getOfflinePlayer(winner).getName() : winner.toString())
                        + " for " + format.format(finalPrice));

        if (sellerPlayer != null) {
            messages.send(sellerPlayer, "auction.sold",
                    "item", itemName(auction.getRawItem()),
                    "price", format.format(finalPrice),
                    "buyer", Bukkit.getOfflinePlayer(winner).getName());
        }

        // Remove from active auctions after a delay so the buyer can see it
        // Actually remove immediately
        auctions.remove(auction.getId());
    }

    private void returnItemToSeller(AuctionItem auction) {
        UUID seller = auction.getSeller();
        Player sellerPlayer = Bukkit.getPlayer(seller);
        ItemStack item = auction.getItem();

        if (sellerPlayer != null) {
            boolean dropped = dev.minted.shop.Inventories.giveOrDrop(sellerPlayer, item, item.getAmount());
            if (dropped) {
                messages.send(sellerPlayer, "auction.expired.overflow");
            }
            messages.send(sellerPlayer, "auction.expired.no_bids",
                    "item", itemName(auction.getRawItem()));
        } else {
            // TODO: Store for offline collection
        }
    }

    public void cancelAuction(Player player, long auctionId) {
        AuctionItem auction = auctions.get(auctionId);
        if (auction == null) return;
        if (!auction.getSeller().equals(player.getUniqueId())) return;
        if (auction.getState() != AuctionState.ACTIVE) return;
        if (auction.getHighestBidder() != null) return; // Cannot cancel if has bids

        returnItemToSeller(auction);
        auction.setState(AuctionState.CANCELLED);
        dao.updateAuction(auction);
        auctions.remove(auctionId);

        messages.send(player, "auction.cancelled", "item", itemName(auction.getRawItem()));
    }

    public void collectExpired(Player player) {
        // TODO: Implement collection of expired/unsold items for offline players
    }

    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        List<Long> toExpire = new ArrayList<>();
        for (Map.Entry<Long, AuctionItem> entry : auctions.entrySet()) {
            AuctionItem a = entry.getValue();
            if (a.getState() == AuctionState.ACTIVE && a.isExpired()) {
                toExpire.add(entry.getKey());
            }
        }
        for (long id : toExpire) {
            expireAuction(id);
        }
    }

    public List<AuctionItem> getActiveAuctions() {
        List<AuctionItem> active = new ArrayList<>();
        for (AuctionItem a : auctions.values()) {
            if (a.isActive()) {
                active.add(a);
            }
        }
        active.sort(Comparator.comparingLong(AuctionItem::getEndsAt));
        return active;
    }

    public List<AuctionItem> getPlayerAuctions(UUID player) {
        List<AuctionItem> list = new ArrayList<>();
        for (AuctionItem a : auctions.values()) {
            if (a.getSeller().equals(player) && a.getState() == AuctionState.ACTIVE) {
                list.add(a);
            }
        }
        return list;
    }

    public List<AuctionItem> getPlayerBids(UUID player) {
        List<AuctionItem> list = new ArrayList<>();
        for (AuctionItem a : auctions.values()) {
            if (a.getHighestBidder() != null && a.getHighestBidder().equals(player) && a.isActive()) {
                list.add(a);
            }
        }
        return list;
    }

    public AuctionItem getAuction(long id) {
        return auctions.get(id);
    }

    public int getActiveCount(UUID player) {
        int count = 0;
        for (AuctionItem a : auctions.values()) {
            if (a.getSeller().equals(player) && a.getState() == AuctionState.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    private String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}