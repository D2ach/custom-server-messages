package top.morndream.customservermessages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class CustomServerMessagesPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private final PluginSettings settings = new PluginSettings();
    private final VanishHook vanishHook = new VanishHook();
    private final LuckPermsHook luckPermsHook = new LuckPermsHook();
    private VaultHook vaultHook;
    private WelcomeService welcomeService;
    private PlayerMessageListener messageListener;

    @Override
    public void onEnable() {
        vaultHook = new VaultHook(getLogger());
        welcomeService = new WelcomeService(this);
        saveDefaultConfig();
        reloadAll();
        messageListener = new PlayerMessageListener(this);
        getServer().getPluginManager().registerEvents(messageListener, this);

        Objects.requireNonNull(getCommand("customservermessages"), "Command customservermessages is not defined in plugin.yml")
            .setExecutor(this);
        Objects.requireNonNull(getCommand("customservermessages"), "Command customservermessages is not defined in plugin.yml")
            .setTabCompleter(this);

        WelcomeCommand welcomeCommand = new WelcomeCommand(this);
        Objects.requireNonNull(getCommand("welcome"), "Command welcome is not defined in plugin.yml")
            .setExecutor(welcomeCommand);
        Objects.requireNonNull(getCommand("welcome"), "Command welcome is not defined in plugin.yml")
            .setTabCompleter(welcomeCommand);

        getLogger().info(
            "CustomServerMessages enabled. join=" + settings.joinEnabled()
                + ", joinGroupRules=" + settings.joinGroupRules().size()
                + ", firstJoin=" + settings.firstJoinEnabled()
                + ", firstJoinReward=" + settings.firstJoinRewardEnabled()
                + ", welcome=" + settings.welcomeEnabled()
                + ", quit=" + settings.quitEnabled()
                + ", quitGroupRules=" + settings.quitGroupRules().size()
                + ", kick=" + settings.kickEnabled()
                + ", luckPerms=" + luckPermsHook.hooked()
                + ", cmi=" + vanishHook.hooked()
                + ", vault=" + vaultHook.available()
        );
    }

    @Override
    public void onDisable() {
        if (messageListener != null) {
            messageListener.clear();
        }
        vaultHook.clearClaims();
    }

    public void reloadAll() {
        reloadConfig();
        settings.load(getConfig());
        luckPermsHook.hook();
        vanishHook.hook();
        vaultHook.hook();
    }

    public PluginSettings settings() {
        return settings;
    }

    public VanishHook vanishHook() {
        return vanishHook;
    }

    public VaultHook vaultHook() {
        return vaultHook;
    }

    public LuckPermsHook luckPermsHook() {
        return luckPermsHook;
    }

    public WelcomeService welcomeService() {
        return welcomeService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
            reloadAll();
            sendPrefixedMessage(sender, getConfig().getString("messages.reloaded", "&aReloaded."));
            return true;
        }

        sendRawMessage(sender, "&7Usage: /" + label + " [status|reload]");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
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

    private String replaceStatusPlaceholders(String raw) {
        String output = raw == null ? "" : raw;
        output = output.replace("{enabled}", String.valueOf(settings.enabled()));
        output = output.replace("{send_to_console}", String.valueOf(settings.sendToConsole()));
        output = output.replace("{luckperms}", String.valueOf(luckPermsHook.hooked()));
        output = output.replace("{cmi}", String.valueOf(vanishHook.hooked()));
        output = output.replace("{vault}", String.valueOf(vaultHook.available()));
        output = output.replace("{join_enabled}", String.valueOf(settings.joinEnabled()));
        output = output.replace("{join_group_rules}", String.valueOf(settings.joinGroupRules().size()));
        output = output.replace("{first_join_enabled}", String.valueOf(settings.firstJoinEnabled()));
        output = output.replace("{first_join_reward}", String.valueOf(settings.firstJoinRewardEnabled()));
        output = output.replace("{welcome_enabled}", String.valueOf(settings.welcomeEnabled()));
        output = output.replace("{quit_enabled}", String.valueOf(settings.quitEnabled()));
        output = output.replace("{quit_group_rules}", String.valueOf(settings.quitGroupRules().size()));
        output = output.replace("{kick_enabled}", String.valueOf(settings.kickEnabled()));
        return output;
    }

    private void sendPrefixedMessage(CommandSender sender, String raw) {
        String prefix = getConfig().getString("messages.prefix", "");
        sender.sendMessage(serializer.deserialize(prefix + (raw == null ? "" : raw)));
    }

    private void sendRawMessage(CommandSender sender, String raw) {
        sender.sendMessage(serializer.deserialize(raw == null ? "" : raw));
    }
}
