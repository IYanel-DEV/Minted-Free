package dev.minted.banknote;

import dev.minted.bank.MoneyFormat;
import dev.minted.compat.NotePayloadNbt;
import dev.minted.compat.ServerVersion;
import dev.minted.util.Reflection;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes a banknote's data onto a paper item.
 *
 * <p>The value is carried on a lore line the player can read, and the machine
 * payload sits on a second line as {@code MINTED:<denom>|<serial>|<signature>}.
 * The signature is a keyed hash of the denomination and serial, so a player who
 * edits the value to something richer produces a note that no longer verifies.
 * This is forgery resistance, not real cryptography; a copied intact note is
 * still valid by design.
 *
 * <p>This signed payload is what "registers" a note as money: {@link #read}
 * returns a value only when the data verifies, so a blank paper, a renamed
 * paper, or a note whose value was edited is simply not money and cannot be
 * redeemed or deposited. Since 0.5.1 the payload rides in hidden NBT and the
 * lore only shows the value; servers Minted cannot reach reflectively and notes
 * minted before the switch fall back to the legacy lore line.
 */
public final class BanknoteParser {

    private static final String MARKER = "MINTED";
    private static final String SECRET = "minted-banknote-v1";
    // Base for the per-denomination custom-model-data; callers add a denomination
    // index so resource-pack servers can skin $1/$5/... differently.
    private static final int MODEL_DATA_BASE = 7000;

    private final MoneyFormat format;
    private final ServerVersion version;

    public BanknoteParser(MoneyFormat format, ServerVersion version) {
        this.format = format;
        this.version = version;
    }

    public ItemStack write(ItemStack item, Banknote note) {
        return write(item, note, 0);
    }

    /**
     * Writes the note and skins it with {@code MODEL_DATA_BASE + modelIndex} so a
     * resource pack can give each denomination its own texture. The model data is
     * cosmetic only and applied through reflection, so it is silently skipped on
     * servers older than 1.14. The machine payload is hidden in NBT; only the
     * value stays visible on the lore.
     *
     * @return the stored note (a fresh stack when the payload went into NBT)
     */
    public ItemStack write(ItemStack item, Banknote note, int modelIndex) {
        ItemMeta meta = item.getItemMeta();
        // Big money reads as a cheque, not a wad of cash: anything above $1,000 is
        // a "Check", the small stuff stays a "Banknote". Cosmetic name only - the
        // value and signature are identical either way, so it never affects parsing.
        String kind = note.getDenomination() > 1000 ? "Check" : "Banknote";
        meta.setDisplayName(ChatColor.GREEN + kind + " " + ChatColor.GRAY + "("
                + format.format(note.getDenomination()) + ")");

        List<String> lore = new ArrayList<String>(1);
        lore.add(ChatColor.GRAY + "Value: " + ChatColor.WHITE + format.format(note.getDenomination()));
        meta.setLore(lore);
        item.setItemMeta(meta);

        applyModelData(meta, MODEL_DATA_BASE + modelIndex);
        item.setItemMeta(meta);

        String payload = payload(note);
        ItemStack stored = NotePayloadNbt.write(version, item, payload);
        if (stored != null) {
            return stored;
        }

        // NMS unreachable on this server: keep the machine line on the lore so
        // the note still verifies (older servers see the code again).
        ItemStack legacy = item.clone();
        ItemMeta legacyMeta = legacy.getItemMeta();
        List<String> legacyLore = new ArrayList<String>(legacyMeta.getLore());
        legacyLore.add(ChatColor.DARK_GRAY + MARKER + ":" + payload);
        legacyMeta.setLore(legacyLore);
        legacy.setItemMeta(legacyMeta);
        return legacy;
    }

    /** @return the note if the item is a genuine, verified banknote, else null */
    public Banknote read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String hidden = NotePayloadNbt.read(version, item);
        if (hidden != null) {
            return parse(hidden);
        }
        if (!item.getItemMeta().hasLore()) {
            return null;
        }
        for (String line : item.getItemMeta().getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (stripped.startsWith(MARKER + ":")) {
                return parse(stripped.substring(MARKER.length() + 1));
            }
        }
        return null;
    }

    private Banknote parse(String payload) {
        String[] parts = payload.split("\\|");
        if (parts.length != 3) {
            return null;
        }
        double denomination;
        try {
            denomination = Double.parseDouble(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }
        String serial = parts[1];
        String expected = sign(denomination, serial);
        return expected.equals(parts[2]) ? new Banknote(denomination, serial) : null;
    }

    private String payload(Banknote note) {
        return note.getDenomination() + "|" + note.getSerial() + "|"
                + sign(note.getDenomination(), note.getSerial());
    }

    private String sign(double denomination, String serial) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((SECRET + "|" + denomination + "|" + serial).getBytes("UTF-8"));
            return toHex(hash).substring(0, 24);
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // custom-model-data arrived in 1.14; reach it by reflection so resource-pack
    // servers can skin notes while older servers just ignore it.
    private void applyModelData(ItemMeta meta, int modelData) {
        try {
            Method setter = Reflection.getMethod(meta.getClass(), "setCustomModelData", int.class);
            Reflection.invoke(setter, meta, Integer.valueOf(modelData));
        } catch (Reflection.ReflectionException ignored) {
            // Pre-1.14 server; nothing to skin.
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
