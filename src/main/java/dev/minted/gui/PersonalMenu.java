package dev.minted.gui;

import dev.minted.bank.BankAccount;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * A player's own menu: wallet and bank balances with deposit and withdraw
 * buttons. Reached from {@code /minted gui} and {@code /bank}, this is how a
 * player checks their balances and moves money without a target. Withdrawing
 * pays out physical signed notes. In physical mode the wallet balance is the
 * notes the player holds and Deposit banks every note in their inventory; in
 * digital mode Deposit is a wallet-to-bank move of a chosen amount.
 */
public final class PersonalMenu extends Menu {

    private final GuiContext ctx;
    private final Player viewer;

    public PersonalMenu(GuiContext ctx, Player viewer) {
        super(ChatColor.DARK_GREEN + "Your bank", 3);
        this.ctx = ctx;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        boolean physical = ctx.walletService().isPhysical();
        set(11, Icon.of(Material.GOLD_INGOT, ChatColor.GOLD + "Wallet",
                ChatColor.GRAY + ctx.format().format(ctx.walletService().balance(viewer))), null);
        set(15, Icon.of(Material.EMERALD, ChatColor.GREEN + "Bank",
                ChatColor.GRAY + ctx.format().format(ctx.bank().bankBalance(viewer.getUniqueId()))), null);

        String depositLore = physical ? "All banknotes in your inventory." : "Wallet into bank.";
        set(21, Icon.of(Material.GOLD_BLOCK, ChatColor.GOLD + "Deposit",
                ChatColor.GRAY + depositLore), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                if (ctx.walletService().isPhysical()) {
                    int locked = ctx.combatLock().secondsLeft(player);
                    if (locked > 0) {
                        ctx.messages().send(player, "bank.combat-lock", "seconds", String.valueOf(locked));
                    } else {
                        depositNotes(player);
                    }
                    new PersonalMenu(ctx, viewer).open(player);
                } else {
                    new AmountMenu(ctx, ChatColor.DARK_GREEN + "Deposit", "Deposit", deposit()).open(player);
                }
            }
        });
        set(23, Icon.of(Material.IRON_INGOT, ChatColor.WHITE + "Withdraw",
                ChatColor.GRAY + "Bank into cash."), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new AmountMenu(ctx, ChatColor.DARK_GREEN + "Withdraw", "Withdraw", withdraw()).open(player);
            }
        });
    }

    // Physical mode: bank every note in the inventory, honouring the cap.
    private void depositNotes(Player player) {
        BankAccount account = ctx.bankEconomy().getCached(player.getUniqueId());
        if (account == null) {
            player.sendMessage(ChatColor.RED + "Your account is still loading, try again in a moment.");
            return;
        }
        double[] result = ctx.notes().depositInventory(player, account);
        double banked = result[0];
        double left = result[1];
        if (banked <= 0) {
            ctx.messages().send(player, left > 0 ? "bank.full" : "bank.no-notes");
        } else if (left > 0) {
            ctx.messages().send(player, "bank.deposited-partial",
                    "banked", ctx.format().format(banked), "left", ctx.format().format(left));
        } else {
            ctx.messages().send(player, "bank.deposited-notes", "amount", ctx.format().format(banked));
        }
    }

    private AmountMenu.AmountChoice deposit() {
        return new AmountMenu.AmountChoice() {
            @Override
            public void chosen(Player player, double amount) {
                int locked = ctx.combatLock().secondsLeft(player);
                if (locked > 0) {
                    ctx.messages().send(player, "bank.combat-lock", "seconds", String.valueOf(locked));
                    new PersonalMenu(ctx, viewer).open(player);
                    return;
                }
                if (!ctx.bank().deposit(player.getUniqueId(), amount)) {
                    player.sendMessage(ChatColor.RED + "Deposit failed - not enough in your wallet, "
                            + "or the bank limit would be passed.");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Deposited " + ChatColor.WHITE + ctx.format().format(amount)
                            + ChatColor.GREEN + ".");
                }
                new PersonalMenu(ctx, viewer).open(player);
            }
        };
    }

    private AmountMenu.AmountChoice withdraw() {
        return new AmountMenu.AmountChoice() {
            @Override
            public void chosen(Player player, double amount) {
                BankAccount account = ctx.bankEconomy().getCached(player.getUniqueId());
                if (account == null) {
                    player.sendMessage(ChatColor.RED + "Your account is still loading, try again in a moment.");
                    return;
                }
                List<ItemStack> notes = ctx.banknotes().mint(account, amount);
                if (notes.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Withdraw failed - not enough in your bank.");
                } else {
                    boolean dropped = ctx.banknotes().give(player, notes);
                    player.sendMessage(ChatColor.GREEN + "Here is " + ChatColor.WHITE + ctx.format().format(amount)
                            + ChatColor.GREEN + " in cash."
                            + (dropped ? " " + ChatColor.GRAY
                                    + "Your inventory was full, so some notes dropped at your feet." : ""));
                }
                new PersonalMenu(ctx, viewer).open(player);
            }
        };
    }
}
