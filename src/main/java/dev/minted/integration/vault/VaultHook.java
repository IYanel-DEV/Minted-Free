package dev.minted.integration.vault;

import dev.minted.api.MintedEconomy;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

/**
 * Registers Minted with Vault. Kept in its own class so the Vault types it
 * references are only ever resolved after the caller has confirmed Vault is
 * installed; on a server without Vault this class is never loaded.
 */
public final class VaultHook {

    private VaultHook() {
    }

    /** Registers Minted at the highest priority so it wins when selected. */
    public static void register(Plugin plugin, MintedEconomy economy) {
        plugin.getServer().getServicesManager().register(Economy.class,
                new VaultEconomy(economy), plugin, ServicePriority.Highest);
        plugin.getLogger().info("Hooked Vault: Minted is now available as a Vault economy provider.");
    }
}
