package dev.minted.shop;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * The same inventory-scan-and-remove and give-or-drop semantics {@code Trade}
 * uses, shared with the community {@link Market} so a listing deposit or a
 * buy-back matches the global shop's behaviour exactly. Deliberately mirrors
 * Trade's private helpers rather than refactoring that frozen path.
 */
public final class Inventories {

    private Inventories() {
    }

    public static int count(Player player, ItemStack template) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && ShopItem.sameStock(stack, template)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Removes up to {@code quantity} units matching the template; returns how many were removed. */
    public static int remove(Player player, ItemStack template, int quantity) {
        Inventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        int remaining = quantity;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !ShopItem.sameStock(stack, template)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - taken);
            inventory.setItem(slot, stack.getAmount() > 0 ? stack : null);
            remaining -= taken;
        }
        return quantity - remaining;
    }

    /** Gives the stack, dropping any overflow at the player's feet; true if anything dropped. */
    public static boolean giveOrDrop(Player player, ItemStack template, int quantity) {
        int max = template.getMaxStackSize();
        int remaining = quantity;
        boolean overflowed = false;
        while (remaining > 0) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(remaining, max));
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                overflowed = true;
            }
            remaining -= stack.getAmount();
        }
        return overflowed;
    }
}
