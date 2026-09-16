package dev.minted.shop.command;

import dev.minted.shop.Currency;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.menu.ShopBrowseMenu;
import dev.minted.shop.menu.ShopEditorMenu;
import dev.minted.shop.menu.ShopMenu;

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
 * {@code /eshop} - opens shops for players and, behind {@code minted.shop.admin},
 * creates, deletes, edits, reloads and reconfigures them. Every player-facing
 * line comes from the language bundle; admin edits run through the service so
 * they hit memory and the database together. Tab completion lives in
 * {@link ShopTabCompleter}.
 */
public final class ShopCommand implements CommandExecutor {

    private static final String ADMIN = "minted.shop.admin";
    private static final String USE = "minted.shop.use";
    private static final List<String> SUBCOMMANDS =
            Arrays.asList("create", "delete", "edit", "reload", "setcurrency");

    private final ShopContext ctx;

    public ShopCommand(ShopContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return openDefault(sender);
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (SUBCOMMANDS.contains(first)) {
            return admin(sender, label, first, args);
        }
        return open(sender, args[0]);
    }

    private boolean openDefault(CommandSender sender) {
        Player player = asPlayer(sender);
        if (player == null || !ready(player)) {
            return true;
        }
        if (ctx.shops().first() == null) {
            ctx.messages().send(player, "shop.no-shops");
            return true;
        }
        if (ctx.shops().all().size() == 1) {
            new ShopMenu(ctx, ctx.shops().first(), 0, null).open(player);
        } else {
            new ShopBrowseMenu(ctx).open(player);
        }
        return true;
    }

    private boolean open(CommandSender sender, String name) {
        Player player = asPlayer(sender);
        if (player == null || !ready(player)) {
            return true;
        }
        Shop shop = ctx.shops().get(name);
        if (shop == null) {
            ctx.messages().send(player, "shop.unknown", "shop", name);
            return true;
        }
        new ShopMenu(ctx, shop, 0, null).open(player);
        return true;
    }

    private boolean admin(CommandSender sender, String label, String action, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            ctx.messages().send(sender, "shop.admin.no-permission");
            return true;
        }
        if ("reload".equals(action)) {
            ctx.shops().reload();
            ctx.messages().send(sender, "shop.admin.reloaded");
            return true;
        }
        if ("create".equals(action)) {
            return create(sender, label, args);
        }
        if ("delete".equals(action)) {
            return delete(sender, label, args);
        }
        if ("edit".equals(action)) {
            return edit(sender, label, args);
        }
        return setCurrency(sender, label, args);
    }

    private boolean create(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            ctx.messages().send(sender, "shop.admin.create-usage", "label", label);
            return true;
        }
        String name = args[1];
        if (ctx.shops().exists(name)) {
            ctx.messages().send(sender, "shop.admin.exists", "shop", name);
            return true;
        }
        ctx.shops().create(name, defaultIcon(name), Currency.WALLET);
        ctx.messages().send(sender, "shop.admin.created", "shop", name);
        return true;
    }

    private boolean delete(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            ctx.messages().send(sender, "shop.admin.delete-usage", "label", label);
            return true;
        }
        Shop shop = ctx.shops().get(args[1]);
        if (shop == null) {
            ctx.messages().send(sender, "shop.unknown", "shop", args[1]);
            return true;
        }
        ctx.shops().delete(shop);
        ctx.messages().send(sender, "shop.admin.deleted", "shop", shop.getName());
        return true;
    }

    private boolean edit(CommandSender sender, String label, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 2) {
            ctx.messages().send(player, "shop.admin.edit-usage", "label", label);
            return true;
        }
        Shop shop = ctx.shops().get(args[1]);
        if (shop == null) {
            ctx.messages().send(player, "shop.unknown", "shop", args[1]);
            return true;
        }
        new ShopEditorMenu(ctx, shop, 0).open(player);
        return true;
    }

    private boolean setCurrency(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            ctx.messages().send(sender, "shop.admin.currency-usage", "label", label);
            return true;
        }
        Shop shop = ctx.shops().get(args[1]);
        if (shop == null) {
            ctx.messages().send(sender, "shop.unknown", "shop", args[1]);
            return true;
        }
        String choice = args[2].toLowerCase(Locale.ROOT);
        if (!"wallet".equals(choice) && !"bank".equals(choice)) {
            ctx.messages().send(sender, "shop.admin.currency-invalid");
            return true;
        }
        Currency currency = Currency.fromId(choice);
        ctx.shops().setCurrency(shop, currency);
        ctx.messages().send(sender, "shop.admin.currency-changed", "shop", shop.getName(),
                "currency", currency.display());
        return true;
    }

    private Player asPlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        ctx.messages().send(sender, "shop.player-only");
        return null;
    }

    private boolean ready(Player player) {
        if (!player.hasPermission(USE)) {
            ctx.messages().send(player, "shop.no-permission");
            return false;
        }
        if (!ctx.shops().isReady()) {
            ctx.messages().send(player, "shop.not-ready");
            return false;
        }
        return true;
    }

    private ItemStack defaultIcon(String name) {
        ItemStack icon = new ItemStack(Material.EMERALD);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + name);
        icon.setItemMeta(meta);
        return icon;
    }
}
