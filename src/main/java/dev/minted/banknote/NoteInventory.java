package dev.minted.banknote;

import dev.minted.bank.BankAccount;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All the ways money as physical banknotes touches a player's inventory: reading
 * the wallet balance off the notes they hold, charging a purchase from those
 * notes, paying them cash, and moving notes into the bank.
 *
 * <p>This is the single money-in / money-out surface for the physical economy.
 * A note only counts if {@link BanknoteManager} verifies its signature, so lost
 * notes are lost money and forged paper is worth nothing - the balance is always
 * recomputed from what is actually in the inventory, never stored.
 */
public final class NoteInventory {

    private static final double EPSILON = 1.0E-6;
    // Ceiling (in cents) for the exact-payment subset search - enough for any
    // sensible purchase, while keeping the search bounded.
    private static final long EXACT_MATCH_MAX_CENTS = 100_000_000L;

    private final BanknoteManager notes;

    public NoteInventory(BanknoteManager notes) {
        this.notes = notes;
    }

    /** Sum of every genuine note in the player's 36 inventory slots. */
    public double value(Player player) {
        double total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            total += notes.faceValue(stack) * amount(stack);
        }
        return total;
    }

    /** Pays the player {@code amount} in freshly minted cash, dropping any overflow. */
    public void credit(Player player, double amount) {
        notes.give(player, notes.mintNotes(amount));
    }

    /**
     * Charges {@code amount} from the notes the player holds. First the wallet
     * looks for an exact combination of notes summing to the price, so paying
     * with a $12 wad for a $12 item costs no change at all. If no exact set
     * exists, genuine notes are consumed largest-first until the price is
     * covered and the difference is minted back as change. If the held notes
     * cannot cover the price nothing is taken.
     *
     * @return true when the charge went through
     */
    public boolean charge(Player player, double amount) {
        if (amount <= EPSILON) {
            return true;
        }
        Inventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        List<Integer> slots = noteSlots(contents);
        if (total(contents, slots) + EPSILON < amount) {
            return false;
        }

        int[] exact = exactMatch(contents, slots, toCents(amount));
        if (exact != null) {
            applyTake(inventory, contents, exact);
            return true;
        }

        double collected = 0;
        int[] take = new int[contents.length];
        for (int slot : slots) {
            double face = notes.faceValue(contents[slot]);
            int held = contents[slot].getAmount();
            while (held > 0 && collected + EPSILON < amount) {
                collected += face;
                held--;
                take[slot]++;
            }
            if (collected + EPSILON >= amount) {
                break;
            }
        }
        applyTake(inventory, contents, take);

        double change = collected - amount;
        if (change > EPSILON) {
            credit(player, change);
        }
        return true;
    }

    /**
     * Looks for a set of held notes that sums to exactly {@code amountCents}.
     * Prefers the largest face values so as few slots as possible are touched.
     *
     * @return a per-slot {@code take} array, or null when no exact set exists
     */
    private int[] exactMatch(ItemStack[] contents, List<Integer> slots, long amountCents) {
        // The subset search is bounded in cents; enormous purchases skip it and
        // let the greedy take-with-change path mint proper notes instead.
        if (amountCents <= 0 || amountCents > EXACT_MATCH_MAX_CENTS) {
            return null;
        }
        Map<Long, List<Integer>> byValue = new HashMap<Long, List<Integer>>();
        for (int slot : slots) {
            long cents = toCents(notes.faceValue(contents[slot]));
            if (cents <= 0) {
                continue;
            }
            List<Integer> members = byValue.get(cents);
            if (members == null) {
                members = new ArrayList<Integer>();
                byValue.put(cents, members);
            }
            members.add(slot);
        }
        if (byValue.isEmpty()) {
            return null;
        }
        List<Long> values = new ArrayList<Long>(byValue.keySet());
        Collections.sort(values, new Comparator<Long>() {
            @Override
            public int compare(Long a, Long b) {
                return Long.compare(b, a);
            }
        });
        long[] available = new long[values.size()];
        for (int i = 0; i < values.size(); i++) {
            long total = 0;
            for (int slot : byValue.get(values.get(i))) {
                total += contents[slot].getAmount();
            }
            available[i] = total;
        }
        long[] used = new long[values.size()];
        if (!findExact(values, available, used, 0, amountCents, new HashSet<Long>())) {
            return null;
        }
        int[] take = new int[contents.length];
        for (int i = 0; i < values.size(); i++) {
            long remaining = used[i];
            for (int slot : byValue.get(values.get(i))) {
                int held = contents[slot].getAmount();
                int consume = (int) Math.min(remaining, held);
                take[slot] = consume;
                remaining -= consume;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        return take;
    }

    // Recursive subset search over distinct face values, descending. Only exact
    // sums matter, so each value can contribute at most remaining/value notes;
    // the memo clips the exponential blow-up when the player holds many values.
    private boolean findExact(List<Long> values, long[] available, long[] used,
                              int index, long remainingCents, Set<Long> memo) {
        if (remainingCents == 0) {
            return true;
        }
        if (index >= values.size() || remainingCents < 0) {
            return false;
        }
        long key = ((long) index << 32) | remainingCents;
        if (memo.contains(key)) {
            return false;
        }
        long value = values.get(index);
        long cap = Math.min(available[index], remainingCents / value);
        for (long count = cap; count >= 0; count--) {
            used[index] = count;
            if (findExact(values, available, used, index + 1,
                    remainingCents - count * value, memo)) {
                return true;
            }
        }
        used[index] = 0;
        memo.add(key);
        return false;
    }

    private static long toCents(double value) {
        return Math.round(value * 100.0);
    }

    /**
     * Banks every genuine note in the inventory, honouring the bank cap: notes
     * are deposited one at a time so a partial fit banks what it can and leaves
     * the rest as items.
     *
     * @return {amount banked, value still in inventory}
     */
    public double[] depositInventory(Player player, BankAccount account) {
        Inventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        double banked = 0;
        double left = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            double face = notes.faceValue(stack);
            if (face <= 0) {
                continue;
            }
            int deposited = bankStack(account, face, stack.getAmount());
            banked += face * deposited;
            int remaining = stack.getAmount() - deposited;
            if (deposited > 0) {
                inventory.setItem(slot, remaining > 0 ? sized(stack, remaining) : null);
            }
            left += face * remaining;
        }
        return new double[] {banked, left};
    }

    /**
     * Banks the stack in the player's main hand, honouring the bank cap the same
     * way {@link #depositInventory} does but touching only that one stack.
     *
     * @return {amount banked, value left in hand}
     */
    public double[] depositHeld(Player player, BankAccount account) {
        ItemStack held = player.getInventory().getItemInHand();
        double face = notes.faceValue(held);
        if (face <= 0) {
            return new double[] {0, 0};
        }
        int deposited = bankStack(account, face, held.getAmount());
        int remaining = held.getAmount() - deposited;
        if (deposited > 0) {
            player.getInventory().setItemInHand(remaining > 0 ? sized(held, remaining) : null);
        }
        return new double[] {face * deposited, face * remaining};
    }

    // Deposits up to `count` notes of one face value, stopping the moment the cap
    // refuses the next one. Returns how many actually went in.
    private int bankStack(BankAccount account, double face, int count) {
        int deposited = 0;
        while (deposited < count && account.deposit(face)) {
            deposited++;
        }
        return deposited;
    }

    private List<Integer> noteSlots(final ItemStack[] contents) {
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < contents.length; i++) {
            if (notes.faceValue(contents[i]) > 0) {
                slots.add(i);
            }
        }
        Collections.sort(slots, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Double.compare(notes.faceValue(contents[b]), notes.faceValue(contents[a]));
            }
        });
        return slots;
    }

    private double total(ItemStack[] contents, List<Integer> slots) {
        double sum = 0;
        for (int slot : slots) {
            sum += notes.faceValue(contents[slot]) * contents[slot].getAmount();
        }
        return sum;
    }

    private void applyTake(Inventory inventory, ItemStack[] contents, int[] take) {
        for (int slot = 0; slot < take.length; slot++) {
            if (take[slot] == 0) {
                continue;
            }
            int remaining = contents[slot].getAmount() - take[slot];
            inventory.setItem(slot, remaining > 0 ? sized(contents[slot], remaining) : null);
        }
    }

    private int amount(ItemStack stack) {
        return stack == null ? 0 : stack.getAmount();
    }

    private ItemStack sized(ItemStack stack, int amount) {
        ItemStack copy = stack.clone();
        copy.setAmount(amount);
        return copy;
    }
}
