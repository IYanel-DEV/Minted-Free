package dev.minted.command;

import dev.minted.backend.NamesDao;
import dev.minted.bank.BankAccount;
import dev.minted.bank.EconomyService;
import dev.minted.bank.EconomyStats;
import dev.minted.bank.Fees;
import dev.minted.bank.MoneyFormat;
import dev.minted.bank.Purse;
import dev.minted.bank.WalletService;
import dev.minted.ledger.LedgerService;
import dev.minted.sound.SoundFX;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * {@code /pay <player> <amount>} - moves wallet money to another player. An
 * online target is paid from wallet to wallet in the usual way; an offline
 * target, resolved through the names Minted has seen, is paid into their bank
 * so the money is waiting when they next join. Offline settlement runs off the
 * main thread (the target's account read blocks on storage); if the bank is
 * unwilling to take the money, the payer's wallet is put back first.
 */
public final class PayCommand implements CommandExecutor {

    private final Plugin plugin;
    private final WalletService wallet;
    private final EconomyService bank;
    private final NamesDao names;
    private final MoneyFormat format;
    private final SoundFX sounds;
    private final LedgerService ledger;
    private final double transferPercent;
    private final EconomyStats stats;

    public PayCommand(Plugin plugin, WalletService wallet, EconomyService bank, NamesDao names,
                      MoneyFormat format, SoundFX sounds, LedgerService ledger,
                      double transferPercent, EconomyStats stats) {
        this.plugin = plugin;
        this.wallet = wallet;
        this.bank = bank;
        this.names = names;
        this.format = format;
        this.sounds = sounds;
        this.ledger = ledger;
        this.transferPercent = transferPercent;
        this.stats = stats;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can pay.");
            return true;
        }
        if (!sender.hasPermission("minted.pay")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <player> <amount>");
            return true;
        }
        if (!wallet.isReady()) {
            sender.sendMessage(ChatColor.RED + "The economy is still starting up.");
            return true;
        }

        final Player from = (Player) sender;
        String raw = args[0];

        double amount = parseAmount(args[1]);
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Enter an amount greater than zero.");
            return true;
        }

        Player target = from.getServer().getPlayerExact(raw);
        if (target != null) {
            if (target.equals(from)) {
                sender.sendMessage(ChatColor.RED + "You cannot pay yourself.");
                return true;
            }
            payOnline(from, target, amount);
            return true;
        }

        payOffline(from, raw, amount);
        return true;
    }

    private void payOnline(final Player from, final Player target, final double amount) {
        double fee = Fees.of(transferPercent, amount);
        double total = amount + fee;
        Purse sender = wallet.purseFor(from);
        Purse receiver = wallet.purseFor(target);
        if (sender == null || receiver == null) {
            from.sendMessage(ChatColor.RED + "Transfer failed - an account is still loading, try again.");
            return;
        }
        // The purse hides physical cash and the digital wallet alike, so one path
        // covers both modes. The fee is charged on top of the amount and vanishes.
        if (!sender.charge(total)) {
            from.sendMessage(ChatColor.RED + "Transfer failed - check your balance and the recipient's limit.");
            return;
        }
        if (!receiver.credit(amount)) {
            sender.credit(total);
            from.sendMessage(ChatColor.RED + "Transfer failed - the recipient cannot hold that much.");
            return;
        }
        if (fee > 0 && !wallet.isPhysical()) {
            stats.burn(fee);
        }
        from.sendMessage(ChatColor.GREEN + "Paid " + ChatColor.WHITE + format.format(amount)
                + ChatColor.GREEN + " to " + target.getName() + "."
                + (fee > 0 ? " " + ChatColor.GRAY + "(" + format.format(fee) + " fee taken)." : ""));
        target.sendMessage(ChatColor.GREEN + "Received " + ChatColor.WHITE + format.format(amount)
                + ChatColor.GREEN + " from " + from.getName() + ".");
        ledger.record(from.getUniqueId(), -total, "pay", "to " + target.getName());
        ledger.record(target.getUniqueId(), amount, "pay", "from " + from.getName());
        sounds.paySent(from);
        sounds.payReceived(target);
    }

    // The target is not online. Charge the payer's wallet up front, then settle
    // asynchronously into the target's bank. Any failure puts the wallet back
    // and tells the payer; there is no recipient to message while they are away.
    private void payOffline(final Player from, final String raw, final double amount) {
        final double fee = Fees.of(transferPercent, amount);
        final double total = amount + fee;
        if (!wallet.charge(from, total)) {
            from.sendMessage(ChatColor.RED + "Transfer failed - check your balance and the recipient's limit.");
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                String outcome;
                boolean refund = true;
                try {
                    UUID offline = names.uuidByName(raw);
                    if (offline == null) {
                        outcome = ChatColor.RED + "Pick a player who has played here before.";
                    } else {
                        BankAccount account = bank.account(offline);
                        if (account != null && account.deposit(amount)) {
                            String shown = display(offline);
                            if (fee > 0 && !wallet.isPhysical()) {
                                stats.burn(fee);
                            }
                            ledger.record(from.getUniqueId(), -total, "pay", "to " + shown);
                            ledger.record(offline, amount, "pay", "from " + from.getName());
                            outcome = ChatColor.GREEN + "Paid " + ChatColor.WHITE + format.format(amount)
                                    + ChatColor.GREEN + " to " + shown
                                    + ChatColor.GREEN + " (banked for them while offline)."
                                    + (fee > 0
                                            ? " " + ChatColor.GRAY + "(" + format.format(fee) + " fee taken)."
                                            : "");
                            refund = false;
                        } else {
                            outcome = ChatColor.RED + "Transfer failed - the recipient's bank is full.";
                        }
                    }
                } catch (RuntimeException e) {
                    outcome = ChatColor.RED + "Payment could not be settled right now, try again.";
                }
                if (refund) {
                    // Never leave the payer holding the loss of a failed delivery.
                    wallet.refund(from, total);
                }
                final String result = outcome;
                from.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        from.sendMessage(result);
                    }
                });
            }
        });
    }

    private String display(UUID uuid) {
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        java.util.Map<UUID, String> known = names.names(java.util.Collections.singleton(uuid));
        String name = known.get(uuid);
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    private double parseAmount(String raw) {
        try {
            double value = Double.parseDouble(raw);
            return Double.isFinite(value) ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}