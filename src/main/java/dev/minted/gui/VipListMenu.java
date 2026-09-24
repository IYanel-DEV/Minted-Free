package dev.minted.gui;

import dev.minted.compat.Heads;
import dev.minted.gui.theme.Design;
import dev.minted.vip.VipMember;
import dev.minted.vip.VipService;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * The dashboard's VIP page: every VIP with the {@code /<username>} shop
 * command they advertise, click-to-remove on each entry, and a prompt to add
 * more. Reached from the "VIP list" tile of {@link AdminMenu}; the back arrow
 * returns there. The list is the live service view, so a removal takes effect
 * the moment the click lands.
 */
public final class VipListMenu extends Menu {

    /** Rows 1-4 inside the frame: seven columns times four rows. */
    private static final int[] CONTENT = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final GuiContext ctx;
    private final VipService vips;
    private final Player viewer;
    private int page;

    public VipListMenu(GuiContext ctx, VipService vips, Player viewer) {
        super(Design.title(Design.Accent.NEUTRAL, "VIP list"), 6);
        this.ctx = ctx;
        this.vips = vips;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.NEUTRAL));
        fillEmpty(d.filler());

        List<VipMember> all = vips.roster();
        int pages = Math.max(1, (all.size() + CONTENT.length - 1) / CONTENT.length);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }

        if (all.isEmpty()) {
            set(22, emptyState(), null);
        } else {
            int start = page * CONTENT.length;
            for (int i = 0; i < CONTENT.length && start + i < all.size(); i++) {
                final VipMember member = all.get(start + i);
                set(CONTENT[i], icon(member), new Consumer<Player>() {
                    @Override
                    public void accept(Player player) {
                        if (member.isPermission()) {
                            ctx.messages().send(player, "vip.remove-permission", "player", member.getName());
                        } else {
                            vips.remove(member.getName(), player);
                        }
                        refresh();
                    }
                });
            }
        }

        set(45, d.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new AdminMenu(ctx, viewer).open(player);
            }
        });
        if (page > 0) {
            set(48, d.prev(), new Consumer<Player>() {
                @Override
                public void accept(Player player) {
                    page--;
                    refresh();
                }
            });
        }
        set(49, d.pageInfo(page + 1, pages), null);
        if (page < pages - 1) {
            set(50, d.next(), new Consumer<Player>() {
                @Override
                public void accept(Player player) {
                    page++;
                    refresh();
                }
            });
        }
        set(51, Icon.of(Material.EMERALD, Design.IN + "" + ChatColor.BOLD + "Add VIP",
                Design.lore("Put a player on the VIP list.", null, "Click to type their name.")),
                new Consumer<Player>() {
                    @Override
                    public void accept(final Player player) {
                        promptAdd(player);
                    }
                });
        set(53, d.close(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                player.closeInventory();
            }
        });
    }

    private ItemStack emptyState() {
        ItemStack board = Heads.skull();
        if (board == null) {
            board = new ItemStack(Material.PAPER);
        }
        return Icon.of(board, Design.HEADING + "" + ChatColor.BOLD + "No VIPs yet",
                Design.lore("VIPs get a /<username> command that opens their shop.",
                        null, "Add one with the emerald button."));
    }

    private ItemStack icon(VipMember member) {
        ItemStack head = Heads.player(member.getName());
        if (head == null) {
            head = new ItemStack(Material.PAPER);
        }
        List<String> details = new ArrayList<String>();
        if (member.isPermission()) {
            details.add(Design.LABEL + "Granted by the " + VipService.PERMISSION + " permission.");
        } else {
            details.add(Design.LABEL + "Added "
                    + new SimpleDateFormat("yyyy-MM-dd").format(new Date(member.getAdded()))
                    + " by " + member.getBy());
        }
        boolean shop = vips.hasShop(member.getUuid());
        return Icon.of(head, Design.MONEY + "" + ChatColor.BOLD + member.getName(),
                Design.lore(shop ? "Type /" + member.getName() + " to open their shop."
                                : "Their /" + member.getName() + " command starts when they create a shop.",
                        details,
                        member.isPermission()
                                ? "Managed by your permission plugin."
                                : Design.OUT + "Click to remove VIP."));
    }

    /** Closes the menu, asks for a name in chat, then reopens with the result. */
    private void promptAdd(final Player player) {
        player.closeInventory();
        player.sendMessage(ChatColor.GRAY + "Type the name of the player to make a VIP. "
                + ChatColor.WHITE + "Type anything else to cancel.");
        ctx.prompt().await(player, new Consumer<String>() {
            @Override
            public void accept(String raw) {
                String name = raw == null ? "" : raw.trim();
                if (name.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Cancelled.");
                    new VipListMenu(ctx, vips, player).open(player);
                    return;
                }
                vips.add(name, player, new Runnable() {
                    @Override
                    public void run() {
                        new VipListMenu(ctx, vips, player).open(player);
                    }
                });
            }
        });
    }
}