package top.morndream.customservermessages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Loaded plugin configuration snapshot. */
public final class PluginSettings {
    private boolean enabled;
    private boolean sendToConsole;

    private boolean joinEnabled;
    private List<String> joinLines = List.of();
    private List<GroupMessageRule> joinGroupRules = List.of();

    private boolean firstJoinEnabled;
    private List<String> firstJoinLines = List.of();
    private boolean firstJoinRewardEnabled;
    private int firstJoinRewardMin;
    private int firstJoinRewardMax;
    private String firstJoinRewardButton;
    private String firstJoinRewardButtonHover;
    private String firstJoinRewardPlayerMessage;
    private Duration firstJoinRewardButtonLifetime = Duration.ofMinutes(10);

    private boolean quitEnabled;
    private List<String> quitLines = List.of();
    private List<GroupMessageRule> quitGroupRules = List.of();

    private boolean kickEnabled;
    private List<String> kickLines = List.of();

    public void load(FileConfiguration config) {
        enabled = config.getBoolean("enabled", true);
        sendToConsole = config.getBoolean("send-to-console", false);

        joinEnabled = config.getBoolean("join.enabled", true);
        joinLines = new ArrayList<>(config.getStringList("join.lines"));
        joinGroupRules = loadGroupMessageRules(config, "join.group-messages");

        firstJoinEnabled = config.getBoolean("first-join.enabled", true);
        firstJoinLines = new ArrayList<>(config.getStringList("first-join.lines"));
        firstJoinRewardEnabled = config.getBoolean("first-join.reward.enabled", true);
        firstJoinRewardMin = config.getInt("first-join.reward.min", 10);
        firstJoinRewardMax = config.getInt("first-join.reward.max", 20);
        firstJoinRewardButton = config.getString("first-join.reward.button", "&a&l[点击欢迎]");
        firstJoinRewardButtonHover = config.getString(
            "first-join.reward.button-hover",
            "&7首次点击可获得金币"
        );
        firstJoinRewardPlayerMessage = config.getString(
            "first-join.reward.player-message",
            "&a你获得了 &e<reward> &a金币。"
        );
        firstJoinRewardButtonLifetime = Duration.ofMinutes(
            Math.max(1, config.getInt("first-join.reward.button-lifetime-minutes", 10))
        );

        quitEnabled = config.getBoolean("quit.enabled", true);
        quitLines = new ArrayList<>(config.getStringList("quit.lines"));
        quitGroupRules = loadGroupMessageRules(config, "quit.group-messages");

        kickEnabled = config.getBoolean("kick.enabled", true);
        kickLines = new ArrayList<>(config.getStringList("kick.lines"));
    }

    private static List<GroupMessageRule> loadGroupMessageRules(FileConfiguration config, String path) {
        List<GroupMessageRule> rules = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return rules;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection ruleSection = section.getConfigurationSection(key);
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

    public boolean enabled() {
        return enabled;
    }

    public boolean sendToConsole() {
        return sendToConsole;
    }

    public boolean joinEnabled() {
        return joinEnabled;
    }

    public List<String> joinLines() {
        return joinLines;
    }

    public List<GroupMessageRule> joinGroupRules() {
        return joinGroupRules;
    }

    public boolean firstJoinEnabled() {
        return firstJoinEnabled;
    }

    public List<String> firstJoinLines() {
        return firstJoinLines;
    }

    public boolean firstJoinRewardEnabled() {
        return firstJoinRewardEnabled;
    }

    public int firstJoinRewardMin() {
        return firstJoinRewardMin;
    }

    public int firstJoinRewardMax() {
        return firstJoinRewardMax;
    }

    public String firstJoinRewardButton() {
        return firstJoinRewardButton;
    }

    public String firstJoinRewardButtonHover() {
        return firstJoinRewardButtonHover;
    }

    public String firstJoinRewardPlayerMessage() {
        return firstJoinRewardPlayerMessage;
    }

    public Duration firstJoinRewardButtonLifetime() {
        return firstJoinRewardButtonLifetime;
    }

    public boolean quitEnabled() {
        return quitEnabled;
    }

    public List<String> quitLines() {
        return quitLines;
    }

    public List<GroupMessageRule> quitGroupRules() {
        return quitGroupRules;
    }

    public boolean kickEnabled() {
        return kickEnabled;
    }

    public List<String> kickLines() {
        return kickLines;
    }
}
