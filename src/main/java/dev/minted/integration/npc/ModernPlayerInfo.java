package dev.minted.integration.npc;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Fake-player tab entries for Minecraft 1.19.3 and newer.
 *
 * <p>From 1.19.3 the player-list packet became <i>PlayerInfoUpdate</i>, whose
 * actions are a set rather than a single field, and from 1.19.4 removals moved
 * into their own packet keyed by UUID. The 1.19.3+ data entries are the newer
 * {@code PlayerInfoData} shape - profile, latency, the {@code listed} flag and
 * profile UUID - which the old four-argument form can no longer build on the
 * newest servers. This class is the only one in Minted that touches those
 * modern structures (using getPlayerInfoActions() and getUUIDLists(), both
 * ProtocolLib 5-only getters), so it is isolated: it is never loaded on a
 * pre-1.19.3 server, where ProtocolLib itself would have no such methods.
 */
final class ModernPlayerInfo {

    private ModernPlayerInfo() {
    }

    static void sendAdd(ProtocolManager pm, Player viewer, WrappedGameProfile profile, String displayName) {
        PacketContainer packet = pm.createPacket(PacketType.Play.Server.PLAYER_INFO);
        packet.getPlayerInfoActions().write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                EnumWrappers.PlayerInfoAction.UPDATE_LISTED));
        PlayerInfoData data = new PlayerInfoData(profile.getUUID(), 0, true,
                EnumWrappers.NativeGameMode.SURVIVAL, profile, WrappedChatComponent.fromText(displayName));
        packet.getPlayerInfoDataLists().write(0, Collections.singletonList(data));
        pm.sendServerPacket(viewer, packet);
    }

    static void sendRemove(ProtocolManager pm, Player viewer, UUID npcUuid, String name, String displayName) {
        MinecraftVersion version = pm.getMinecraftVersion();
        if (version.isAtLeast(new MinecraftVersion(1, 19, 4))) {
            PacketContainer packet = pm.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
            packet.getUUIDLists().write(0, Collections.singletonList(npcUuid));
            pm.sendServerPacket(viewer, packet);
        } else {
            PacketContainer packet = pm.createPacket(PacketType.Play.Server.PLAYER_INFO);
            packet.getPlayerInfoActions().write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER));
            packet.getPlayerInfoDataLists().write(0, Collections.singletonList(
                    new PlayerInfoData(new WrappedGameProfile(npcUuid, name), 0,
                            EnumWrappers.NativeGameMode.NOT_SET, WrappedChatComponent.fromText(displayName))));
            pm.sendServerPacket(viewer, packet);
        }
    }
}