package top.morndream.customservermessages;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Builds and delivers the plugin's custom welcome interaction. */
public final class WelcomeService {
    private final CustomServerMessagesPlugin plugin;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public WelcomeService(CustomServerMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendWelcomeButtonToOnlinePlayers(String newPlayerName, UUID joinedPlayerId) {
        plugin.vaultHook().prepareWelcomeClaims(joinedPlayerId);
        Component button = createButton(newPlayerName, joinedPlayerId, true);
        sendToPlayers(button, joinedPlayerId);
    }

    public void sendPreviewButton() {
        Component button = createButton("测试", UUID.randomUUID(), false);
        sendToPlayers(button, null);
    }

    public void sendWelcomeMessageAsPlayer(Player player, String newPlayerName) {
        Component chatLine = player.displayName()
            .append(Component.text(": "))
            .append(serializer.deserialize(
                plugin.settings().welcomeMessageTemplate().replace("[new]", newPlayerName)
            ));
        sendToPlayers(chatLine, null);
    }

    private Component createButton(String newPlayerName, UUID joinedPlayerId, boolean rewardEnabled) {
        PluginSettings settings = plugin.settings();
        Component hover = serializer.deserialize(
            settings.welcomeButtonHover().replace("[new]", newPlayerName)
        );
        return serializer.deserialize(settings.welcomeButton().replace("[new]", newPlayerName))
            .hoverEvent(HoverEvent.showText(hover))
            .clickEvent(ClickEvent.callback(
                audience -> {
                    if (!(audience instanceof Player clicker)) {
                        return;
                    }
                    if (clicker.getUniqueId().equals(joinedPlayerId)) {
                        return;
                    }
                    sendWelcomeMessageAsPlayer(clicker, newPlayerName);
                    if (rewardEnabled) {
                        plugin.vaultHook().trySilentWelcomeReward(settings, clicker, joinedPlayerId);
                    }
                },
                options -> options
                    .uses(ClickCallback.UNLIMITED_USES)
                    .lifetime(settings.welcomeRewardButtonLifetime())
            ));
    }

    private void sendToPlayers(Component component, UUID excludedPlayerId) {
        VanishHook vanish = plugin.vanishHook();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (excludedPlayerId != null && onlinePlayer.getUniqueId().equals(excludedPlayerId)) {
                continue;
            }
            if (vanish.isVanished(onlinePlayer)) {
                continue;
            }
            onlinePlayer.getScheduler().execute(
                plugin,
                () -> onlinePlayer.sendMessage(component),
                null,
                1L
            );
        }
    }
}
