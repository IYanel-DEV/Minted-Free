package dev.minted.vip;

import dev.minted.backend.NamesDao;
import dev.minted.lang.Messages;
import dev.minted.shop.PlayerShopHooks;
import dev.minted.shop.Shop;
import dev.minted.shop.ShopContext;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The admin-managed VIP list behind {@code /minted vip} and the dashboard's
 * VIP page, persisted in {@code vips.yml}. The VIP perk - the automatic
 * {@code /<username>} shop command - is derived from this list combined with
 * shop ownership: adding or removing a VIP, creating or deleting their shop,
 * a rename or a reload all funnel through {@link #syncAliases()}, so the
 * commands on the server always match the truth.
 */
public final class VipService implements PlayerShopHooks {

    /** The permission that counts as VIP alongside the stored list. */
    public static final String PERMISSION = "minted.vip";

    private final Plugin plugin;
    private final VipStore store;
    private final NamesDao names;
    private final Messages messages;
    private final ShopContext ctx;

    /** Keyed by uuid; the display order comes from {@link #roster()}. */
    private final Map<UUID, VipEntry> entries = new LinkedHashMap<UUID, VipEntry>();

    /**
     * Players who count through {@link #PERMISSION}: uuid to last-known name.
     * Permissions of offline players are not readable through the API, so the
     * state seen on join/quit is what restarts resume from; it self-corrects
     * the moment the player is online again.
     */
    private final Map<UUID, String> permissionHolders = new LinkedHashMap<UUID, String>();

    private VipShopCommands aliases;

    public VipService(Plugin plugin, VipStore store, NamesDao names, Messages messages, ShopContext ctx) {
        this.plugin = plugin;
        this.store = store;
        this.names = names;
        this.messages = messages;
        this.ctx = ctx;
        try {
            for (VipEntry entry : store.load()) {
                entries.put(entry.getUuid(), entry);
            }
            permissionHolders.putAll(store.loadPermissions());
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Could not read vips.yml ("
                    + failure.getMessage() + "); starting with an empty VIP list.");
        }
    }

    /** Wires the {@code /<username>} command registry; called once at startup. */
    public void setAliases(VipShopCommands aliases) {
        this.aliases = aliases;
    }

    /** Every VIP, sorted by name so lists and menus have a stable order. */
    public List<VipEntry> all() {
        List<VipEntry> sorted = new ArrayList<VipEntry>(entries.values());
        Collections.sort(sorted, new Comparator<VipEntry>() {
            @Override
            public int compare(VipEntry left, VipEntry right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return sorted;
    }

    /** VIPs from both sources: the stored list plus minted.vip holders. */
    public int size() {
        int extra = 0;
        for (UUID uuid : permissionHolders.keySet()) {
            if (!entries.containsKey(uuid)) {
                extra++;
            }
        }
        return entries.size() + extra;
    }

    /**
     * The merged roster behind the dashboard page and {@code /minted vip list}:
     * stored entries (removable from here) plus the players who count only
     * through {@link #PERMISSION}, marked so nobody expects the menu to be
     * able to revoke a permission node.
     */
    public List<VipMember> roster() {
        List<VipMember> members = new ArrayList<VipMember>();
        for (VipEntry entry : all()) {
            members.add(VipMember.stored(entry));
        }
        for (Map.Entry<UUID, String> holder : permissionHolders.entrySet()) {
            if (!entries.containsKey(holder.getKey())) {
                members.add(VipMember.permission(holder.getKey(), holder.getValue()));
            }
        }
        Collections.sort(members, new Comparator<VipMember>() {
            @Override
            public int compare(VipMember left, VipMember right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return members;
    }

    /** The roster row for one player, or null when they are no VIP. */
    public VipMember memberFor(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        VipEntry entry = entries.get(uuid);
        if (entry != null) {
            return VipMember.stored(entry);
        }
        String name = permissionHolders.get(uuid);
        return name == null ? null : VipMember.permission(uuid, name);
    }

    public boolean isVip(UUID uuid) {
        return uuid != null && (entries.containsKey(uuid) || permissionHolders.containsKey(uuid));
    }

    /** The other half of the perk: does this player currently own a shop? */
    public boolean hasShop(UUID uuid) {
        return uuid != null && ctx.shops().playerShopOf(uuid) != null;
    }

    /** Whether the {@code /<username>} commands are switched on in config. */
    public boolean aliasesEnabled() {
        return aliases != null && aliases.isEnabled();
    }

    /**
     * Adds a player by name. Online players resolve instantly; anyone else is
     * resolved through the seen-name table off the main thread. {@code after}
     * runs once the attempt has resolved - success or not - so a menu can
     * reopen itself; it may be null.
     */
    public void add(String rawName, final CommandSender by, final Runnable after) {
        final String name = rawName == null ? "" : rawName.trim();
        if (!VipEntry.isCommandName(name)) {
            messages.send(by, "vip.invalid-name", "player", name.isEmpty() ? "?" : name);
            run(after);
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            finishAdd(online.getUniqueId(), online.getName(), by, after);
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                UUID uuid = null;
                String canonical = name;
                RuntimeException failure = null;
                try {
                    uuid = names.uuidByName(name);
                    if (uuid != null) {
                        String seen = names.names(Collections.singletonList(uuid)).get(uuid);
                        if (seen != null) {
                            canonical = seen;
                        }
                    }
                } catch (RuntimeException broken) {
                    failure = broken;
                }
                final UUID resolved = uuid;
                final String known = canonical;
                final RuntimeException problem = failure;
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (problem != null) {
                            plugin.getLogger().warning("Could not look up '" + name + "': " + problem.getMessage());
                            messages.send(by, "vip.lookup-failed", "player", name);
                            VipService.run(after);
                        } else if (resolved == null) {
                            messages.send(by, "vip.unknown-player", "player", name);
                            VipService.run(after);
                        } else {
                            finishAdd(resolved, known, by, after);
                        }
                    }
                });
            }
        });
    }

    /** Removes a VIP by (case-insensitive) name; returns whether it matched. */
    public boolean remove(String rawName, CommandSender by) {
        String name = rawName == null ? "" : rawName.trim();
        VipEntry entry = find(name);
        if (entry == null) {
            String holder = findHolder(name);
            if (holder != null) {
                messages.send(by, "vip.remove-permission", "player", holder);
            } else {
                messages.send(by, "vip.not-vip", "player", name);
            }
            return false;
        }
        entries.remove(entry.getUuid());
        persist();
        syncAliases();
        messages.send(by, "vip.removed", "player", entry.getName());
        if (isVip(entry.getUuid())) {
            messages.send(by, "vip.still-permission", "player", entry.getName());
        }
        return true;
    }

    /** Follows a VIP's rename: refreshes the stored name and the command label. */
    public void refreshName(Player player) {
        VipEntry entry = entries.get(player.getUniqueId());
        if (entry == null || entry.getName().equals(player.getName())) {
            return;
        }
        entry.setName(player.getName());
        persist();
        syncAliases();
    }

    /**
     * Records or drops a player in the permission-based half of the roster.
     * Called on join and quit, because those are the moments a permission
     * plugin's answer is trustworthy for that player.
     */
    public void observePermission(Player player) {
        UUID uuid = player.getUniqueId();
        String known = permissionHolders.get(uuid);
        if (player.hasPermission(PERMISSION)) {
            if (known != null && known.equals(player.getName())) {
                return;
            }
            permissionHolders.put(uuid, player.getName());
        } else {
            if (known == null) {
                return;
            }
            permissionHolders.remove(uuid);
        }
        persist();
        syncAliases();
    }

    /** Re-evaluates every {@code /<username>} command; safe at any time. */
    public void syncAliases() {
        if (aliases != null) {
            aliases.sync(roster());
        }
    }

    /** Drops every registered shop command; used when the plugin shuts down. */
    public void shutdown() {
        if (aliases != null) {
            aliases.unregisterAll();
        }
    }

    // --- PlayerShopHooks -----------------------------------------------------

    @Override
    public void onPlayerShopCreated(Shop shop) {
        syncAliases();
        VipMember member = memberFor(shop.getOwner());
        if (member == null || !aliasesEnabled()) {
            return;
        }
        Player online = Bukkit.getPlayer(shop.getOwner());
        if (online != null) {
            messages.send(online, "vip.alias-live", "command", member.getName());
        }
    }

    @Override
    public void onPlayerShopDeleted(Shop shop) {
        syncAliases();
    }

    @Override
    public void onShopsLoaded() {
        syncAliases();
    }

    private void finishAdd(UUID uuid, String name, CommandSender by, Runnable after) {
        if (entries.containsKey(uuid)) {
            messages.send(by, "vip.already", "player", name);
            run(after);
            return;
        }
        if (permissionHolders.containsKey(uuid)) {
            messages.send(by, "vip.already-permission", "player", name);
            run(after);
            return;
        }
        entries.put(uuid, new VipEntry(uuid, name, System.currentTimeMillis(), senderName(by)));
        persist();
        syncAliases();
        messages.send(by, "vip.added", "player", name);
        Player target = Bukkit.getPlayer(uuid);
        if (target != null) {
            messages.send(target, hasShop(uuid) ? "vip.welcome-shop" : "vip.welcome", "command", name);
        }
        run(after);
    }

    private VipEntry find(String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        for (VipEntry entry : entries.values()) {
            if (entry.getName().toLowerCase(Locale.ROOT).equals(needle)) {
                return entry;
            }
        }
        return null;
    }

    /** The stored name of a permission-based VIP matching this name, or null. */
    private String findHolder(String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<UUID, String> holder : permissionHolders.entrySet()) {
            if (holder.getValue().toLowerCase(Locale.ROOT).equals(needle)) {
                return holder.getValue();
            }
        }
        return null;
    }

    private void persist() {
        try {
            store.save(entries.values(), permissionHolders);
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Could not save vips.yml: " + failure.getMessage());
        }
    }

    private static String senderName(CommandSender sender) {
        return sender instanceof Player ? ((Player) sender).getName() : "Console";
    }

    private static void run(Runnable task) {
        if (task != null) {
            task.run();
        }
    }
}