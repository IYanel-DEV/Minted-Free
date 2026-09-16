package dev.minted.command;

import dev.minted.banknote.BanknoteManager;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopItem;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * {@code /sell [amount]} sells the stack in the player's hand to the global shop
 * named in {@code shops.global}. It only routes: it finds the matching
 * {@link ShopItem} and hands off to {@link dev.minted.shop.Trade#sell}, so the
 * shop's currency, the discount and sell-multiplier nodes, the balance cap and
 * every {@code sell.*} message apply exactly as they do inside the menu.
 */
public final class SellCommand implements CommandExecutor {

    private final ShopContext ctx;
    private final BanknoteManager banknotes;
    private final String globalShop;

    public SellCommand(ShopContext ctx, BanknoteManager banknotes, String globalShop) {
        this.ctx = ctx;
        this.banknotes = banknotes;
        this.globalShop = globalShop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ctx.messages().get("shop.player-only"));
            return true;
        }
        if (!sender.hasPermission("minted.sell")) {
            ctx.messages().send(sender, "shop.no-permission");
            return true;
        }
        Player player = (Player) sender;

        if (!ctx.shops().isReady()) {
            ctx.messages().send(player, "shop.not-ready");
            return true;
        }

        ItemStack held = player.getInventory().getItemInHand();
        // Empty hand, or a banknote: money is not shop goods, so refuse the same way.
        if (held == null || held.getType().name().equals("AIR") || banknotes.isBanknote(held)) {
            ctx.messages().send(player, "sell.hold");
            return true;
        }

        int amount = resolveAmount(player, args, held.getAmount());
        if (amount <= 0) {
            return true;
        }

        Shop shop = ctx.shops().get(globalShop);
        ShopItem match = shop == null ? null : find(shop, held);
        if (match == null) {
            ctx.messages().send(player, "sell.no-global");
            return true;
        }

        ctx.trade().sell(player, shop, match, amount);
        return true;
    }

    // Prefers a sellable match, but returns any similar item so Trade can answer
    // with sell.not-sellable rather than the shop pretending not to stock it.
    private ShopItem find(Shop shop, ItemStack held) {
        ShopItem similar = null;
        for (ShopItem item : shop.allItems()) {
            if (!item.copy().isSimilar(held)) {
                continue;
            }
            if (item.isSellable() && item.getSellPrice() > 0) {
                return item;
            }
            similar = item;
        }
        return similar;
    }

    private int resolveAmount(Player player, String[] args, int held) {
        if (args.length == 0) {
            return held;
        }
        String token = args[0].toLowerCase(Locale.ROOT);
        if ("all".equals(token) || "hand".equals(token)) {
            return held;
        }
        try {
            int value = Integer.parseInt(token);
            if (value <= 0) {
                ctx.messages().send(player, "sell.amount-invalid");
                return 0;
            }
            return value;
        } catch (NumberFormatException e) {
            ctx.messages().send(player, "sell.amount-invalid");
            return 0;
        }
    }
}
