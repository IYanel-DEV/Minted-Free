package dev.minted.shop;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * The one place a shop item is turned into text and back.
 *
 * <p>An {@link ItemStack} is a {@code ConfigurationSerializable}, so Bukkit's
 * own object stream captures everything the server knows about it - enchants,
 * lore, book pages, potion effects, leather colour, spawner type, fireworks -
 * without this class hand-writing any NBT. The byte form is Base64-encoded for
 * storage in a text column. Encoding and decoding both run on the main thread
 * (writes come from menu clicks, loads happen at startup), so no version shim is
 * needed: the same server that wrote a row reads it back.
 *
 * <p>{@link #decode(String)} throws {@link DecodeException} rather than
 * returning null so a corrupt row can be skipped and logged without guessing
 * why it failed.
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemStack item) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes);
            try {
                out.writeObject(item);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialize a shop item", e);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static ItemStack decode(String data) {
        try {
            byte[] raw = Base64.getDecoder().decode(data);
            BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(raw));
            try {
                return (ItemStack) in.readObject();
            } finally {
                in.close();
            }
        } catch (IOException | ClassNotFoundException | ClassCastException | IllegalArgumentException e) {
            throw new DecodeException(e);
        }
    }

    /** Thrown when a stored item string cannot be read back into an ItemStack. */
    public static final class DecodeException extends RuntimeException {
        DecodeException(Throwable cause) {
            super(cause);
        }
    }
}
