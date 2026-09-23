package dev.minted.shop.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.shop.Currency;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;
import dev.minted.shop.ShopItem;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * The admin grid for one page of a shop. Items sit at their real slots; the
 * bottom row holds the page and shop controls. Clicking an empty slot while
 * holding an item stocks it there; clicking a stocked slot with an empty hand
 * opens {@link SlotEditorMenu}; dropping a held item onto a stocked slot
 * replaces it. Every change is saved through the service at once.
 */
public final class ShopEditorMenu extends Menu {

    private final ShopContext ctx;
    private final Shop shop;
    private final int page;
    private final Player viewer;

    public ShopEditorMenu(ShopContext ctx, Shop shop, int page, Player viewer) {
        super(ctx.messages().get(viewer, "editor.title", "shop", shop.getName(),
                "page", String.valueOf(page + 1), "pages", String.valueOf(Math.max(shop.pageCount(), page + 1))), 6);
        this.ctx = ctx;
        this.shop = shop;
        this.page = page;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        for (int slot = 0; slot < Shop.SLOTS_PER_PAGE; slot++) {
            ShopItem item = shop.itemAt(page, slot);
            setClick(slot, item == null ? null : describe(item), slotHandler(slot));
        }
        renderControls();
    }

    private void renderControls() {
        set(45, Icon.of(Material.ARROW, ctx.messages().get(viewer, "editor.prev-page")), openPage(Math.max(0, page - 1)));
        set(46, Icon.of(Material.PAPER, ctx.messages().get(viewer, "editor.add-page")), openPage(shop.pageCount()));
        set(47, Icon.of(Material.NAME_TAG, ctx.messages().get(viewer, "editor.rename"),
                ctx.messages().get(viewer, "editor.rename-lore")), rename());
        set(48, Icon.of(Material.ITEM_FRAME, ctx.messages().get(viewer, "editor.set-icon"),
                ctx.messages().get(viewer, "editor.set-icon-lore")), setIcon());
        set(49, Icon.of(Material.BOOK, ctx.messages().get(viewer, "editor.slot-hint")), null);
        set(50, Icon.of(Material.EMERALD, ctx.messages().get(viewer, "editor.currency",
                "currency", shop.getCurrency().display()), ctx.messages().get(viewer, "editor.currency-lore")), toggleCurrency());
        set(51, Icon.of(Material.CHEST, ctx.messages().get(viewer, "editor.add-hint"),
                ctx.messages().get(viewer, "editor.add-hint-lore"), ctx.messages().get(viewer, "editor.add-hint-lore2")), addHeld());
        set(53, Icon.of(Material.ARROW, ctx.messages().get(viewer, "editor.next-page")), openPage(page + 1));
    }

    private ClickHandler slotHandler(final int slot) {
        return new ClickHandler() {
            @Override
            public void click(Player player, ClickType type) {
                if (!type.isLeftClick()) {
                    return;
                }
                ItemStack held = Display.heldUnit(player);
                ShopItem existing = shop.itemAt(page, slot);
                if (existing == null) {
                    placeInto(player, slot, held);
                } else if (held != null) {
                    existing.setItem(held);
                    ctx.shops().saveItem(shop, existing);
                    ctx.messages().send(player, "editor.replaced", "item", name(held));
                    reopen(player);
                } else {
                    new SlotEditorMenu(ctx, shop, existing, page, player).open(player);
                }
            }
        };
    }

    private void placeInto(Player player, int slot, ItemStack held) {
        if (held == null) {
            ctx.messages().send(player, "shop.admin.hold-item");
            return;
        }
        ShopItem item = new ShopItem(shop.getId(), page, slot, held,
                ShopItem.NOT_OFFERED, ShopItem.NOT_OFFERED, null);
        ctx.shops().saveItem(shop, item);
        ctx.messages().send(player, "editor.placed", "item", name(held));
        reopen(player);
    }

    private Consumer<Player> addHeld() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                ItemStack held = Display.heldUnit(player);
                if (held == null) {
                    ctx.messages().send(player, "shop.admin.hold-item");
                    return;
                }
                int free = firstFreeSlot();
                if (free < 0) {
                    ctx.messages().send(player, "editor.page-full");
                    return;
                }
                placeInto(player, free, held);
            }
        };
    }

    private Consumer<Player> rename() {
        return new Consumer<Player>() {
            @Override
            public void accept(final Player player) {
                player.closeInventory();
                ctx.messages().send(player, "editor.rename-prompt");
                ctx.prompt().await(player, new Consumer<String>() {
                    @Override
                    public void accept(String input) {
                        String name = input.trim();
                        if (input.equalsIgnoreCase("cancel") || name.isEmpty()) {
                            return;
                        }
                        if (ctx.shops().exists(name) && ctx.shops().get(name) != shop) {
                            ctx.messages().send(player, "shop.admin.exists", "shop", name);
                            return;
                        }
                        ctx.shops().rename(shop, name);
                        ctx.messages().send(player, "editor.renamed", "shop", name);
                        reopen(player);
                    }
                });
            }
        };
    }

    private Consumer<Player> setIcon() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                ItemStack held = Display.heldUnit(player);
                if (held == null) {
                    ctx.messages().send(player, "shop.admin.hold-item");
                    return;
                }
                ctx.shops().setIcon(shop, held);
                ctx.messages().send(player, "editor.icon-set");
                reopen(player);
            }
        };
    }

    private Consumer<Player> toggleCurrency() {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                Currency next = shop.getCurrency() == Currency.WALLET ? Currency.BANK : Currency.WALLET;
                ctx.shops().setCurrency(shop, next);
                ctx.messages().send(player, "editor.currency-changed", "currency", next.display());
                reopen(player);
            }
        };
    }

    private Consumer<Player> openPage(final int target) {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new ShopEditorMenu(ctx, shop, Math.max(0, target), player).open(player);
            }
        };
    }

    private void reopen(Player player) {
        new ShopEditorMenu(ctx, shop, page, player).open(player);
    }

    private int firstFreeSlot() {
        for (int slot = 0; slot < Shop.SLOTS_PER_PAGE; slot++) {
            if (shop.itemAt(page, slot) == null) {
                return slot;
            }
        }
        return -1;
    }

    private ItemStack describe(ShopItem item) {
        return Display.withLore(item.copy(), Arrays.asList(
                priceLine("slot.buy-current", item.getBuyPrice(), item.isBuyable()),
                priceLine("slot.sell-current", item.getSellPrice(), item.isSellable()),
                ctx.messages().get(viewer, "slot.category-current", "category",
                        item.getCategory() == null ? ctx.messages().get(viewer, "common.none") : item.getCategory()),
                ctx.messages().get(viewer, "editor.slot-hint")));
    }

    private String priceLine(String key, double price, boolean offered) {
        return ctx.messages().get(viewer, key, "price", offered ? ctx.format().format(price) : ctx.messages().get(viewer, "common.none"));
    }

    private String name(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
