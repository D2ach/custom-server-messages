package com.codexmc.customservermessages;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomServerMessagesPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final long VANISH_JOIN_DELAY_TICKS = 2L;

    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private final Set<UUID> kickedPlayers = new HashSet<>();
    /** joinedPlayer -> welcomers who already received gold */
    private final Map<UUID, Set<UUID>> welcomeRewardClaims = new ConcurrentHashMap<>();

    private Object luckPerms;
    private boolean cmiHooked;
    private Object vaultEconomy;

    private boolean enabledFlag;
    private boolean sendToConsole;

    private boolean joinEnabled;
    private List<String> joinLines;
    private List<GroupMessageRule> joinGroupRules;

    private boolean firstJoinEnabled;
    private List<String> firstJoinLines;
    private boolean firstJoinRewardEnabled;
    private int firstJoinRewardMin;
    private int firstJoinRewardMax;
    private String firstJoinRewardButton;
    private String firstJoinRewardButtonHover;
    private String firstJoinRewardPrefix;
    private String firstJoinRewardPlayerMessage;
    private String firstJoinRewardAlreadyClaimed;
    private Duration firstJoinRewardButtonLifetime;

    private boolean quitEnabled;
    private List<String> quitLines;
    private List<GroupMessageRule> quitGroupRules;

    private boolean kickEnabled;
    private List<String> kickLines;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        hookLuckPerms();
        hookCmi();
        hookVault();
        getServer().getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(getCommand("customservermessages"), "Command customservermessages is not defined in plugin.yml")
            .setExecutor(this);
        Objects.requireNonNull(getCommand("customservermessages"), "Command customservermessages is not defined in plugin.yml")
            .setTabCompleter(this);

        getLogger().info(
            "CustomServerMessages enabled. join=" + joinEnabled
                + ", joinGroupRules=" + joinGroupRules.size()
                + ", firstJoin=" + firstJoinEnabled
                + ", firstJoinReward=" + firstJoinRewardEnabled
                + ", quit=" + quitEnabled
                + ", quitGroupRules=" + quitGroupRules.size()
                + ", kick=" + kickEnabled
                + ", luckPerms=" + (luckPerms != null)
                + ", cmi=" + cmiHooked
                + ", vault=" + (vaultEconomy != null)
        );
    }

    @Override
    public void onDisable() {
        kickedPlayers.clear();
        welcomeRewardClaims.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabledFlag) {
            return;
        }

        event.joinMessage(null);
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();

        // New players / non-ops never join vanished; only delay vanish check for ops.
        if (firstJoin || !player.isOp()) {
            handlePlayerJoin(player, firstJoin);
            return;
        }

        player.getScheduler().runDelayed(this, task -> {
            if (!enabledFlag || !player.isOnline() || isVanished(player)) {
                return;
            }
            handlePlayerJoin(player, firstJoin);
        }, null, VANISH_JOIN_DELAY_TICKS);
    }

    private void handlePlayerJoin(Player player, boolean firstJoin) {
        if (firstJoin && firstJoinEnabled) {
            broadcastConfiguredLines(firstJoinLines, player, "");
        } else if (joinEnabled) {
            broadcastConfiguredLines(resolveJoinLines(player), player, "");
        }

        if (firstJoin) {
            sendWelcomeButtons(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabledFlag) {
            return;
        }

        event.quitMessage(null);
        Player player = event.getPlayer();
        welcomeRewardClaims.remove(player.getUniqueId());
        if (kickedPlayers.remove(player.getUniqueId())) {
            return;
        }

        if (isVanished(player)) {
            return;
        }

        if (quitEnabled) {
            broadcastConfiguredLines(resolveQuitLines(player), player, "");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        if (!enabledFlag) {
            return;
        }

        event.leaveMessage(null);
        Player player = event.getPlayer();
        kickedPlayers.add(player.getUniqueId());
        player.getScheduler().runDelayed(this, task -> kickedPlayers.remove(player.getUniqueId()), null, 40L);

        if (isVanished(player)) {
            return;
        }

        if (kickEnabled) {
            broadcastConfiguredLines(kickLines, player, plainText(event.reason()));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("customservermessages.admin")) {
            sendPrefixedMessage(sender, getConfig().getString("messages.no-permission", "&cNo permission."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            for (String line : getConfig().getStringList("messages.status")) {
                sendRawMessage(sender, replaceStatusPlaceholders(line));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadSettings();
            hookLuckPerms();
            hookCmi();
            hookVault();
            sendPrefixedMessage(sender, getConfig().getString("messages.reloaded", "&aReloaded."));
            return true;
        }

        sendRawMessage(sender, "&7Usage: /" + label + " [status|reload]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("customservermessages.admin") || args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : List.of("status", "reload")) {
            if (option.startsWith(input)) {
                matches.add(option);
            }
        }
        return matches;
    }

    private void loadSettings() {
        enabledFlag = getConfig().getBoolean("enabled", true);
        sendToConsole = getConfig().getBoolean("send-to-console", false);

        joinEnabled = getConfig().getBoolean("join.enabled", true);
        joinLines = new ArrayList<>(getConfig().getStringList("join.lines"));
        joinGroupRules = loadGroupMessageRules("join.group-messages");

        firstJoinEnabled = getConfig().getBoolean("first-join.enabled", true);
        firstJoinLines = new ArrayList<>(getConfig().getStringList("first-join.lines"));
        firstJoinRewardEnabled = getConfig().getBoolean("first-join.reward.enabled", true);
        firstJoinRewardMin = getConfig().getInt("first-join.reward.min", 10);
        firstJoinRewardMax = getConfig().getInt("first-join.reward.max", 20);
        firstJoinRewardButton = getConfig().getString("first-join.reward.button", "&a&l[点击欢迎]");
        firstJoinRewardButtonHover = getConfig().getString(
            "first-join.reward.button-hover",
            "&7点击欢迎新玩家，首次可获得金币"
        );
        firstJoinRewardPrefix = getConfig().getString(
            "first-join.reward.button-prefix",
            "&e新玩家 &f<player> &e加入了！ "
        );
        firstJoinRewardPlayerMessage = getConfig().getString(
            "first-join.reward.player-message",
            "&a欢迎新玩家 &e<player> &a！你获得了 &e<reward> &a金币。"
        );
        firstJoinRewardAlreadyClaimed = getConfig().getString(
            "first-join.reward.already-claimed",
            "&7你已经欢迎过 &e<player> &7了。"
        );
        firstJoinRewardButtonLifetime = Duration.ofMinutes(
            Math.max(1, getConfig().getInt("first-join.reward.button-lifetime-minutes", 10))
        );

        quitEnabled = getConfig().getBoolean("quit.enabled", true);
        quitLines = new ArrayList<>(getConfig().getStringList("quit.lines"));
        quitGroupRules = loadGroupMessageRules("quit.group-messages");

        kickEnabled = getConfig().getBoolean("kick.enabled", true);
        kickLines = new ArrayList<>(getConfig().getStringList("kick.lines"));
    }

    private void hookLuckPerms() {
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

    private void hookCmi() {
        cmiHooked = Bukkit.getPluginManager().isPluginEnabled("CMI");
    }

    private void hookVault() {
        vaultEconomy = null;
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            return;
        }

        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            var registration = Bukkit.getServicesManager().getRegistration(economyClass);
            if (registration != null) {
                vaultEconomy = registration.getProvider();
            }
        } catch (ClassNotFoundException ignored) {
            vaultEconomy = null;
        }
    }

    private void sendWelcomeButtons(Player joinedPlayer) {
        if (!firstJoinRewardEnabled) {
            return;
        }
        if (vaultEconomy == null) {
            getLogger().warning("Welcome button skipped: Vault economy is unavailable.");
            return;
        }

        UUID joinedId = joinedPlayer.getUniqueId();
        String joinedName = joinedPlayer.getName();
        welcomeRewardClaims.putIfAbsent(joinedId, ConcurrentHashMap.newKeySet());

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(joinedId) || isVanished(online)) {
                continue;
            }
            online.sendMessage(buildWelcomeButtonMessage(joinedPlayer, joinedId, joinedName));
        }
    }

    private Component buildWelcomeButtonMessage(Player joinedPlayer, UUID joinedId, String joinedName) {
        Component prefix = serializer.deserialize(
            replacePlayerPlaceholders(firstJoinRewardPrefix, joinedPlayer, "", 0)
        );
        Component button = serializer.deserialize(firstJoinRewardButton == null ? "" : firstJoinRewardButton)
            .hoverEvent(HoverEvent.showText(serializer.deserialize(
                firstJoinRewardButtonHover == null ? "" : firstJoinRewardButtonHover
            )))
            .clickEvent(ClickEvent.callback(
                audience -> {
                    if (audience instanceof Player clicker) {
                        handleWelcomeClick(clicker, joinedId, joinedName);
                    }
                },
                options -> options
                    .uses(ClickCallback.UNLIMITED_USES)
                    .lifetime(firstJoinRewardButtonLifetime)
            ));
        return prefix.append(button);
    }

    private void handleWelcomeClick(Player clicker, UUID joinedId, String joinedName) {
        if (!firstJoinRewardEnabled || clicker.getUniqueId().equals(joinedId)) {
            return;
        }
        if (vaultEconomy == null) {
            getLogger().warning("Welcome reward skipped: Vault economy is unavailable.");
            return;
        }

        Set<UUID> claimed = welcomeRewardClaims.computeIfAbsent(joinedId, ignored -> ConcurrentHashMap.newKeySet());
        if (!claimed.add(clicker.getUniqueId())) {
            if (firstJoinRewardAlreadyClaimed != null && !firstJoinRewardAlreadyClaimed.isBlank()) {
                sendRawMessage(clicker, firstJoinRewardAlreadyClaimed.replace("<player>", joinedName));
            }
            return;
        }

        int min = Math.min(firstJoinRewardMin, firstJoinRewardMax);
        int max = Math.max(firstJoinRewardMin, firstJoinRewardMax);
        int amount = ThreadLocalRandom.current().nextInt(min, max + 1);
        if (!depositMoney(clicker, amount)) {
            claimed.remove(clicker.getUniqueId());
            return;
        }

        if (firstJoinRewardPlayerMessage != null && !firstJoinRewardPlayerMessage.isBlank()) {
            sendRawMessage(
                clicker,
                firstJoinRewardPlayerMessage
                    .replace("<player>", joinedName)
                    .replace("<reward>", String.valueOf(amount))
            );
        }
    }

    private boolean depositMoney(Player player, int amount) {
        try {
            Object result = vaultEconomy.getClass()
                .getMethod("depositPlayer", OfflinePlayer.class, double.class)
                .invoke(vaultEconomy, player, (double) amount);
            if (result != null) {
                Object success = result.getClass().getMethod("transactionSuccess").invoke(result);
                if (Boolean.FALSE.equals(success)) {
                    getLogger().warning("Welcome reward failed for " + player.getName() + ".");
                    return false;
                }
            }
            return true;
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Welcome reward error for " + player.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean isVanished(Player player) {
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

    private boolean hasVanishedMetadata(Player player) {
        if (!player.hasMetadata("vanished")) {
            return false;
        }

        for (org.bukkit.metadata.MetadataValue value : player.getMetadata("vanished")) {
            if (value != null && value.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private Boolean invokeBooleanMethod(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Object value = target.getClass().getMethod(methodName).invoke(target);
        return value instanceof Boolean bool ? bool : null;
    }

    private List<GroupMessageRule> loadGroupMessageRules(String path) {
        List<GroupMessageRule> rules = new ArrayList<>();
        org.bukkit.configuration.ConfigurationSection section = getConfig().getConfigurationSection(path);
        if (section == null) {
            return rules;
        }

        for (String key : section.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection ruleSection = section.getConfigurationSection(key);
            if (ruleSection == null) {
                continue;
            }

            List<String> groups = new ArrayList<>();
            for (String group : ruleSection.getStringList("groups")) {
                if (group != null && !group.isBlank()) {
                    groups.add(group.toLowerCase(Locale.ROOT));
                }
            }

            List<String> lines = new ArrayList<>(ruleSection.getStringList("lines"));
            if (!groups.isEmpty() && !lines.isEmpty()) {
                rules.add(new GroupMessageRule(groups, lines));
            }
        }

        return rules;
    }

    private List<String> resolveJoinLines(Player player) {
        for (GroupMessageRule rule : joinGroupRules) {
            if (matchesGroupRule(player, rule)) {
                return rule.lines();
            }
        }
        return joinLines;
    }

    private List<String> resolveQuitLines(Player player) {
        for (GroupMessageRule rule : quitGroupRules) {
            if (matchesGroupRule(player, rule)) {
                return rule.lines();
            }
        }
        return quitLines;
    }

    private boolean matchesGroupRule(Player player, GroupMessageRule rule) {
        String primaryGroup = getLuckPermsPrimaryGroup(player).toLowerCase(Locale.ROOT);
        if (!primaryGroup.isEmpty() && rule.groups().contains(primaryGroup)) {
            return true;
        }

        Object user = getLuckPermsUser(player);
        Object queryOptions = getLuckPermsQueryOptions(player);
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
                String groupName = invokeStringMethod(group, "getName");
                if (!groupName.isEmpty() && rule.groups().contains(groupName.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    private void broadcastConfiguredLines(List<String> lines, Player player, String reason) {
        if (lines.isEmpty()) {
            return;
        }

        for (String rawLine : lines) {
            Component component = serializer.deserialize(replacePlayerPlaceholders(rawLine, player, reason));
            Bukkit.getServer().sendMessage(component);
            if (sendToConsole) {
                getLogger().info(plainText(component));
            }
        }
    }

    private String replacePlayerPlaceholders(String raw, Player player, String reason) {
        return replacePlayerPlaceholders(raw, player, reason, 0);
    }

    private String replacePlayerPlaceholders(String raw, Player player, String reason, int rewardAmount) {
        String output = raw == null ? "" : raw;
        output = output.replace("<player>", player.getName());
        output = output.replace("<display_name>", plainText(player.displayName()));
        output = output.replace("<world>", player.getWorld().getName());
        output = output.replace("<online>", String.valueOf(Bukkit.getOnlinePlayers().size()));
        output = output.replace("<max_online>", String.valueOf(Bukkit.getMaxPlayers()));
        output = output.replace("<reason>", reason == null || reason.isBlank() ? "unknown" : reason);
        output = output.replace("<reward>", String.valueOf(rewardAmount));
        output = output.replace("<lp_prefix>", getLuckPermsPrefix(player));
        output = output.replace("<lp_suffix>", getLuckPermsSuffix(player));
        output = output.replace("<lp_primary_group>", getLuckPermsPrimaryGroup(player));
        return output;
    }

    private String replaceStatusPlaceholders(String raw) {
        String output = raw == null ? "" : raw;
        output = output.replace("{enabled}", String.valueOf(enabledFlag));
        output = output.replace("{send_to_console}", String.valueOf(sendToConsole));
        output = output.replace("{luckperms}", String.valueOf(luckPerms != null));
        output = output.replace("{cmi}", String.valueOf(cmiHooked));
        output = output.replace("{vault}", String.valueOf(vaultEconomy != null));
        output = output.replace("{join_enabled}", String.valueOf(joinEnabled));
        output = output.replace("{join_group_rules}", String.valueOf(joinGroupRules.size()));
        output = output.replace("{first_join_enabled}", String.valueOf(firstJoinEnabled));
        output = output.replace("{first_join_reward}", String.valueOf(firstJoinRewardEnabled));
        output = output.replace("{quit_enabled}", String.valueOf(quitEnabled));
        output = output.replace("{quit_group_rules}", String.valueOf(quitGroupRules.size()));
        output = output.replace("{kick_enabled}", String.valueOf(kickEnabled));
        return output;
    }

    private String getLuckPermsPrefix(Player player) {
        Object metaData = getLuckPermsMetaData(player);
        return invokeNullableStringMethod(metaData, "getPrefix");
    }

    private String getLuckPermsSuffix(Player player) {
        Object metaData = getLuckPermsMetaData(player);
        return invokeNullableStringMethod(metaData, "getSuffix");
    }

    private String getLuckPermsPrimaryGroup(Player player) {
        Object user = getLuckPermsUser(player);
        return invokeNullableStringMethod(user, "getPrimaryGroup");
    }

    private Object getLuckPermsMetaData(Player player) {
        Object user = getLuckPermsUser(player);
        Object queryOptions = getLuckPermsQueryOptions(player);
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

    private Object getLuckPermsQueryOptions(Player player) {
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

    private Object getLuckPermsUser(Player player) {
        if (luckPerms == null) {
            return null;
        }

        try {
            Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUniqueId());
            if (user != null) {
                return user;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Object adapter = luckPerms.getClass().getMethod("getPlayerAdapter", Class.class).invoke(luckPerms, Player.class);
            return adapter.getClass().getMethod("getUser", Player.class).invoke(adapter, player);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private String invokeNullableStringMethod(Object target, String methodName) {
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

    private String invokeStringMethod(Object target, String methodName) {
        return invokeNullableStringMethod(target, methodName);
    }

    private Method findCompatibleSingleArgMethod(Class<?> owner, String methodName, Object argument) {
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
            if (parameterType.isAssignableFrom(argumentClass)) {
                return method;
            }

            if (implementsInterface(argumentClass, parameterType)) {
                return method;
            }

            if (fallback == null && parameterType.getName().equals("java.lang.Object")) {
                fallback = method;
            }
        }
        return fallback;
    }

    private boolean implementsInterface(Class<?> concreteType, Class<?> candidateInterface) {
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

    private void sendPrefixedMessage(CommandSender sender, String raw) {
        String prefix = getConfig().getString("messages.prefix", "");
        sender.sendMessage(serializer.deserialize((prefix == null ? "" : prefix) + (raw == null ? "" : raw)));
    }

    private void sendRawMessage(CommandSender sender, String raw) {
        sender.sendMessage(serializer.deserialize(raw == null ? "" : raw));
    }

    private String plainText(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    private record GroupMessageRule(List<String> groups, List<String> lines) {
    }
}
