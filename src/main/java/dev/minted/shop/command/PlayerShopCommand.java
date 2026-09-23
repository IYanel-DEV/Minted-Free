package dev.minted.shop.command;

import dev.minted.shop.Inventories;
import dev.minted.shop.Market;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopItem;
import dev.minted.shop.catalog.Category;
import dev.minted.shop.menu.HomeMenu;
import dev.minted.shop.menu.MySalesMenu;
import dev.minted.shop.menu.PlayerShopsMenu;
import dev.minted.shop.menu.SellPickerMenu;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /pshop} - a player's personal storefront. Players open one shop per
 * account (capped by config), stock it from their inventory at their own price
 * ({@code add}/{@code addstack} or the visual {@code sell} picker), and manage
 * earnings through {@code my}. Everything runs on the {@link Market} and
 * {@link ShopService} as the community marketplace does - real stock, real
 * wallet money, earnings collected, stock withdrawn.
 */
public final class PlayerShopCommand implements CommandExecutor {

    private static final String OWN = "minted.shop.own";
    private static final String USE = "minted.shop.use";
    private static final List<String> SUBCOMMANDS =
            Arrays.asList("create", "add", "addstack", "sell", "my", "browse", "open", "delete");

    private final ShopContext ctx;
    private final int maxShops;

    public PlayerShopCommand(ShopContext ctx, int maxShops) {
        this.ctx = ctx;
        this.maxShops = maxShops;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        if (!player.hasPermission(USE)) {
            ctx.messages().send(player, "shop.no-permission");
            return true;
        }
        if (!ctx.shops().isReady()) {
            ctx.messages().send(player, "shop.not-ready");
            return true;
        }

        String token = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if ("create".equals(token)) {
            return create(player, label, args);
        }
        if ("add".equals(token) || "addstack".equals(token)) {
            return add(player, label, args, "addstack".equals(token));
        }
        if ("sell".equals(token)) {
            return sell(player);
        }
        if ("my".equals(token)) {
            return my(player);
        }
        if ("delete".equals(token)) {
            return delete(player, label, args);
        }
        if ("open".equals(token)) {
            return open(player, args);
        }
        if ("browse".equals(token) || token.isEmpty()) {
            if ("browse".equals(token)) {
                new PlayerShopsMenu(ctx, player).open(player);
            } else {
                openOwn(player);
            }
            return true;
        }
        ctx.messages().send(player, "pshop.usage", "label", label);
        return true;
    }

    private boolean create(Player player, String label, String[] args) {
        if (!player.hasPermission(OWN)) {
            ctx.messages().send(player, "pshop.permission");
            return true;
        }
        if (args.length < 2) {
            ctx.messages().send(player, "pshop.create-usage", "label", label);
            return true;
        }
        String name = args[1];
        if (ctx.shops().exists(name)) {
            ctx.messages().send(player, "pshop.exists", "shop", name);
            return true;
        }
        if (ctx.shops().playerShopCount(player.getUniqueId()) >= maxShops) {
            ctx.messages().send(player, "pshop.limit");
            return true;
        }
        Shop shop = ctx.shops().createPlayerShop(name, icon(player, name), player.getUniqueId());
        ctx.messages().send(player, "pshop.created", "shop", name);
        new HomeMenu(ctx, shop, player).open(player);
        return true;
    }

    private boolean add(Player player, String label, String[] args, boolean allHeld) {
        if (!player.hasPermission(OWN)) {
            ctx.messages().send(player, "pshop.permission");
            return true;
        }
        if (args.length < 2) {
            ctx.messages().send(player, allHeld ? "pshop.addstack-usage" : "pshop.add-usage",
                    "label", label);
            return true;
        }
        double price = parsePrice(player, args[1]);
        if (price <= 0) {
            return true;
        }
        Shop shop = ctx.shops().playerShopOf(player.getUniqueId());
        if (shop == null) {
            ctx.messages().send(player, "pshop.no-shop");
            return true;
        }
        ItemStack held = player.getInventory().getItemInHand();
        if (held == null || held.getType() == Material.AIR) {
            ctx.messages().send(player, "pshop.hold");
            return true;
        }
        int amount = allHeld ? Inventories.count(player, held) : held.getAmount();
        Category category = Category.guess(held.getType());
        ctx.market().list(player, shop, held.clone(), amount, price, -1, category);
        return true;
    }

    private boolean sell(Player player) {
        if (!player.hasPermission(OWN)) {
            ctx.messages().send(player, "pshop.permission");
            return true;
        }
        Shop shop = ctx.shops().playerShopOf(player.getUniqueId());
        if (shop == null) {
            ctx.messages().send(player, "pshop.no-shop");
            return true;
        }
        new SellPickerMenu(ctx, shop, player).open(player);
        return true;
    }

    private boolean my(Player player) {
        if (!player.hasPermission(OWN)) {
            ctx.messages().send(player, "pshop.permission");
            return true;
        }
        Shop shop = ctx.shops().playerShopOf(player.getUniqueId());
        if (shop == null) {
            ctx.messages().send(player, "pshop.no-shop");
            return true;
        }
        new MySalesMenu(ctx, shop, player).open(player);
        return true;
    }

    private boolean open(Player player, String[] args) {
        if (args.length < 2) {
            ctx.messages().send(player, "pshop.open-usage");
            return true;
        }
        Shop shop = ctx.shops().get(args[1]);
        if (shop == null || !shop.isPlayerShop()) {
            ctx.messages().send(player, "pshop.unknown", "shop", args[1]);
            return true;
        }
        new HomeMenu(ctx, shop, player).open(player);
        return true;
    }

    private boolean delete(Player player, String label, String[] args) {
        if (!player.hasPermission(OWN)) {
            ctx.messages().send(player, "pshop.permission");
            return true;
        }
        Shop shop = null;
        if (args.length >= 2) {
            Shop named = ctx.shops().get(args[1]);
            if (named != null && named.isPlayerShop() && named.ownedBy(player.getUniqueId())) {
                shop = named;
            }
        }
        if (shop == null) {
            shop = ctx.shops().playerShopOf(player.getUniqueId());
        }
        if (shop == null) {
            ctx.messages().send(player, "pshop.no-shop");
            return true;
        }
        for (ShopItem item : shop.allItems()) {
            if (item.getStock() > 0 || item.getEarnings() > 0) {
                ctx.messages().send(player, "pshop.occupied");
                return true;
            }
        }
        ctx.shops().delete(shop);
        ctx.messages().send(player, "pshop.deleted", "shop", shop.getName());
        return true;
    }

    private void openOwn(Player player) {
        Shop shop = ctx.shops().playerShopOf(player.getUniqueId());
        if (shop == null) {
            ctx.messages().send(player, "pshop.no-shop");
            return;
        }
        new HomeMenu(ctx, shop, player).open(player);
    }

    private double parsePrice(Player player, String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            ctx.messages().send(player, "pshop.price-invalid");
            return 0;
        }
    }

    private ItemStack icon(Player player, String name) {
        ItemStack held = player.getInventory().getItemInHand();
        ItemStack icon = held != null && held.getType() != Material.AIR ? held.clone() : new ItemStack(Material.EMERALD);
        icon.setAmount(1);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + name);
        icon.setItemMeta(meta);
        return icon;
    }

    private Player asPlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        ctx.messages().send(sender, "shop.player-only");
        return null;
    }
}