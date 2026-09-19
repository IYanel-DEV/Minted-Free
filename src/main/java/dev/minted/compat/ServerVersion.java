package dev.minted.compat;

import org.bukkit.Bukkit;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Snapshot of the running server's Minecraft version.
 *
 * <p>Minted supports 1.8 through 1.26 in a single jar, which means every
 * version-sensitive decision routes through this type. The version numbers
 * are parsed from Bukkit's version string, while the NMS/craftbukkit package
 * names are derived from the server's own classpath so they stay correct on
 * forks and exotic minor versions.
 */
public final class ServerVersion {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-[^\\s]*)?", Pattern.CASE_INSENSITIVE);

    private static final int SUPPORTED_MAJOR = 1;
    private static final int SUPPORTED_MINOR_BASE = 8;

    private final int major;
    private final int minor;
    private final int patch;

    private final String craftBukkitPackage;
    private final String nmsPackage;

    private ServerVersion(int major, int minor, int patch, String craftBukkitPackage, String nmsPackage) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.craftBukkitPackage = craftBukkitPackage;
        this.nmsPackage = nmsPackage;
    }

    /**
     * Detects the running server's version.
     *
     * @return a parsed snapshot of this server
     * @throws IllegalStateException if the version string cannot be parsed
     */
    public static ServerVersion detect() {
        String bukkitVersion = Bukkit.getBukkitVersion();
        Matcher matcher = VERSION_PATTERN.matcher(bukkitVersion);

        if (!matcher.find()) {
            throw new IllegalStateException("Unrecognised server version: " + bukkitVersion);
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;

        String craftBukkitPackage = Bukkit.getServer().getClass().getPackage().getName();
        String nmsPackage = resolveNmsPackage(craftBukkitPackage);

        return new ServerVersion(major, minor, patch, craftBukkitPackage, nmsPackage);
    }

    private static String resolveNmsPackage(String craftBukkitPackage) {
        if (craftBukkitPackage.startsWith("org.bukkit.craftbukkit")) {
            String suffixed = "net.minecraft.server" + craftBukkitPackage.substring("org.bukkit.craftbukkit".length());
            return suffixed;
        }
        return "net.minecraft";
    }

    /**
     * @return true if this server is at or above the given release
     */
    public boolean isAtLeast(int major, int minor) {
        return this.major > major || (this.major == major && this.minor >= minor);
    }

    /**
     * @return true if this server is at or above the given full release
     */
    public boolean isAtLeast(int major, int minor, int patch) {
        if (this.major != major) {
            return this.major > major;
        }
        if (this.minor != minor) {
            return this.minor > minor;
        }
        return this.patch >= patch;
    }

    /**
     * @return true if this server is strictly below the given release
     */
    public boolean isBelow(int major, int minor) {
        return !isAtLeast(major, minor);
    }

    /**
     * @return false when running on a 1.7 or older server, which is unsupported
     */
    public boolean isSupported() {
        return major > SUPPORTED_MAJOR || (major == SUPPORTED_MAJOR && minor >= SUPPORTED_MINOR_BASE);
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    /**
     * Fully-qualified package of the server's craftbukkit implementation,
     * e.g. {@code org.bukkit.craftbukkit.v1_16_R3}.
     */
    public String getCraftBukkitPackage() {
        return craftBukkitPackage;
    }

    /**
     * Fully-qualified root of the NMS hierarchy: versioned for 1.8-1.16
     * ({@code net.minecraft.server.v1_16_R3}), unversioned from 1.17 on
     * ({@code net.minecraft}).
     */
    public String getNmsPackage() {
        return nmsPackage;
    }

    @Override
    public String toString() {
        // 1.13.2 or 1.21; only print the patch when the server actually has one.
        return patch > 0
                ? String.format(Locale.ROOT, "%d.%d.%d", major, minor, patch)
                : String.format(Locale.ROOT, "%d.%d", major, minor);
    }
}