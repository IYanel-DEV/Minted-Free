package dev.minted.wallet;

import dev.minted.bank.MoneyFormat;
import dev.minted.bank.Purse;
import dev.minted.banknote.BanknoteManager;
import dev.minted.banknote.NoteInventory;
import dev.minted.compat.Glass;
import dev.minted.compat.NotePayloadNbt;
import dev.minted.compat.ServerVersion;
import dev.minted.gui.theme.Design;
import dev.minted.lang.Messages;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Map;

/**
 * The Wallet: an item that stores banknotes and checks inside itself, saving
 * inventory space while the money still stays spendable.
 *
 * <p>A wallet is recognised purely by its {@code minted-wallet} NBT tag - a
 * plain leather or a renamed one is not a wallet. Contents live as a compact
 * {@code cents:count,...} string in that tag, so nothing is stored on the
 * server and a wallet is fully portable. When enabled the held wallet's notes
 * count towards the physical economy through {@link #purse}, so shops, /pay
 * and money requests draw on them automatically.
 */
public final class WalletManager {

    public static final String WALLET_TAG = "minted-wallet";
    // Model-data slot the resource pack skins the wallet with (notes start at
    // 7000 in the banknote parser; this sits far above them).
    private static final int WALLET_MODEL = 7999;

    private final ServerVersion version;
    private final NoteInventory notes;
    private final BanknoteManager banknotes;
    private final MoneyFormat format;
    private final Messages messages;
    private final boolean enabled;
    private final Material material;
    private final Design design;

    public WalletManager(ServerVersion version, NoteInventory notes, BanknoteManager banknotes,
                         MoneyFormat format, Messages messages, boolean enabled, Material material) {
        this.version = version;
        this.notes = notes;
        this.banknotes = banknotes;
        this.format = format;
        this.messages = messages;
        this.enabled = enabled;
        this.material = material;
        this.design = new Design(new Glass(version));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public MoneyFormat format() {
        return format;
    }

    public Messages messages() {
        return messages;
    }

    Design design() {
        return design;
    }

    public boolean isWallet(ItemStack item) {
        return item != null && NotePayloadNbt.read(version, item, WALLET_TAG) != null;
    }

    /** @return the wallet held in the main hand, or null */
    public ItemStack held(Player player) {
        ItemStack hand = player.getInventory().getItemInHand();
        return isWallet(hand) ? hand : null;
    }

    public WalletContents contents(ItemStack wallet) {
        String data = NotePayloadNbt.read(version, wallet, WALLET_TAG);
        return WalletContents.decode(data);
    }

    /** @return the value of the notes inside the held wallet, or 0 */
    public double value(Player player) {
        ItemStack hand = held(player);
        if (hand == null) {
            return 0;
        }
        return contents(hand).valueCents() / 100.0;
    }

    /**
     * A fresh, empty wallet item. Returns null on the rare server whose NMS
     * cannot be reached, where a wallet cannot store anything.
     */
    public ItemStack create() {
        ItemStack wallet = new ItemStack(material);
        ItemMeta meta = wallet.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Wallet");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Stores banknotes and checks.",
                ChatColor.GRAY + "Hold it and spend - the cash inside counts.",
                "", ChatColor.GRAY + "Right-click to open."));
        wallet.setItemMeta(meta);
        return NotePayloadNbt.write(version, wallet, WALLET_TAG, "", WALLET_MODEL);
    }

    /** Persists {@code contents} into the held wallet. False when nothing is held. */
    public boolean save(Player player, WalletContents contents) {
        ItemStack hand = player.getInventory().getItemInHand();
        if (!isWallet(hand)) {
            return false;
        }
        ItemStack stored = NotePayloadNbt.write(version, hand, WALLET_TAG, contents.encode(), WALLET_MODEL);
        if (stored == null) {
            messages.send(player, "wallet.cannot-store");
            return false;
        }
        stored.setAmount(hand.getAmount());
        player.getInventory().setItemInHand(stored);
        return true;
    }

    /** Settles the whole physical balance - inventory notes then the held wallet. */
    public Purse purse(final Player player) {
        return new Purse() {
            @Override
            public double balance() {
                return notes.value(player) + value(player);
            }

            @Override
            public boolean charge(double amount) {
                return chargeWallet(player, amount);
            }

            @Override
            public boolean credit(double amount) {
                notes.credit(player, amount);
                return true;
            }
        };
    }

    /**
     * Charges {@code amount} from inventory notes first and, when they run out,
     * from the notes inside the held wallet. Any overshoot on the last wallet
     * note is minted back as ordinary change. Nothing is taken when the total is
     * short.
     */
    public boolean chargeWallet(Player player, double amount) {
        if (amount <= 0) {
            return true;
        }
        double inventory = notes.value(player);
        double wallet = value(player);
        if (inventory + wallet + 1.0E-6 < amount) {
            return false;
        }
        if (inventory >= amount) {
            return notes.charge(player, amount);
        }
        if (inventory > 0) {
            notes.charge(player, inventory);
        }
        double stillNeeded = amount - (inventory - notes.value(player));
        if (stillNeeded <= 1.0E-6) {
            return true;
        }
        ItemStack hand = held(player);
        if (hand == null) {
            return false;
        }
        WalletContents contents = contents(hand);
        WalletContents.Result result = contents.take(Math.round(stillNeeded * 100.0));
        if (!result.covered) {
            return false;
        }
        save(player, contents);
        if (result.changeCents > 0) {
            notes.credit(player, result.changeCents / 100.0);
        }
        return true;
    }

    /**
     * Moves every genuine banknote from the inventory into the held wallet,
     * honouring the wallet's caps.
     *
     * @return {value banked, value left in the inventory}
     */
    public double[] depositInventory(Player player) {
        ItemStack hand = held(player);
        if (hand == null) {
            return new double[] {0, 0};
        }
        WalletContents contents = contents(hand);
        Inventory inventory = player.getInventory();
        ItemStack[] items = inventory.getContents();
        double banked = 0;
        double left = 0;
        for (int slot = 0; slot < items.length; slot++) {
            ItemStack stack = items[slot];
            double face = banknotes.faceValue(stack);
            if (face <= 0) {
                continue;
            }
            long cents = Math.round(face * 100.0);
            int accepted = contents.add(cents, stack.getAmount());
            banked += face * accepted;
            int remaining = stack.getAmount() - accepted;
            if (accepted > 0) {
                inventory.setItem(slot, remaining > 0 ? sized(stack, remaining) : null);
            }
            left += face * remaining;
        }
        save(player, contents);
        return new double[] {banked, left};
    }

    /** Takes {@code count} notes of one face value out of the held wallet. */
    public Draw withdraw(Player player, long cents, int count) {
        ItemStack hand = held(player);
        if (hand == null) {
            return Draw.none();
        }
        WalletContents contents = contents(hand);
        int removed = contents.remove(cents, count);
        if (removed <= 0) {
            return Draw.none();
        }
        save(player, contents);
        boolean dropped = banknotes.give(player, banknotes.mint(cents / 100.0, removed));
        return new Draw(removed, cents / 100.0, dropped);
    }

    /** Empties the held wallet back into the inventory, dropping any overflow. */
    public Draw empty(Player player) {
        ItemStack hand = held(player);
        if (hand == null) {
            return Draw.none();
        }
        WalletContents contents = contents(hand);
        int count = contents.noteCount();
        if (count == 0) {
            return Draw.none();
        }
        Map<Long, Integer> snapshot = contents.snapshot();
        boolean dropped = false;
        for (Map.Entry<Long, Integer> entry : snapshot.entrySet()) {
            dropped |= banknotes.give(player, banknotes.mint(entry.getKey() / 100.0, entry.getValue()));
        }
        save(player, new WalletContents());
        return new Draw(count, contents.valueCents() / 100.0, dropped);
    }

    /**
     * /wallet entry point: opens the held wallet, else moves an owned one to the
     * hand, else hands out a fresh wallet. Refuses only when wallets are off.
     */
    public void open(Player player) {
        if (held(player) != null) {
            new WalletMenu(this, player).open(player);
            return;
        }
        if (!enabled) {
            messages.send(player, "wallet.disabled");
            return;
        }
        int slot = findWalletSlot(player);
        if (slot >= 0) {
            ItemStack owned = player.getInventory().getItem(slot);
            player.getInventory().setItem(slot, null);
            putInHand(player, owned);
            new WalletMenu(this, player).open(player);
            return;
        }
        ItemStack wallet = create();
        if (wallet == null) {
            messages.send(player, "wallet.cannot-store");
            return;
        }
        boolean dropped = putInHand(player, wallet);
        messages.send(player, dropped ? "wallet.give-full" : "wallet.give");
        new WalletMenu(this, player).open(player);
    }

    private boolean putInHand(Player player, ItemStack wallet) {
        ItemStack hand = player.getInventory().getItemInHand();
        boolean dropped = false;
        if (hand != null && hand.getType() != Material.AIR) {
            for (ItemStack leftover : player.getInventory().addItem(hand).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                dropped = true;
            }
        }
        player.getInventory().setItemInHand(wallet);
        return dropped;
    }

    // First player-inventory slot that holds a wallet, or -1. The main hand is
    // checked separately, so -1 here genuinely means "has no wallet at all".
    private int findWalletSlot(Player player) {
        ItemStack[] items = player.getInventory().getContents();
        for (int slot = 0; slot < items.length; slot++) {
            if (isWallet(items[slot])) {
                return slot;
            }
        }
        return -1;
    }

    private ItemStack sized(ItemStack stack, int amount) {
        ItemStack copy = stack.clone();
        copy.setAmount(amount);
        return copy;
    }

    /** What a withdrawal moved out of the wallet. */
    public static final class Draw {
        public final int count;
        public final double value;
        public final boolean dropped;

        private Draw(int count, double value, boolean dropped) {
            this.count = count;
            this.value = value;
            this.dropped = dropped;
        }

        private static Draw none() {
            return new Draw(0, 0, false);
        }
    }
}