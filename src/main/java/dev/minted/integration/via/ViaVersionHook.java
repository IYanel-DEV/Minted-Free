package dev.minted.integration.via;

import dev.minted.compat.MaterialLookup;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The optional ViaVersion handler. When ViaVersion (plus ViaBackwards/ViaRewind)
 * is installed, clients on older protocols play through it - so a 1.26 server can
 * still host a 1.8 client. Minted can then read each client's protocol version and
 * shape what it hands that client accordingly.
 *
 * <p>Item resolution stays server-side (Mateerials are the server's Minecraft
 * version; ViaVersion translates between them on the wire), but a low-protocol
 * client browsing the economy catalog can be capped to items their own client
 * knows via {@link #clientMaterial}. Everything is reached through reflection
 * with the plugin-presence guard first, so a server without ViaVersion never so
 * much as loads these classes - and any incompatibility degrades to the server's
 * own resolution instead of breaking a menu.
 *
 * <p>A {@link Version} is the parsed 1.x rubric ({@code major.minor[.patch]}) of a
 * client, e.g. {@code 1.8}, {@code 1.16.5} or {@code 1.21.2}.
 */
public final class ViaVersionHook {

    /** A client's Minecraft version, as major/minor/patch integers (1.x line). */
    public static final class Version {

        private final int major;
        private final int minor;
        private final int patch;

        Version(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        public int major() {
            return major;
        }

        public int minor() {
            return minor;
        }

        public int patch() {
            return patch;
        }

        public boolean isAtLeast(int major, int minor) {
            return this.major > major || (this.major == major && this.minor >= minor);
        }

        @Override
        public String toString() {
            return patch > 0 ? major + "." + minor + "." + patch : major + "." + minor;
        }
    }

    // Protocol number -> 1.x public version string, used only when ViaVersion's
    // own ProtocolVersion class cannot be reached. Covers every 1.8+ release.
    private static final Map<Integer, String> FALLBACK = new HashMap<Integer, String>();

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private static void protocol(int number, String version) {
        FALLBACK.put(Integer.valueOf(number), version);
    }

    static {
        protocol(47, "1.8");
        protocol(107, "1.9");
        protocol(108, "1.9.1");
        protocol(109, "1.9.2");
        protocol(110, "1.9.3");
        protocol(111, "1.9.4");
        protocol(112, "1.10");
        protocol(210, "1.10.2");
        protocol(315, "1.11");
        protocol(316, "1.11.2");
        protocol(335, "1.12");
        protocol(338, "1.12.1");
        protocol(340, "1.12.2");
        protocol(393, "1.13");
        protocol(401, "1.13.1");
        protocol(404, "1.13.2");
        protocol(477, "1.14");
        protocol(480, "1.14.1");
        protocol(485, "1.14.2");
        protocol(490, "1.14.3");
        protocol(498, "1.14.4");
        protocol(573, "1.15");
        protocol(575, "1.15.1");
        protocol(578, "1.15.2");
        protocol(735, "1.16");
        protocol(736, "1.16.1");
        protocol(751, "1.16.2");
        protocol(753, "1.16.3");
        protocol(754, "1.16.5");
        protocol(755, "1.17");
        protocol(756, "1.17.1");
        protocol(757, "1.18");
        protocol(758, "1.18.2");
        protocol(759, "1.19");
        protocol(760, "1.19.2");
        protocol(761, "1.19.3");
        protocol(762, "1.19.4");
        protocol(763, "1.20");
        protocol(764, "1.20.2");
        protocol(765, "1.20.4");
        protocol(766, "1.20.6");
        protocol(767, "1.21");
        protocol(768, "1.21.1");
        protocol(769, "1.21.3");
        protocol(770, "1.21.4");
        protocol(771, "1.21.5");
        protocol(772, "1.21.6");
    }

    private final Plugin plugin;
    private final boolean present;

    public ViaVersionHook(Plugin plugin) {
        this.plugin = plugin;
        this.present = plugin.getServer().getPluginManager().getPlugin("ViaVersion") != null;
    }

    /** True when ViaVersion was found at startup; the only gate every method trusts. */
    public boolean isPresent() {
        return present;
    }

    /** The client's protocol number, or -1 when ViaVersion is absent or it could not be read. */
    public int protocol(Player player) {
        if (!present || player == null) {
            return -1;
        }
        Object api = viaApi();
        if (api == null) {
            return -1;
        }
        try {
            return ((Integer) api.getClass().getMethod("getPlayerVersion", UUID.class)
                    .invoke(api, player.getUniqueId())).intValue();
        } catch (Throwable incompatible) {
            return -1;
        }
    }

    /**
     * The client's Minecraft version, or null when it cannot be determined (no
     * ViaVersion, an unknown protocol, or the version string unparseable).
     */
    public Version clientVersion(Player player) {
        int protocol = protocol(player);
        if (protocol <= 0) {
            return null;
        }
        String name = protocolName(protocol);
        if (name == null) {
            name = FALLBACK.get(Integer.valueOf(protocol));
        }
        return name == null ? null : parse(name);
    }

    /** Whether the client is on at least {@code 1.<minor>}; false when unknown. */
    public boolean clientAtLeast(Player player, int major, int minor) {
        Version version = clientVersion(player);
        return version != null && version.isAtLeast(major, minor);
    }

    /**
     * The item Minted should hand this client for {@code key}: resolved for the
     * client's own version when ViaVersion knows it, otherwise the server's. A
     * low-protocol client never receives a material newer than it can render.
     */
    public Material clientMaterial(MaterialLookup materials, Player player, String key) {
        if (!present || player == null) {
            return materials.get(key);
        }
        Version version = clientVersion(player);
        if (version == null) {
            return materials.get(key);
        }
        return materials.materialForClient(key, version.minor());
    }

    /** A short human line for the admin report, e.g. {@code ViaVersion: 4.10.2}. */
    public String status() {
        if (!present) {
            return "not installed";
        }
        org.bukkit.plugin.Plugin via = plugin.getServer().getPluginManager().getPlugin("ViaVersion");
        return via == null ? "installed" : "installed (v" + via.getDescription().getVersion() + ")";
    }

    /** ViaVersion's own API entry point, whichever packages this install ships. */
    private Object viaApi() {
        try {
            return api("com.viaversion.viaversion.api.Via");
        } catch (Throwable modernMissing) {
            try {
                return api("us.myles.viaversion.api.Via");
            } catch (Throwable oldMissing) {
                return null;
            }
        }
    }

    private static Object api(String className) throws Throwable {
        Class<?> viaClass = Class.forName(className);
        Object api = viaClass.getMethod("getAPI").invoke(null);
        return api == null ? null : api;
    }

    /** The protocol's public version name through ViaVersion's own registry. */
    private String protocolName(int protocol) {
        try {
            Class<?> protocolClass = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            Object version = protocolClass.getMethod("getProtocol", int.class).invoke(null, Integer.valueOf(protocol));
            if (version == null) {
                return null;
            }
            Object name = version.getClass().getMethod("getName").invoke(version);
            return name == null ? null : String.valueOf(name);
        } catch (Throwable notReachable) {
            return null;
        }
    }

    /** Parses "1.21.2", "1.8", "1.16.5" etc into a {@link Version}. */
    static Version parse(String version) {
        if (version == null) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(version.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = matcher.group(3) == null || matcher.group(3).isEmpty()
                    ? 0 : Integer.parseInt(matcher.group(3));
            // Same scheme shift as ServerVersion: Minecraft dropped the leading
            // "1." from 1.26 on, so a reported "26.2" is really 1.26.2.
            if (major > 1) {
                patch = minor;
                minor = major;
                major = 1;
            }
            return new Version(major, minor, patch);
        } catch (NumberFormatException unparseable) {
            return null;
        }
    }
}