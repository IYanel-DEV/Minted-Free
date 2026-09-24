package dev.minted.bank;

import java.util.UUID;

/**
 * The multi-server seam the account layer needs: "are we on a shared database"
 * and "announce this committed change to the other servers". Implemented by
 * {@code dev.minted.network.NetworkCoordinator}; {@link #NONE} is the
 * single-server default, so the economy never branches on null.
 */
public interface NetworkHooks {

    NetworkHooks NONE = new NetworkHooks() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public void announce(UUID uuid) {
            // Single server: nobody to tell.
        }
    };

    /** True while multi-server persistence is active. */
    boolean enabled();

    /**
     * Called (off the main thread) after a balance was committed to the shared
     * database, so the other servers can drop their cached copy.
     */
    void announce(UUID uuid);
}