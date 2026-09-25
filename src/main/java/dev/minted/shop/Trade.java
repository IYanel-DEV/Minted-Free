package dev.minted.shop;

import dev.minted.bank.BankAccount;
import dev.minted.bank.EconomyService;
import dev.minted.bank.EconomyStats;
import dev.minted.bank.MoneyFormat;
import dev.minted.bank.Purse;
import dev.minted.bank.WalletService;
import dev.minted.discord.DiscordWebhookService;
import dev.minted.lang.Messages;
import dev.minted.ledger.LedgerService;
import dev.minted.shop.log.SaleLog;
import dev.minted.tax.TaxService;

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
    private final LedgerService ledger;
    private final PaymentService payments;
    private final DeliveryService deliveries;
    private TaxService taxService;
    private DiscordWebhookService discordWebhook;

    public Trade(WalletService wallet, EconomyService bank, MoneyFormat format, Messages messages,
                 EconomyStats stats, SaleLog sales, LedgerService ledger, PaymentService payments,
                 DeliveryService deliveries, TaxService taxService, DiscordWebhookService discordWebhook) {
        this.wallet = wallet;
        this.bank = bank;
        this.format = format;
        this.messages = messages;
        this.stats = stats;
        this.sales = sales;
        this.ledger = ledger;
        this.payments = payments;
        this.deliveries = deliveries;
        this.taxService = taxService;
        this.discordWebhook = discordWebhook;
    }

    /** Called after TaxService is initialized. */
    public void setTaxService(TaxService taxService) {
        this.taxService = taxService;
    }

    /** Called after DiscordWebhookService is initialized. */
    public void setDiscordWebhook(DiscordWebhookService discordWebhook) {
        this.discordWebhook = discordWebhook;
    }

    public void buy(Player player, Shop shop, ShopItem item, int quantity) {
        if (quantity <= 0) {
            return;
        }
        if (!item.isBuyable()) {
            messages.send(player, "buy.not-buyable");
            return;
        }
        double multiplier = Discounts.buyMultiplier(player, shop.getName());
        double basePrice = item.getBuyPrice() * quantity * multiplier;
        double taxAmount = 0.0;
        if (taxService != null && taxService.isEnabled()) {
            taxAmount = taxService.calculateGlobalShopBuyTax(item.getBuyPrice() * quantity * multiplier);
        }
        double totalPrice = basePrice + taxAmount;
        // The player picks the purse (wallet, bank, or wallet-then-bank), and a
        // bank-funded purchase starts the delivery cooldown.
        PaymentService.Charge charge = payments.charge(player, totalPrice);
        if (charge.isCooling()) {
            messages.send(player, "buy.bank-cooldown", "seconds", String.valueOf(charge.remainingSeconds()));
            return;
        }
        if (charge.isNotReady()) {
            messages.send(player, "buy.not-ready");
            return;
        }
        if (!charge.isPaid()) {
            messages.send(player, "buy.insufficient", "price", format.format(totalPrice));
            return;
        }
        // Money that left the digital economy joins the burned total. Physical
        // cash is exempt by contract: a consumed banknote is untracked pocket
        // money, not a burned digital balance.
        if (charge.usedBank() || !wallet.isPhysical()) {
            stats.burn(basePrice);
        }
        // Burn tax amount (removed from economy)
        if (taxAmount > 0) {
            stats.burn(taxAmount);
        }
        String label = describe(item.raw());
        boolean overflowed;
        if (charge.usedBank() && deliveries.delaySeconds() > 0L) {
            // A bank purchase is delivered, not handed over: the goods arrive
            // after the cooldown the player was just told about.
            deliveries.schedule(player, item.copy(), quantity, label);
            overflowed = false;
        } else {
            overflowed = giveOrDrop(player, item.copy(), quantity);
        }
        sales.record("shop", null, player.getUniqueId(), label, quantity, basePrice);
        ledger.record(player.getUniqueId(), -basePrice, "shop-buy", label);
        if (taxAmount > 0) {
            ledger.record(player.getUniqueId(), -taxAmount, "tax", "global-shop-buy");
        }
        messages.send(player, "buy.success",
                "quantity", String.valueOf(quantity),
                "item", label,
                "price", format.format(basePrice),
                "tax", format.format(taxAmount),
                "total", format.format(basePrice + taxAmount),
                "balance", format.format(charge.purse().balance()));
        if (multiplier < 1.0) {
            messages.send(player, "buy.discount", "percent", trim((1.0 - multiplier) * 100.0));
        }
        if (overflowed) {
            messages.send(player, "buy.overflow");
        }
        if (discordWebhook != null) {
            discordWebhook.sendShopPurchase(player, shop, item, quantity, basePrice, taxAmount);
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
        double baseEarned = item.getSellPrice() * quantitySold * multiplier;
        double taxAmount = 0.0;
        if (taxService != null && taxService.isEnabled()) {
            taxAmount = taxService.calculateGlobalShopSellTax(item.getSellPrice() * quantitySold * multiplier);
        }
        double netEarned = baseEarned - taxAmount;
        // Remove the goods first: paying cash mints notes into the same inventory,
        // and we must not scan the sold stack as if it were still there.
        remove(player, item.raw(), quantitySold);
        if (!purse.credit(netEarned)) {
            // Only a digital bank cap can refuse a payout; hand the goods back.
            giveOrDrop(player, item.copy(), quantitySold);
            messages.send(player, "sell.cap");
            return;
        }
        sales.record("shop", player.getUniqueId(), null, describe(item.raw()), quantitySold, baseEarned);
        ledger.record(player.getUniqueId(), netEarned, "shop-sell", describe(item.raw()));
        if (taxAmount > 0) {
            ledger.record(player.getUniqueId(), -taxAmount, "tax", "global-shop-sell");
        }
        messages.send(player, "sell.success",
                "quantity", String.valueOf(quantitySold),
                "item", describe(item.raw()),
                "earned", format.format(baseEarned),
                "tax", format.format(taxAmount),
                "net", format.format(netEarned),
                "balance", format.format(purse.balance()));
        if (discordWebhook != null) {
            discordWebhook.sendShopSale(player, null, shop, item, quantitySold, baseEarned, taxAmount);
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
            if (stack != null && ShopItem.sameStock(stack, template)) {
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
            if (stack == null || !ShopItem.sameStock(stack, template)) {
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
