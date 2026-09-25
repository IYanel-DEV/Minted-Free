package dev.minted.tax;

import dev.minted.MintedPlugin;
import dev.minted.bank.MoneyFormat;

import java.util.Locale;

/**
 * Handles tax calculation and collection for economy transactions.
 * Taxes are burned (removed from economy) and logged.
 */
public final class TaxService {

    private final MintedPlugin plugin;
    private final MoneyFormat format;
    private final TaxConfig config;

    public TaxService(MintedPlugin plugin, MoneyFormat format) {
        this.plugin = plugin;
        this.format = format;
        this.config = loadConfig();
    }

    private TaxConfig loadConfig() {
        TaxConfig cfg = new TaxConfig();
        cfg.enabled = plugin.getConfig().getBoolean("tax.enabled", false);
        cfg.globalShopBuy = plugin.getConfig().getDouble("tax.global-shop-buy", 0.0);
        cfg.globalShopSell = plugin.getConfig().getDouble("tax.global-shop-sell", 0.0);
        cfg.playerShopBuy = plugin.getConfig().getDouble("tax.player-shop-buy", 0.0);
        cfg.playerShopSell = plugin.getConfig().getDouble("tax.player-shop-sell", 0.0);
        cfg.auctionBuy = plugin.getConfig().getDouble("tax.auction-buy", 0.0);
        cfg.auctionSell = plugin.getConfig().getDouble("tax.auction-sell", 0.0);
        cfg.minimumAmount = plugin.getConfig().getDouble("tax.minimum-amount", 0.0);
        return cfg;
    }

    /** Calculate tax amount for a given base price and tax rate. */
    public double calculateTax(double basePrice, double taxRate) {
        if (!config.enabled || taxRate <= 0) return 0.0;
        if (config.minimumAmount > 0 && basePrice < config.minimumAmount) return 0.0;
        return basePrice * taxRate / 100.0;
    }

    /** Calculate buy tax for global shop. */
    public double calculateGlobalShopBuyTax(double price) {
        return calculateTax(price, config.globalShopBuy);
    }

    /** Calculate sell tax for global shop. */
    public double calculateGlobalShopSellTax(double price) {
        return calculateTax(price, config.globalShopSell);
    }

    /** Calculate buy tax for player shop. */
    public double calculatePlayerShopBuyTax(double price) {
        return calculateTax(price, config.playerShopBuy);
    }

    /** Calculate sell tax for player shop (buy-back). */
    public double calculatePlayerShopSellTax(double price) {
        return calculateTax(price, config.playerShopSell);
    }

    /** Calculate buy tax for auction. */
    public double calculateAuctionBuyTax(double price) {
        return calculateTax(price, config.auctionBuy);
    }

    /** Calculate sell tax for auction. */
    public double calculateAuctionSellTax(double price) {
        return calculateTax(price, config.auctionSell);
    }

    /** Check if tax system is enabled. */
    public boolean isEnabled() {
        return config.enabled;
    }

    /** Format tax rate for display (e.g., "5.0%"). */
    public String formatRate(double rate) {
        return String.format(Locale.US, "%.1f%%", rate);
    }

    /** Configuration holder. */
    private static class TaxConfig {
        boolean enabled;
        double globalShopBuy;
        double globalShopSell;
        double playerShopBuy;
        double playerShopSell;
        double auctionBuy;
        double auctionSell;
        double minimumAmount;
    }
}