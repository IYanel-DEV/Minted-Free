package dev.minted.integration.customitems;

import dev.minted.MintedPlugin;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Identifies custom items from ItemsAdder, Nexo and Oraxen so the shop can
 * tell them apart from the vanilla stacks they are built on. A "Ruby Sword"
 * made of a diamond sword base must never merge with, pay out, or match an
 * ordinary diamond sword - but that only works if both sides of a comparison
 * can name the item the way its owning plugin does.
 *
 * <p>Everything is resolved reflectively against the plugins' public APIs, so
 * no custom-item plugin is a compile-time dependency, none of their classes
 * are ever loaded when they are absent, and a renamed or changed API degrades
 * to exactly the old material-only matching instead of breaking. This class
 * itself only ever touches Bukkit types, so it is always safe to load.
 *
 * <p>Probing happens once, lazily, on the first comparison after startup;
 * every lookup is main-thread only, like the trade paths that call it.
 */
public final class CustomItems {

    private static final String[] EMPTY = new String[0];

    // ItemsAdder: CustomStack.byItemStack(stack) -> wrapper, then .getNamespacedID().
    private static Method iaByStack;
    private static Method iaNamespacedId;
    // Nexo: NexoItems.exists(stack) gate, then .idFromItem(stack).
    private static Method nexoExists;
    private static Method nexoId;
    // Oraxen: OraxenItems.exists(stack) gate, then .getIdByItem(stack).
    private static Method oraxenExists;
    private static Method oraxenId;

    private static final List<String> found = new ArrayList<String>();
    private static boolean probed;

    private CustomItems() {
    }

    /**
     * The owning plugin's identity for this stack, namespaced so two plugins
     * can never hand out the same string, or null when the stack is vanilla
     * (or no provider recognizes it - which is exactly the pre-integration
     * behaviour).
     */
    public static String identity(ItemStack stack) {
        if (stack == null || !enabled()) {
            return null;
        }
        ensureProbed();
        String id;
        if (iaByStack != null) {
            try {
                Object wrapper = iaByStack.invoke(null, stack);
                if (wrapper != null) {
                    id = text(iaNamespacedId.invoke(wrapper));
                    if (id != null) {
                        return "itemsadder:" + id;
                    }
                }
            } catch (Throwable ignored) {
                // One bad call must never fail a trade; fall through.
            }
        }
        if (nexoExists != null) {
            try {
                if (Boolean.TRUE.equals(nexoExists.invoke(null, stack))) {
                    id = text(nexoId.invoke(null, stack));
                    if (id != null) {
                        return "nexo:" + id;
                    }
                }
            } catch (Throwable ignored) {
                // Fall through to the next provider.
            }
        }
        if (oraxenExists != null) {
            try {
                if (Boolean.TRUE.equals(oraxenExists.invoke(null, stack))) {
                    id = text(oraxenId.invoke(null, stack));
                    if (id != null) {
                        return "oraxen:" + id;
                    }
                }
            } catch (Throwable ignored) {
                // Fall through: vanilla matching.
            }
        }
        return null;
    }

    /** Display names of the detected providers, in probe order; empty when none or disabled. */
    public static String[] providers() {
        if (!enabled()) {
            return EMPTY;
        }
        ensureProbed();
        return found.toArray(new String[0]);
    }

    private static boolean enabled() {
        MintedPlugin plugin = MintedPlugin.get();
        return plugin != null && plugin.getConfig().getBoolean("integrations.customitems.enabled", true);
    }

    private static synchronized void ensureProbed() {
        if (probed) {
            return;
        }
        probed = true;
        try {
            Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
            iaByStack = customStack.getMethod("byItemStack", ItemStack.class);
            iaNamespacedId = customStack.getMethod("getNamespacedID");
            found.add("ItemsAdder");
        } catch (Throwable notInstalled) {
            // No ItemsAdder, or its API moved - skip it; matching degrades to vanilla rules.
        }
        try {
            Class<?> nexoItems = Class.forName("com.nexomc.nexo.api.NexoItems");
            nexoExists = nexoItems.getMethod("exists", ItemStack.class);
            nexoId = nexoItems.getMethod("idFromItem", ItemStack.class);
            found.add("Nexo");
        } catch (Throwable notInstalled) {
            // Same for Nexo.
        }
        try {
            Class<?> oraxenItems = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            oraxenExists = oraxenItems.getMethod("exists", ItemStack.class);
            oraxenId = oraxenItems.getMethod("getIdByItem", ItemStack.class);
            found.add("Oraxen");
        } catch (Throwable notInstalled) {
            // Same for Oraxen.
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String id = value.toString();
        if (id.isEmpty()) {
            return null;
        }
        return id;
    }
}