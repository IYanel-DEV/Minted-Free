package dev.minted.integration;

/**
 * A point-in-time snapshot of every third-party integration, shown by
 * {@code /minted report}. Built purely from plugin presence, what Minted
 * actually registered, and a quick live look at the Vault economy resolution -
 * it never depends on a hook being present.
 */
public final class IntegrationReport {

    public final boolean apiReady;
    public final boolean vaultInstalled;
    public final boolean vaultRegistered;
    public final boolean vaultUnlockedInstalled;
    public final boolean vaultUnlockedRegistered;
    public final boolean papiInstalled;
    public final boolean papiRegistered;
    public final boolean essentialsInstalled;
    public final boolean essentialsEconomy;
    public final boolean protocolLibInstalled;
    public final boolean npcsActive;
    public final boolean viaInstalled;
    /** Why the bank-teller hook failed, or null when it did not. */
    public final String npcHookError;
    public final String primary;

    public IntegrationReport(boolean apiReady, boolean vaultInstalled, boolean vaultRegistered,
                             boolean vaultUnlockedInstalled, boolean vaultUnlockedRegistered,
                             boolean papiInstalled, boolean papiRegistered,
                             boolean essentialsInstalled, boolean essentialsEconomy,
                             boolean protocolLibInstalled, boolean npcsActive, boolean viaInstalled,
                             String npcHookError, String primary) {
        this.apiReady = apiReady;
        this.vaultInstalled = vaultInstalled;
        this.vaultRegistered = vaultRegistered;
        this.vaultUnlockedInstalled = vaultUnlockedInstalled;
        this.vaultUnlockedRegistered = vaultUnlockedRegistered;
        this.papiInstalled = papiInstalled;
        this.papiRegistered = papiRegistered;
        this.essentialsInstalled = essentialsInstalled;
        this.essentialsEconomy = essentialsEconomy;
        this.protocolLibInstalled = protocolLibInstalled;
        this.npcsActive = npcsActive;
        this.viaInstalled = viaInstalled;
        this.npcHookError = npcHookError;
        this.primary = primary;
    }
}