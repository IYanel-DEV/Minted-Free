package dev.minted.gui;

import dev.minted.bank.EconomyStats;
import dev.minted.compat.Heads;
import dev.minted.gui.theme.Design;
import dev.minted.vip.VipService;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * The op-only admin dashboard behind {@code /minted dashboard}. A read-only
 * snapshot of the running economy - bank totals, wallet total, money burned out
 * of the economy, and the active sinks - plus quick links to the admin-facing
 * pages: the economy stats, the shops, the bounty board and the sales feed.
 * Every number comes from cached values, so opening it never touches the disk.
 */
public final class AdminMenu extends Menu {

    private final GuiContext ctx;
    private final Player viewer;

    public AdminMenu(GuiContext ctx, Player viewer) {
        super(Design.title(Design.Accent.NEUTRAL, "Minted admin"), 6);
        this.ctx = ctx;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        EconomyStats stats = ctx.stats();
        Design d = ctx.design();
        frame(d.border(Design.Accent.NEUTRAL));
        fillEmpty(d.filler());

        set(10, Icon.of(Heads.icon(Heads.moneySkin(), Material.GOLD_INGOT),
                Design.MONEY + "" + ChatColor.BOLD + "Total in Banks",
                Design.lore("All money stored in bank accounts across the server.",
                        Arrays.asList(Design.HINT + ctx.format().brief(stats.banked()),
                                Design.LABEL + String.valueOf(stats.accountCount()) + " bank accounts in use."),
                        null)), null);

        set(11, Icon.of(Heads.icon(Heads.walletSkin(), Material.BOOK),
                Design.MONEY + "" + ChatColor.BOLD + "Wallets",
                Design.lore("All money held in player wallets (physical cash is not counted).",
                        Arrays.asList(Design.HINT + ctx.format().brief(ctx.wallet().liveSum())),
                        null)), null);

        set(12, Icon.of(Heads.icon(Heads.flameSkin(), Material.LAVA_BUCKET),
                Design.OUT + "" + ChatColor.BOLD + "Burned / Lost",
                Design.lore("Money that left the economy for good.",
                        Arrays.asList(Design.HINT + ctx.format().brief(stats.burned())),
                        "Notes dropped that despawn, plus digital shop spends and fees.")), null);

        final VipService vipService = plugin().getVipService();
        final int vipCount = vipService == null ? 0 : vipService.size();
        set(13, Icon.of(Material.GOLDEN_APPLE, Design.MONEY + "" + ChatColor.BOLD + "VIP list",
                Design.lore("Players who get a /<username> shop command.",
                        Arrays.asList(Design.LABEL + String.valueOf(vipCount) + " VIP" + (vipCount == 1 ? "" : "s")),
                        "Click to manage.")), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                VipService service = plugin().getVipService();
                if (service == null) {
                    player.sendMessage(ChatColor.RED + "The VIP list is not available right now.");
                    return;
                }
                new VipListMenu(ctx, service, viewer).open(player);
            }
        });

        set(19, Icon.of(Heads.icon(Heads.pouchSkin(), Material.GOLD_NUGGET),
                Design.IN + "" + ChatColor.BOLD + "Interest",
                Design.lore("What the bank pays on stored balances.",
                        Arrays.asList(Design.LABEL + (plugin().getConfig().getBoolean("bank.interest.enabled", true)
                                        ? "On - " + plugin().getConfig().getDouble("bank.interest.rate", 0.1) + "%"
                                        : "Off"),
                                Design.HINT + "every "
                                        + plugin().getConfig().getLong("bank.interest.interval-minutes", 30)
                                        + " minutes."),
                        null)), null);

        set(20, Icon.of(Heads.icon(Heads.bankSkin(), Material.CHEST),
                Design.HEADING + "" + ChatColor.BOLD + "Sinks",
                Design.lore("Percentages taken out of the economy.",
                        Arrays.asList(Design.LABEL + "Withdraw "
                                        + percent("bank.fee.withdraw-percent"),
                                Design.LABEL + "Transfer " + percent("bank.fee.transfer-percent")),
                        "Set them under bank.fee in config.yml.")), null);

        set(21, Icon.of(Heads.icon(Heads.noneSkin(), Material.STRING),
                Design.HEADING + "" + ChatColor.BOLD + "Mode",
                Design.lore("How money moves outside the bank.",
                        Arrays.asList(Design.HINT + (ctx.walletService().isPhysical() ? "Physical cash" : "Digital only")),
                        "economy.physical in config.yml.")), null);

        set(29, Icon.of(Material.GOLD_INGOT, Design.MONEY + "" + ChatColor.BOLD + "Economy stats",
                Design.lore("Server totals and the rich list.",
                        null, "Click to open.")), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new EconomyMenu(ctx, viewer).open(player);
            }
        });

        set(30, Icon.of(Material.EMERALD, Design.HEADING + "" + ChatColor.BOLD + "Shops",
                Design.lore("Browse, create and edit the /eshop shops.",
                        null, "Click to open.")), command("eshop"));

        set(31, Icon.of(Material.IRON_SWORD, Design.HEADING + "" + ChatColor.BOLD + "Bounty board",
                Design.lore("Every open bounty and the refund page.",
                        null, "Click to open.")), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new BountyMenu(ctx, 0).open(player);
            }
        });

        set(32, Icon.of(Material.BOOK, Design.HEADING + "" + ChatColor.BOLD + "Recent sales",
                Design.lore("The latest shop and marketplace trades.",
                        null, "Click to open.")), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                SalesMenu.open(ctx, player);
            }
        });

        set(33, Icon.of(Material.REDSTONE, Design.HEADING + "" + ChatColor.BOLD + "Reload config",
                Design.lore("Re-reads config.yml without a restart.",
                        null, "Click to reload.")), command("minted reload"));

        set(34, Icon.of(Heads.icon(Heads.bookSkin(), Material.PAPER),
                Design.HEADING + "" + ChatColor.BOLD + "Inspect player",
                Design.lore("A read-only profile: balances, loan, bounties and recent history.",
                        null, "Type a player's name when prompted.")), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                AdminInspectMenu.begin(ctx, viewer);
            }
        });

        set(45, Icon.of(Material.DIAMOND, Design.MONEY + "" + ChatColor.BOLD + "Minted "
                        + plugin().getDescription().getVersion(),
                Design.lore("Ecosystem totals, live from memory.",
                        Arrays.asList(Design.LABEL + "Server " + plugin().getServerVersion(),
                                Design.LABEL + "Max balance " + ctx.format().brief(ctx.bankEconomy().maxBalance())),
                        "/minted report for the integrations.")), null);

        set(53, d.close(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                player.closeInventory();
            }
        });
    }

    private String percent(String key) {
        return plugin().getConfig().getDouble(key, 0) + "%";
    }

    private Consumer<Player> command(final String cmd) {
        return new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                player.closeInventory();
                player.performCommand(cmd);
            }
        };
    }

    private dev.minted.MintedPlugin plugin() {
        return (dev.minted.MintedPlugin) ctx.plugin();
    }
}