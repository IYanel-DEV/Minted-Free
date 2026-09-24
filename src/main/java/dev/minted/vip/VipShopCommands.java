package dev.minted.vip;

import dev.minted.shop.ShopContext;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registers one {@code /<username>} command per eligible VIP (on the list and
 * owning a player shop) in the server's own command map, so typing the VIP's
 * name opens their shop exactly like any other command - tab-completable and
 * all.
 *
 * <p>The command map and its dispatch table are server internals, not API, so
 * everything here is looked up reflectively and every step is guarded: if a
 * fork hides or renames them, Minted logs one warning and everything else -
 * the VIP list included - keeps working; players just use
 * {@code /pshop open <name>} instead. A name that is already a command is
 * never overridden: the shop stays reachable under {@code /minted:<name>}.
 */
public final class VipShopCommands {

    /** Our commands register under this prefix, so a clash still has /minted:<name>. */
    private static final String FALLBACK = "minted";

    private final Plugin plugin;
    private final ShopContext ctx;
    private final VipService vips;

    /** Lower-cased label to the command we registered under it. Main thread only. */
    private final Map<String, VipShopCommand> registered = new LinkedHashMap<String, VipShopCommand>();

    private boolean mapResolved;
    private CommandMap map;
    private boolean knownResolved;
    private Map<String, Command> knownCommands;
    private boolean syncWarned;

    public VipShopCommands(Plugin plugin, ShopContext ctx, VipService vips) {
        this.plugin = plugin;
        this.ctx = ctx;
        this.vips = vips;
    }

    /** {@code vip.enabled} in config.yml; the next sync applies a change. */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("vip.enabled", true);
    }

    /**
     * Makes the registered commands match the roster: every VIP - from the
     * list or the minted.vip permission - who owns a shop gets a command under
     * their current name, anything else is dropped. Idempotent, so it can run
     * after a shop change, a list or permission change, a rename, a config
     * reload or the initial load.
     */
    public void sync(Collection<VipMember> members) {
        try {
            if (!isEnabled()) {
                unregisterAll();
                return;
            }
            Map<String, VipMember> desired = new LinkedHashMap<String, VipMember>();
            for (VipMember member : members) {
                if (!VipEntry.isCommandName(member.getName())) {
                    continue;
                }
                if (ctx.shops().playerShopOf(member.getUuid()) == null) {
                    continue;
                }
                desired.put(key(member.getName()), member);
            }
            for (String label : new ArrayList<String>(registered.keySet())) {
                VipMember wanted = desired.get(label);
                if (wanted == null || !wanted.getUuid().equals(registered.get(label).owner())) {
                    unregister(label);
                }
            }
            for (Map.Entry<String, VipMember> wanted : desired.entrySet()) {
                if (!registered.containsKey(wanted.getKey())) {
                    register(wanted.getKey(), wanted.getValue());
                }
            }
        } catch (RuntimeException failure) {
            if (!syncWarned) {
                syncWarned = true;
                plugin.getLogger().warning("Could not sync the VIP shop commands ("
                        + failure.getClass().getSimpleName() + ": " + failure.getMessage()
                        + "); further failures are silent.");
            }
        }
    }

    /** Drops every command this class registered; used on shutdown and when disabled. */
    public void unregisterAll() {
        for (String label : new ArrayList<String>(registered.keySet())) {
            unregister(label);
        }
    }

    VipService vips() {
        return vips;
    }

    private void register(String label, VipMember member) {
        CommandMap commands = commandMap();
        if (commands == null) {
            return;
        }
        VipShopCommand command = new VipShopCommand(ctx, this, member.getName(), member.getUuid());
        boolean primary = commands.register(FALLBACK, command);
        registered.put(label, command);
        if (!primary) {
            plugin.getLogger().info("The name /" + member.getName() + " is already a command; "
                    + member.getName() + "'s shop also opens with /" + FALLBACK + ":" + member.getName() + ".");
        }
    }

    private void unregister(String label) {
        VipShopCommand command = registered.remove(label);
        if (command == null) {
            return;
        }
        CommandMap commands = commandMap();
        if (commands == null) {
            return;
        }
        try {
            command.unregister(commands);
        } catch (Throwable ignored) {
            // Falls through to the dispatch-table sweep below, which is what
            // actually stops the label from resolving.
        }
        Map<String, Command> known = knownCommands();
        if (known != null) {
            known.remove(label);
            known.remove(FALLBACK + ":" + label);
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** The server's command map: its getCommandMap() method, else a typed field. */
    private CommandMap commandMap() {
        if (mapResolved) {
            return map;
        }
        mapResolved = true;
        String problem = null;
        try {
            Object server = plugin.getServer();
            Method getter = null;
            for (Method method : server.getClass().getMethods()) {
                if ("getCommandMap".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    getter = method;
                    break;
                }
            }
            if (getter != null) {
                Object result = getter.invoke(server);
                if (result instanceof CommandMap) {
                    map = (CommandMap) result;
                }
            }
            for (Class<?> type = server.getClass(); map == null && type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (CommandMap.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Object value = field.get(server);
                        if (value instanceof CommandMap) {
                            map = (CommandMap) value;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable failure) {
            problem = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        if (map == null) {
            plugin.getLogger().warning("Could not reach the server command map"
                    + (problem == null ? "" : " (" + problem + ")")
                    + "; VIP /<username> shop commands are off - players can still use /pshop open <name>.");
        }
        return map;
    }

    /** SimpleCommandMap's dispatch table, walked up the class hierarchy. */
    @SuppressWarnings("unchecked")
    private Map<String, Command> knownCommands() {
        if (knownResolved) {
            return knownCommands;
        }
        knownResolved = true;
        CommandMap commands = commandMap();
        if (commands == null) {
            return null;
        }
        try {
            for (Class<?> type = commands.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField("knownCommands");
                    field.setAccessible(true);
                    Object value = field.get(commands);
                    if (value instanceof Map) {
                        knownCommands = (Map<String, Command>) value;
                        return knownCommands;
                    }
                } catch (NoSuchFieldException notHere) {
                    // Keep walking up: some forks move it to a superclass.
                }
            }
            plugin.getLogger().warning("The command map keeps no knownCommands table; "
                    + "a stale VIP shop command may linger until restart.");
        } catch (Throwable failure) {
            plugin.getLogger().warning("Could not read the command map ("
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage() + ").");
        }
        return knownCommands;
    }
}