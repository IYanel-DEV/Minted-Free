package dev.minted.util;

import dev.minted.compat.ServerVersion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection helpers for reaching into NMS / craftbukkit code.
 *
 * <p>These classes are not part of the public Bukkit API and their layout
 * changes between releases, so all access goes through {@link ServerVersion}
 * derived package names. Every method in this class either succeeds or throws
 * {@link ReflectionException}; no checked exceptions leak into callers.
 */
public final class Reflection {

    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    private Reflection() {
    }

    public static Class<?> getNmsClass(String name, ServerVersion version) {
        return getClassChecked(version.getNmsPackage() + "." + name);
    }

    public static Class<?> getCraftBukkitClass(String name, ServerVersion version) {
        return getClassChecked(version.getCraftBukkitPackage() + "." + name);
    }

    public static Class<?> getClass(String name) {
        return getClassChecked(name);
    }

    public static Method getMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new ReflectionException("No method " + owner.getName() + "#" + name, e);
        }
    }

    public static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException e) {
            throw new ReflectionException("Failed to invoke " + method, e);
        }
    }

    public static Object invokeStatic(Method method, Object... arguments) {
        return invoke(method, null, arguments);
    }

    public static Field getField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new ReflectionException("No field " + owner.getName() + "#" + name, e);
        }
    }

    public static Object getFieldValue(Object instance, String name) {
        return getFieldValue(instance.getClass(), instance, name);
    }

    public static Object getFieldValue(Class<?> owner, Object instance, String name) {
        try {
            return getField(owner, name).get(instance);
        } catch (IllegalAccessException e) {
            throw new ReflectionException("Failed to read " + owner.getName() + "#" + name, e);
        }
    }

    public static void setFieldValue(Object instance, String name, Object value) {
        try {
            getField(instance.getClass(), name).set(instance, value);
        } catch (IllegalAccessException e) {
            throw new ReflectionException("Failed to write " + instance.getClass().getName() + "#" + name, e);
        }
    }

    private static Class<?> getClassChecked(String name) {
        Class<?> cached = CLASS_CACHE.get(name);
        if (cached != null) {
            return cached;
        }

        try {
            Class<?> clazz = Class.forName(name);
            CLASS_CACHE.put(name, clazz);
            return clazz;
        } catch (ClassNotFoundException e) {
            throw new ReflectionException("Class not found: " + name, e);
        }
    }

    public static final class ReflectionException extends RuntimeException {

        ReflectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}