package dev.minted.vip;

import java.util.UUID;

/**
 * One row of the merged VIP roster: either a stored list entry (admin-managed
 * and removable from the dashboard) or a player who counts purely through the
 * {@code minted.vip} permission - those are marked, and stay under the
 * permission plugin's control because Minted cannot revoke a node itself.
 */
public final class VipMember {

    private final UUID uuid;
    private final String name;
    private final long added;
    private final String by;
    private final boolean permission;

    private VipMember(UUID uuid, String name, long added, String by, boolean permission) {
        this.uuid = uuid;
        this.name = name;
        this.added = added;
        this.by = by;
        this.permission = permission;
    }

    /** A VIP from the stored list; carries the real added-at and author. */
    public static VipMember stored(VipEntry entry) {
        return new VipMember(entry.getUuid(), entry.getName(), entry.getAdded(), entry.getBy(), false);
    }

    /** A VIP who counts only through the minted.vip permission. */
    public static VipMember permission(UUID uuid, String name) {
        return new VipMember(uuid, name, 0L, "permission", true);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    /** Epoch millis of the list entry; 0 for permission-based members. */
    public long getAdded() {
        return added;
    }

    /** Who added a list entry; "permission" for permission-based members. */
    public String getBy() {
        return by;
    }

    /** True when this member counts only through the minted.vip permission. */
    public boolean isPermission() {
        return permission;
    }
}