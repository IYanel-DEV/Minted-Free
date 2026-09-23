package dev.minted.shop.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.shop.Inventories;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopItem;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@code /sell menu}: every distinct stack type in the player's inventory that
 * the global shop buys, with the held amount and what a full clear is worth.
 * Clicking one type sells every unit of it the player holds; "Sell everything"
 * clears the whole sellable inventory. Only the menu surfaces this - the
 * actual trade still runs through {@link dev.minted.shop.Trade#sell}, so the
 * shop's currency, multipliers, cap and all sell messages apply as usual.
 */
public final class AutoSellMenu extends Menu {

    private final ShopContext ctx;
    private final Shop shop;
    private final Player viewer;

    public AutoSellMenu(ShopContext ctx, Shop shop, Player viewer) {
        super(Design.title(Design.Accent.SHOP, "Sell items"), 6);
        this.ctx = ctx;
        this.shop = shop;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.SHOP));
        set(4, d.wallet(ctx.wallet().balance(viewer), ctx.format()), null);

        List<Integer> slots = interiorSlots();
        List<ItemStack> sellable = sellableNow();
        for (int i = 0; i < sellable.size() && i < slots.size(); i++) {
            ItemStack stack = sellable.get(i);
            setClick(slots.get(i), decorate(stack), sellType(stack));
        }

        set(48, Icon.of(Material.HOPPER, Design.title(Design.Accent.SHOP, "Sell everything"),
                Design.lore("Every sellable item in your inventory.",
                        one(Design.IN + "Worth: " + ChatColor.WHITE + ctx.format().format(totalWorth())),
                        "Click to clear your whole inventory.")),
                sellAll());
        set(53, d.close(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                player.closeInventory();
            }
        });
        fillEmpty(d.filler());
    }

    /** Distinct sellable stack types currently in the viewer's inventory. */
    private List<ItemStack> sellableNow() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (ItemStack stack : viewer.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            ShopItem match = shop.matchSellable(stack);
            if (match == null || !match.isSellable() || match.getSellPrice() <= 0) {
                continue;
            }
            if (!alreadyListed(out, stack)) {
                out.add(stack);
            }
        }
        return out;
    }

    private boolean alreadyListed(List<ItemStack> listed, ItemStack stack) {
        for (ItemStack present : listed) {
            if (ShopItem.sameStock(present, stack)) {
                return true;
            }
        }
        return false;
    }

    private double totalWorth() {
        double total = 0;
        for (ItemStack stack : sellableNow()) {
            ShopItem match = shop.matchSellable(stack);
            if (match != null) {
                total += match.getSellPrice() * Inventories.count(viewer, stack);
            }
        }
        return total;
    }

    private ItemStack decorate(ItemStack stack) {
        ShopItem match = shop.matchSellable(stack);
        int held = Inventories.count(viewer, stack);
        List<String> lore = new ArrayList<String>();
        lore.add(Design.HINT + "Held: " + ChatColor.WHITE + held);
        if (match != null) {
            lore.add(Design.IN + "Sell: " + ChatColor.WHITE + ctx.format().format(match.getSellPrice()) + " each");
            lore.add(Design.MONEY + "Total: " + ChatColor.WHITE + ctx.format().format(match.getSellPrice() * held));
        }
        lore.add("");
        lore.add(Design.HINT + "Click to sell every unit you hold.");
        return Display.withLore(stack, lore);
    }

    private ClickHandler sellType(final ItemStack stack) {
        return new ClickHandler() {
            @Override
            public void click(Player player, ClickType type) {
                ShopItem match = shop.matchSellable(stack);
                if (match == null) {
                    reopen(player);
                    return;
                }
                ctx.trade().sell(player, shop, match, Integer.MAX_VALUE);
                reopen(player);
            }
        };
    }

    private Consumer<Player> sellAll() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                for (ItemStack stack : sellableNow()) {
                    ShopItem match = shop.matchSellable(stack);
                    if (match != null) {
                        ctx.trade().sell(player, shop, match, Integer.MAX_VALUE);
                    }
                }
                player.closeInventory();
            }
        };
    }

    private void reopen(Player player) {
        if (sellableNow().isEmpty()) {
            player.closeInventory();
        } else {
            new AutoSellMenu(ctx, shop, viewer).open(player);
        }
    }

    private List<Integer> interiorSlots() {
        List<Integer> slots = new ArrayList<Integer>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    private static List<String> one(String line) {
        List<String> lines = new ArrayList<String>();
        lines.add(line);
        return lines;
    }
}