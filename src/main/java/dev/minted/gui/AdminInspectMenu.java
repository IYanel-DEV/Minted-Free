package dev.minted.gui;

import dev.minted.backend.LedgerEntry;
import dev.minted.bank.Loan;
import dev.minted.bounty.Bounty;
import dev.minted.compat.Heads;
import dev.minted.gui.theme.Design;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The read-only admin profile behind the dashboard's "Inspect player" tile. A
 * single snapshot of everything Minted knows about one account - bank, wallet,
 * net worth, any open loan, open bounties on the target and their recent
 * transaction history. It deliberately carries no actions: an in-game admin can
 * see any player's money but never touch it. Data is fetched off the main thread
 * so the storage reads never stall the server; the menu renders on the main
 * thread afterwards.
 */
public final class AdminInspectMenu extends Menu {

    private static final int SHOWN = 7;

    private final GuiContext ctx;
    private final Player viewer;
    private final String name;
    private final double bank;
    private final double wallet;
    private final Loan loan;
    private final List<Bounty> bounties;
    private final List<LedgerEntry> history;

    public AdminInspectMenu(GuiContext ctx, Player viewer, String name, double bank, double wallet,
                            Loan loan, List<Bounty> bounties, List<LedgerEntry> history) {
        super(Design.title(Design.Accent.NEUTRAL, "Inspect " + name), 4);
        this.ctx = ctx;
        this.viewer = viewer;
        this.name = name;
        this.bank = bank;
        this.wallet = wallet;
        this.loan = loan;
        this.bounties = bounties;
        this.history = history;
    }

    /** Prompts the admin for a player name, then loads and opens the profile. */
    public static void begin(final GuiContext ctx, final Player viewer) {
        viewer.sendMessage(ChatColor.GRAY + "Type the name of the player to inspect. "
                + ChatColor.WHITE + "Type anything else to cancel.");
        ctx.prompt().await(viewer, new Consumer<String>() {
            @Override
            public void accept(String raw) {
                final String name = raw == null ? "" : raw.trim();
                if (name.isEmpty()) {
                    viewer.sendMessage(ChatColor.RED + "Inspection cancelled.");
                    return;
                }
                Player online = Bukkit.getPlayerExact(name);
                if (online != null) {
                    load(ctx, viewer, online.getUniqueId(), name);
                    return;
                }
                // Offline resolution needs storage; hop off the main thread and come back.
                ctx.plugin().getServer().getScheduler().runTaskAsynchronously(ctx.plugin(), new Runnable() {
                    @Override
                    public void run() {
                        final UUID[] resolved = new UUID[1];
                        RuntimeException failure = null;
                        try {
                            resolved[0] = ctx.names().uuidByName(name);
                        } catch (RuntimeException sql) {
                            failure = sql;
                        }
                        final RuntimeException lookupFailure = failure;
                        ctx.plugin().getServer().getScheduler().runTask(ctx.plugin(), new Runnable() {
                            @Override
                            public void run() {
                                if (lookupFailure != null || resolved[0] == null) {
                                    viewer.sendMessage(ChatColor.RED + "No player named '" + ChatColor.WHITE
                                            + name + ChatColor.RED + "' has ever joined.");
                                    new AdminMenu(ctx, viewer).open(viewer);
                                    return;
                                }
                                load(ctx, viewer, resolved[0], name);
                            }
                        });
                    }
                });
            }
        });
    }

    /** Loads every read-only number for the target, then opens the menu. */
    private static void load(final GuiContext ctx, final Player viewer, final UUID uuid, final String name) {
        ctx.plugin().getServer().getScheduler().runTaskAsynchronously(ctx.plugin(), new Runnable() {
            @Override
            public void run() {
                double bank = 0;
                double wallet = 0;
                Loan loan = null;
                List<Bounty> bounties = new ArrayList<Bounty>();
                List<LedgerEntry> history = new ArrayList<LedgerEntry>();
                String display = name;
                RuntimeException failure = null;
                try {
                    bank = ctx.bankEconomy().account(uuid).getBalance();
                    wallet = ctx.wallet().account(uuid).getBalance();
                    loan = ctx.loans().activeLoan(uuid);
                    bounties = ctx.bounties().pendingOn(uuid);
                    history = ctx.ledger().recent(uuid, SHOWN);
                    Map<UUID, String> known = ctx.names().names(Collections.singletonList(uuid));
                    String stored = known.get(uuid);
                    if (stored != null && !stored.isEmpty()) {
                        display = stored;
                    }
                } catch (RuntimeException e) {
                    failure = e;
                }
                final double finalBank = bank;
                final double finalWallet = wallet;
                final Loan finalLoan = loan;
                final List<Bounty> finalBounties = bounties;
                final List<LedgerEntry> finalHistory = history;
                final String finalDisplay = display;
                final RuntimeException finalFailure = failure;
                ctx.plugin().getServer().getScheduler().runTask(ctx.plugin(), new Runnable() {
                    @Override
                    public void run() {
                        if (finalFailure != null) {
                            viewer.sendMessage(ChatColor.RED + "Could not load that player, try again.");
                            new AdminMenu(ctx, viewer).open(viewer);
                            return;
                        }
                        new AdminInspectMenu(ctx, viewer, finalDisplay, finalBank, finalWallet,
                                finalLoan, finalBounties, finalHistory).open(viewer);
                    }
                });
            }
        });
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.NEUTRAL));
        fillEmpty(d.filler());

        set(10, Icon.of(Material.EMERALD, Design.MONEY + "" + ChatColor.BOLD + "Bank",
                Design.lore("Money held in the bank account.",
                        Arrays.asList(Design.IN + ctx.format().format(bank)),
                        "The balance the primary commands read.")), null);

        set(11, Icon.of(Heads.icon(Heads.walletSkin(), Material.BOOK),
                Design.MONEY + "" + ChatColor.BOLD + "Wallet",
                Design.lore("Money held in the digital wallet.",
                        Arrays.asList(Design.HINT + ctx.format().format(wallet)), null)), null);

        set(12, Icon.of(Heads.icon(Heads.moneySkin(), Material.GOLD_INGOT),
                Design.MONEY + "" + ChatColor.BOLD + "Net worth",
                Design.lore("Bank plus wallet.",
                        Arrays.asList(Design.HINT + ctx.format().format(bank + wallet)), null)), null);

        set(13, loanTile(), null);

        set(14, bountyTile(), null);

        set(16, d.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new AdminMenu(ctx, viewer).open(player);
            }
        });

        if (history.isEmpty()) {
            set(22, Icon.of(Material.PAPER, Design.HEADING + "" + ChatColor.BOLD + "No recent activity",
                    Design.lore("No money movements are recorded for this player.",
                            null, null)), null);
        }
        int i = 0;
        for (LedgerEntry entry : history) {
            int slot = 19 + i;
            if (slot > 25) {
                break;
            }
            set(slot, entry(entry), null);
            i++;
        }
    }

    private ItemStack loanTile() {
        if (loan == null) {
            return Icon.of(Material.GLASS_BOTTLE, Design.HEADING + "" + ChatColor.BOLD + "Loan",
                    Design.lore("No open loan.",
                            null, "Loans are taken through the bank."));
        }
        return Icon.of(Material.GLASS_BOTTLE, Design.HEADING + "" + ChatColor.BOLD + "Loan",
                Design.lore("An open bank loan.",
                        Arrays.asList(Design.OUT + ctx.format().format(loan.owed()) + " owed",
                                Design.LABEL + "Principal " + Design.HINT + ctx.format().brief(loan.amount()),
                                Design.HINT + left(loan.dueAt())),
                        null));
    }

    private ItemStack bountyTile() {
        double total = 0;
        for (Bounty bounty : bounties) {
            total += bounty.amount();
        }
        if (bounties.isEmpty()) {
            return Icon.of(Material.IRON_SWORD, Design.HEADING + "" + ChatColor.BOLD + "Bounties",
                    Design.lore("No open bounties on this player.", null, null));
        }
        return Icon.of(Material.IRON_SWORD, Design.HEADING + "" + ChatColor.BOLD + "Bounties",
                Design.lore("Open bounties on this player.",
                        Arrays.asList(Design.OUT + ctx.format().brief(total) + " escrowed",
                                Design.LABEL + String.valueOf(bounties.size())
                                        + (bounties.size() == 1 ? " posting" : " postings")),
                        null));
    }

    private ItemStack entry(LedgerEntry entry) {
        boolean in = entry.delta() >= 0;
        ChatColor sign = in ? Design.IN : Design.OUT;
        String amount = ctx.format().brief(Math.abs(entry.delta()));
        List<String> lore = new ArrayList<String>();
        if (!entry.detail().isEmpty()) {
            lore.add(Design.LABEL + entry.detail());
        }
        lore.add("");
        lore.add(sign + (in ? "+" : "-") + amount);
        lore.add(Design.HINT + label(entry.kind()));
        lore.add("");
        lore.add(Design.HINT + ago(entry.ts()));
        return Icon.of(in ? Material.GOLD_INGOT : Material.REDSTONE,
                sign + "" + ChatColor.BOLD + (in ? "+" : "-") + amount
                        + ChatColor.DARK_GRAY + " " + label(entry.kind()),
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

    private static String left(long dueAt) {
        long seconds = (dueAt - System.currentTimeMillis()) / 1000;
        if (seconds < 0) {
            return "overdue";
        }
        if (seconds < 60) {
            return seconds + "s left";
        }
        if (seconds < 3600) {
            return seconds / 60 + "m left";
        }
        if (seconds < 86400) {
            return seconds / 3600 + "h left";
        }
        return seconds / 86400 + "d left";
    }
}