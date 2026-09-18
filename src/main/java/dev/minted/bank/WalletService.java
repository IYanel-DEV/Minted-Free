package dev.minted.bank;

import dev.minted.banknote.NoteInventory;
import dev.minted.wallet.WalletManager;

import org.bukkit.entity.Player;

/**
 * The wallet, in whichever form the server chose. In physical mode a player's
 * wallet <em>is</em> the signed banknotes in their inventory, so the balance is
 * recomputed from what they hold and money moves as real notes. In digital mode
 * it is an ordinary {@link EconomyService} balance, the classic behaviour.
 *
 * <p>Everything that reads or moves wallet money - {@code /balance}, {@code /pay},
 * shops, menus - goes through this class, so the physical/digital choice lives in
 * exactly one place and the rest of the plugin never branches on it.
 */
public final class WalletService {

    private final boolean physical;
    private final EconomyService walletEconomy;
    private final NoteInventory notes;
    private final WalletManager wallets;

    public WalletService(boolean physical, EconomyService walletEconomy, NoteInventory notes) {
        this(physical, walletEconomy, notes, null);
    }

    public WalletService(boolean physical, EconomyService walletEconomy, NoteInventory notes, WalletManager wallets) {
        this.physical = physical;
        this.walletEconomy = walletEconomy;
        this.notes = notes;
        this.wallets = wallets;
    }

    public boolean isPhysical() {
        return physical;
    }

    public boolean isReady() {
        return walletEconomy.isReady();
    }

    /** @return the player's purse, or null if their digital account is still loading */
    public Purse purseFor(Player player) {
        if (physical) {
            // A wallet item, when enabled, is part of the physical purse: the notes
            // inside count for /pay, shops and requests while the player holds it.
            if (wallets != null && wallets.isEnabled()) {
                return wallets.purse(player);
            }
            return Purse.physical(player, notes);
        }
        BankAccount account = walletEconomy.getCached(player.getUniqueId());
        return account == null ? null : Purse.digital(account);
    }

    public double balance(Player player) {
        Purse purse = purseFor(player);
        return purse == null ? 0 : purse.balance();
    }

    /**
     * Moves wallet money between two online players. Digital mode uses the atomic
     * account transfer; physical mode charges the sender's notes and pays the
     * same value to the receiver as cash.
     *
     * @return true on success; false if either side is unready or the sender is short
     */
    public boolean transfer(Player from, Player to, double amount) {
        if (from == null || to == null || amount <= 0) {
            return false;
        }
        if (!physical) {
            return walletEconomy.transfer(walletEconomy.getCached(from.getUniqueId()),
                    walletEconomy.getCached(to.getUniqueId()), amount);
        }
        Purse sender = purseFor(from);
        if (sender == null || !sender.charge(amount)) {
            return false;
        }
        purseFor(to).credit(amount);
        return true;
    }
}
