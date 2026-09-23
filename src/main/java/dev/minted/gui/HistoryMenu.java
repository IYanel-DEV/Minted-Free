package dev.minted.gui;

import dev.minted.backend.LedgerEntry;
import dev.minted.gui.theme.Design;
import dev.minted.ledger.LedgerService;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A player's own transaction history: the newest money movements on their
 * balance, one row each, with the sign, the kind, the detail and when it
 * happened. Read off the main thread so the database never stalls the menu.
 */
public final class HistoryMenu extends Menu {

    private final GuiContext ctx;
    private final Player viewer;
    private final List<LedgerEntry> entries;

    private static final int SHOWN = 7;

    public HistoryMenu(GuiContext ctx, Player viewer, List<LedgerEntry> entries) {
        super(Design.title(Design.Accent.BANK, "Your history"), 3);
        this.ctx = ctx;
        this.viewer = viewer;
        this.entries = entries;
    }

    /** Fetches the history async, then opens the menu on the main thread. */
    public static void open(final GuiContext ctx, final Player viewer) {
        final UUID uuid = viewer.getUniqueId();
        final LedgerService ledger = ctx.ledger();
        ctx.plugin().getServer().getScheduler().runTaskAsynchronously(ctx.plugin(), new Runnable() {
            @Override
            public void run() {
                RuntimeException failure = null;
                List<LedgerEntry> entries = new ArrayList<LedgerEntry>();
                try {
                    entries = ledger.recent(uuid, SHOWN);
                } catch (RuntimeException e) {
                    failure = e;
                }
                final RuntimeException finalFailure = failure;
                final List<LedgerEntry> finalEntries = entries;
                ctx.plugin().getServer().getScheduler().runTask(ctx.plugin(), new Runnable() {
                    @Override
                    public void run() {
                        if (finalFailure != null) {
                            viewer.sendMessage(ChatColor.RED + "Your history is still loading, try again.");
                            return;
                        }
                        new HistoryMenu(ctx, viewer, finalEntries).open(viewer);
                    }
                });
            }
        });
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.BANK));
        if (entries.isEmpty()) {
            set(13, Icon.of(Material.PAPER, Design.HEADING + "" + ChatColor.BOLD + "No activity yet",
                    Design.lore("Money movements are recorded here.",
                            null, "Pay, trade, bank and bounty.")), null);
        }
        int i = 0;
        for (LedgerEntry entry : entries) {
            int slot = 10 + i;
            if (slot > 16) {
                break;
            }
            set(slot, entry(entry), null);
            i++;
        }
        set(18, d.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new PersonalMenu(ctx, viewer).open(player);
            }
        });
        fillEmpty(d.filler());
    }

    private ItemStack entry(LedgerEntry entry) {
        boolean in = entry.delta() >= 0;
        ChatColor sign = in ? Design.IN : Design.OUT;
        String amount = ctx.format().brief(Math.abs(entry.delta()));
        List<String> lore = new ArrayList<String>();
        if (!entry.detail().isEmpty()) {
            lore.add(Design.LABEL + entry.detail());
        }
        String kind = label(entry.kind());
        lore.add("");
        lore.add(sign + (in ? "+" : "-") + amount);
        lore.add(Design.HINT + label(entry.kind()));
        lore.add("");
        lore.add(Design.HINT + ago(entry.ts()));
        return Icon.of(in ? Material.GOLD_INGOT : Material.REDSTONE,
                sign + "" + ChatColor.BOLD + (in ? "+" : "-") + amount
                        + ChatColor.DARK_GRAY + " " + kind,
                lore);
    }

    private static String label(String kind) {
        if ("shop-buy".equals(kind)) {
            return "Shop purchase";
        }
        if ("shop-sell".equals(kind)) {
            return "Shop sale";
        }
        if ("deposit".equals(kind)) {
            return "Bank deposit";
        }
        if ("withdraw".equals(kind)) {
            return "Bank withdrawal";
        }
        if ("pay".equals(kind)) {
            return "Payment";
        }
        if ("request".equals(kind)) {
            return "Money request";
        }
        if ("bounty".equals(kind)) {
            return "Bounty";
        }
        if ("loan".equals(kind)) {
            return "Loan";
        }
        if ("repay".equals(kind)) {
            return "Loan repayment";
        }
        if ("interest".equals(kind)) {
            return "Bank interest";
        }
        if ("admin".equals(kind)) {
            return "Adjusted by staff";
        }
        return "Money movement";
    }

    private static String ago(long when) {
        long seconds = Math.max(0, (System.currentTimeMillis() - when) / 1000);
        if (seconds < 60) {
            return seconds + "s ago";
        }
        if (seconds < 3600) {
            return seconds / 60 + "m ago";
        }
        if (seconds < 86400) {
            return seconds / 3600 + "h ago";
        }
        return seconds / 86400 + "d ago";
    }
}