package dev.minted.shop;

import dev.minted.bank.MoneyFormat;
import dev.minted.bank.Purse;
import dev.minted.bank.WalletService;
import dev.minted.banknote.BanknoteManager;
import dev.minted.lang.Messages;
import dev.minted.shop.catalog.Category;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.UUID;

/**
 * The community marketplace's money and stock movements. Buys and sells go
 * through the same {@link WalletService} purses and caps the global shop uses,
 * so physical/digital economy and the balance limit are all honoured. Each sale
 * accrues to the seller's per-listing earnings ledger; the seller mints those to
 * their own purse with Collect. Every stock change is persisted through {@link
 * ShopService}'s single writer thread.
 */
public final class Market {

    /** Most units a single listing can hold - the physical cap anyone could carry. */
    public static final int LISTING_CAP = 999;

    private final ShopService shops;
    private final WalletService wallet;
    private final BanknoteManager banknotes;
    private final MoneyFormat format;
    private final Messages messages;

    public Market(ShopService shops, WalletService wallet, BanknoteManager banknotes,
                  MoneyFormat format, Messages messages) {
        this.shops = shops;
        this.wallet = wallet;
        this.banknotes = banknotes;
        this.format = format;
        this.messages = messages;
    }

    public boolean isBanknote(ItemStack item) {
        return banknotes.isBanknote(item);
    }

    /**
     * Creates a listing: removes {@code amount} of the unit stack from the owner,
     * pins the price/buy-back/category. Refuses banknotes and non-positive prices.
     *
     * @return the created listing, or null if it could not be made (message sent)
     */
    public ShopItem list(Player owner, Shop community, ItemStack unit, int amount,
                         double price, double buyBack, Category category) {
        if (community == null || !community.isCommunity()) {
            return null;
        }
        if (unit == null) {
            messages.send(owner, "community.list.hold");
            return null;
        }
        if (isBanknote(unit)) {
            messages.send(owner, "community.list.banknote");
            return null;
        }
        if (price <= 0) {
            messages.send(owner, "community.list.price-invalid");
            return null;
        }
        int have = Inventories.count(owner, unit);
        int stock = Math.min(Math.min(amount, have), LISTING_CAP);
        if (stock <= 0) {
            messages.send(owner, "community.list.none");
            return null;
        }
        int address = community.firstFreeAddress();
        if (address < 0) {
            messages.send(owner, "community.list.full");
            return null;
        }
        Inventories.remove(owner, unit, stock);
        ShopItem listing = new ShopItem(community.getId(),
                address / Shop.SLOTS_PER_PAGE, address % Shop.SLOTS_PER_PAGE,
                unit.clone(), price, ShopItem.NOT_OFFERED, category.key());
        listing.setOwner(owner.getUniqueId());
        listing.setStock(stock);
        listing.setBuyBackPrice(buyBack > 0 ? buyBack : ShopItem.NOT_OFFERED);
        shops.saveItem(community, listing);
        messages.send(owner, "community.list.created",
                "quantity", String.valueOf(stock), "item", name(unit), "price", format.format(price));
        return listing;
    }

    public void buy(Player buyer, Shop community, ShopItem listing, int quantity) {
        if (listing.getOwner() == null || listing.getStock() <= 0) {
            messages.send(buyer, "community.buy.gone");
            return;
        }
        if (listing.getOwner().equals(buyer.getUniqueId())) {
            messages.send(buyer, "community.buy.own");
            return;
        }
        Purse purse = wallet.purseFor(buyer);
        if (purse == null) {
            messages.send(buyer, "community.buy.not-ready");
            return;
        }
        int qty = (int) Math.min(quantity, listing.getStock());
        if (qty <= 0) {
            messages.send(buyer, "community.buy.gone");
            return;
        }
        double price = listing.getBuyPrice() * qty;
        if (!purse.charge(price)) {
            messages.send(buyer, "community.buy.insufficient", "price", format.format(price));
            return;
        }
        listing.setStock(listing.getStock() - qty);
        listing.addEarnings(price);
        boolean dropped = Inventories.giveOrDrop(buyer, listing.copy(), qty);
        persist(community, listing);
        messages.send(buyer, "community.buy.success",
                "quantity", String.valueOf(qty), "item", name(listing.raw()), "price", format.format(price));
        if (dropped) {
            messages.send(buyer, "community.buy.overflow");
        }
    }

    /** Sells the seller's matching items into a listing whose owner offers a buy-back. */
    public void sellTo(Player seller, Shop community, ShopItem listing, int quantity) {
        if (listing.getOwner() == null || !listing.buysBack()) {
            messages.send(seller, "community.sell.closed");
            return;
        }
        if (listing.getOwner().equals(seller.getUniqueId())) {
            messages.send(seller, "community.sell.own");
            return;
        }
        Player owner = Bukkit.getPlayer(listing.getOwner());
        if (owner == null) {
            messages.send(seller, "community.sell.owner-offline");
            return;
        }
        int room = (int) (LISTING_CAP - listing.getStock());
        int qty = Math.min(Math.min(quantity, Inventories.count(seller, listing.raw())), room);
        if (qty <= 0) {
            messages.send(seller, room <= 0 ? "community.sell.full" : "community.sell.none",
                    "item", name(listing.raw()));
            return;
        }
        double payout = listing.getBuyBackPrice() * qty;
        Purse ownerPurse = wallet.purseFor(owner);
        Purse sellerPurse = wallet.purseFor(seller);
        if (ownerPurse == null || sellerPurse == null) {
            messages.send(seller, "community.buy.not-ready");
            return;
        }
        if (!ownerPurse.charge(payout)) {
            messages.send(seller, "community.sell.owner-broke");
            return;
        }
        // Remove the goods before crediting: paying cash mints notes into the
        // seller's inventory, and we must not scan the sold stack as still there.
        int removed = Inventories.remove(seller, listing.raw(), qty);
        double earned = listing.getBuyBackPrice() * removed;
        if (!sellerPurse.credit(earned)) {
            // The seller hit their balance cap; hand the goods back and refund
            // the owner, so neither side silently loses anything.
            Inventories.giveOrDrop(seller, listing.copy(), removed);
            ownerPurse.credit(payout);
            messages.send(seller, "community.sell.cap");
            return;
        }
        listing.setStock(listing.getStock() + removed);
        persist(community, listing);
        messages.send(seller, "community.sell.success",
                "quantity", String.valueOf(removed), "item", name(listing.raw()),
                "earned", format.format(earned));
    }

    /** Mints the listing's earnings to the owner's purse and resets them to zero. */
    public void collect(Player owner, Shop community, ShopItem listing) {
        if (listing.getEarnings() <= 0) {
            messages.send(owner, "community.collect.none");
            return;
        }
        Purse purse = wallet.purseFor(owner);
        if (purse == null) {
            messages.send(owner, "community.buy.not-ready");
            return;
        }
        double amount = listing.getEarnings();
        if (!purse.credit(amount)) {
            // Balance cap reached; keep the earnings so they are never lost.
            messages.send(owner, "community.collect.cap");
            return;
        }
        listing.setEarnings(0);
        persist(community, listing);
        messages.send(owner, "community.collect.done", "amount", format.format(amount));
    }

    /** Returns all remaining stock to the owner and drops the listing (earnings survive). */
    public void withdraw(Player owner, Shop community, ShopItem listing) {
        long stock = listing.getStock();
        if (stock > 0) {
            Inventories.giveOrDrop(owner, listing.copy(), (int) stock);
        }
        listing.setStock(0);
        messages.send(owner, "community.withdraw.done", "quantity", String.valueOf(stock));
        persist(community, listing);
    }

    // A community row that has no stock and no pending earnings is spent - delete
    // it. Otherwise keep it: sold-out listings linger only to hold their earnings.
    private void persist(Shop community, ShopItem listing) {
        if (listing.getStock() <= 0 && listing.getEarnings() <= 0) {
            shops.removeItem(community, listing.getPage(), listing.getSlot());
        } else {
            shops.saveItem(community, listing);
        }
    }

    private String name(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
