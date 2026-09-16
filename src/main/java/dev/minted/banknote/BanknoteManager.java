package dev.minted.banknote;

import dev.minted.bank.BankAccount;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bridges banknotes and balances. Paper is the material: it is the one item
 * that has existed under the same name from 1.8 all the way to 1.26, so a note
 * minted on any supported server is recognisable on any other.
 *
 * <p>Only signed notes are money. A note counts iff its {@code MINTED:...} lore
 * line verifies in {@link BanknoteParser}; blank paper, renamed paper and edited
 * notes simply do not read back and can be neither redeemed nor deposited. That
 * signature is the registration - there is no separate ledger of valid serials.
 */
public final class BanknoteManager {

    // Notes divide into these values largest-first; an odd remainder becomes a
    // single plain note. Sorted descending once at construction.
    private final double[] denominations;
    private final BanknoteParser parser;

    public BanknoteManager(BanknoteParser parser, List<Double> denominations) {
        this.parser = parser;
        this.denominations = sortedDescending(denominations);
    }

    /**
     * Withdraws {@code amount} from the account and mints physical notes for it.
     *
     * <p>The amount is split into the configured denominations, equal notes are
     * combined into stacks (never past {@link Material#PAPER}'s max stack size),
     * and any leftover no denomination divides becomes one final plain note. The
     * digital balance is debited once, first; if it cannot cover the amount the
     * account is left untouched and an empty list is returned (no partial mint).
     *
     * @return the note stacks, or an empty list if the balance was short
     */
    public List<ItemStack> mint(BankAccount account, double amount) {
        if (amount <= 0 || !account.withdraw(amount)) {
            return new ArrayList<ItemStack>();
        }
        return mintNotes(amount);
    }

    /**
     * Mints physical notes for {@code amount} without touching any balance. This
     * is money created at the point of payment - a shop paying a seller, change
     * handed back on a purchase - where there is no digital account to debit.
     */
    public List<ItemStack> mintNotes(double amount) {
        List<ItemStack> notes = new ArrayList<ItemStack>();
        if (amount <= 0) {
            return notes;
        }
        double remaining = amount;
        for (int i = 0; i < denominations.length; i++) {
            double denom = denominations[i];
            // How many whole notes of this denomination the remainder holds. This
            // MUST stay a long, not an int: a payout far above the largest note
            // (e.g. 10,000 trillion against a $1 note) yields a count past
            // Integer.MAX_VALUE, and an int cast there silently truncates and mints
            // the WRONG amount of money. Do not "simplify" this back to int.
            long count = (long) Math.floor((remaining + EPSILON) / denom);
            if (count <= 0) {
                continue;
            }
            remaining -= denom * (double) count;
            mintDenomination(notes, denom, i, count);
        }
        if (remaining > EPSILON) {
            // Leftover odd value: one plain note, its own denomination.
            stack(notes, round(remaining), 0, 1);
        }
        return notes;
    }

    /**
     * Verifies the note and, if genuine, credits its value to the account. The
     * caller consumes the item only when this returns true.
     *
     * @return true if the note was valid and its value was banked
     */
    public boolean redeem(BankAccount account, ItemStack item) {
        Banknote note = parser.read(item);
        return note != null && account.deposit(note.getDenomination());
    }

    public boolean isBanknote(ItemStack item) {
        return parser.read(item) != null;
    }

    /** @return the note's face value, or 0 if the item is not a genuine note */
    public double faceValue(ItemStack item) {
        Banknote note = parser.read(item);
        return note != null ? note.getDenomination() : 0;
    }

    /**
     * Hands the note stacks to the player, dropping any that do not fit at their
     * feet so physical cash is never destroyed.
     *
     * @return true when some notes had to drop
     */
    public boolean give(Player player, List<ItemStack> notes) {
        boolean dropped = false;
        for (ItemStack note : notes) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(note);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                dropped = true;
            }
        }
        return dropped;
    }

    // One denomination's worth of notes. Small counts become ordinary stacks of
    // that denomination. But a single denomination sometimes has to cover a payout
    // far larger than itself (the largest note against a billion-dollar amount) -
    // that is an inventory flood, and a value no int could hold. When the count
    // would spill past a single stack we instead bundle the whole chunk into ONE
    // larger ad-hoc note: the parser signs any face value, so a $500,000,000,000
    // note is exactly as real as a $1 one. This is what keeps the total stack
    // count small and bounded no matter how huge the amount - do not remove it.
    private void mintDenomination(List<ItemStack> out, double denom, int modelIndex, long count) {
        if (count > Material.PAPER.getMaxStackSize()) {
            stack(out, round(denom * (double) count), modelIndex, 1);
            return;
        }
        // count fits in one stack (<= max stack size), so this cast cannot overflow.
        stack(out, denom, modelIndex, (int) count);
    }

    // Mints `count` notes of one denomination into as few stacks as the max
    // stack size allows. Notes in a stack share a serial, so the stack collapses
    // in the inventory the way real change does; distinct stacks get new serials.
    private void stack(List<ItemStack> out, double denom, int modelIndex, int count) {
        int max = Material.PAPER.getMaxStackSize();
        int remaining = count;
        while (remaining > 0) {
            int size = Math.min(remaining, max);
            ItemStack note = new ItemStack(Material.PAPER);
            note = parser.write(note, new Banknote(denom, UUID.randomUUID().toString()), modelIndex);
            note.setAmount(size);
            out.add(note);
            remaining -= size;
        }
    }

    private static double[] sortedDescending(List<Double> values) {
        double[] array = new double[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        Arrays.sort(array);
        for (int i = 0; i < array.length / 2; i++) {
            double swap = array[i];
            array[i] = array[array.length - 1 - i];
            array[array.length - 1 - i] = swap;
        }
        return array;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // Guards the integer division against binary-float drift (e.g. 0.1 + 0.2).
    private static final double EPSILON = 1.0E-6;
}
