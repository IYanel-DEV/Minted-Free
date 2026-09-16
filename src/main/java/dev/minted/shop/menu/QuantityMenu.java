package dev.minted.shop.menu;

import dev.minted.gui.Icon;
import dev.minted.gui.Menu;
import dev.minted.gui.theme.Design;
import dev.minted.shop.ShopContext;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Quantity picker for a buy or a sell: a row of preset counts and a "custom"
 * button that reads a number from chat. The same shape as the money
 * {@code AmountMenu}, but it hands back a whole-item count rather than an
 * amount; the caller does the actual trade.
 */
public final class QuantityMenu extends Menu {

    /** Receives the chosen quantity, always on the main thread. */
    public interface QuantityChoice {
        void chosen(Player player, int quantity);
    }

    private static final int[] PRESETS = {1, 8, 16, 32, 64};

    private final ShopContext ctx;
    private final QuantityChoice choice;

    public QuantityMenu(ShopContext ctx, String title, QuantityChoice choice) {
        super(title, 1);
        this.ctx = ctx;
        this.choice = choice;
    }

    @Override
    protected void build() {
        for (int i = 0; i < PRESETS.length; i++) {
            final int quantity = PRESETS[i];
            set(1 + i, Icon.of(Material.PAPER, ctx.messages().get("quantity.button", "amount", String.valueOf(quantity))),
                    new Consumer<Player>() {
                        @Override
                        public void accept(Player player) {
                            choice.chosen(player, quantity);
                        }
                    });
        }
        set(7, Icon.of(Material.NAME_TAG, ctx.messages().get("quantity.custom"),
                        ctx.messages().get("quantity.custom-lore")),
                new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        askCustom(player);
                    }
                });
        fillEmpty(ctx.design().filler());
    }

    private void askCustom(final Player player) {
        player.closeInventory();
        ctx.messages().send(player, "quantity.prompt");
        ctx.prompt().await(player, new Consumer<String>() {
            @Override
            public void accept(String input) {
                if (input.equalsIgnoreCase("cancel")) {
                    ctx.messages().send(player, "quantity.cancelled");
                    return;
                }
                int quantity = parse(input);
                if (quantity <= 0) {
                    ctx.messages().send(player, "quantity.invalid");
                    return;
                }
                choice.chosen(player, quantity);
            }
        });
    }

    private int parse(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
