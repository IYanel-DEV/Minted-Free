package dev.minted.shop.menu;

import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shows the player's own inventory as a picker for listing an item: click a
 * stack to take it into the listing builder. Banknotes are hidden - real money
 * is never merchandise. One click lists up to that one slot's amount.
 */
public final class SellPickerMenu extends Menu {

    private final ShopContext ctx;
    private final Shop shop;
    private final Player viewer;

    public SellPickerMenu(ShopContext ctx, Shop shop, Player viewer) {
        super(Design.title(accent(shop), "Pick an item to sell"), 6);
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
        ItemStack[] contents = viewer.getInventory().getContents();
        int i = 0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR || ctx.market().isBanknote(stack)) {
                continue;
            }
            if (i >= slots.size()) {
                break;
            }
            set(slots.get(i), decorate(stack), pick(stack.clone()));
            i++;
        }

        set(49, d.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new HomeMenu(ctx, shop, viewer).open(player);
            }
        });
        fillEmpty(d.filler());
    }

    private ItemStack decorate(ItemStack stack) {
        List<String> lore = new ArrayList<String>();
        lore.add(Design.HINT + "Amount: " + org.bukkit.ChatColor.WHITE + stack.getAmount());
        lore.add("");
        lore.add(Design.HINT + "Click to list this stack.");
        return Display.withLore(stack, lore);
    }

    private Consumer<Player> pick(final ItemStack stack) {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new ListingBuilderMenu(ctx, shop, viewer, stack).open(player);
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
