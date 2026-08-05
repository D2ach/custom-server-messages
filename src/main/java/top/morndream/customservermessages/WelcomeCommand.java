package top.morndream.customservermessages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Administrative and manual commands for the custom welcome feature. */
public final class WelcomeCommand implements CommandExecutor, TabCompleter {
    private final CustomServerMessagesPlugin plugin;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public WelcomeCommand(CustomServerMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    private void sendLegacy(CommandSender sender, String raw) {
        sender.sendMessage(serializer.deserialize(raw == null ? "" : raw));
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (args.length == 0) {
            sender.sendMessage(command.getUsage());
            return true;
        }

        PluginSettings settings = plugin.settings();
        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "reload":
                if (!sender.hasPermission("welcome.reload")) {
                    sendLegacy(sender, settings.welcomeErrorMessage());
                    return true;
                }
                plugin.reloadAll();
                sendLegacy(sender, settings.welcomeReloadMessage());
                return true;

            case "try":
                if (!sender.hasPermission("welcome.try")) {
                    sendLegacy(sender, settings.welcomeErrorMessage());
                    return true;
                }
                plugin.welcomeService().sendPreviewButton();
                return true;

            case "msg":
                if (!sender.hasPermission("welcome.msg")) {
                    sendLegacy(sender, settings.welcomeErrorMessage());
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sendLegacy(sender, settings.welcomePlayerOnlyMessage());
                    return true;
                }
                if (args.length < 2) {
                    sendLegacy(sender, settings.welcomeErrorMessage());
                    return true;
                }
                plugin.welcomeService().sendWelcomeMessageAsPlayer(player, args[1]);
                return true;

            default:
                sendLegacy(sender, settings.welcomeErrorMessage());
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            if ("reload".startsWith(input) && sender.hasPermission("welcome.reload")) {
                completions.add("reload");
            }
            if ("try".startsWith(input) && sender.hasPermission("welcome.try")) {
                completions.add("try");
            }
            if ("msg".startsWith(input) && sender.hasPermission("welcome.msg")) {
                completions.add("msg");
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("msg") && sender.hasPermission("welcome.msg")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                    completions.add(online.getName());
                }
            }
            return completions;
        }

        return List.of();
    }
}
