package dev.minted.shop;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Locale;

/**
 * Reads a player's best price-bending permission for a shop.
 *
 * <p>Discounts and sell multipliers are flat nodes, not per-target grants:
 * {@code minted.shop.<name>.discount.<percent>} lowers a buy price, and
 * {@code minted.shop.<name>.sellmult.<factor>} raises a sell payout. A player
 * may hold several; the strongest one wins. The nodes are matched against the
 * player's effective permissions because their numeric tail cannot be known in
 * advance, so {@code hasPermission} with a fixed string would not find them.
 */
public final class Discounts {

    private Discounts() {
    }

    /** Buy price multiplier in {@code (0, 1]}: {@code 1 - bestPercent/100}. */
    public static double buyMultiplier(Player player, String shop) {
        double bestPercent = best(player, "minted.shop." + shop.toLowerCase(Locale.ROOT) + ".discount.");
        if (bestPercent <= 0) {
            return 1.0;
        }
        return 1.0 - Math.min(bestPercent, 100.0) / 100.0;
    }

    /** Sell payout multiplier of at least {@code 1}: the highest factor held. */
    public static double sellMultiplier(Player player, String shop) {
        double best = best(player, "minted.shop." + shop.toLowerCase(Locale.ROOT) + ".sellmult.");
        return best > 1.0 ? best : 1.0;
    }

    private static double best(Player player, String prefix) {
        double best = 0;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String node = info.getPermission().toLowerCase(Locale.ROOT);
            if (node.startsWith(prefix)) {
                double value = parse(node.substring(prefix.length()));
                if (value > best) {
                    best = value;
                }
            }
        }
        return best;
    }

    private static double parse(String tail) {
        try {
            return Double.parseDouble(tail);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
