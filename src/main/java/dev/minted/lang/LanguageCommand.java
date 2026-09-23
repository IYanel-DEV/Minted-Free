package dev.minted.lang;

import dev.minted.lang.LanguageManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Language commands:
 * /language - View current language
 * /language set <code> - Set your personal language
 * /language reset - Reset to server default
 * /language list - List available languages
 * /language global <code> - (Admin) Set global server language
 * /language reload - (Admin) Reload language files
 *
 * <p>Every line resolves through the selected language, so the command itself
 * switches language the moment a player picks one.
 */
public final class LanguageCommand implements CommandExecutor, TabCompleter {

    private final LanguageManager languageManager;

    public LanguageCommand(LanguageManager languageManager) {
        this.languageManager = languageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showCurrent(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "set":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can set personal language.");
                    return true;
                }
                if (args.length < 2) {
                    send(sender, "language.usage", "label", label);
                    return true;
                }
                setPlayerLanguage((Player) sender, args[1]);
                break;

            case "reset":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can reset personal language.");
                    return true;
                }
                resetPlayerLanguage((Player) sender);
                break;

            case "list":
                listLanguages(sender);
                break;

            case "global":
                if (!sender.hasPermission("minted.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 2) {
                    send(sender, "language.usage", "label", label);
                    return true;
                }
                setGlobalLanguage(sender, args[1]);
                break;

            case "reload":
                if (!sender.hasPermission("minted.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                languageManager.reload();
                send(sender, "language.reloaded");
                break;

            default:
                send(sender, "language.usage", "label", label);
                break;
        }
        return true;
    }

    private void showCurrent(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            String playerLang = languageManager.getPlayerLanguage(player.getUniqueId());
            send(player, "language.current",
                    "language", displayName(playerLang, playerLang), "global", languageManager.getGlobalLanguage());
        } else {
            String global = languageManager.getGlobalLanguage();
            send(sender, "language.current_global", "language", displayName(global, global));
        }
    }

    private void setPlayerLanguage(Player player, String code) {
        code = code.toLowerCase(Locale.ROOT);
        if (!languageManager.isLanguageAvailable(code)) {
            send(player, "language.not_available", "code", code);
            player.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", languageManager.getAvailableLanguageCodes()));
            return;
        }

        languageManager.setPlayerLanguage(player.getUniqueId(), code);
        send(player, "language.set", "language", displayName(code, code), "code", code);
    }

    private void resetPlayerLanguage(Player player) {
        languageManager.resetPlayerLanguage(player.getUniqueId());
        String global = languageManager.getGlobalLanguage();
        send(player, "language.reset", "language", displayName(global, global));
    }

    private void listLanguages(CommandSender sender) {
        send(sender, "language.available_list");
        for (String code : languageManager.getAvailableLanguageCodes()) {
            boolean isDefault = code.equals(languageManager.getGlobalLanguage());
            String marker = isDefault ? "&a(default)" : "";
            send(sender, "language.list_entry", "code", code,
                    "name", languageManager.getAvailableLanguages().get(code), "marker", marker);
        }
    }

    private void setGlobalLanguage(CommandSender sender, String code) {
        code = code.toLowerCase(Locale.ROOT);
        if (!languageManager.isLanguageAvailable(code)) {
            send(sender, "language.not_available", "code", code);
            return;
        }

        String oldGlobal = languageManager.getGlobalLanguage();
        languageManager.setGlobalLanguage(code);
        String displayName = languageManager.getAvailableLanguages().get(code);

        send(sender, "language.global_changed",
                "old", displayName(oldGlobal, oldGlobal), "new", displayName, "code", code);

        // Notify online players whose language matches old global
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerLang = languageManager.getPlayerLanguage(player.getUniqueId());
            if (playerLang.equals(oldGlobal)) {
                send(player, "language.global_notify", "name", displayName, "code", code);
            }
        }
    }

    /**
     * Resolves a message for the sender: per-player when a player, otherwise the
     * current global language.
     */
    private void send(CommandSender sender, String key, String... pairs) {
        if (sender instanceof Player) {
            sender.sendMessage(languageManager.get((Player) sender, key, pairs));
        } else {
            sender.sendMessage(languageManager.getGlobal(key, pairs));
        }
    }

    private String displayName(String code, String fallback) {
        String name = languageManager.getAvailableLanguages().get(code);
        return name == null ? fallback : name;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("set", "reset", "list"));
            if (sender.hasPermission("minted.admin")) {
                completions.addAll(Arrays.asList("global", "reload"));
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set") || (args[0].equalsIgnoreCase("global") && sender.hasPermission("minted.admin"))) {
                completions.addAll(languageManager.getAvailableLanguageCodes());
            }
        }

        return filter(completions, args[args.length - 1]);
    }

    private List<String> filter(List<String> list, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String s : list) {
            if (s.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(s);
            }
        }
        return result;
    }
}