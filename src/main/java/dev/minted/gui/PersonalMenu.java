package dev.minted.gui;

import dev.minted.bank.BankAccount;
import dev.minted.compat.Heads;
import dev.minted.gui.theme.Design;

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
        super(Design.title(Design.Accent.BANK, "Your bank"), 3);
        this.ctx = ctx;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        boolean physical = ctx.walletService().isPhysical();
        Design d = ctx.design();
        frame(d.border(Design.Accent.BANK));
        set(11, d.wallet(ctx.walletService().balance(viewer), ctx.format()), null);
        set(15, d.bank(ctx.bank().bankBalance(viewer.getUniqueId()), ctx.format()), null);

        String depositLore = physical ? "All banknotes in your inventory." : "Wallet into bank.";
        set(21, Icon.of(Heads.icon(Heads.moneySkin(), Material.GOLD_BLOCK),
                Design.MONEY + "" + ChatColor.BOLD + "Deposit",
                Design.lore(depositLore, null, "Click to deposit.")), new Consumer<Player>() {
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
                    new AmountMenu(ctx, Design.title(Design.Accent.BANK, "Deposit"), "Deposit", deposit()).open(player);
                }
            }
        });
        set(22, Icon.of(Heads.icon(Heads.crownSkin(), Material.GOLD_INGOT),
                Design.MONEY + "" + ChatColor.BOLD + "Stats",
                Design.lore("See the server-wide money totals.",
                        null, "Click to view economy stats.")), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new EconomyMenu(ctx, viewer).open(player);
            }
        });
        set(4, Icon.of(Heads.icon(Heads.bookSkin(), Material.BOOK),
                Design.HEADING + "" + ChatColor.BOLD + "Loans",
                Design.lore("Borrow from the bank, or repay a loan.",
                        null, "Click to open loans.")), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new LoansMenu(ctx, viewer).open(player);
            }
        });
        set(23, Icon.of(Heads.icon(Heads.silverSkin(), Material.IRON_INGOT),
                Design.HEADING + "" + ChatColor.BOLD + "Withdraw",
                Design.lore("Bank into cash.", null, "Click to withdraw.")), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new AmountMenu(ctx, Design.title(Design.Accent.BANK, "Withdraw"), "Withdraw", withdraw()).open(player);
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
