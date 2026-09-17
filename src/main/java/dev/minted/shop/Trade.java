package dev.minted.shop;

import dev.minted.bank.BankAccount;
import dev.minted.bank.EconomyService;
import dev.minted.bank.EconomyStats;
import dev.minted.bank.MoneyFormat;
import dev.minted.bank.Purse;
import dev.minted.bank.WalletService;
import dev.minted.lang.Messages;
import dev.minted.shop.log.SaleLog;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

/**
 * Runs buys and sells against the shop's currency and the player's inventory.
 *
 * <p>Money moves through a {@link Purse}: a wallet purse (physical notes or a
 * digital balance, whichever the server chose) for wallet shops, and the digital
 * bank account for bank shops. So there is one money-in and one money-out path
 * and paying a seller in cash is just {@code purse.credit}. Discount and
 * sell-multiplier nodes are read here, in the trade path only, and every outcome
 * is reported through {@link Messages}.
 */
public final class Trade {

    private final WalletService wallet;
    private final EconomyService bank;
    private final MoneyFormat format;
    private final Messages messages;
    private final EconomyStats stats;
    private final SaleLog sales;

    public Trade(WalletService wallet, EconomyService bank, MoneyFormat format, Messages messages,
                 EconomyStats stats, SaleLog sales) {
        this.wallet = wallet;
        this.bank = bank;
        this.format = format;
        this.messages = messages;
        this.stats = stats;
        this.sales = sales;
    }

    public void buy(Player player, Shop shop, ShopItem item, int quantity) {
        if (quantity <= 0) {
            return;
        }
        Purse purse = purseFor(shop, player);
        if (purse == null) {
            messages.send(player, "buy.not-ready");
            return;
        }
        if (!item.isBuyable()) {
            messages.send(player, "buy.not-buyable");
            return;
        }
        double multiplier = Discounts.buyMultiplier(player, shop.getName());
        double price = item.getBuyPrice() * quantity * multiplier;
        if (!purse.charge(price)) {
            messages.send(player, "buy.insufficient", "price", format.format(price));
            return;
        }
        // Buying from a digital shop destroys the money (nothing receives it),
        // so it joins the burned total. Physical-cash spends are exempt by
        // contract: a consumed banknote is untracked pocket money, not a burned
        // digital balance.
        if (shop.getCurrency() == Currency.BANK || !wallet.isPhysical()) {
            stats.burn(price);
        }
        boolean overflowed = giveOrDrop(player, item.copy(), quantity);
        sales.record("shop", null, player.getUniqueId(), describe(item.raw()), quantity, price);
        messages.send(player, "buy.success",
                "quantity", String.valueOf(quantity),
                "item", describe(item.raw()),
                "price", format.format(price),
                "balance", format.format(purse.balance()));
        if (multiplier < 1.0) {
            messages.send(player, "buy.discount", "percent", trim((1.0 - multiplier) * 100.0));
        }
        if (overflowed) {
            messages.send(player, "buy.overflow");
        }
    }

    public void sell(Player player, Shop shop, ShopItem item, int quantity) {
        if (quantity <= 0) {
            return;
        }
        Purse purse = purseFor(shop, player);
        if (purse == null) {
            messages.send(player, "sell.not-ready");
            return;
        }
        if (!item.isSellable() || item.getSellPrice() <= 0) {
            messages.send(player, "sell.not-sellable");
            return;
        }
        int quantitySold = Math.min(quantity, count(player, item.raw()));
        if (quantitySold <= 0) {
            messages.send(player, "sell.none", "item", describe(item.raw()));
            return;
        }
        double multiplier = Discounts.sellMultiplier(player, shop.getName());
        double earned = item.getSellPrice() * quantitySold * multiplier;
        // Remove the goods first: paying cash mints notes into the same inventory,
        // and we must not scan the sold stack as if it were still there.
        remove(player, item.raw(), quantitySold);
        if (!purse.credit(earned)) {
            // Only a digital bank cap can refuse a payout; hand the goods back.
            giveOrDrop(player, item.copy(), quantitySold);
            messages.send(player, "sell.cap");
            return;
        }
        sales.record("shop", player.getUniqueId(), null, describe(item.raw()), quantitySold, earned);
        messages.send(player, "sell.success",
                "quantity", String.valueOf(quantitySold),
                "item", describe(item.raw()),
                "earned", format.format(earned),
                "balance", format.format(purse.balance()));
        if (multiplier > 1.0) {
            messages.send(player, "sell.bonus", "factor", trim(multiplier));
        }
    }

    public void sellOne(Player player, Shop shop, ShopItem item) {
        sell(player, shop, item, 1);
    }

    // Bank shops trade the digital bank account; wallet shops use the wallet
    // purse, which is physical notes or a digital balance depending on config.
    private Purse purseFor(Shop shop, Player player) {
        if (shop.getCurrency() == Currency.BANK) {
            BankAccount account = bank.getCached(player.getUniqueId());
            return account == null ? null : Purse.digital(account);
        }
        return wallet.purseFor(player);
    }

    /** Gives the stack, dropping any overflow at the player's feet. */
    private boolean giveOrDrop(Player player, ItemStack template, int quantity) {
        int max = template.getMaxStackSize();
        int remaining = quantity;
        boolean overflowed = false;
        while (remaining > 0) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(remaining, max));
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                overflowed = true;
            }
            remaining -= stack.getAmount();
        }
        return overflowed;
    }

    private int count(Player player, ItemStack template) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.isSimilar(template)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void remove(Player player, ItemStack template, int quantity) {
        Inventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        int remaining = quantity;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !stack.isSimilar(template)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - taken);
            inventory.setItem(slot, stack.getAmount() > 0 ? stack : null);
            remaining -= taken;
        }
    }

    private String describe(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String trim(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
