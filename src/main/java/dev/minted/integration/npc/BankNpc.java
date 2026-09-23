package dev.minted.integration.npc;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.UUID;

/**
 * One placed bank teller: where it stands, who it pretends to be, and what
 * skin it wears.
 *
 * <p>The entity id is assigned at runtime (see {@link NpcManager}) and is
 * deliberately not persisted - every restart registers the tellers against
 * fresh ids so they never collide with the server's own entities. The stable
 * persistence key is the UUID, which is also what the fake player entry in
 * each viewer's tab list is keyed by.
 */
public final class BankNpc {

    private final UUID uuid;
    private final String name;
    private final String display;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final String skinValue;
    private final String skinSignature;
    private int entityId;

    public BankNpc(UUID uuid, String name, String world, double x, double y, double z, float yaw,
                   String skinValue, String skinSignature, String display) {
        this.uuid = uuid;
        this.name = name;
        this.display = display;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.skinValue = skinValue;
        this.skinSignature = skinSignature;
    }

    /** Loads a teller from one section of {@code npcs.yml}, or null when broken. */
    public static BankNpc stored(ConfigurationSection section) {
        try {
            UUID uuid = UUID.fromString(section.getString("uuid", ""));
            String name = section.getString("name", null);
            String world = section.getString("world", null);
            if (name == null || world == null) {
                return null;
            }
            return new BankNpc(uuid, name, world,
                    section.getDouble("x", 0), section.getDouble("y", 0), section.getDouble("z", 0),
                    (float) section.getDouble("yaw", 0),
                    section.getString("skin-value", ""),
                    section.getString("skin-signature", null),
                    section.getString("display-name", name));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    public void storeTo(ConfigurationSection section) {
        section.set("uuid", uuid.toString());
        section.set("name", name);
        section.set("display-name", display);
        section.set("world", world);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", yaw);
        section.set("skin-value", skinValue);
        section.set("skin-signature", skinSignature);
    }

    /** @return a copy standing at the given spot, used by {@code /minted npc here}. */
    public BankNpc at(String newWorld, double newX, double newY, double newZ, float newYaw) {
        BankNpc copy = new BankNpc(uuid, name, newWorld, newX, newY, newZ, newYaw, skinValue, skinSignature, display);
        copy.entityId = entityId;
        return copy;
    }

    /** @return a copy wearing the given skin, used after a Mojang skin fetch. */
    public BankNpc withSkin(String newValue, String newSignature) {
        BankNpc copy = new BankNpc(uuid, name, world, x, y, z, yaw, newValue, newSignature, display);
        copy.entityId = entityId;
        return copy;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    /**
     * @return the on-screen name: the configured display with {@code &} colour
     *         codes translated, e.g. {@code "&eBanker"} renders as yellow
     *         "Banker" in the name tag and tab list
     */
    public String display() {
        return ChatColor.translateAlternateColorCodes('&', display);
    }

    public String world() {
        return world;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public String skinValue() {
        return skinValue;
    }

    public String skinSignature() {
        return skinSignature;
    }

    public int entityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }
}