package dev.minted.discord;

import dev.minted.MintedPlugin;
import dev.minted.lang.Messages;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopItem;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Sends beautiful Discord embeds for economy events.
 * All HTTP is async on a dedicated single-thread executor so it never blocks the main thread.
 */
public final class DiscordWebhookService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#?([A-Fa-f0-9]{6})$");
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private final MintedPlugin plugin;
    private final Messages messages;
    private final ExecutorService executor;
    private final DiscordConfig config;
    private final boolean enabled;

    public DiscordWebhookService(MintedPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Minted-Discord-Webhook");
            t.setDaemon(true);
            return t;
        });
        this.config = loadConfig();
        this.enabled = config.enabled;
    }

    private DiscordConfig loadConfig() {
        DiscordConfig cfg = new DiscordConfig();
        cfg.enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        cfg.defaultWebhookUrl = plugin.getConfig().getString("discord.default-webhook-url", "");
        cfg.webhookUrls = new java.util.HashMap<>();
        if (plugin.getConfig().isConfigurationSection("discord.webhooks")) {
            for (String key : plugin.getConfig().getConfigurationSection("discord.webhooks").getKeys(false)) {
                cfg.webhookUrls.put(key, plugin.getConfig().getString("discord.webhooks." + key, ""));
            }
        }
        cfg.embedColor = parseColor(plugin.getConfig().getString("discord.embed.color", "#4CAF50"));
        cfg.thumbnailUrl = plugin.getConfig().getString("discord.embed.thumbnail-url", "");
        cfg.authorIconUrl = plugin.getConfig().getString("discord.embed.author-icon-url", "");
        cfg.footerText = plugin.getConfig().getString("discord.embed.footer-text", "Minted Economy");
        cfg.showServerName = plugin.getConfig().getBoolean("discord.embed.show-server-name", true);
        cfg.showServerIcon = plugin.getConfig().getBoolean("discord.embed.show-server-icon", false);
        cfg.largeTransferThreshold = plugin.getConfig().getLong("discord.large-transfer-threshold", 10000);
        cfg.pingRoles = new java.util.HashMap<>();
        if (plugin.getConfig().isConfigurationSection("discord.embed.ping-on")) {
            for (String key : plugin.getConfig().getConfigurationSection("discord.embed.ping-on").getKeys(false)) {
                cfg.pingRoles.put(key, plugin.getConfig().getString("discord.embed.ping-on." + key, ""));
            }
        }
        return cfg;
    }

    private Color parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return new Color(0x4CAF50);
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        if (!HEX_COLOR.matcher(hex).matches()) return new Color(0x4CAF50);
        try {
            int rgb = Integer.parseInt(clean, 16);
            return new Color(rgb);
        } catch (NumberFormatException e) {
            return new Color(0x4CAF50);
        }
    }

    /**
     * Base embed with the branded header (author = server name, optional icon),
     * configurable color and thumbnail. All event embeds build on this.
     */
    private EmbedBuilder baseEmbed(String title, String emoji, Color color) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(emoji + " " + title)
                .setColor(color)
                .setTimestamp()
                .setFooter(config.footerText + " | " + getServerNameSafe(),
                        config.showServerIcon && config.authorIconUrl != null && !config.authorIconUrl.isEmpty()
                                ? config.authorIconUrl : null);
        if (config.showServerName) {
            if (config.authorIconUrl != null && !config.authorIconUrl.isEmpty()) {
                embed.setAuthor(getServerNameSafe(), config.authorIconUrl);
            } else {
                embed.setAuthor(getServerNameSafe());
            }
        }
        if (config.thumbnailUrl != null && !config.thumbnailUrl.isEmpty()) {
            embed.setThumbnail(config.thumbnailUrl);
        }
        return embed;
    }

    /** A full-width blank divider field that visually groups field rows. */
    private static String DIVIDER = "\u200b";

    /** Send a shop sale notification (global/community shop). */
    public void sendShopSale(Player buyer, Player seller, Shop shop, ShopItem item, int quantity, double totalPrice, double taxAmount) {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("shop-sale");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        EmbedBuilder embed = baseEmbed("Shop Sale", "\ud83d\uded2", config.embedColor)
                .setDescription("**" + buyer.getName() + "** bought **" + formatNumber(quantity)
                        + " × " + formatItemName(item) + "** from **" + shop.getName() + "**")
                .addField("**Buyer**", buyer.getName(), true)
                .addField("**Seller**", seller != null ? seller.getName() : "Server", true)
                .addField("**Shop**", shop.getName(), true)
                .addField(DIVIDER, DIVIDER, false)
                .addField("**Item**", formatItemName(item), true)
                .addField("**Quantity**", formatNumber(quantity), true)
                .addField("**Total**", "💰 " + formatPrice(totalPrice), true);
        if (taxAmount > 0) {
            embed.addField(DIVIDER, DIVIDER, false)
                    .addField("**Tax Collected**", "🧾 " + formatPrice(taxAmount), true);
        }

        sendAsync(webhookUrl, embed.build());
    }

    /** Send a shop purchase notification (player bought from global/community shop). */
    public void sendShopPurchase(Player buyer, Shop shop, ShopItem item, int quantity, double totalPrice, double taxAmount) {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("shop-purchase");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        EmbedBuilder embed = baseEmbed("Shop Purchase", "\ud83d\uded0", config.embedColor)
                .setDescription("**" + buyer.getName() + "** purchased **" + formatNumber(quantity)
                        + " × " + formatItemName(item) + "** from **" + shop.getName() + "**")
                .addField("**Buyer**", buyer.getName(), true)
                .addField("**Shop**", shop.getName(), true)
                .addField("**Total**", "💰 " + formatPrice(totalPrice), true)
                .addField(DIVIDER, DIVIDER, false)
                .addField("**Item**", formatItemName(item), true)
                .addField("**Quantity**", formatNumber(quantity), true);
        if (taxAmount > 0) {
            embed.addField("**Tax Paid**", "🧾 " + formatPrice(taxAmount), true);
        }

        sendAsync(webhookUrl, embed.build());
    }

    /** Send a player shop sale notification. */
    public void sendPlayerShopSale(Player buyer, Player shopOwner, Shop shop, ShopItem item, int quantity, double totalPrice, double taxAmount) {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("player-shop-sale");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        EmbedBuilder embed = baseEmbed("Player Shop Sale", "\ud83c\udfea", new Color(0x26A69A))
                .setDescription("**" + buyer.getName() + "** bought **" + formatNumber(quantity)
                        + " × " + formatItemName(item) + "** from **" + shopOwner.getName() + "**")
                .addField("**Buyer**", buyer.getName(), true)
                .addField("**Shop Owner**", shopOwner.getName(), true)
                .addField("**Shop**", shop.getName(), true)
                .addField(DIVIDER, DIVIDER, false)
                .addField("**Item**", formatItemName(item), true)
                .addField("**Quantity**", formatNumber(quantity), true)
                .addField("**Total**", "💰 " + formatPrice(totalPrice), true);
        if (taxAmount > 0) {
            embed.addField("**Tax Collected**", "🧾 " + formatPrice(taxAmount), true);
        }

        sendAsync(webhookUrl, embed.build());
    }

    /** Send a player shop purchase notification. */
    public void sendPlayerShopPurchase(Player buyer, Player shopOwner, Shop shop, ShopItem item, int quantity, double totalPrice, double taxAmount) {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("player-shop-purchase");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        EmbedBuilder embed = baseEmbed("Player Shop Purchase", "\ud83d\uded0", new Color(0x26A69A))
                .setDescription("**" + buyer.getName() + "** purchased **" + formatNumber(quantity)
                        + " × " + formatItemName(item) + "** from **" + shopOwner.getName() + "**")
                .addField("**Buyer**", buyer.getName(), true)
                .addField("**Shop Owner**", shopOwner.getName(), true)
                .addField("**Shop**", shop.getName(), true)
                .addField(DIVIDER, DIVIDER, false)
                .addField("**Item**", formatItemName(item), true)
                .addField("**Quantity**", formatNumber(quantity), true)
                .addField("**Total**", "💰 " + formatPrice(totalPrice), true);
        if (taxAmount > 0) {
            embed.addField("**Tax Paid**", "🧾 " + formatPrice(taxAmount), true);
        }

        sendAsync(webhookUrl, embed.build());
    }

    /** Send an auction creation notification. */
    public void sendAuctionCreate(Player seller, String itemName, int quantity, double startPrice, double buyoutPrice, int durationHours) {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("auction-create");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        EmbedBuilder embed = baseEmbed("Auction Created", "\ud83d\udce6", new Color(0x7E57C2))
                .setDescription("**" + seller.getName() + "** is auctioning **" + formatNumber(quantity)
                        + " × " + itemName + "**")
                .addField("**Seller**", seller.getName(), true)
                .addField("**Item**", itemName, true)
                .addField("**Quantity**", formatNumber(quantity), true)
                .addField(DIVIDER, DIVIDER, false)
                .addField("**Starting Bid**", "💲 " + formatPrice(startPrice), true)
                .addField("**Buyout**", buyoutPrice > 0 ? "⚡ " + formatPrice(buyoutPrice) : "—", true)
                .addField("**Duration**", "⏱️ " + durationHours + "h", true);

        sendAsync(webhookUrl, embed.build());
    }

    /** Send an auction bid notification. */
    public void sendAuctionBid(Player bidder, String itemName, double bidAmount, double previousBid) {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("auction-bid");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        EmbedBuilder embed = baseEmbed("Auction Bid", "\ud83d\udcb0", new Color(0x42A5F5))
                .setDescription("**" + bidder.getName() + "** placed a bid on **" + itemName + "**")
                .addField("**Bidder**", bidder.getName(), true)
                .addField("**Item**", itemName, true)
                .addField("**New Bid**", "💲 " + formatPrice(bidAmount), true)
                .addField(DIVIDER, DIVIDER, false)
                .addField("**Previous Bid**", previousBid > 0 ? "💲 " + formatPrice(previousBid) : "—", true);

        sendAsync(webhookUrl, embed.build());
    }

    /** Send an auction buyout notification. */
    public void sendAuctionBuyout(Player buyer, String itemName, int quantity, double buyoutPrice, Player seller) {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("auction-buyout");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String ping = config.pingRoles.getOrDefault("auction-buyout", "");
        EmbedBuilder embed = baseEmbed("Auction Buyout", "\u26a1", new Color(0xFFD700))
                .setDescription("**" + buyer.getName() + "** instantly bought **" + formatNumber(quantity)
                        + " × " + itemName + "** for **" + formatPrice(buyoutPrice) + "**")
                .addField("**Buyer**", buyer.getName(), true)
                .addField("**Seller**", seller != null ? seller.getName() : "Unknown", true)
                .addField("**Item**", itemName, true)
                .addField(DIVIDER, DIVIDER, false)
                .addField("**Quantity**", formatNumber(quantity), true)
                .addField("**Buyout Price**", "⚡ " + formatPrice(buyoutPrice), true);

        sendAsync(webhookUrl, embed.build(), ping);
    }

    /** Send an auction expiration notification. */
    public void sendAuctionExpire(String itemName, int quantity, Player seller, double highestBid, Player highestBidder) {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("auction-expire");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        EmbedBuilder embed = baseEmbed("Auction Expired", "\u23f0", new Color(0xFF7043))
                .setDescription("The auction for **" + formatNumber(quantity) + " × " + itemName + "** by **" + seller.getName() + "** has ended")
                .addField("**Seller**", seller.getName(), true)
                .addField("**Item**", itemName, true)
                .addField("**Quantity**", formatNumber(quantity), true)
                .addField(DIVIDER, DIVIDER, false);
        if (highestBid > 0 && highestBidder != null) {
            embed.addField("**Highest Bid**", "💲 " + formatPrice(highestBid), true)
                    .addField("**Highest Bidder**", highestBidder.getName(), true)
                    .addField("**Result**", "📦 Item returned to seller", false);
        } else {
            embed.addField("**Result**", "📦 No bids — item returned to seller", false);
        }

        sendAsync(webhookUrl, embed.build());
    }

    /** Send an auction claim notification. */
    public void sendAuctionClaim(Player claimer, String itemName, int quantity, double price, boolean wasBuyout) {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("auction-claim");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        EmbedBuilder embed = baseEmbed(wasBuyout ? "Auction Buyout Claimed" : "Auction Won", "\u2705", new Color(0x4CAF50))
                .setDescription("**" + claimer.getName() + "** claimed **" + formatNumber(quantity)
                        + " × " + itemName + "**" + (wasBuyout ? " via buyout" : ""))
                .addField("**Winner**", claimer.getName(), true)
                .addField("**Item**", itemName, true)
                .addField("**Quantity**", formatNumber(quantity), true)
                .addField(DIVIDER, DIVIDER, false)
                .addField("**Price Paid**", "💰 " + formatPrice(price), true);

        sendAsync(webhookUrl, embed.build());
    }

    /** Send a bounty placement notification. */
    public void sendBountyPlace(Player placer, Player target, double amount, String note) {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("bounty-place");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        EmbedBuilder embed = baseEmbed("Bounty Placed", "\ud83c\udfaf", new Color(0xE91E63))
                .setDescription("**" + placer.getName() + "** placed a **" + formatPrice(amount)
                        + "** bounty on **" + target.getName() + "**")
                .addField("**Placer**", placer.getName(), true)
                .addField("**Target**", target.getName(), true)
                .addField("**Reward**", "🎯 " + formatPrice(amount), true);
        if (note != null && !note.isEmpty()) {
            embed.addField(DIVIDER, DIVIDER, false)
                    .addField("**Note**", note, false);
        }

        sendAsync(webhookUrl, embed.build());
    }

    /** Send a bounty claim notification. */
    public void sendBountyClaim(Player killer, Player target, double amount) {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("bounty-claim");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        EmbedBuilder embed = baseEmbed("Bounty Claimed", "\ud83d\udc80", new Color(0xAD1457))
                .setDescription("**" + killer.getName() + "** claimed the bounty on **" + target.getName() + "**")
                .addField("**Killer**", killer.getName(), true)
                .addField("**Target**", target.getName(), true)
                .addField("**Reward Collected**", "🎯 " + formatPrice(amount), true);

        sendAsync(webhookUrl, embed.build());
    }

    /** Send a balance milestone notification. */
    public void sendBalanceMilestone(Player player, double balance, String milestone) {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("balance-milestone");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        EmbedBuilder embed = baseEmbed("Balance Milestone Reached", "\ud83c\udfc6", new Color(0x9C27B0))
                .setDescription("**" + player.getName() + "** just hit the **" + milestone + "** milestone!")
                .addField("**Player**", player.getName(), true)
                .addField("**Milestone**", "🏆 " + milestone, true)
                .addField("**New Balance**", "💰 " + formatPrice(balance), true);

        sendAsync(webhookUrl, embed.build());
    }

    /** Send a large transfer notification. */
    public void sendLargeTransfer(Player from, Player to, double amount, String reason) {
        if (!enabled) return;
        if (amount < config.largeTransferThreshold) return;
        String webhookUrl = getWebhookUrl("large-transfer");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String ping = config.pingRoles.getOrDefault("large-transfer", "");
        EmbedBuilder embed = baseEmbed("Large Transfer", "\ud83d\udcb8", new Color(0x2196F3))
                .setDescription("**" + formatPrice(amount) + "** was transferred from **" + from.getName()
                        + "** to **" + to.getName() + "**")
                .addField("**From**", from.getName(), true)
                .addField("**To**", to.getName(), true)
                .addField("**Amount**", "💸 " + formatPrice(amount), true);
        if (reason != null && !reason.isEmpty()) {
            embed.addField(DIVIDER, DIVIDER, false)
                    .addField("**Reason**", reason, false);
        }

        sendAsync(webhookUrl, embed.build(), ping);
    }

    /** Send server start notification. */
    public void sendServerStart() {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("server-start");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String serverName = getServerNameSafe();
        
        EmbedBuilder embed = baseEmbed("Server Started", "\ud83d\udfe2", new Color(0x4CAF50))
                .setDescription("The server is now **online**.")
                .addField("**Server**", serverName, true)
                .addField("**Version**", normalizeVersion(Bukkit.getVersion()), true)
                .addField("**Minted Version**", plugin.getDescription().getVersion(), true)
                .addField(DIVIDER, DIVIDER, false)
                .addField("**Players Online**", "👥 " + Bukkit.getOnlinePlayers().size(), true);

        sendAsync(webhookUrl, embed.build());
    }

    /** Send server stop notification. */
    public void sendServerStop() {
        if (!enabled) return;
        String webhookUrl = getWebhookUrl("server-stop");
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String serverName = getServerNameSafe();
        
        EmbedBuilder embed = baseEmbed("Server Stopped", "\ud83d\udd34", new Color(0xF44336))
                .setDescription("The server is now **offline**.")
                .addField("**Server**", serverName, true)
                .addField("**Version**", normalizeVersion(Bukkit.getVersion()), true)
                .addField("**Minted Version**", plugin.getDescription().getVersion(), true);

        sendAsync(webhookUrl, embed.build());
    }

    /** Safely get server name across different Bukkit/Paper versions. */
    private String getServerNameSafe() {
        try {
            // Try static Bukkit.getServerName() (newer versions)
            return (String) Bukkit.class.getMethod("getServerName").invoke(null);
        } catch (Exception e) {
            try {
                // Fallback to Bukkit.getServer().getName()
                return Bukkit.getServer().getName();
            } catch (Exception e2) {
                return "Unknown Server";
            }
        }
    }

    private String getWebhookUrl(String event) {
        String url = config.webhookUrls.get(event);
        if (url != null && !url.isEmpty()) {
            plugin.getLogger().info("[Discord] Using event-specific webhook for: " + event);
            return url;
        }
        if (!config.defaultWebhookUrl.isEmpty()) {
            plugin.getLogger().info("[Discord] Using default webhook for: " + event);
            return config.defaultWebhookUrl;
        }
        plugin.getLogger().warning("[Discord] No webhook URL configured for event: " + event);
        return null;
    }

    private String formatItemName(ShopItem item) {
        return item.copy().getItemMeta() != null && item.copy().getItemMeta().hasDisplayName()
                ? item.copy().getItemMeta().getDisplayName()
                : item.copy().getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String formatNumber(long number) {
        return NUMBER_FORMAT.format(number);
    }

    private String formatPrice(double price) {
        return plugin.getConfig().getString("currency.symbol", "$") + NUMBER_FORMAT.format(price);
    }

    /** Turn "git-Paper-432 (MC: 1.26.2)" into "Paper 1.26.2" style for display. */
    private String normalizeVersion(String version) {
        if (version == null || version.isEmpty()) return "Unknown";
        String mc = "1.x";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("MC:\\s*([0-9._]+)").matcher(version);
        if (m.find()) mc = m.group(1);
        String server = "Server";
        if (version.toLowerCase(Locale.ROOT).contains("paper")) server = "Paper";
        else if (version.toLowerCase(Locale.ROOT).contains("spigot")) server = "Spigot";
        else if (version.toLowerCase(Locale.ROOT).contains("purpur")) server = "Purpur";
        else if (version.toLowerCase(Locale.ROOT).contains("fabric")) server = "Fabric";
        else if (version.toLowerCase(Locale.ROOT).contains("forge")) server = "Forge";
        else if (version.toLowerCase(Locale.ROOT).contains("vanilla")) server = "Vanilla";
        return server + " " + mc;
    }

    private void sendAsync(String webhookUrl, String jsonPayload) {
        sendAsync(webhookUrl, jsonPayload, "");
    }

    private void sendAsync(String webhookUrl, String jsonPayload, String pingContent) {
        // Skip if no webhook URL configured
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            plugin.getLogger().info("[Discord] No webhook URL configured, skipping send");
            return;
        }
        if (!enabled) {
            plugin.getLogger().info("[Discord] Webhook service is disabled, skipping send");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                String finalPayload = jsonPayload;
                if (pingContent != null && !pingContent.isEmpty()) {
                    // Add ping content to the payload
                    finalPayload = finalPayload.replace("\"content\":\"\"", "\"content\":\"" + escapeJson(pingContent) + "\"");
                }
                // Log payload size for debugging
                if (finalPayload.length() > 2000) {
                    plugin.getLogger().warning("Discord webhook payload may be too large: " + finalPayload.length() + " chars");
                }
                HttpURLConnection conn = (HttpURLConnection) new URL(webhookUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "Minted-Discord-Webhook/1.0");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(finalPayload.getBytes(StandardCharsets.UTF_8));
                }
                int responseCode = conn.getResponseCode();
                if (responseCode >= 400) {
                    // Read error response body for debugging
                    String errorBody = "";
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        errorBody = sb.toString();
                    } catch (Exception ignored) {}
                    plugin.getLogger().warning("Discord webhook returned " + responseCode + ": " + conn.getResponseMessage() + (errorBody.isEmpty() ? "" : " - " + errorBody));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send Discord webhook", e);
            }
        }, executor);
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    public void shutdown() {
        executor.shutdown();
    }

    /** Configuration holder. */
    private static class DiscordConfig {
        boolean enabled;
        String defaultWebhookUrl;
        java.util.Map<String, String> webhookUrls;
        Color embedColor;
        String thumbnailUrl;
        String authorIconUrl;
        String footerText;
        boolean showServerName;
        boolean showServerIcon;
        long largeTransferThreshold;
        java.util.Map<String, String> pingRoles;
    }

    /** Fluent builder for Discord embed JSON. */
    private static class EmbedBuilder {
        private String title;
        private String description;
        private int color = 0x4CAF50;
        private java.util.List<Field> fields = new java.util.ArrayList<>();
        private String footerText;
        private String footerIconUrl;
        private String thumbnailUrl;
        private String imageUrl;
        private String authorName;
        private String authorUrl;
        private String authorIconUrl;
        private String timestamp;

        EmbedBuilder setTitle(String title) { this.title = title; return this; }
        EmbedBuilder setDescription(String desc) { this.description = desc; return this; }
        EmbedBuilder setColor(Color color) { this.color = color.getRGB() & 0xFFFFFF; return this; }
        EmbedBuilder addField(String name, String value, boolean inline) {
            fields.add(new Field(name, value, inline));
            return this;
        }
        EmbedBuilder setAuthor(String name) { this.authorName = name; return this; }
        EmbedBuilder setAuthor(String name, String iconUrl) { this.authorName = name; this.authorIconUrl = iconUrl; return this; }
        EmbedBuilder setFooter(String text, String iconUrl) { this.footerText = text; this.footerIconUrl = iconUrl; return this; }
        EmbedBuilder setFooter(String text) { return setFooter(text, null); }
        EmbedBuilder setThumbnail(String url) { this.thumbnailUrl = url; return this; }
        EmbedBuilder setImage(String url) { this.imageUrl = url; return this; }
        EmbedBuilder setTimestamp() { this.timestamp = java.time.Instant.now().toString(); return this; }

        String build() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"embeds\":[{");
            boolean first = true;
            if (authorName != null) {
                sb.append("\"author\":{\"name\":\"").append(escapeJson(authorName)).append("\"");
                if (authorIconUrl != null && !authorIconUrl.isEmpty()) {
                    sb.append(",\"icon_url\":\"").append(escapeJson(authorIconUrl)).append("\"");
                }
                sb.append("}");
                first = false;
            }
            if (title != null) {
                if (!first) sb.append(",");
                sb.append("\"title\":\"").append(escapeJson(title)).append("\"");
                first = false;
            }
            if (description != null) {
                if (!first) sb.append(",");
                sb.append("\"description\":\"").append(escapeJson(description)).append("\"");
                first = false;
            }
            if (!first) sb.append(",");
            sb.append("\"color\":").append(color);
            first = false;
            if (!fields.isEmpty()) {
                sb.append(",\"fields\":[");
                for (int i = 0; i < fields.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(fields.get(i).toJson());
                }
                sb.append("]");
                first = false;
            }
            if (footerText != null) {
                if (!first) sb.append(",");
                sb.append("\"footer\":{\"text\":\"").append(escapeJson(footerText)).append("\"");
                if (footerIconUrl != null) sb.append(",\"icon_url\":\"").append(escapeJson(footerIconUrl)).append("\"");
                sb.append("}");
                first = false;
            }
            if (thumbnailUrl != null) {
                if (!first) sb.append(",");
                sb.append("\"thumbnail\":{\"url\":\"").append(escapeJson(thumbnailUrl)).append("\"}");
                first = false;
            }
            if (imageUrl != null) {
                if (!first) sb.append(",");
                sb.append("\"image\":{\"url\":\"").append(escapeJson(imageUrl)).append("\"}");
                first = false;
            }
            if (timestamp != null) {
                if (!first) sb.append(",");
                sb.append("\"timestamp\":\"").append(timestamp).append("\"");
                first = false;
            }
            sb.append("}],\"content\":\"\"}");
            return sb.toString();
        }

        private String escapeJson(String input) {
            return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }

private static class Field {
        private final String name, value;
        private final boolean inline;
        Field(String name, String value, boolean inline) { 
            this.name = name != null && !name.isEmpty() ? name : "\u200b";
            this.value = value != null && !value.isEmpty() ? value : "\u200b";
            this.inline = inline;
        }
        String toJson() {
            String nameJson = escapeJson(this.name);
            String valueJson = escapeJson(this.value);
            // Discord limits: name max 256, value max 1024
            if (nameJson.length() > 256) nameJson = nameJson.substring(0, 253) + "...";
            if (valueJson.length() > 1024) valueJson = valueJson.substring(0, 1021) + "...";
            return "{\"name\":\"" + nameJson + "\",\"value\":\"" + valueJson + "\",\"inline\":" + inline + "}";
        }
        private String escapeJson(String input) {
            return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }
    }
    }
}