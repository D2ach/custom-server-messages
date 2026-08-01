package top.morndream.customservermessages;

import java.lang.reflect.Method;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Soft-depend LuckPerms lookups for placeholders and group rules. */
public final class LuckPermsHook {
    private Object luckPerms;

    public void hook() {
        luckPerms = null;
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            return;
        }

        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            luckPerms = providerClass.getMethod("get").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            luckPerms = null;
        }
    }

    public boolean hooked() {
        return luckPerms != null;
    }

    public String prefix(Player player) {
        return invokeNullableStringMethod(metaData(player), "getPrefix");
    }

    public String suffix(Player player) {
        return invokeNullableStringMethod(metaData(player), "getSuffix");
    }

    public String primaryGroup(Player player) {
        return invokeNullableStringMethod(user(player), "getPrimaryGroup");
    }

    public boolean matchesGroupRule(Player player, GroupMessageRule rule) {
        String primaryGroup = primaryGroup(player).toLowerCase(Locale.ROOT);
        if (!primaryGroup.isEmpty() && rule.groups().contains(primaryGroup)) {
            return true;
        }

        Object user = user(player);
        Object queryOptions = queryOptions(player);
        if (user == null || queryOptions == null) {
            return false;
        }

        try {
            Method method = findCompatibleSingleArgMethod(user.getClass(), "getInheritedGroups", queryOptions);
            if (method == null) {
                return false;
            }
            Object value = method.invoke(user, queryOptions);
            if (!(value instanceof Iterable<?> groups)) {
                return false;
            }
            for (Object group : groups) {
                String groupName = invokeNullableStringMethod(group, "getName");
                if (!groupName.isEmpty() && rule.groups().contains(groupName.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    private Object metaData(Player player) {
        Object user = user(player);
        Object queryOptions = queryOptions(player);
        if (user == null || queryOptions == null) {
            return null;
        }

        try {
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Method method = findCompatibleSingleArgMethod(cachedData.getClass(), "getMetaData", queryOptions);
            if (method == null) {
                return null;
            }
            return method.invoke(cachedData, queryOptions);
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private Object queryOptions(Player player) {
        if (luckPerms == null) {
            return null;
        }

        try {
            Object contextManager = luckPerms.getClass().getMethod("getContextManager").invoke(luckPerms);
            Method method = findCompatibleSingleArgMethod(contextManager.getClass(), "getQueryOptions", player);
            if (method == null) {
                return null;
            }
            Object result = method.invoke(contextManager, player);
            if (result instanceof java.util.Optional<?> optional) {
                return optional.orElse(null);
            }
            return result;
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private Object user(Player player) {
        if (luckPerms == null) {
            return null;
        }

        try {
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class)
                .invoke(userManager, player.getUniqueId());
            if (user != null) {
                return user;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Object adapter = luckPerms.getClass().getMethod("getPlayerAdapter", Class.class)
                .invoke(luckPerms, Player.class);
            return adapter.getClass().getMethod("getUser", Player.class).invoke(adapter, player);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String invokeNullableStringMethod(Object target, String methodName) {
        if (target == null) {
            return "";
        }
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof String string ? string : "";
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private static Method findCompatibleSingleArgMethod(Class<?> owner, String methodName, Object argument) {
        if (owner == null || methodName == null || argument == null) {
            return null;
        }

        Class<?> argumentClass = argument.getClass();
        Method fallback = null;
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (parameterType.isAssignableFrom(argumentClass) || implementsInterface(argumentClass, parameterType)) {
                return method;
            }

            if (fallback == null && parameterType.getName().equals("java.lang.Object")) {
                fallback = method;
            }
        }
        return fallback;
    }

    private static boolean implementsInterface(Class<?> concreteType, Class<?> candidateInterface) {
        if (concreteType == null || candidateInterface == null || !candidateInterface.isInterface()) {
            return false;
        }

        for (Class<?> current = concreteType; current != null; current = current.getSuperclass()) {
            for (Class<?> implemented : current.getInterfaces()) {
                if (implemented == candidateInterface || candidateInterface.isAssignableFrom(implemented)) {
                    return true;
                }
            }
        }
        return false;
    }
}
