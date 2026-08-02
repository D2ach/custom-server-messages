package top.morndream.customservermessages;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Join / quit / kick broadcast listener. */
public final class PlayerMessageListener implements Listener {
    private static final long VANISH_JOIN_DELAY_TICKS = 2L;

    private final CustomServerMessagesPlugin plugin;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private final Set<UUID> kickedPlayers = new HashSet<>();

    public PlayerMessageListener(CustomServerMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    public void clear() {
        kickedPlayers.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        PluginSettings settings = plugin.settings();
        if (!settings.enabled()) {
            return;
        }

        event.joinMessage(null);
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();

        if (firstJoin || !player.isOp()) {
            handlePlayerJoin(player, firstJoin);
            return;
        }

        player.getScheduler().runDelayed(plugin, task -> {
            if (!settings.enabled() || !player.isOnline() || plugin.vanishHook().isVanished(player)) {
                return;
            }
            handlePlayerJoin(player, firstJoin);
        }, null, VANISH_JOIN_DELAY_TICKS);
    }

    private void handlePlayerJoin(Player player, boolean firstJoin) {
        PluginSettings settings = plugin.settings();
        if (firstJoin && settings.firstJoinEnabled()) {
            broadcastJoinLines(settings.firstJoinLines(), player, "", firstJoin);
            return;
        }

        if (settings.joinEnabled()) {
            broadcastJoinLines(resolveJoinLines(player), player, "", firstJoin);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        PluginSettings settings = plugin.settings();
        if (!settings.enabled()) {
            return;
        }

        event.quitMessage(null);
        Player player = event.getPlayer();
        plugin.vaultHook().clearClaimsFor(player.getUniqueId());
        if (kickedPlayers.remove(player.getUniqueId())) {
            return;
        }

        if (plugin.vanishHook().isVanished(player)) {
            return;
        }

        if (settings.quitEnabled()) {
            broadcastJoinLines(resolveQuitLines(player), player, "", false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        PluginSettings settings = plugin.settings();
        if (!settings.enabled()) {
            return;
        }

        event.leaveMessage(Component.empty());
        Player player = event.getPlayer();
        kickedPlayers.add(player.getUniqueId());
        player.getScheduler().runDelayed(plugin, task -> kickedPlayers.remove(player.getUniqueId()), null, 40L);

        if (plugin.vanishHook().isVanished(player)) {
            return;
        }

        if (settings.kickEnabled()) {
            broadcastJoinLines(settings.kickLines(), player, plainText(event.reason()), false);
        }
    }

    private void broadcastJoinLines(List<String> lines, Player joinedPlayer, String reason, boolean attachWelcomeButton) {
        if (lines.isEmpty()) {
            return;
        }

        PluginSettings settings = plugin.settings();
        VanishHook vanish = plugin.vanishHook();
        VaultHook vault = plugin.vaultHook();

        boolean canAttachButton = attachWelcomeButton
            && settings.firstJoinRewardEnabled()
            && vault.available();
        if (attachWelcomeButton && settings.firstJoinRewardEnabled() && !vault.available()) {
            plugin.getLogger().warning("Welcome button skipped: Vault economy is unavailable.");
        }

        UUID joinedId = joinedPlayer.getUniqueId();
        String joinedName = joinedPlayer.getName();
        if (canAttachButton) {
            vault.prepareWelcomeClaims(joinedId);
        }

        for (String rawLine : lines) {
            Component base = serializer.deserialize(replacePlayerPlaceholders(rawLine, joinedPlayer, reason));
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(base);
            }
            if (settings.sendToConsole()) {
                plugin.getLogger().info(plainText(base));
            }
        }

        // Button on its own line below the welcome message (not appended to the same line).
        if (canAttachButton) {
            Component button = vault.buildWelcomeButton(settings, joinedId, joinedName);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(joinedId) && !vanish.isVanished(online)) {
                    online.sendMessage(button);
                }
            }
        }
    }

    private List<String> resolveJoinLines(Player player) {
        PluginSettings settings = plugin.settings();
        for (GroupMessageRule rule : settings.joinGroupRules()) {
            if (plugin.luckPermsHook().matchesGroupRule(player, rule)) {
                return rule.lines();
            }
        }
        return settings.joinLines();
    }

    private List<String> resolveQuitLines(Player player) {
        PluginSettings settings = plugin.settings();
        for (GroupMessageRule rule : settings.quitGroupRules()) {
            if (plugin.luckPermsHook().matchesGroupRule(player, rule)) {
                return rule.lines();
            }
        }
        return settings.quitLines();
    }

    private String replacePlayerPlaceholders(String raw, Player player, String reason) {
        LuckPermsHook luckPerms = plugin.luckPermsHook();
        String output = raw == null ? "" : raw;
        output = output.replace("<player>", player.getName());
        output = output.replace("<display_name>", plainText(player.displayName()));
        output = output.replace("<world>", player.getWorld().getName());
        output = output.replace("<online>", String.valueOf(Bukkit.getOnlinePlayers().size()));
        output = output.replace("<max_online>", String.valueOf(Bukkit.getMaxPlayers()));
        output = output.replace("<reason>", reason == null || reason.isBlank() ? "unknown" : reason);
        output = output.replace("<reward>", "0");
        output = output.replace("<lp_prefix>", luckPerms.prefix(player));
        output = output.replace("<lp_suffix>", luckPerms.suffix(player));
        output = output.replace("<lp_primary_group>", luckPerms.primaryGroup(player));
        return output;
    }

    private static String plainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
