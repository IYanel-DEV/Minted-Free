package dev.minted.gui;

import dev.minted.gui.theme.Design;
import dev.minted.request.PaymentRequest;
import dev.minted.request.RequestMessage;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * The menu shown when a player interacts with another: the opener's own wallet
 * and bank balances alongside Send and Request buttons aimed at the target.
 */
public final class InteractionMenu extends Menu {

    private final GuiContext ctx;
    private final Player viewer;
    private final Player target;

    public InteractionMenu(GuiContext ctx, Player viewer, Player target) {
        super(Design.title(Design.Accent.BANK, "Pay " + target.getName()), 3);
        this.ctx = ctx;
        this.viewer = viewer;
        this.target = target;
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.BANK));
        set(4, Icon.of(Material.PAPER, Design.HEADING + "" + ChatColor.BOLD + target.getName(),
                Design.lore("Send or request money.", null, null)), null);
        set(11, d.wallet(ctx.walletService().balance(viewer), ctx.format()), null);
        set(13, d.bank(ctx.bank().bankBalance(viewer.getUniqueId()), ctx.format()), null);

        set(20, Icon.of(Material.GOLD_BLOCK, Design.MONEY + "" + ChatColor.BOLD + "Send money",
                Design.lore("Give money to " + target.getName() + ".", null, "Click to choose an amount.")),
                new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new AmountMenu(ctx, Design.title(Design.Accent.BANK, "Send money"), "Send", send()).open(player);
            }
        });
        set(24, Icon.of(Material.CHEST, Design.MONEY + "" + ChatColor.BOLD + "Request money",
                Design.lore("Ask " + target.getName() + " to pay you.", null, "Click to choose an amount.")),
                new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new AmountMenu(ctx, Design.title(Design.Accent.BANK, "Request money"), "Request", request()).open(player);
            }
        });
    }

    private AmountMenu.AmountChoice send() {
        return new AmountMenu.AmountChoice() {
            @Override
            public void chosen(Player player, double amount) {
                if (!target.isOnline()) {
                    player.sendMessage(ChatColor.RED + target.getName() + " is no longer online.");
                    return;
                }
                if (!ctx.walletService().transfer(viewer, target, amount)) {
                    player.sendMessage(ChatColor.RED + "Transfer failed - check your balance and their limit.");
                    return;
                }
                player.sendMessage(ChatColor.GREEN + "Sent " + ChatColor.WHITE + ctx.format().format(amount)
                        + ChatColor.GREEN + " to " + target.getName() + ".");
                target.sendMessage(ChatColor.GREEN + "Received " + ChatColor.WHITE + ctx.format().format(amount)
                        + ChatColor.GREEN + " from " + viewer.getName() + ".");
                ctx.sounds().paySent(viewer);
                ctx.sounds().payReceived(target);
                new InteractionMenu(ctx, viewer, target).open(player);
            }
        };
    }

    private AmountMenu.AmountChoice request() {
        return new AmountMenu.AmountChoice() {
            @Override
            public void chosen(Player player, double amount) {
                if (!target.isOnline()) {
                    player.sendMessage(ChatColor.RED + target.getName() + " is no longer online.");
                    return;
                }
                PaymentRequest req = ctx.requests().create(viewer.getUniqueId(), target.getUniqueId(), amount);
                RequestMessage.send(target, viewer.getName(), req, ctx.format());
                ctx.sounds().requestReceived(target);
                player.sendMessage(ChatColor.GREEN + "Requested " + ChatColor.WHITE + ctx.format().format(amount)
                        + ChatColor.GREEN + " from " + target.getName() + ".");
                player.closeInventory();
            }
        };
    }
}
