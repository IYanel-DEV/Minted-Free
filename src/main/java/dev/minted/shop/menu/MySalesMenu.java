package dev.minted.shop.menu;

import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopItem;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The seller's own view: every listing they own with its remaining stock and
 * uncollected earnings. Left-click collects the earnings, right-click withdraws
 * the stock - both behind a {@link ConfirmMenu}. Sold-out listings still appear
 * here while they hold earnings to collect.
 */
public final class MySalesMenu extends Menu {

    private final ShopContext ctx;
    private final Shop shop;
    private final Player viewer;

    public MySalesMenu(ShopContext ctx, Shop shop, Player viewer) {
        super(Design.title(accent(shop), "My sales"), 6);
        this.ctx = ctx;
        this.shop = shop;
        this.viewer = viewer;
    }

    private static Design.Accent accent(Shop shop) {
        return shop.isPlayerShop() ? Design.Accent.SHOP : Design.Accent.COMMUNITY;
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(accent(shop)));

        List<Integer> slots = interiorSlots();
        int i = 0;
        for (ShopItem listing : mine()) {
            if (i >= slots.size()) {
                break;
            }
            setClick(slots.get(i), tile(listing), actions(listing));
            i++;
        }

        set(49, d.back(), new java.util.function.Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new HomeMenu(ctx, shop, viewer).open(player);
            }
        });
        fillEmpty(d.filler());
    }

    private List<ShopItem> mine() {
        List<ShopItem> out = new ArrayList<ShopItem>();
        for (ShopItem item : shop.allItems()) {
            if (viewer.getUniqueId().equals(item.getOwner())) {
                out.add(item);
            }
        }
        return out;
    }

    private ItemStack tile(ShopItem listing) {
        List<String> lore = new ArrayList<String>();
        lore.add(Design.HINT + "Stock: " + ChatColor.WHITE + listing.getStock());
        lore.add(Design.MONEY + "Price: " + ChatColor.WHITE + ctx.format().format(listing.getBuyPrice()));
        lore.add(Design.IN + "Earnings: " + ChatColor.WHITE + ctx.format().format(listing.getEarnings()));
        lore.add("");
        lore.add(Design.HINT + "Left-click: collect earnings");
        lore.add(Design.HINT + "Right-click: withdraw stock");
        return Display.withLore(listing.copy(), lore);
    }

    private ClickHandler actions(final ShopItem listing) {
        return new ClickHandler() {
            @Override
            public void click(Player player, ClickType type) {
                if (type.isRightClick()) {
                    confirmWithdraw(player, listing);
                } else {
                    confirmCollect(player, listing);
                }
            }
        };
    }

    private void confirmCollect(Player player, final ShopItem listing) {
        List<String> detail = Arrays.asList(
                Design.IN + "Collect " + ChatColor.WHITE + ctx.format().format(listing.getEarnings()));
        new ConfirmMenu(ctx, "Collect earnings", "Collect your earnings?", detail,
                new java.util.function.Consumer<Player>() {
                    @Override
                    public void accept(Player p) {
                        ctx.market().collect(p, shop, listing);
                        new MySalesMenu(ctx, shop, viewer).open(p);
                    }
                }, back()).open(player);
    }

    private void confirmWithdraw(Player player, final ShopItem listing) {
        List<String> detail = Arrays.asList(
                Design.HINT + "Return " + ChatColor.WHITE + listing.getStock() + Design.HINT + " units to you",
                Design.HINT + "Earnings stay claimable.");
        new ConfirmMenu(ctx, "Withdraw stock", "Withdraw all stock?", detail,
                new java.util.function.Consumer<Player>() {
                    @Override
                    public void accept(Player p) {
                        ctx.market().withdraw(p, shop, listing);
                        new MySalesMenu(ctx, shop, viewer).open(p);
                    }
                }, back()).open(player);
    }

    private java.util.function.Consumer<Player> back() {
        return new java.util.function.Consumer<Player>() {
            @Override
            public void accept(Player p) {
                new MySalesMenu(ctx, shop, viewer).open(p);
            }
        };
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
}
