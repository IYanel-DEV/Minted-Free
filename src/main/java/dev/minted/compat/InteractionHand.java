package dev.minted.compat;

import dev.minted.util.Reflection;

import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.lang.reflect.Method;

/**
 * Tells whether an entity interaction came from the off hand.
 *
 * <p>From 1.9 an entity right-click fires the event once per hand, so without
 * this filter the menu would open twice. {@code getHand()} does not exist in
 * the 1.8 API this plugin compiles against, so it is reached by reflection; on
 * 1.8 there is only one hand and the answer is always false.
 */
public final class InteractionHand {

    private InteractionHand() {
    }

    public static boolean isOffHand(PlayerInteractEntityEvent event) {
        return offHand(event);
    }

    /**
     * Same off-hand test for block/air interactions. From 1.9 a right-click also
     * fires once per hand, so a note deposit must ignore the off-hand pass.
     */
    public static boolean isOffHand(PlayerInteractEvent event) {
        return offHand(event);
    }

    private static boolean offHand(Object event) {
        try {
            Method getHand = Reflection.getMethod(event.getClass(), "getHand");
            Object hand = Reflection.invoke(getHand, event);
            return hand != null && "OFF_HAND".equals(hand.toString());
        } catch (Reflection.ReflectionException e) {
            return false;
        }
    }
}
