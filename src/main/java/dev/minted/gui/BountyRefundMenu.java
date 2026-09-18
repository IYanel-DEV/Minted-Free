package dev.minted.gui;

import dev.minted.bounty.Bounty;
import dev.minted.bounty.BountyService;
import dev.minted.gui.theme.Design;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The refund page behind a bounty-board row. Lists every open bounty the viewer
 * placed on that target (every open bounty, for admins); clicking one pays the
 * escrowed reward straight back into its placer's bank and closes it.
 */
public final class BountyRefundMenu extends Menu {

    private final GuiContext ctx;
    private final UUID target;
    private final UUID viewer;
    private final boolean admin;

    private static final String targetName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() != null ? player.getName() : uuid.toString().substring(0, 8);
    }

    public BountyRefundMenu(GuiContext ctx, UUID target, UUID viewer, boolean admin) {
        super(Design.title(Design.Accent.NEUTRAL, "Refund bounties"), 6);
        this.ctx = ctx;
        this.target = target;
        this.viewer = viewer;
        this.admin = admin;
    }

    @Override
    protected void build() {
        BountyService bounties = ctx.bounties();
        Design design = ctx.design();
        frame(design.border(Design.Accent.NEUTRAL));

        List<Bounty> rows = new ArrayList<Bounty>();
        for (Bounty bounty : bounties.pendingOn(target)) {
            if (admin || bounty.placer().equals(viewer)) {
                rows.add(bounty);
            }
        }

        if (rows.isEmpty()) {
            set(13, Icon.of(Material.PAPER, Design.HINT + "" + ChatColor.BOLD + "Nothing to refund",
                    Design.lore("You have no open bounty on this player.",
                            null, "Back returns to the board.")), null);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                final Bounty bounty = rows.get(i);
                int slot = 10 + i + (2 * (i / 7));
                if (slot > 43) {
                    break;
                }
                set(slot, entry(bounty), new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        refund(player, bounty);
                    }
                });
            }
        }

        set(49, design.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new BountyMenu(ctx, 0).open(player);
            }
        });
        fillEmpty(design.filler());
    }

    private ItemStack entry(Bounty bounty) {
        List<String> lore = new ArrayList<String>();
        lore.add(Design.MONEY + "Reward: " + ctx.format().format(bounty.amount()));
        if (bounty.note() != null) {
            lore.add(Design.LABEL + "Note: " + Design.HINT + bounty.note());
        }
        if (admin) {
            lore.add(Design.LABEL + "Placed by: " + Design.HINT + targetName(bounty.placer()));
        }
        return Icon.of(Material.GOLD_INGOT,
                Design.HEADING + "" + ChatColor.BOLD + "Bounty #" + bounty.id() + " on " + targetName(bounty.target()),
                Design.lore(null, lore, "Click to refund it to your bank."));
    }

    private void refund(Player player, Bounty bounty) {
        if (ctx.bounties().refund(bounty, viewer, admin)) {
            ctx.messages().send(player, "bounty.refunded", "amount", ctx.format().format(bounty.amount()));
        } else {
            ctx.messages().send(player, "bounty.refund-failed");
        }
        new BountyRefundMenu(ctx, target, viewer, admin).open(player);
    }
}