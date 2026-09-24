package dev.minted.shop.menu;

import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A directory of every player-owned storefront, shown behind the "Player shops"
 * tile on {@link ChooseMenu}. Their icons carry the owner's name; clicking one
 * opens that shop as a normal buyer sees it.
 */
public final class PlayerShopsMenu extends Menu {

    private final ShopContext ctx;
    private final Player viewer;

    public PlayerShopsMenu(ShopContext ctx, Player viewer) {
        super(Design.title(Design.Accent.NEUTRAL, "Player shops"), 6);
        this.ctx = ctx;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        frame(ctx.design().border(Design.Accent.NEUTRAL));
        List<Shop> shops = new ArrayList<Shop>();
        for (Shop shop : ctx.shops().all()) {
            if (shop.isPlayerShop()) {
                shops.add(shop);
            }
        }
        int slot = 0;
        int size = getInventory().getSize() - 9;
        for (Shop shop : shops) {
            if (slot >= size) {
                break;
            }
            set(slot, icon(shop), open(shop));
            slot++;
        }
        fillEmpty(ctx.design().filler());
    }

    private ItemStack icon(Shop shop) {
        List<String> lore = new ArrayList<String>(Arrays.asList(
                Design.HINT + "Owner: " + ChatColor.WHITE + ownerName(shop.getOwner()),
                "",
                Design.HINT + "Click to browse."));
        return Display.withLore(shop.getIcon(), lore);
    }

    private String ownerName(UUID owner) {
        if (owner == null) {
            return "a player";
        }
        String name = Bukkit.getOfflinePlayer(owner).getName();
        return name == null ? "a player" : name;
    }

    private java.util.function.Consumer<Player> open(final Shop shop) {
        return new java.util.function.Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new HomeMenu(ctx, shop, viewer).open(player);
            }
        };
    }
}