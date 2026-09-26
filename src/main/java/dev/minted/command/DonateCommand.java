package dev.minted.command;

import dev.minted.bank.MoneyFormat;
import dev.minted.bank.Purse;
import dev.minted.bank.WalletService;
import dev.minted.compat.ActionBar;
import dev.minted.ledger.LedgerService;
import dev.minted.sound.DonationMusic;
import dev.minted.sound.SoundFX;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /donate <player> <amount>} - a no-fee, happy-spirited gift from wallet
 * to wallet. Unlike {@code /pay}, the target must be online: they get the money,
 * a chat line, text floating above the health bar, and a little fanfare unless
 * donation spam just played one (see {@link DonationMusic}).
 */
public final class DonateCommand implements CommandExecutor, TabCompleter {

    private final WalletService wallet;
    private final MoneyFormat format;
    private final SoundFX sounds;
    private final LedgerService ledger;
    private final DonationMusic music;

    public DonateCommand(WalletService wallet, MoneyFormat format, SoundFX sounds,
                         LedgerService ledger, DonationMusic music) {
        this.wallet = wallet;
        this.format = format;
        this.sounds = sounds;
        this.ledger = ledger;
        this.music = music;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can donate.");
            return true;
        }
        if (!sender.hasPermission("minted.donate")) {
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

        double amount = parseAmount(args[1]);
        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Enter an amount greater than zero.");
            return true;
        }

        Player target = from.getServer().getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Pick a player who is online - they need to see it land!");
            return true;
        }
        if (target.equals(from)) {
            sender.sendMessage(ChatColor.RED + "You cannot donate to yourself.");
            return true;
        }

        donate(from, target, amount);
        return true;
    }

    private void donate(final Player from, final Player target, final double amount) {
        Purse sender = wallet.purseFor(from);
        Purse receiver = wallet.purseFor(target);
        if (sender == null || receiver == null) {
            from.sendMessage(ChatColor.RED + "Donation failed - an account is still loading, try again.");
            return;
        }
        if (!sender.charge(amount)) {
            from.sendMessage(ChatColor.RED + "Donation failed - check your balance and the recipient's limit.");
            return;
        }
        if (!receiver.credit(amount)) {
            sender.credit(amount);
            from.sendMessage(ChatColor.RED + "Donation failed - the recipient cannot hold that much.");
            return;
        }
        from.sendMessage(ChatColor.GREEN + "Donated " + ChatColor.WHITE + format.format(amount)
                + ChatColor.GREEN + " to " + target.getName() + ".");
        target.sendMessage(ChatColor.GREEN + "You were donated " + ChatColor.WHITE + format.format(amount)
                + ChatColor.GREEN + " by " + from.getName() + "!");
        ActionBar.send(target, ChatColor.GOLD + "" + ChatColor.BOLD + format.format(amount)
                + ChatColor.GREEN + " donated to you by " + ChatColor.WHITE + from.getName());
        ledger.record(from.getUniqueId(), -amount, "donate", "to " + target.getName());
        ledger.record(target.getUniqueId(), amount, "donate", "from " + from.getName());
        sounds.donateSent(from);
        music.play(target);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player p : sender.getServer().getOnlinePlayers()) {
                if ((sender.equals(p) || sender.hasPermission("minted.donate.others")) && p.getName().toLowerCase().startsWith(prefix)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
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