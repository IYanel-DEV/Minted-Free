package dev.minted.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * The one-line way to reach Minted from another plugin.
 *
 * <p>Minted registers its {@link MintedEconomy} with Bukkit's service manager,
 * which is the only lookup that works across plugin classloaders. This helper
 * just wraps that lookup:
 * <pre>
 * MintedEconomy economy = MintedAPI.economy();
 * </pre>
 *
 * <p>{@link #economy()} returns null when Minted is not installed or has not
 * finished enabling, so a caller should null-check before use and never assume
 * the plugin is present. A plugin that wants to be told when Minted goes away
 * can listen for Bukkit's {@code ServiceUnregisterEvent}.
 */
public final class MintedAPI {

    private MintedAPI() {
    }

    /** @return Minted's economy, or null when Minted is absent or still enabling */
    public static MintedEconomy economy() {
        RegisteredServiceProvider<MintedEconomy> registration =
                Bukkit.getServicesManager().getRegistration(MintedEconomy.class);
        return registration == null ? null : registration.getProvider();
    }

    /** @return true when {@link #economy()} would return a provider */
    public static boolean isAvailable() {
        return economy() != null;
    }
}
