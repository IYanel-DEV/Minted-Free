package dev.minted.gui;

import dev.minted.bank.EconomyStats;
import dev.minted.gui.theme.Design;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * The server-wide economy page: the total money sitting in everyone's bank and
 * how much has been burned out of the economy by shop purchases. Physical cash
 * is deliberately absent - notes are neither banked nor counted as burned here.
 * Reached from the personal menu and {@code /mstats}; the numbers are snapshots
 * kept fresh by {@link EconomyStats#refresh()} on a timer.
 */
public final class EconomyMenu extends Menu {

    private final GuiContext ctx;
    private final Player viewer;

    public EconomyMenu(GuiContext ctx, Player viewer) {
        super(Design.title(Design.Accent.BANK, "Economy stats"), 3);
        this.ctx = ctx;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        EconomyStats stats = ctx.stats();
        Design d = ctx.design();
        frame(d.border(Design.Accent.BANK));

        set(11, Icon.of(Material.GOLD_INGOT, Design.MONEY + "" + ChatColor.BOLD + "Total in Banks",
                Design.lore("All money stored in bank accounts across the server.",
                        Arrays.asList(Design.HINT + ctx.format().brief(stats.banked()),
                                Design.LABEL + String.valueOf(stats.accountCount()) + " bank accounts in use."),
                        null)), null);

        set(15, Icon.of(Material.LAVA_BUCKET, Design.OUT + "" + ChatColor.BOLD + "Burned / Lost",
                Design.lore("Money that left the economy for good.",
                        Arrays.asList(Design.HINT + ctx.format().brief(stats.burned())),
                        "Notes dropped that despawn, plus digital shop spends.")), null);

        set(10, Icon.of(Material.DIAMOND, Design.HEADING + "" + ChatColor.BOLD + "Richest players",
                Design.lore("Who holds the biggest bank balances.",
                        null, "Click to see the ranking.")), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                LeaderboardMenu.open(ctx, player);
            }
        });

        set(13, Icon.of(Material.BOOK, Design.HEADING + "" + ChatColor.BOLD + "Recent sales",
                Design.lore("The latest shop and marketplace trades.",
                        null, "Click to see the sales feed.")), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                SalesMenu.open(ctx, player);
            }
        });

        set(18, d.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new PersonalMenu(ctx, viewer).open(player);
            }
        });
    }
}