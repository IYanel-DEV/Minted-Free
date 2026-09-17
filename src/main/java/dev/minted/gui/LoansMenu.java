package dev.minted.gui;

import dev.minted.bank.BankAccount;
import dev.minted.bank.Loan;
import dev.minted.bank.LoanService;
import dev.minted.gui.theme.Design;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bank loans. With no open loan the page offers to borrow up to the cap; with
 * one it shows the repayment total and deadline and offers to pay the loan off
 * from the bank balance in full.
 */
public final class LoansMenu extends Menu {

    private final GuiContext ctx;
    private final Player viewer;

    public LoansMenu(GuiContext ctx, Player viewer) {
        super(Design.title(Design.Accent.BANK, "Bank loans"), 3);
        this.ctx = ctx;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        Design d = ctx.design();
        frame(d.border(Design.Accent.BANK));
        UUID uuid = viewer.getUniqueId();
        Loan loan = ctx.loans().activeLoan(uuid);

        if (loan == null) {
            set(11, Icon.of(Material.GOLD_INGOT, Design.MONEY + "" + ChatColor.BOLD + "Take a loan",
                    Design.lore("Borrow cash straight into your bank.",
                            Arrays.asList(Design.LABEL + "Cap: " + Design.MONEY + ctx.format().brief(ctx.loans().maxLoan()),
                                    Design.LABEL + "Fee: " + Design.HINT + feePercent() + "%",
                                    Design.LABEL + "Term: " + Design.HINT + termText()),
                            "Click to choose the amount.")), new Consumer<Player>() {
                @Override
                public void accept(Player player) {
                    new AmountMenu(ctx, Design.title(Design.Accent.BANK, "Take a loan"), "Borrow",
                            choose()).open(player);
                }
            });
        } else {
            boolean overdue = loan.timeLeft() < 0;
            set(11, Icon.of(Material.BOOK, Design.HEADING + "" + ChatColor.BOLD + "Open loan",
                    Design.lore("Your current loan with the bank.",
                            Arrays.asList(Design.LABEL + "Borrowed: " + Design.MONEY + ctx.format().brief(loan.amount()),
                                    Design.LABEL + "To repay: " + Design.OUT + ctx.format().brief(loan.owed()),
                                    Design.LABEL + (overdue ? "Overdue" : "Due")
                                            + ": " + Design.HINT + relative(loan.timeLeft())),
                            "Pay it off outright to close it.")), null);
            set(13, Icon.of(Material.EMERALD, Design.IN + "" + ChatColor.BOLD + "Repay",
                    Design.lore("Pay the full owed balance from your bank.",
                            Arrays.asList(Design.OUT + ctx.format().brief(loan.owed())),
                            "Click to repay.")), new Consumer<Player>() {
                @Override
                public void accept(Player player) {
                    repay(player);
                }
            });
        }

        set(18, d.back(), new Consumer<Player>() {
            @Override
            public void accept(Player player) {
                new PersonalMenu(ctx, viewer).open(player);
            }
        });
        fillEmpty(d.filler());
    }

    private AmountMenu.AmountChoice choose() {
        return new AmountMenu.AmountChoice() {
            @Override
            public void chosen(Player player, double amount) {
                UUID uuid = player.getUniqueId();
                LoanService loans = ctx.loans();
                if (loans.activeLoan(uuid) != null) {
                    ctx.messages().send(player, "bank.loan.active");
                } else if (amount <= 0) {
                    ctx.messages().send(player, "bank.loan.invalid");
                } else if (amount > loans.maxLoan()) {
                    ctx.messages().send(player, "bank.loan.limit", "max", ctx.format().format(loans.maxLoan()));
                } else {
                    BankAccount account = ctx.bankEconomy().getCached(uuid);
                    if (account == null) {
                        ctx.messages().send(player, "bank.loan.not-ready");
                    } else if (!loans.take(uuid, amount)) {
                        ctx.messages().send(player, "bank.loan.cap");
                    } else {
                        ctx.messages().send(player, "bank.loan.taken",
                                "amount", ctx.format().format(amount),
                                "owed", ctx.format().format(owedFor(amount)));
                    }
                }
                new LoansMenu(ctx, viewer).open(player);
            }
        };
    }

    private void repay(Player player) {
        UUID uuid = player.getUniqueId();
        Loan loan = ctx.loans().activeLoan(uuid);
        if (loan == null) {
            ctx.messages().send(player, "bank.loan.none");
        } else if (!ctx.loans().repay(uuid)) {
            ctx.messages().send(player, "bank.loan.short", "owed", ctx.format().format(loan.owed()));
        } else {
            ctx.messages().send(player, "bank.loan.repaid", "owed", ctx.format().format(loan.owed()));
        }
        new LoansMenu(ctx, viewer).open(player);
    }

    private double owedFor(double amount) {
        return Math.round(amount * (100 + feePercent()) * 100.0) / 10000.0;
    }

    private double feePercent() {
        double fee = ctx.loans().contractRate();
        return Math.round(fee * 100.0) / 100.0;
    }

    private String termText() {
        long minutes = Math.round(ctx.loans().termMs() / 60000.0);
        if (minutes < 60) {
            return minutes + " min";
        }
        if (minutes < 1440) {
            return minutes / 60 + " h";
        }
        return minutes / 1440 + " d";
    }

    private static String relative(long millis) {
        long minutes = Math.max(0, (millis + 59) / 60000);
        if (minutes < 60) {
            return minutes + " min";
        }
        if (minutes < 1440) {
            return minutes / 60 + " h " + minutes % 60 + " min";
        }
        return minutes / 1440 + " d " + (minutes % 1440) / 60 + " h";
    }
}