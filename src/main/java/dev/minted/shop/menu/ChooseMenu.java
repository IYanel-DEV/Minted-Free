package dev.minted.shop.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopType;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * The two-floor front door {@code /eshop} opens: a Global tile (admin catalog)
 * and a Community tile (the player marketplace), with the viewer's wallet
 * balance across the top. Each tile leads to the same category-home browser.
 */
public final class ChooseMenu extends Menu {

    private final ShopContext ctx;
    private final Player viewer;

    public ChooseMenu(ShopContext ctx, Player viewer) {
        super(Design.title(Design.Accent.NEUTRAL, "Minted Shop"), 3);
        this.ctx = ctx;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.NEUTRAL));
        set(4, d.wallet(ctx.wallet().balance(viewer), ctx.format()), null);

        set(11, Icon.of(Material.EMERALD, Design.title(Design.Accent.SHOP, "Global Shop"),
                Design.lore("Buy and sell against the server.", null, "Click to browse.")),
                new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        openGlobal(player);
                    }
                });

        Shop community = ctx.shops().community();
        set(15, Icon.of(community != null ? community.getIcon().getType() : Material.CHEST,
                Design.title(Design.Accent.COMMUNITY, "Community Market"),
                Design.lore("Trade real items with players.", null, "Click to browse.")),
                new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        if (community == null) {
                            player.sendMessage(ChatColor.RED + "The community market is still loading.");
                            return;
                        }
                        new HomeMenu(ctx, community, player).open(player);
                    }
                });
        fillEmpty(d.filler());
    }

    private void openGlobal(Player player) {
        int globals = 0;
        Shop only = null;
        for (Shop shop : ctx.shops().all()) {
            if (shop.getType() == ShopType.GLOBAL) {
                globals++;
                only = shop;
            }
        }
        if (globals == 0) {
            player.sendMessage(ChatColor.RED + "There are no global shops.");
        } else if (globals == 1) {
            new HomeMenu(ctx, only, player).open(player);
        } else {
            new ShopBrowseMenu(ctx).open(player);
        }
    }
}
