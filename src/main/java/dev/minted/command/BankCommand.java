package dev.minted.command;

import dev.minted.bank.Amounts;
import dev.minted.bank.BankAccount;
import dev.minted.bank.BankService;
import dev.minted.bank.CombatLock;
import dev.minted.bank.EconomyService;
import dev.minted.bank.MoneyFormat;
import dev.minted.banknote.BanknoteManager;
import dev.minted.banknote.NoteInventory;
import dev.minted.gui.GuiContext;
import dev.minted.gui.PersonalMenu;
import dev.minted.lang.Messages;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /bank} opens the bank menu. From chat it moves money: {@code /bank
 * withdraw <amount>} pays out physical signed notes, and {@code /bank deposit}
 * with notes in your inventory banks every banknote you carry. In digital mode
 * {@code /bank deposit <amount>} also moves wallet money the classic way; in
 * physical mode there is no digital wallet to move, so only the note deposit
 * exists.
 */
public final class BankCommand implements CommandExecutor {

    private final BankService bank;
    private final EconomyService bankEconomy;
    private final BanknoteManager banknotes;
    private final NoteInventory notes;
    private final GuiContext gui;
    private final MoneyFormat format;
    private final Messages messages;
    private final boolean physical;
    private final CombatLock combatLock;

    public BankCommand(BankService bank, EconomyService bankEconomy, BanknoteManager banknotes, NoteInventory notes,
                       GuiContext gui, MoneyFormat format, Messages messages, boolean physical, CombatLock combatLock) {
        this.bank = bank;
        this.bankEconomy = bankEconomy;
        this.banknotes = banknotes;
        this.notes = notes;
        this.gui = gui;
        this.format = format;
        this.messages = messages;
        this.physical = physical;
        this.combatLock = combatLock;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players have a bank.");
            return true;
        }
        if (!sender.hasPermission("minted.bank")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        Player player = (Player) sender;
        if (!bank.isReady() || !bank.isLoaded(player.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "Your account is still loading, try again in a moment.");
            return true;
        }

        if (args.length == 0) {
            new PersonalMenu(gui, player).open(player);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if ("deposit".equals(action)) {
            int locked = combatLock.secondsLeft(player);
            if (locked > 0) {
                messages.send(player, "bank.combat-lock", "seconds", String.valueOf(locked));
                return true;
            }
            // In physical mode /bank deposit always banks the notes you carry; an
            // amount would have no digital wallet to draw from, so it is ignored.
            if (physical || args.length == 1) {
                return depositNotes(player);
            }
            return move(player, args[1]);
        }
        if ("withdraw".equals(action) && args.length == 2) {
            return withdrawCash(player, args[1]);
        }
        sender.sendMessage(ChatColor.RED + "Usage: /" + label + " [deposit|withdraw] <amount>");
        return true;
    }

    /** wallet -> bank, digital. Only reachable when the wallet is digital. */
    private boolean move(Player player, String rawAmount) {
        UUID uuid = player.getUniqueId();
        double amount = Amounts.resolve(rawAmount, bank.walletBalance(uuid), bank.bankHeadroom(uuid));
        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Enter an amount greater than zero.");
            return true;
        }
        if (!bank.deposit(uuid, amount)) {
            player.sendMessage(ChatColor.RED
                    + "Deposit failed - not enough in your wallet, or the bank limit would be passed.");
            return true;
        }
        player.sendMessage(ChatColor.GREEN + "Deposited " + ChatColor.WHITE
                + format.format(amount) + ChatColor.GREEN + ".");
        return true;
    }

    /** bank -> physical notes. The digital balance drops; the player gets cash. */
    private boolean withdrawCash(Player player, String rawAmount) {
        BankAccount account = bankEconomy.getCached(player.getUniqueId());
        if (account == null) {
            player.sendMessage(ChatColor.RED + "Your account is still loading, try again in a moment.");
            return true;
        }
        double amount = Amounts.resolve(rawAmount, account.getBalance(), Double.MAX_VALUE);
        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Enter an amount greater than zero.");
            return true;
        }
        List<org.bukkit.inventory.ItemStack> minted = banknotes.mint(account, amount);
        if (minted.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Withdraw failed - not enough in your bank.");
            return true;
        }
        boolean dropped = banknotes.give(player, minted);
        messages.send(player, dropped ? "bank.cash-out-dropped" : "bank.cash-out",
                "amount", format.format(amount));
        return true;
    }

    /** Banks every genuine banknote in the player's inventory, respecting the cap. */
    private boolean depositNotes(Player player) {
        BankAccount account = bankEconomy.getCached(player.getUniqueId());
        if (account == null) {
            player.sendMessage(ChatColor.RED + "Your account is still loading, try again in a moment.");
            return true;
        }
        double[] result = notes.depositInventory(player, account);
        double banked = result[0];
        double left = result[1];
        if (banked <= 0) {
            messages.send(player, left > 0 ? "bank.full" : "bank.no-notes");
            return true;
        }
        if (left > 0) {
            messages.send(player, "bank.deposited-partial", "banked", format.format(banked), "left", format.format(left));
        } else {
            messages.send(player, "bank.deposited-notes", "amount", format.format(banked));
        }
        return true;
    }
}
