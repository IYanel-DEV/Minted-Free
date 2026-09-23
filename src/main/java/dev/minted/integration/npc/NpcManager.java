package dev.minted.integration.npc;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;

import dev.minted.MintedPlugin;
import dev.minted.compat.ServerVersion;
import dev.minted.gui.GuiContext;
import dev.minted.gui.PersonalMenu;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Self-built bank tellers: fake players shown entirely through packets, so no
 * Citizens dependency and no invisible real entity. Each teller gets a
 * PlayerInfo entry (long enough for the client to download its skin), a NAMED
 * entity spawn with a name tag, per-viewer head tracking, and a right-click
 * that opens the player's own bank menu.
 *
 * <p>Every packet path is version-guarded by {@link ServerVersion}: int vs
 * double coordinates, the UUID field, the single-action vs actions-set player
 * list, and the optional chat-component name tag. The 1.19.3+ player-list
 * branch is the only code that calls ProtocolLib 5-only getters, and it lives
 * in {@link ModernPlayerInfo} so it is never loaded on older servers.
 */
public final class NpcManager implements Listener {

    private static final String DEFAULT_SKIN_VALUE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTAxMDA4ZTFkOGI5MjRhOGQ0YjFkMTFmMmZkMWNmZmE0YzdjZWFlYTBlNzhiZmZkYWJkM2NlODFkZjdmOTI4YiJ9fX0=";
    private static final String DEFAULT_NAME = "&eBanker";
    private static final java.util.regex.Pattern VALID_NAME =
            java.util.regex.Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int ENTITY_ID_BASE = 2_000_000_000;
    private static final double LOOK_RADIUS_SQ = 64.0 * 64.0;
    private static final double EYE_OFFSET = 1.65;
    private static final long TICK_INTERVAL = 4L;
    private static final long INTERACT_DEBOUNCE_MS = 500L;

    private final MintedPlugin plugin;
    private final ServerVersion version;
    private final GuiContext gui;
    private final NpcStore store;
    private final long tabHideTicks;
    private final String defaultName;
    private final String defaultSkinValue;
    private final String defaultSkinSignature;
    private final String defaultSkinPlayer;
    private final ProtocolManager protocol;
    private final Map<Integer, BankNpc> npcs = new HashMap<Integer, BankNpc>();
    private final Map<Integer, Map<UUID, byte[]>> headYaws = new HashMap<Integer, Map<UUID, byte[]>>();
    private final Map<UUID, Long> lastInteract = new HashMap<UUID, Long>();

    private int nextEntityId = ENTITY_ID_BASE;
    private int taskId = -1;
    private PacketAdapter interactListener;

    public NpcManager(MintedPlugin plugin, ServerVersion version, GuiContext gui, NpcStore store,
                      long tabHideSeconds, String defaultName, String defaultSkinValue,
                      String defaultSkinSignature, String defaultSkinPlayer) {
        this.plugin = plugin;
        this.version = version;
        this.gui = gui;
        this.store = store;
        this.tabHideTicks = Math.max(1L, tabHideSeconds) * 20L;
        this.defaultName = defaultName == null || defaultName.isEmpty() ? DEFAULT_NAME : defaultName;
        this.defaultSkinValue = defaultSkinValue == null || defaultSkinValue.isEmpty()
                ? DEFAULT_SKIN_VALUE : defaultSkinValue;
        this.defaultSkinSignature = defaultSkinSignature == null || defaultSkinSignature.isEmpty()
                ? null : defaultSkinSignature;
        this.defaultSkinPlayer = defaultSkinPlayer == null ? "" : defaultSkinPlayer;
        this.protocol = ProtocolLibrary.getProtocolManager();
    }

    /** Loads saved tellers, registers packet and event listeners, starts head tracking. */
    public void initialize() {
        int assigned = 0;
        for (BankNpc npc : store.load()) {
            npc.setEntityId(nextEntityId++);
            npcs.put(npc.entityId(), npc);
            assigned++;
        }
        if (assigned > 0) {
            plugin.getLogger().info("Loaded " + assigned + " bank NPC teller(s).");
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            spawnToVisible(player);
        }
        this.interactListener = interactListener();
        protocol.addPacketListener(interactListener);
        this.taskId = plugin.getServer().getScheduler()
                .scheduleSyncRepeatingTask(plugin, headLookTask(), TICK_INTERVAL, TICK_INTERVAL);
    }

    /** Stops packets and head tracking; hides everything still on screen. */
    public void shutdown() {
        if (taskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }
        if (interactListener != null) {
            protocol.removePacketListener(interactListener);
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            for (BankNpc npc : npcs.values()) {
                if (npc.world().equals(player.getWorld().getName())) {
                    destroyFrom(player, npc);
                }
            }
        }
        npcs.clear();
        headYaws.clear();
        lastInteract.clear();
    }

    // --- /minted npc surface -------------------------------------------------

    public List<BankNpc> npcs() {
        return Collections.unmodifiableList(new ArrayList<BankNpc>(npcs.values()));
    }

    public BankNpc find(String name) {
        for (BankNpc npc : npcs.values()) {
            if (npc.name().equalsIgnoreCase(name)) {
                return npc;
            }
        }
        return null;
    }

    /**
     * Places a teller at the admin's location. The name is optional: when blank
     * the config default is used ({@code integrations.npcs.name}). The skin is
     * the config default ({@code integrations.npcs.skin-value/-signature})
     * unless {@code skinPlayer} is given, in which case that player's skin is
     * fetched from Mojang after spawning. {@code integrations.npcs.skin-player}
     * acts as the config-level skinPlayer.
     *
     * @return an error message, or null when the teller was placed
     */
    public String create(Player admin, String name, String skinPlayer) {
        String display = name == null || name.isEmpty() ? defaultName : name;
        String userName = deriveUsername(display);
        if (!VALID_NAME.matcher(userName).matches()) {
            return "Name must be 1-16 letters, digits or underscores.";
        }
        if (find(userName) != null) {
            return "A teller named '" + userName + "' already exists.";
        }
        String useSkinPlayer = skinPlayer == null || skinPlayer.isEmpty() ? defaultSkinPlayer : skinPlayer;
        Location spot = admin.getLocation();
        BankNpc npc = new BankNpc(UUID.randomUUID(), userName, spot.getWorld().getName(),
                spot.getX(), spot.getY(), spot.getZ(), spot.getYaw(),
                defaultSkinValue, defaultSkinSignature, display);
        npc.setEntityId(nextEntityId++);
        npcs.put(npc.entityId(), npc);
        save();
        spawnToAll(npc);
        if (useSkinPlayer != null && !useSkinPlayer.isEmpty()) {
            fetchSkin(admin, npc, useSkinPlayer);
        }
        return null;
    }

    /** Removes a teller by name and hides it from everyone. */
    public String remove(String name) {
        BankNpc npc = find(name);
        if (npc == null) {
            return "No teller named '" + name + "'.";
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (npc.world().equals(player.getWorld().getName())) {
                destroyFrom(player, npc);
            }
        }
        npcs.remove(npc.entityId());
        headYaws.remove(npc.entityId());
        save();
        return null;
    }

    /** Moves an existing teller to the admin's feet and respawns it there. */
    public String here(Player admin, String name) {
        BankNpc npc = find(name);
        if (npc == null) {
            return "No teller named '" + name + "'.";
        }
        Location spot = admin.getLocation();
        BankNpc moved = npc.at(spot.getWorld().getName(), spot.getX(), spot.getY(), spot.getZ(), spot.getYaw());
        npcs.put(moved.entityId(), moved);
        respawn(moved);
        save();
        return null;
    }

    // --- spawn / hide --------------------------------------------------------

    private void spawnToVisible(Player player) {
        for (BankNpc npc : npcs.values()) {
            spawnTo(player, npc);
        }
    }

    private void spawnToAll(BankNpc npc) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (npc.world().equals(player.getWorld().getName())) {
                spawnTo(player, npc);
            }
        }
    }

    private void respawn(BankNpc npc) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!npc.world().equals(player.getWorld().getName())) {
                continue;
            }
            destroyFrom(player, npc);
            spawnTo(player, npc);
        }
    }

    private void spawnTo(Player viewer, BankNpc npc) {
        if (!npc.world().equals(viewer.getWorld().getName())) {
            return;
        }
        // The player-list entry and the named-entity spawn are sent
        // independently. A 1.19.3+ server that refuses to construct the info
        // packet (Mojang shuffles PLAYER_INFO layout almost every release) must
        // not stop the teller from appearing in the world, so a failure on one
        // side is logged and the other half still goes out.
        boolean tabAdded = false;
        try {
            sendInfoAdd(viewer, npc);
            tabAdded = true;
        } catch (Throwable failure) {
            plugin.getLogger().warning("Could not add teller '" + npc.name() + "' to "
                    + viewer.getName() + "'s player list: " + failure);
        }
        try {
            sendSpawn(viewer, npc);
        } catch (Throwable failure) {
            plugin.getLogger().warning("Could not spawn teller '" + npc.name() + "' for "
                    + viewer.getName() + ": " + failure);
            return;
        }
        if (tabAdded) {
            try {
                scheduleTabHide(viewer, npc);
            } catch (Throwable ignored) {
                // Worst case the entry lingers until the player relogs.
            }
        }
    }

    private void destroyFrom(Player viewer, BankNpc npc) {
        if (!npc.world().equals(viewer.getWorld().getName())) {
            return;
        }
        try {
            PacketContainer destroy = protocol.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            if (version.isAtLeast(1, 17)) {
                destroy.getIntLists().write(0, Collections.singletonList(npc.entityId()));
            } else {
                destroy.getIntegerArrays().write(0, new int[] {npc.entityId()});
            }
            protocol.sendServerPacket(viewer, destroy);
        } catch (Throwable failure) {
            plugin.getLogger().warning("Could not hide teller '" + npc.name() + "' from "
                    + viewer.getName() + ": " + failure);
        }
    }

    // --- player-list packets ------------------------------------------------

    private void sendInfoAdd(Player viewer, BankNpc npc) {
        WrappedGameProfile profile = new WrappedGameProfile(npc.uuid(), npc.name());
        profile.getProperties().put("textures",
                new WrappedSignedProperty("textures", npc.skinValue(), npc.skinSignature()));
        if (version.isAtLeast(1, 19, 3)) {
            ModernPlayerInfo.sendAdd(protocol, viewer, profile, npc.display());
        } else {
            PacketContainer info = protocol.createPacket(PacketType.Play.Server.PLAYER_INFO);
            info.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER);
            PlayerInfoData data = new PlayerInfoData(profile, 2, EnumWrappers.NativeGameMode.SURVIVAL,
                    WrappedChatComponent.fromText(npc.display()));
            info.getPlayerInfoDataLists().write(0, Collections.singletonList(data));
            protocol.sendServerPacket(viewer, info);
        }
    }

    private void sendInfoRemove(Player viewer, BankNpc npc) {
        if (version.isAtLeast(1, 19, 3)) {
            ModernPlayerInfo.sendRemove(protocol, viewer, npc.uuid(), npc.name(), npc.display());
        } else {
            PacketContainer info = protocol.createPacket(PacketType.Play.Server.PLAYER_INFO);
            info.getPlayerInfoAction().write(0, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER);
            PlayerInfoData data = new PlayerInfoData(new WrappedGameProfile(npc.uuid(), npc.name()),
                    0, EnumWrappers.NativeGameMode.SURVIVAL, WrappedChatComponent.fromText(npc.display()));
            info.getPlayerInfoDataLists().write(0, Collections.singletonList(data));
            protocol.sendServerPacket(viewer, info);
        }
    }

    private void scheduleTabHide(Player viewer, BankNpc npc) {
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!viewer.isOnline()
                        || !viewer.getWorld().getName().equals(npc.world())) {
                    return;
                }
                try {
                    sendInfoRemove(viewer, npc);
                } catch (Throwable ignored) {
                    // Worst case the entry lingers until the player relogs.
                }
            }
        }, tabHideTicks);
    }

    // --- spawn packet --------------------------------------------------------

    private void sendSpawn(Player viewer, BankNpc npc) {
        if (version.isAtLeast(1, 19, 4)) {
            sendModernSpawn(viewer, npc);
            return;
        }
        boolean uuidField = version.isAtLeast(1, 9);
        PacketContainer spawn = protocol.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        spawn.getIntegers().write(0, npc.entityId());
        if (uuidField) {
            spawn.getUUIDs().write(0, npc.uuid());
        }
        if (version.isAtLeast(1, 17)) {
            spawn.getDoubles().write(0, npc.x()).write(1, npc.y()).write(2, npc.z());
        } else {
            spawn.getIntegers().write(1, (int) Math.floor(npc.x()));
            spawn.getIntegers().write(2, (int) Math.floor(npc.y()));
            spawn.getIntegers().write(3, (int) Math.floor(npc.z()));
        }
        byte yaw = toPacketByte(npc.yaw());
        spawn.getBytes().write(0, yaw).write(1, (byte) 0);
        if (!uuidField) {
            spawn.getStrings().write(0, npc.name());
        }
        spawn.getDataWatcherModifier().write(0, datawatcherFor(npc));
        protocol.sendServerPacket(viewer, spawn);
    }

    /**
     * Modern entity spawn. From 1.19.4 players are added with the generic
     * SPAWN_ENTITY (AddEntity) packet; the classic NAMED_ENTITY_SPAWN was
     * removed entirely in 1.20.2, so building it on a new server now throws
     * "Could not find packet for type NAMED_ENTITY_SPAWN".
     */
    private void sendModernSpawn(Player viewer, BankNpc npc) {
        PacketContainer spawn = protocol.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getModifier().writeDefaults();
        spawn.getIntegers().write(0, npc.entityId());
        spawn.getUUIDs().write(0, npc.uuid());
        spawn.getEntityTypeModifier().write(0, EntityType.PLAYER);
        spawn.getDoubles().write(0, npc.x()).write(1, npc.y()).write(2, npc.z());
        byte yaw = toPacketByte(npc.yaw());
        spawn.getBytes().write(0, (byte) 0).write(1, yaw).write(2, yaw);
        protocol.sendServerPacket(viewer, spawn);
    }

    private WrappedDataWatcher datawatcherFor(BankNpc npc) {
        WrappedDataWatcher watcher = new WrappedDataWatcher();
        try {
            if (version.isAtLeast(1, 13)) {
                WrappedChatComponent component = WrappedChatComponent.fromText(npc.display());
                watcher.setObject(2, WrappedDataWatcher.Registry.getChatComponentSerializer(true),
                        Optional.of(component.getHandle()), true);
                watcher.setObject(3, WrappedDataWatcher.Registry.get(Boolean.class), Boolean.TRUE, true);
            } else if (version.isAtLeast(1, 9)) {
                watcher.setObject(2, npc.display(), true);
                watcher.setObject(3, (byte) 1, true);
            }
        } catch (Throwable ignored) {
            // Name tags are version-sensitive and best-effort: a server that
            // rejects the metadata still gets the teller, just without a float.
        }
        return watcher;
    }

    // --- head tracking -------------------------------------------------------

    private Runnable headLookTask() {
        return new Runnable() {
            @Override
            public void run() {
                if (npcs.isEmpty()) {
                    return;
                }
                for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                    if (viewer.isDead()) {
                        continue;
                    }
                    for (BankNpc npc : npcs.values()) {
                        if (!npc.world().equals(viewer.getWorld().getName())) {
                            continue;
                        }
                        Location eye = viewer.getEyeLocation();
                        double dx = npc.x() - eye.getX();
                        double dy = npc.y() + EYE_OFFSET - eye.getY();
                        double dz = npc.z() - eye.getZ();
                        if (dx * dx + dy * dy + dz * dz > LOOK_RADIUS_SQ) {
                            continue;
                        }
                        byte yawByte = toPacketByte((float) Math.toDegrees(Math.atan2(-dx, dz)));
                        Map<UUID, byte[]> perViewer = headYaws.get(npc.entityId());
                        if (perViewer == null) {
                            perViewer = new HashMap<UUID, byte[]>();
                            headYaws.put(npc.entityId(), perViewer);
                        }
                        byte[] last = perViewer.get(viewer.getUniqueId());
                        if (last == null || last[0] != yawByte) {
                            sendHeadRotation(viewer, npc.entityId(), yawByte);
                            perViewer.put(viewer.getUniqueId(), new byte[] {yawByte});
                        }
                    }
                }
            }
        };
    }

    private void sendHeadRotation(Player viewer, int entityId, byte yawByte) {
        try {
            PacketContainer rotation = protocol.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
            rotation.getIntegers().write(0, entityId);
            rotation.getBytes().write(0, yawByte);
            protocol.sendServerPacket(viewer, rotation);
        } catch (Throwable ignored) {
            // Head-turning is cosmetic; never take the task down.
        }
    }

    // --- right-click ---------------------------------------------------------

    private PacketAdapter interactListener() {
        return new PacketAdapter(plugin, PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                final Player player = event.getPlayer();
                if (player == null || player.getWorld() == null || npcs.isEmpty()) {
                    return;
                }
                final int entityId = event.getPacket().getIntegers().read(0);
                final BankNpc npc = npcs.get(entityId);
                if (npc == null || !npc.world().equals(player.getWorld().getName())) {
                    return;
                }
                EnumWrappers.EntityUseAction action = null;
                try {
                    action = event.getPacket().getEntityUseActions().read(0);
                } catch (Throwable ignored) {
                    // 1.19.3+ changed the field shape; a failed read still counts as interact.
                }
                if (action != null && action != EnumWrappers.EntityUseAction.INTERACT
                        && action != EnumWrappers.EntityUseAction.INTERACT_AT) {
                    return;
                }
                openBank(player);
            }
        };
    }

    private void openBank(final Player player) {
        long now = System.currentTimeMillis();
        Long last = lastInteract.get(player.getUniqueId());
        if (last != null && now - last < INTERACT_DEBOUNCE_MS) {
            return;
        }
        lastInteract.put(player.getUniqueId(), now);
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                if (!gui.ready(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Your account is still loading, try again in a moment.");
                    return;
                }
                new PersonalMenu(gui, player).open(player);
            }
        });
    }

    // --- persistence + skin --------------------------------------------------

    private synchronized void save() {
        try {
            store.save(npcs());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not save bank NPCs: " + e.getMessage());
        }
    }

    private void fetchSkin(final Player admin, final BankNpc npc, final String skinPlayer) {
        admin.sendMessage(ChatColor.GRAY + "Fetching the skin of '" + skinPlayer + "' ...");
        SkinResolver.resolve(plugin, skinPlayer, new SkinResolver.Callback() {
            @Override
            public void resolved(String value, String signature) {
                BankNpc current = npcs.get(npc.entityId());
                if (current == null) {
                    return;
                }
                if (value == null || value.isEmpty()) {
                    admin.sendMessage(ChatColor.RED + "Could not fetch that skin (offline or unknown name); "
                            + "'" + npc.name() + "' keeps its default teller skin.");
                    return;
                }
                BankNpc skinned = current.withSkin(value, signature == null ? null : signature);
                npcs.put(skinned.entityId(), skinned);
                respawn(skinned);
                save();
                admin.sendMessage(ChatColor.GREEN + "Teller '" + npc.name() + "' now wears "
                        + skinPlayer + "'s skin.");
            }
        });
    }

    // --- Bukkit events -------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        spawnToVisible(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String fromWorld = event.getFrom().getName();
        for (BankNpc npc : npcs.values()) {
            if (npc.world().equals(fromWorld)) {
                destroyFrom(player, npc);
            }
        }
        spawnToVisible(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (BankNpc npc : npcs.values()) {
            if (npc.world().equals(player.getWorld().getName())) {
                destroyFrom(player, npc);
            }
        }
        for (Map<UUID, byte[]> perViewer : headYaws.values()) {
            perViewer.remove(player.getUniqueId());
        }
        lastInteract.remove(player.getUniqueId());
    }

    // --- helpers -------------------------------------------------------------

    /**
     * Derives the fake-player username from a display name: {@code &} colour
     * codes are stripped and only letters, digits and underscores survive, so
     * {@code "&eBanker"} becomes {@code "Banker"} - always a valid Minecraft
     * name, never longer than 16 characters.
     */
    private String deriveUsername(String display) {
        String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', display));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < plain.length() && builder.length() < 16; i++) {
            char c = plain.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
                builder.append(c);
            }
        }
        return builder.length() == 0 ? "Banker" : builder.toString();
    }

    private byte toPacketByte(float degrees) {
        return (byte) Math.floor(degrees * 256.0 / 360.0);
    }
}