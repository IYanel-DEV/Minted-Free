package dev.minted.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Amount picker shared by send, request and the bank pages: a row of preset
 * buttons plus a "custom" button that closes the menu and reads a number from
 * chat. The chosen amount is handed back to {@link AmountChoice}; this class
 * never touches money itself.
 */
public final class AmountMenu extends Menu {

    /** Receives the amount a player settled on, always on the main thread. */
    public interface AmountChoice {
        void chosen(Player player, double amount);
    }

    private final GuiContext ctx;
    private final String verb;
    private final AmountChoice choice;

    public AmountMenu(GuiContext ctx, String title, String verb, AmountChoice choice) {
        super(title, 1);
        this.ctx = ctx;
        this.verb = verb;
        this.choice = choice;
    }

    @Override
    protected void build() {
        double[] presets = ctx.presets();
        for (int i = 0; i < presets.length; i++) {
            final double amount = presets[i];
            set(1 + i * 2, Icon.of(Material.GOLD_INGOT, ChatColor.GOLD + verb + " " + ctx.format().format(amount)),
                    new Consumer<Player>() {
                        @Override
                        public void accept(Player player) {
                            choice.chosen(player, amount);
                        }
                    });
        }
        set(7, Icon.of(Material.PAPER, ChatColor.YELLOW + "Custom amount",
                        ChatColor.GRAY + "Type a number in chat, or 'cancel'."),
                new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        askCustom(player);
                    }
                });
    }

    private void askCustom(final Player player) {
        player.closeInventory();
        player.sendMessage(ChatColor.GRAY + "Type an amount in chat, or " + ChatColor.WHITE + "cancel"
                + ChatColor.GRAY + ".");
        ctx.prompt().await(player, new Consumer<String>() {
            @Override
            public void accept(String input) {
                if (input.equalsIgnoreCase("cancel")) {
                    player.sendMessage(ChatColor.GRAY + "Cancelled.");
                    return;
                }
                double amount = parse(input);
                if (amount <= 0) {
                    player.sendMessage(ChatColor.RED + "That is not a valid amount.");
                    return;
                }
                choice.chosen(player, amount);
            }
        });
    }

    private double parse(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
