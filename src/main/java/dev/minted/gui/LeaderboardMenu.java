package dev.minted.gui;

import dev.minted.bank.EconomyService;
import dev.minted.backend.NamesDao;
import dev.minted.gui.theme.Design;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-wide riches ranking: the top bank balances, richest first. The data is
 * read off the main thread (account rows plus display names), then the menu is
 * built and opened on it.
 */
public final class LeaderboardMenu extends Menu {

    private final GuiContext ctx;
    private final Player viewer;
    private final List<EntryRow> rows;
    private final int page;

    private static final int SHOWN = 8;
    private static final int TOTAL = 64;

    public LeaderboardMenu(GuiContext ctx, Player viewer, List<EntryRow> rows, int page) {
        super(Design.title(Design.Accent.BANK, "Richest players"), 3);
        this.ctx = ctx;
        this.viewer = viewer;
        this.rows = rows;
        this.page = page;
    }

    /** Fetches the ranking async, then opens the menu on the main thread. */
    public static void open(final GuiContext ctx, final Player viewer) {
        final EconomyService bank = ctx.bankEconomy();
        final NamesDao names = ctx.names();
        ctx.plugin().getServer().getScheduler().runTaskAsynchronously(ctx.plugin(), new Runnable() {
            @Override
            public void run() {
                RuntimeException failure = null;
                List<EntryRow> rows = new ArrayList<EntryRow>();
                try {
                    Map<UUID, Double> top = bank.topBalances(TOTAL);
                    Map<UUID, String> known = names.names(new ArrayList<UUID>(top.keySet()));
                    int i = 0;
                    for (Entry<UUID, Double> entry : top.entrySet()) {
                        rows.add(new EntryRow(i, display(entry.getKey(), known), entry.getValue()));
                        i++;
                    }
                } catch (RuntimeException e) {
                    failure = e;
                }
                final RuntimeException finalFailure = failure;
                final List<EntryRow> finalRows = rows;
                ctx.plugin().getServer().getScheduler().runTask(ctx.plugin(), new Runnable() {
                    @Override
                    public void run() {
                        if (finalFailure != null) {
                            viewer.sendMessage(ChatColor.RED + "The leaderboard is still loading, try again.");
                            return;
                        }
                        new LeaderboardMenu(ctx, viewer, finalRows, 0).open(viewer);
                    }
                });
            }
        });
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.BANK));
        int pages = Math.max(1, (int) Math.ceil((double) rows.size() / SHOWN));
        int current = Math.min(page, pages - 1);
        int start = current * SHOWN;
        int shown = 0;
        for (int i = start; i < Math.min(start + SHOWN, rows.size()); i++) {
            EntryRow row = rows.get(i);
            int slot = 9 + shown;
            if (slot > 16) {
                break;
            }
            Material material = material(row.rank);
            ChatColor colour = row.rank == 0 ? Design.MONEY : Design.HEADING;
            set(slot, Icon.of(material, colour + "" + ChatColor.BOLD + "#" + (row.rank + 1) + " " + row.name,
                    Design.lore("Bank balance",
                            Arrays.asList(Design.MONEY + ctx.format().brief(row.balance)), null)), null);
            shown++;
        }
        set(18, d.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new EconomyMenu(ctx, viewer).open(player);
            }
        });
        if (current > 0) {
            setClick(20, d.prev(), (p, t) -> new LeaderboardMenu(ctx, viewer, rows, current - 1).open(p));
        }
        set(22, d.pageInfo(current + 1, pages), null);
        if (current < pages - 1) {
            setClick(24, d.next(), (p, t) -> new LeaderboardMenu(ctx, viewer, rows, current + 1).open(p));
        }
        fillEmpty(d.filler());
    }

    private static String display(UUID uuid, Map<UUID, String> known) {
        String name = known.get(uuid);
        if (name != null) {
            return name;
        }
        return uuid.toString().substring(0, 8);
    }

    private static Material material(int index) {
        if (index <= 0) {
            return Material.DIAMOND;
        }
        if (index == 1) {
            return Material.GOLD_INGOT;
        }
        if (index == 2) {
            return Material.IRON_INGOT;
        }
        return Material.PAPER;
    }

    /** One prepared leaderboard row: the rank, the name to show, the balance. */
    public static final class EntryRow {
        public final int rank;
        public final String name;
        public final double balance;

        public EntryRow(int rank, String name, double balance) {
            this.rank = rank;
            this.name = name;
            this.balance = balance;
        }
    }
}