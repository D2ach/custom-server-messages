package top.morndream.customservermessages;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Soft-depend Vault economy + first-join welcome rewards. */
public final class VaultHook {
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private final Map<UUID, Set<UUID>> welcomeRewardClaims = new ConcurrentHashMap<>();
    private final Logger logger;

    private Object vaultEconomy;

    public VaultHook(Logger logger) {
        this.logger = logger;
    }

    public void hook() {
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

    public boolean available() {
        return vaultEconomy != null;
    }

    public void clearClaims() {
        welcomeRewardClaims.clear();
    }

    public void clearClaimsFor(UUID joinedPlayerId) {
        welcomeRewardClaims.remove(joinedPlayerId);
    }

    public void prepareWelcomeClaims(UUID joinedPlayerId) {
        welcomeRewardClaims.putIfAbsent(joinedPlayerId, ConcurrentHashMap.newKeySet());
    }

    /**
     * Pays a one-time random reward to the clicker for welcoming {@code joinedId}.
     * No chat tip on success or re-click.
     */
    public void trySilentWelcomeReward(PluginSettings settings, Player clicker, UUID joinedId) {
        if (clicker.getUniqueId().equals(joinedId)) {
            return;
        }
        if (vaultEconomy == null) {
            logger.warning("Welcome reward skipped: Vault economy is unavailable.");
            return;
        }

        Set<UUID> claimed = welcomeRewardClaims.computeIfAbsent(joinedId, ignored -> ConcurrentHashMap.newKeySet());
        if (!claimed.add(clicker.getUniqueId())) {
            return;
        }

        int min = Math.min(settings.welcomeRewardMin(), settings.welcomeRewardMax());
        int max = Math.max(settings.welcomeRewardMin(), settings.welcomeRewardMax());
        int amount = ThreadLocalRandom.current().nextInt(min, max + 1);
        if (depositMoney(clicker, amount)) {
            return;
        }
        claimed.remove(clicker.getUniqueId());
    }

    public Component buildWelcomeButton(PluginSettings settings, UUID joinedId, String joinedName) {
        return serializer.deserialize(nullToEmpty(settings.firstJoinRewardButton()))
            .hoverEvent(HoverEvent.showText(serializer.deserialize(nullToEmpty(settings.firstJoinRewardButtonHover()))))
            .clickEvent(ClickEvent.callback(
                audience -> {
                    if (audience instanceof Player clicker) {
                        handleWelcomeClick(settings, clicker, joinedId, joinedName);
                    }
                },
                options -> options
                    .uses(ClickCallback.UNLIMITED_USES)
                    .lifetime(settings.firstJoinRewardButtonLifetime())
            ));
    }

    private void handleWelcomeClick(
        PluginSettings settings,
        Player clicker,
        UUID joinedId,
        String joinedName
    ) {
        if (!settings.firstJoinRewardEnabled() || clicker.getUniqueId().equals(joinedId)) {
            return;
        }
        if (vaultEconomy == null) {
            logger.warning("Welcome reward skipped: Vault economy is unavailable.");
            return;
        }

        Set<UUID> claimed = welcomeRewardClaims.computeIfAbsent(joinedId, ignored -> ConcurrentHashMap.newKeySet());
        if (!claimed.add(clicker.getUniqueId())) {
            return;
        }

        int min = Math.min(settings.firstJoinRewardMin(), settings.firstJoinRewardMax());
        int max = Math.max(settings.firstJoinRewardMin(), settings.firstJoinRewardMax());
        int amount = ThreadLocalRandom.current().nextInt(min, max + 1);
        if (!depositMoney(clicker, amount)) {
            claimed.remove(clicker.getUniqueId());
            return;
        }

        String message = settings.firstJoinRewardPlayerMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        clicker.sendMessage(serializer.deserialize(
            message
                .replace("<player>", joinedName)
                .replace("<reward>", String.valueOf(amount))
        ));
    }

    private boolean depositMoney(Player player, int amount) {
        try {
            Object result = vaultEconomy.getClass()
                .getMethod("depositPlayer", OfflinePlayer.class, double.class)
                .invoke(vaultEconomy, player, (double) amount);
            if (result != null) {
                Object success = result.getClass().getMethod("transactionSuccess").invoke(result);
                if (Boolean.FALSE.equals(success)) {
                    logger.warning("Welcome reward failed for " + player.getName() + ".");
                    return false;
                }
            }
            return true;
        } catch (ReflectiveOperationException exception) {
            logger.warning("Welcome reward error for " + player.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
