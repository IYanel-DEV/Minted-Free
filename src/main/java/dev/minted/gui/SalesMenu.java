package dev.minted.gui;

import dev.minted.backend.NamesDao;
import dev.minted.gui.theme.Design;
import dev.minted.shop.log.SaleEntry;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The server-wide sales feed: the most recent shop and marketplace trades, one
 * row per sale, with what changed hands and when. Read off the main thread so
 * the database never stalls the menu.
 */
public final class SalesMenu extends Menu {

    private final GuiContext ctx;
    private final Player viewer;
    private final List<SaleEntry> entries;
    private final Map<UUID, String> names;

    private static final int SHOWN = 7;

    public SalesMenu(GuiContext ctx, Player viewer, List<SaleEntry> entries, Map<UUID, String> names) {
        super(Design.title(Design.Accent.COMMUNITY, "Recent sales"), 3);
        this.ctx = ctx;
        this.viewer = viewer;
        this.entries = entries;
        this.names = names;
    }

    /** Fetches the feed async, then opens the menu on the main thread. */
    public static void open(final GuiContext ctx, final Player viewer) {
        ctx.plugin().getServer().getScheduler().runTaskAsynchronously(ctx.plugin(), new Runnable() {
            @Override
            public void run() {
                RuntimeException failure = null;
                List<SaleEntry> entries = new ArrayList<SaleEntry>();
                Map<UUID, String> names = null;
                try {
                    entries = ctx.sales().recent(SHOWN);
                    Set<UUID> uuids = new HashSet<UUID>();
                    for (SaleEntry entry : entries) {
                        addIfUuid(uuids, entry.sellerUuid());
                        addIfUuid(uuids, entry.buyerUuid());
                    }
                    names = ctx.names().names(uuids);
                } catch (RuntimeException e) {
                    failure = e;
                }
                final RuntimeException finalFailure = failure;
                final List<SaleEntry> finalEntries = entries;
                final Map<UUID, String> finalNames = names;
                ctx.plugin().getServer().getScheduler().runTask(ctx.plugin(), new Runnable() {
                    @Override
                    public void run() {
                        if (finalFailure != null) {
                            viewer.sendMessage(ChatColor.RED + "The sales feed is still loading, try again.");
                            return;
                        }
                        new SalesMenu(ctx, viewer, finalEntries, finalNames).open(viewer);
                    }
                });
            }
        });
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.COMMUNITY));
        int i = 0;
        for (SaleEntry entry : entries) {
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
                new EconomyMenu(ctx, viewer).open(player);
            }
        });
        fillEmpty(d.filler());
    }

    private ItemStack entry(SaleEntry entry) {
        String seller = who(entry.sellerUuid());
        String buyer = who(entry.buyerUuid());
        List<String> lore = new ArrayList<String>();
        lore.add(Design.LABEL + (entry.isShopSale() ? "Shop trade" : "Player market"));
        lore.add("");
        lore.add(Design.MONEY + ctx.format().brief(entry.price()) + " paid by " + Design.HINT + buyer);
        lore.add(Design.HINT + "to " + Design.HEADING + seller);
        lore.add("");
        lore.add(Design.HINT + ago(entry.ts()) + "  " + Design.LABEL + entry.qty() + "x");
        return Icon.of(Material.PAPER,
                Design.HEADING + "" + ChatColor.BOLD + entry.item() + " x" + entry.qty(), lore);
    }

    private String who(UUID uuid) {
        if (uuid == null) {
            return "the Shop";
        }
        String name = names.get(uuid);
        if (name != null) {
            return name;
        }
        return uuid.toString().substring(0, 8);
    }

    private static void addIfUuid(Set<UUID> uuids, UUID uuid) {
        if (uuid != null) {
            uuids.add(uuid);
        }
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