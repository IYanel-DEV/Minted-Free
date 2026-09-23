package dev.minted.shop.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Edits one stocked slot: its buy and sell prices, its category, the stack
 * itself, or removes it. Prices and the category are typed in chat through the
 * shared {@code ChatPrompt}; a price of {@code -1} closes that side of the trade.
 */
public final class SlotEditorMenu extends Menu {

    private final ShopContext ctx;
    private final Shop shop;
    private final ShopItem item;
    private final int returnPage;
    private final Player viewer;

    public SlotEditorMenu(ShopContext ctx, Shop shop, ShopItem item, int returnPage, Player viewer) {
        super(ctx.messages().get(viewer, "slot.title"), 3);
        this.ctx = ctx;
        this.shop = shop;
        this.item = item;
        this.returnPage = returnPage;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        frame(ctx.design().border(Design.Accent.SHOP));
        set(13, preview(), null);
        set(10, Icon.of(Material.GOLD_INGOT, ctx.messages().get(viewer, "slot.set-buy")), price(true));
        set(11, Icon.of(Material.GOLD_NUGGET, ctx.messages().get(viewer, "slot.set-sell")), price(false));
        set(12, Icon.of(Material.BOOK, ctx.messages().get(viewer, "slot.set-category")), category());
        set(14, Icon.of(Material.CHEST, ctx.messages().get(viewer, "slot.replace")), replace());
        set(15, Icon.of(Material.BARRIER, ctx.messages().get(viewer, "slot.remove")), remove());
        set(22, ctx.design().back(), back());
        fillEmpty(ctx.design().filler());
    }

    private ItemStack preview() {
        return Display.withLore(item.copy(), Arrays.asList(
                line("slot.buy-current", item.getBuyPrice(), item.isBuyable()),
                line("slot.sell-current", item.getSellPrice(), item.isSellable()),
                ctx.messages().get(viewer, "slot.category-current", "category",
                        item.getCategory() == null ? ctx.messages().get(viewer, "common.none") : item.getCategory())));
    }

    private Consumer<Player> price(final boolean buy) {
        return new Consumer<Player>() {
            @Override
            public void accept(final Player player) {
                player.closeInventory();
                ctx.messages().send(player, buy ? "slot.buy-prompt" : "slot.sell-prompt");
                ctx.prompt().await(player, readPrice(player, buy));
            }
        };
    }

    private Consumer<String> readPrice(final Player player, final boolean buy) {
        return new Consumer<String>() {
            @Override
            public void accept(String input) {
                if (input.equalsIgnoreCase("cancel")) {
                    return;
                }
                Double value = parse(input);
                if (value == null) {
                    ctx.messages().send(player, "slot.price-invalid");
                    return;
                }
                applyPrice(player, buy, value);
            }
        };
    }

    private void applyPrice(Player player, boolean buy, double value) {
        boolean disable = value < 0;
        double stored = disable ? ShopItem.NOT_OFFERED : value;
        if (buy) {
            item.setBuyPrice(stored);
        } else {
            item.setSellPrice(stored);
        }
        ctx.shops().saveItem(shop, item);
        ctx.messages().send(player, priceMessage(buy, disable), "price", ctx.format().format(value));
        new SlotEditorMenu(ctx, shop, item, returnPage, player).open(player);
    }

    private Consumer<Player> category() {
        return new Consumer<Player>() {
            @Override
            public void accept(final Player player) {
                player.closeInventory();
                ctx.messages().send(player, "slot.category-prompt");
                ctx.prompt().await(player, readCategory(player));
            }
        };
    }

    private Consumer<String> readCategory(final Player player) {
        return new Consumer<String>() {
            @Override
            public void accept(String input) {
                if (input.equalsIgnoreCase("cancel")) {
                    return;
                }
                if (input.trim().isEmpty() || input.equalsIgnoreCase("clear")) {
                    item.setCategory(null);
                    ctx.shops().saveItem(shop, item);
                    ctx.messages().send(player, "slot.category-cleared");
                } else {
                    item.setCategory(input.trim());
                    ctx.shops().saveItem(shop, item);
                    ctx.messages().send(player, "slot.category-set", "category", input.trim());
                }
                new SlotEditorMenu(ctx, shop, item, returnPage, player).open(player);
            }
        };
    }

    private Consumer<Player> replace() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                ItemStack held = Display.heldUnit(player);
                if (held == null) {
                    ctx.messages().send(player, "shop.admin.hold-item");
                    return;
                }
                item.setItem(held);
                ctx.shops().saveItem(shop, item);
                ctx.messages().send(player, "slot.replaced");
                new SlotEditorMenu(ctx, shop, item, returnPage, player).open(player);
            }
        };
    }

    private Consumer<Player> remove() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                ctx.shops().removeItem(shop, item.getPage(), item.getSlot());
                ctx.messages().send(player, "editor.removed");
                new ShopEditorMenu(ctx, shop, returnPage, player).open(player);
            }
        };
    }

private Consumer<Player> back() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new ShopEditorMenu(ctx, shop, returnPage, player).open(player);
            }
        };
    }

    private String line(String key, double price, boolean offered) {
        return ctx.messages().get(viewer, key, "price", offered ? ctx.format().format(price) : ctx.messages().get(viewer, "common.none"));
    }

    private String priceMessage(boolean buy, boolean disable) {
        if (buy) {
            return disable ? "slot.buy-cleared" : "slot.buy-set";
        }
        return disable ? "slot.sell-cleared" : "slot.sell-set";
    }

    private Double parse(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? Double.valueOf(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
