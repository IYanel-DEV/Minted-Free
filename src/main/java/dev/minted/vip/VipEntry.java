package dev.minted.vip;

import org.bukkit.configuration.ConfigurationSection;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One line of the VIP list: the player, when they were made a VIP and by whom.
 * The name is stored next to the uuid because the VIP perk - the
 * {@code /<username>} shop command - is named after it, and it is refreshed on
 * join so a rename follows the player.
 */
public final class VipEntry {

    /**
     * A name that is safe to use as a server command label. Real Minecraft
     * names always match, which is why they can double as the shop command.
     */
    private static final Pattern COMMAND_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final UUID uuid;
    private String name;
    private final long added;
    private final String by;

    public VipEntry(UUID uuid, String name, long added, String by) {
        this.uuid = uuid;
        this.name = name;
        this.added = added;
        this.by = by == null || by.isEmpty() ? "Console" : by;
    }

    /** Whether this name can be registered as a command label. */
    public static boolean isCommandName(String name) {
        return name != null && COMMAND_NAME.matcher(name).matches();
    }

    /** Rebuilds an entry from its {@code vips.yml} section, or null if broken. */
    public static VipEntry stored(ConfigurationSection section) {
        String id = section.getString("uuid");
        String name = section.getString("name");
        if (id == null || name == null || name.isEmpty()) {
            return null;
        }
        try {
            return new VipEntry(UUID.fromString(id), name,
                    section.getLong("added"), section.getString("by"));
        } catch (IllegalArgumentException badId) {
            return null;
        }
    }

    public void storeTo(ConfigurationSection section) {
        section.set("uuid", uuid.toString());
        section.set("name", name);
        section.set("added", added);
        section.set("by", by);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getAdded() {
        return added;
    }

    public String getBy() {
        return by;
    }
}