package eu.nurkert.porticlegun.messages;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Loads messages.yml and resolves player-facing texts in the player's language, with placeholders and colour codes.
 */
public final class MessageManager {

    private static final String MISSING = "Missing message: ";
    private static final Map<UUID, String> PLAYER_LANGUAGES = new HashMap<>();
    private static FileConfiguration messagesConfig;
    private static JavaPlugin plugin;
    private static String defaultLanguage = "en";

    private MessageManager() {}

    public static void init(JavaPlugin javaPlugin) {
        plugin = javaPlugin;
        loadMessages();
    }

    public static void reload(JavaPlugin javaPlugin) {
        plugin = javaPlugin;
        loadMessages();
    }

    private static void loadMessages() {
        if (plugin == null) {
            return;
        }

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for messages.");
        }

        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(file);
        // texts added in newer versions are missing from an older messages.yml; they come from the jar instead
        InputStream bundled = plugin.getResource("messages.yml");
        if (bundled != null) {
            messagesConfig.setDefaults(
                    YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
        defaultLanguage = normalizeLanguage(messagesConfig.getString("default-language", "en"));
        if (!getAvailableLanguages().contains(defaultLanguage)) {
            Set<String> languages = getAvailableLanguages();
            if (!languages.isEmpty()) {
                defaultLanguage = languages.iterator().next();
                plugin.getLogger()
                        .log(
                                Level.WARNING,
                                "Default language missing from messages.yml. Falling back to {0}.",
                                defaultLanguage);
            }
        }
    }

    public static String getMessage(String key) {
        return getMessage(defaultLanguage, key, Collections.emptyMap());
    }

    public static String getMessage(String key, Map<String, String> placeholders) {
        return getMessage(defaultLanguage, key, placeholders);
    }

    public static String getMessage(CommandSender sender, String key) {
        return getMessage(sender, key, Collections.emptyMap());
    }

    public static String getMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        String language = defaultLanguage;
        if (sender instanceof Player) {
            language = resolveLanguage((Player) sender);
        }
        return getMessage(language, key, placeholders);
    }

    public static String getMessage(String language, String key) {
        return getMessage(language, key, Collections.emptyMap());
    }

    public static String getMessage(String language, String key, Map<String, String> placeholders) {
        ensureMessagesLoaded();
        if (messagesConfig == null) {
            return ChatColor.RED + "Missing messages.yml";
        }

        String normalizedLanguage = normalizeLanguage(language);
        String path = normalizedLanguage + "." + key;
        String message = messagesConfig.getString(path);
        if (message == null) {
            message = messagesConfig.getString(defaultLanguage + "." + key);
        }
        if (message == null) {
            return ChatColor.RED + MISSING + key;
        }

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String placeholderKey = "%" + entry.getKey() + "%";
                String value = entry.getValue() == null ? "" : entry.getValue();
                message = message.replace(placeholderKey, value);
            }
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * @return whether a text (e.g. an item name) is the placeholder shown for a missing message
     */
    public static boolean isMissing(String text) {
        return text != null && text.contains(MISSING);
    }

    public static boolean matchesConfiguredValue(String key, String value) {
        ensureMessagesLoaded();
        if (messagesConfig == null) {
            return false;
        }
        for (String language : getAvailableLanguages()) {
            String message = messagesConfig.getString(language + "." + key);
            if (message != null) {
                String formatted = ChatColor.translateAlternateColorCodes('&', message);
                if (formatted.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Overrides the language used for a player instead of matching their Minecraft locale.
     *
     * @param playerId the player
     * @param language a language key from messages.yml (e.g. {@code de}), or {@code null} for automatic detection
     */
    public static void setPlayerLanguage(UUID playerId, String language) {
        if (language == null) {
            PLAYER_LANGUAGES.remove(playerId);
            return;
        }
        PLAYER_LANGUAGES.put(playerId, normalizeLanguage(language));
    }

    public static Optional<String> getPlayerLanguage(UUID playerId) {
        return Optional.ofNullable(PLAYER_LANGUAGES.get(playerId));
    }

    public static Set<String> getAvailableLanguages() {
        ensureMessagesLoaded();
        if (messagesConfig == null) {
            return Collections.emptySet();
        }
        Set<String> languages = new HashSet<>();
        for (String key : messagesConfig.getKeys(false)) {
            if ("default-language".equalsIgnoreCase(key)) {
                continue;
            }
            languages.add(normalizeLanguage(key));
        }
        return languages;
    }

    private static String resolveLanguage(Player player) {
        UUID playerId = player.getUniqueId();
        if (PLAYER_LANGUAGES.containsKey(playerId)) {
            String chosen = PLAYER_LANGUAGES.get(playerId);
            if (getAvailableLanguages().contains(chosen)) {
                return chosen;
            }
        }

        String locale = player.getLocale();
        if (locale != null) {
            String normalizedLocale = normalizeLanguage(locale);
            if (getAvailableLanguages().contains(normalizedLocale)) {
                return normalizedLocale;
            }
            String[] parts = normalizedLocale.split("[_-]");
            if (parts.length > 0) {
                String languageOnly = parts[0];
                if (getAvailableLanguages().contains(languageOnly)) {
                    return languageOnly;
                }
            }
        }

        return defaultLanguage;
    }

    private static String normalizeLanguage(String language) {
        if (language == null) {
            return defaultLanguage;
        }
        return language.toLowerCase(Locale.ROOT);
    }

    private static void ensureMessagesLoaded() {
        if (messagesConfig == null && plugin != null) {
            loadMessages();
        }
    }
}
