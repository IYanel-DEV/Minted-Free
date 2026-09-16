package dev.minted.banknote;

import dev.minted.bank.MoneyFormat;
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
 */
public final class BanknoteParser {

    private static final String MARKER = "MINTED";
    private static final String SECRET = "minted-banknote-v1";
    private static final int MODEL_DATA = 7001;

    private final MoneyFormat format;

    public BanknoteParser(MoneyFormat format) {
        this.format = format;
    }

    public void write(ItemStack item, Banknote note) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Banknote " + ChatColor.GRAY + "("
                + format.format(note.getDenomination()) + ")");

        List<String> lore = new ArrayList<String>(2);
        lore.add(ChatColor.GRAY + "Value: " + ChatColor.WHITE + format.format(note.getDenomination()));
        lore.add(ChatColor.DARK_GRAY + MARKER + ":" + payload(note));
        meta.setLore(lore);

        applyModelData(meta);
        item.setItemMeta(meta);
    }

    /** @return the note if the item is a genuine, verified banknote, else null */
    public Banknote read(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
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
    private void applyModelData(ItemMeta meta) {
        try {
            Method setter = Reflection.getMethod(meta.getClass(), "setCustomModelData", int.class);
            Reflection.invoke(setter, meta, Integer.valueOf(MODEL_DATA));
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
