package dev.minted.integration.vaultunlocked;

import dev.minted.api.MintedEconomy;

import net.milkbowl.vault2.economy.Economy;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

/**
 * Registers Minted with VaultUnlocked (the {@code vault2} economy API), next
 * to the classic Vault hook. Kept in its own class so the vault2 types it
 * references are only ever resolved after {@link #apiPresent()} has confirmed
 * the API classes exist; on a server without VaultUnlocked neither this class
 * nor {@link VaultUnlockedEconomy} is ever loaded.
 */
public final class VaultUnlockedHook {

    private VaultUnlockedHook() {
    }

    /**
     * Whether the VaultUnlocked API classes are on this server. Checked through
     * this plugin's own classloader, which - like every Bukkit plugin - can see
     * classes carried by other installed plugins, so the answer tracks the
     * VaultUnlocked install without a hard dependency.
     */
    public static boolean apiPresent() {
        try {
            Class.forName("net.milkbowl.vault2.economy.Economy");
            return true;
        } catch (ClassNotFoundException missing) {
            return false;
        }
    }

    /** Registers Minted at the highest priority so it wins when selected. */
    public static void register(Plugin plugin, MintedEconomy economy) {
        plugin.getServer().getServicesManager().register(Economy.class,
                new VaultUnlockedEconomy(economy), plugin, ServicePriority.Highest);
        plugin.getLogger().info("Hooked VaultUnlocked: Minted is now available on the vault2 economy API.");
    }
}