package dev.minted.banknote;

import dev.minted.bank.BankAccount;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
     * Charges {@code amount} from the notes the player holds: genuine notes are
     * consumed largest-first until the price is covered, then the difference is
     * minted back as change. If the held notes cannot cover the price nothing is
     * taken.
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
