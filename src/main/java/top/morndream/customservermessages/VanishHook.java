package top.morndream.customservermessages;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/** Soft-depend CMI / vanish metadata checks. */
public final class VanishHook {
    private boolean cmiHooked;

    public void hook() {
        cmiHooked = Bukkit.getPluginManager().isPluginEnabled("CMI");
    }

    public boolean hooked() {
        return cmiHooked;
    }

    public boolean isVanished(Player player) {
        if (player == null) {
            return false;
        }

        if (hasVanishedMetadata(player)) {
            return true;
        }

        if (!cmiHooked) {
            return false;
        }

        try {
            Class<?> cmiUserClass = Class.forName("com.Zrips.CMI.Containers.CMIUser");
            Object user = cmiUserClass.getMethod("getUser", Player.class).invoke(null, player);
            if (user == null) {
                return false;
            }

            Object cmiVanished = invokeBooleanMethod(user, "isCMIVanished");
            if (Boolean.TRUE.equals(cmiVanished)) {
                return true;
            }

            Object vanished = invokeBooleanMethod(user, "isVanished");
            return Boolean.TRUE.equals(vanished);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean hasVanishedMetadata(Player player) {
        if (!player.hasMetadata("vanished")) {
            return false;
        }

        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value != null && value.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private static Boolean invokeBooleanMethod(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Object value = target.getClass().getMethod(methodName).invoke(target);
        return value instanceof Boolean bool ? bool : null;
    }
}
