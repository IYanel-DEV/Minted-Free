package dev.minted.compat;

import dev.minted.util.Reflection;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Carries the banknote machine payload in hidden item NBT instead of a lore
 * line, so the code players see on a note is just "Value: $X".
 *
 * <p>Every access is reflective, because NMS class names and packages change
 * between releases (versioned {@code net.minecraft.server.vX_Rn} before 1.17,
 * {@code net.minecraft.world.item}/ {@code net.minecraft.nbt} from 1.17 on).
 * Any version or fork Minted cannot reach falls back to {@code null} and the
 * parser writes the payload on the lore instead, so redemption still works.
 */
public final class NotePayloadNbt {

    private static final String TAG = "minted";

    private NotePayloadNbt() {
    }

    /**
     * @return the item with its payload hidden in NBT, or null when this server
     *     cannot be reached reflectively (caller falls back to a lore line)
     */
    public static ItemStack write(ServerVersion version, ItemStack item, String payload) {
        return write(version, item, TAG, payload, 0);
    }

    /**
     * Stores {@code payload} under {@code tag} and optionally skins the item with
     * {@code modelData} via a raw {@code CustomModelData} int in NBT. Writing the
     * tag directly (instead of Bukkit's {@code setCustomModelData}) keeps the skin
     * working on 1.13 servers whose players connect through Via - the int rides on
     * the item tag and modern clients read it natively, while a 1.14+ server
     * surfaces it the usual way. Model data is purely cosmetic; {@code modelData}
     * {@code <= 0} simply writes nothing.
     *
     * @return the item with its data in NBT, or null when NMS is unreachable
     */
    public static ItemStack write(ServerVersion version, ItemStack item, String tag, String payload, int modelData) {
        try {
            Class<?> itemStack = itemStackClass(version);
            Class<?> tagType = tagClass(version);

            Object nms = asNmsCopy(item, version);
            Object nbt = Reflection.invoke(Reflection.getMethod(itemStack, "getTag"), nms);
            if (nbt == null) {
                nbt = newTag(tagType);
                if (nbt == null) {
                    return null;
                }
            }
            Reflection.invoke(Reflection.getMethod(tagType, "setString", String.class, String.class),
                    nbt, tag, payload);
            if (modelData > 0) {
                Reflection.invoke(Reflection.getMethod(tagType, "setInt", String.class, int.class),
                        nbt, "CustomModelData", modelData);
            }
            Reflection.invoke(Reflection.getMethod(itemStack, "setTag", tagType), nms, nbt);
            return asBukkitCopy(version, nms);
        } catch (Reflection.ReflectionException e) {
            return null;
        }
    }

    /** @return the hidden payload under the default tag, or null if there is none */
    public static String read(ServerVersion version, ItemStack item) {
        return read(version, item, TAG);
    }

    /** @return the hidden payload under {@code tag}, or null when there is none */
    public static String read(ServerVersion version, ItemStack item, String tag) {
        if (item == null) {
            return null;
        }
        try {
            Class<?> itemStack = itemStackClass(version);
            Class<?> tagType = tagClass(version);
            Object nms = asNmsCopy(item, version);
            Object nbt = Reflection.invoke(Reflection.getMethod(itemStack, "getTag"), nms);
            if (nbt == null) {
                return null;
            }
            Boolean has = (Boolean) Reflection.invoke(
                    Reflection.getMethod(tagType, "hasKey", String.class), nbt, tag);
            if (!Boolean.TRUE.equals(has)) {
                return null;
            }
            return (String) Reflection.invoke(
                    Reflection.getMethod(tagType, "getString", String.class), nbt, tag);
        } catch (Reflection.ReflectionException e) {
            return null;
        }
    }

    private static Object asNmsCopy(ItemStack item, ServerVersion version) {
        Method copy = Reflection.getMethod(
                Reflection.getCraftBukkitClass("inventory.CraftItemStack", version),
                "asNMSCopy", ItemStack.class);
        return Reflection.invokeStatic(copy, item);
    }

    private static ItemStack asBukkitCopy(ServerVersion version, Object nms) {
        Method copy = Reflection.getMethod(
                Reflection.getCraftBukkitClass("inventory.CraftItemStack", version),
                "asBukkitCopy", itemStackClass(version));
        return (ItemStack) Reflection.invokeStatic(copy, nms);
    }

    private static Object newTag(Class<?> tagType) {
        try {
            Constructor<?> ctor = tagType.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Class<?> itemStackClass(ServerVersion version) {
        // 1.17 moved NMS into split packages; the compat layer only knows the
        // root, so map the two moved classes by release threshold here.
        return version.isAtLeast(1, 17)
                ? Reflection.getClass("net.minecraft.world.item.ItemStack")
                : Reflection.getNmsClass("ItemStack", version);
    }

    private static Class<?> tagClass(ServerVersion version) {
        return version.isAtLeast(1, 17)
                ? Reflection.getClass("net.minecraft.nbt.NBTTagCompound")
                : Reflection.getNmsClass("NBTTagCompound", version);
    }
}