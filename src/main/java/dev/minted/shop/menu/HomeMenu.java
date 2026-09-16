package dev.minted.shop.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopItem;
import dev.minted.shop.catalog.Category;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A shop's front page: one tile per category that actually has stock, plus All,
 * Search and (for the community market) Sell items / My sales. Clicking a
 * category opens {@link GridMenu}. Works the same for a global shop and the
 * community marketplace; only the accent and the community actions differ.
 */
public final class HomeMenu extends Menu {

    private final ShopContext ctx;
    private final Shop shop;
    private final Player viewer;

    public HomeMenu(ShopContext ctx, Shop shop, Player viewer) {
        super(Design.title(accent(shop), shop.getName()), 6);
        this.ctx = ctx;
        this.shop = shop;
        this.viewer = viewer;
    }

    private static Design.Accent accent(Shop shop) {
        return shop.isCommunity() ? Design.Accent.COMMUNITY : Design.Accent.SHOP;
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(accent(shop)));
        set(4, d.wallet(ctx.wallet().balance(viewer), ctx.format()), null);

        List<Integer> slots = interiorSlots();
        int i = 0;
        for (Category category : categoriesWithStock()) {
            if (i >= slots.size()) {
                break;
            }
            set(slots.get(i), categoryTile(category), openCategory(category));
            i++;
        }

        set(45, Icon.of(Material.BOOK, Design.title(accent(shop), "All items"),
                Design.lore("Every item in this shop.", null, "Click to browse.")), openCategory(null));
        set(46, d.search(null), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                askSearch(player);
            }
        });
        if (shop.isCommunity()) {
            set(48, Icon.of(Material.HOPPER, Design.title(Design.Accent.COMMUNITY, "Sell items"),
                    Design.lore("List your own items for sale.", null, "Click to pick from your inventory.")),
                    new Consumer<Player>() {
                        @Override
                        public void accept(Player player) {
                            new SellPickerMenu(ctx, shop, viewer).open(player);
                        }
                    });
            set(50, Icon.of(Material.EMERALD, Design.title(Design.Accent.COMMUNITY, "My sales"),
                    Design.lore("Your listings, stock and earnings.", null, "Click to manage.")),
                    new Consumer<Player>() {
                        @Override
                        public void accept(Player player) {
                            new MySalesMenu(ctx, shop, viewer).open(player);
                        }
                    });
        }
        set(53, d.close(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                player.closeInventory();
            }
        });
        fillEmpty(d.filler());
    }

    private Set<Category> categoriesWithStock() {
        Set<Category> present = new LinkedHashSet<Category>();
        for (ShopItem item : shop.allItems()) {
            if (!shop.isCommunity() || item.getStock() > 0) {
                present.add(Category.byKey(item.getCategory()));
            }
        }
        return present;
    }

    private org.bukkit.inventory.ItemStack categoryTile(Category category) {
        Material icon = ctx.materials().get(category.iconKey());
        return Icon.of(icon == null ? Material.CHEST : icon, Design.title(accent(shop), category.display()),
                Design.lore("Browse " + category.display().toLowerCase() + ".", null, "Click to open."));
    }

    private List<Integer> interiorSlots() {
        List<Integer> slots = new ArrayList<Integer>();
        for (int row = 1; row <= 3; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    private Consumer<Player> openCategory(final Category category) {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new GridMenu(ctx, shop, viewer, category, null, Sort.NONE, 0).open(player);
            }
        };
    }

    private void askSearch(final Player player) {
        player.closeInventory();
        ctx.messages().send(player, "community.search.prompt");
        ctx.prompt().await(player, new Consumer<String>() {
            @Override
            public void accept(String input) {
                if (input == null || input.trim().isEmpty() || input.equalsIgnoreCase("cancel")) {
                    new HomeMenu(ctx, shop, viewer).open(player);
                    return;
                }
                new GridMenu(ctx, shop, viewer, null, input.trim(), Sort.NONE, 0).open(player);
            }
        });
    }
}
