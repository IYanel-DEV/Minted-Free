package dev.minted.compat;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Puts a message in the slot above the hotbar (the text shown over the health
 * bar). The spigot action-bar overload of {@code sendMessage} did not exist in
 * the 1.8 API this plugin compiles against, so it is reached by reflection on
 * every server that has it; where the method is genuinely missing, the message
 * degrades to a normal chat line rather than vanishing.
 */
public final class ActionBar {

    private static final ConcurrentMap<Class<?>, Method> SEND_METHODS = new ConcurrentHashMap<Class<?>, Method>();

    private ActionBar() {
    }

    public static void send(Player player, String text) {
        try {
            Class<?> spigotClass = player.getClass().getMethod("spigot").invoke(player).getClass();
            Method send = SEND_METHODS.get(spigotClass);
            if (send == null) {
                send = findSendMessage(spigotClass);
                SEND_METHODS.put(spigotClass, send);
            }
            if (send == null) {
                player.sendMessage(text);
                return;
            }
            Object actionBar = actionBarType(send.getParameterTypes()[0]);
            BaseComponent[] line = new BaseComponent[]{new TextComponent(text)};
            send.invoke(player.spigot(), actionBar, line);
        } catch (Throwable t) {
            player.sendMessage(text);
        }
    }

    private static Method findSendMessage(Class<?> owner) {
        for (Method m : owner.getMethods()) {
            if (!"sendMessage".equals(m.getName())) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 2 && params[0].getName().equals("net.md_5.bungee.api.ChatMessageType")) {
                return m;
            }
        }
        return null;
    }

    private static Object actionBarType(Class<?> messageType) throws ReflectiveOperationException {
        for (Object constant : messageType.getEnumConstants()) {
            if (constant.toString().equals("ACTION_BAR")) {
                return constant;
            }
        }
        throw new NoSuchFieldException("ACTION_BAR");
    }
}