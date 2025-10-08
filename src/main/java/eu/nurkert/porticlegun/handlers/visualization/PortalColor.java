package eu.nurkert.porticlegun.handlers.visualization;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public final class PortalColor {

    private static final Map<String, PortalColor> BY_NAME = new LinkedHashMap<>();
    private static final List<PortalColor> REGISTERED_COLORS = new ArrayList<>();
    private static final List<PortalColor> DEFAULT_COLORS = new ArrayList<>();

    public static final PortalColor BLACK = registerDefault("BLACK", ChatColor.BLACK, Color.BLACK, DyeColor.BLACK);
    public static final PortalColor DARK_BLUE = registerDefault("DARK_BLUE", ChatColor.DARK_BLUE, Color.fromBGR(170, 0, 0), DyeColor.BLUE);
    public static final PortalColor DARK_GREEN = registerDefault("DARK_GREEN", ChatColor.DARK_GREEN, Color.GREEN, DyeColor.GREEN);
    public static final PortalColor DARK_AQUA = registerDefault("DARK_AQUA", ChatColor.DARK_AQUA, Color.AQUA, DyeColor.CYAN);
    public static final PortalColor DARK_RED = registerDefault("DARK_RED", ChatColor.DARK_RED, Color.RED, DyeColor.RED);
    public static final PortalColor DARK_PURPLE = registerDefault("DARK_PURPLE", ChatColor.DARK_PURPLE, Color.PURPLE, DyeColor.PURPLE);
    public static final PortalColor GOLD = registerDefault("GOLD", ChatColor.GOLD, Color.ORANGE, DyeColor.ORANGE);
    public static final PortalColor DARK_GRAY = registerDefault("DARK_GRAY", ChatColor.DARK_GRAY, Color.fromBGR(85, 85, 85), DyeColor.GRAY);
    public static final PortalColor BLUE = registerDefault("BLUE", ChatColor.BLUE, Color.BLUE, DyeColor.LIGHT_BLUE);
    public static final PortalColor GREEN = registerDefault("GREEN", ChatColor.GREEN, Color.LIME, DyeColor.LIME);
    public static final PortalColor AQUA = registerDefault("AQUA", ChatColor.AQUA, Color.TEAL, DyeColor.LIGHT_BLUE);
    public static final PortalColor RED = registerDefault("RED", ChatColor.RED, Color.MAROON, DyeColor.PINK);
    public static final PortalColor LIGHT_PURPLE = registerDefault("LIGHT_PURPLE", ChatColor.LIGHT_PURPLE, Color.FUCHSIA, DyeColor.MAGENTA);
    public static final PortalColor YELLOW = registerDefault("YELLOW", ChatColor.YELLOW, Color.YELLOW, DyeColor.YELLOW);
    public static final PortalColor WHITE = registerDefault("WHITE", ChatColor.WHITE, Color.WHITE, DyeColor.WHITE);

    private final String name;
    private final String chatColor;
    private final Color bukkitColor;
    private final DyeColor dyeColor;
    private final Particle.DustOptions dustOptions;
    private final boolean custom;

    private PortalColor(String name, String chatColor, Color bukkitColor, DyeColor dyeColor, boolean custom) {
        this.name = name;
        this.chatColor = chatColor;
        this.bukkitColor = bukkitColor;
        this.dyeColor = dyeColor;
        this.custom = custom;
        this.dustOptions = new Particle.DustOptions(bukkitColor, 1);
    }

    private static PortalColor registerDefault(String name, ChatColor chatColor, Color bukkitColor, DyeColor dyeColor) {
        PortalColor color = register(name, chatColor.toString(), bukkitColor, dyeColor, false);
        DEFAULT_COLORS.add(color);
        return color;
    }

    private static PortalColor register(String name, String chatColor, Color bukkitColor, DyeColor dyeColor, boolean custom) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(chatColor, "chatColor");
        Objects.requireNonNull(bukkitColor, "bukkitColor");
        DyeColor resolvedDyeColor = resolveDyeColor(bukkitColor, dyeColor);
        PortalColor color = new PortalColor(name, chatColor, bukkitColor, resolvedDyeColor, custom);
        PortalColor previous = BY_NAME.put(name, color);
        if (previous != null) {
            if (!custom) {
                BY_NAME.put(name, previous);
                return previous;
            }
            int index = REGISTERED_COLORS.indexOf(previous);
            if (index >= 0) {
                REGISTERED_COLORS.set(index, color);
            } else {
                REGISTERED_COLORS.add(color);
            }
            return color;
        }
        REGISTERED_COLORS.add(color);
        return color;
    }

    public static PortalColor registerCustom(String name, String chatColor, Color bukkitColor, DyeColor dyeColor) {
        String normalized = normalizeName(name);
        return register(normalized, chatColor, bukkitColor, dyeColor, true);
    }

    public static void unregisterCustomColors() {
        Iterator<PortalColor> iterator = REGISTERED_COLORS.iterator();
        while (iterator.hasNext()) {
            PortalColor color = iterator.next();
            if (color.custom) {
                iterator.remove();
            }
        }
        BY_NAME.clear();
        REGISTERED_COLORS.clear();
        for (PortalColor defaultColor : DEFAULT_COLORS) {
            BY_NAME.put(defaultColor.name, defaultColor);
            REGISTERED_COLORS.add(defaultColor);
        }
    }

    public static PortalColor valueOf(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Color name cannot be null");
        }
        PortalColor color = BY_NAME.get(normalizeName(name));
        if (color == null) {
            throw new IllegalArgumentException("No portal color named " + name);
        }
        return color;
    }

    public static PortalColor[] values() {
        return REGISTERED_COLORS.toArray(new PortalColor[0]);
    }

    public static List<PortalColor> valuesList() {
        return Collections.unmodifiableList(REGISTERED_COLORS);
    }

    public String getChatColor() {
        return chatColor;
    }

    public Color getBukkitColor() {
        return bukkitColor;
    }

    public DyeColor getDyeColor() {
        return dyeColor;
    }

    public Particle.DustOptions getDustOptions() {
        return dustOptions;
    }

    public PortalColor next() {
        List<PortalColor> colors = REGISTERED_COLORS;
        if (colors.isEmpty()) {
            return this;
        }
        int index = colors.indexOf(this);
        if (index < 0) {
            return colors.get(0);
        }
        return colors.get((index + 1) % colors.size());
    }

    public PortalColor previous() {
        List<PortalColor> colors = REGISTERED_COLORS;
        if (colors.isEmpty()) {
            return this;
        }
        int index = colors.indexOf(this);
        if (index < 0) {
            return colors.get(0);
        }
        return colors.get((index - 1 + colors.size()) % colors.size());
    }

    public String getName() {
        return name;
    }

    public boolean isCustom() {
        return custom;
    }

    @Override
    public String toString() {
        return name;
    }

    private static String normalizeName(String name) {
        return name.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    public static void reloadCustomColors(List<Map<?, ?>> entries, Logger logger) {
        unregisterCustomColors();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (Map<?, ?> rawEntry : entries) {
            if (rawEntry == null || rawEntry.isEmpty()) {
                continue;
            }
            String name = getString(rawEntry.get("name"));
            if (name == null || name.isEmpty()) {
                log(logger, "Skipping custom portal color without name.");
                continue;
            }

            String chatColorSource = getString(rawEntry.get("chat-color"));
            String chatColor = parseChatColor(chatColorSource);
            if (chatColor == null && chatColorSource != null) {
                log(logger, "Invalid chat-color '" + chatColorSource + "' for custom portal color '" + name + "'.");
            }

            String particleSource = getString(rawEntry.get("particle-color"));
            Color bukkitColor = parseBukkitColor(particleSource);
            if (bukkitColor == null && particleSource != null) {
                log(logger, "Invalid particle-color '" + particleSource + "' for custom portal color '" + name + "'.");
            }
            Object dyeSource = rawEntry.get("banner-dye");
            DyeColor dyeColor = parseDyeColor(dyeSource);
            if (dyeColor == null && dyeSource != null) {
                log(logger, "Invalid banner-dye '" + dyeSource + "' for custom portal color '" + name + "'. Banners only support vanilla dye colours.");
            }

            if (bukkitColor == null) {
                log(logger, "Skipping custom portal color '" + name + "' because no particle-color is defined.");
                continue;
            }

            if (chatColor == null) {
                chatColor = chatColorFromColor(bukkitColor, logger, name);
            }

            PortalColor portalColor = registerCustom(name, chatColor, bukkitColor, dyeColor);
            if (!portalColor.isCustom()) {
                log(logger, "Custom portal color '" + name + "' conflicts with an existing color and will be ignored.");
            }
        }
    }

    private static String parseChatColor(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.startsWith("#") && text.length() == 7) {
            return toHexChatCode(text.substring(1));
        }
        if (text.length() == 2 && (text.charAt(0) == '&' || text.charAt(0) == '§')) {
            ChatColor byChar = ChatColor.getByChar(text.charAt(1));
            return byChar != null ? byChar.toString() : null;
        }
        try {
            return ChatColor.valueOf(text.toUpperCase(Locale.ROOT)).toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Color parseBukkitColor(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.startsWith("#") && text.length() == 7) {
            try {
                int rgb = Integer.parseInt(text.substring(1), 16);
                return Color.fromRGB(rgb);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        DyeColor dyeColor = parseDyeColor(text);
        if (dyeColor != null) {
            return dyeColor.getColor();
        }
        return null;
    }

    private static DyeColor parseDyeColor(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String text = ((String) value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return DyeColor.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String chatColorFromColor(Color color, Logger logger, String name) {
        if (color == null) {
            return ChatColor.WHITE.toString();
        }
        try {
            return toHexChatCode(String.format("%06X", color.asRGB()));
        } catch (IllegalArgumentException exception) {
            log(logger, "Unable to create chat color for custom portal color '" + name + "': " + exception.getMessage());
            return ChatColor.WHITE.toString();
        }
    }

    private static DyeColor resolveDyeColor(Color target, DyeColor requested) {
        if (requested != null) {
            return requested;
        }
        if (target == null) {
            return DyeColor.WHITE;
        }
        DyeColor closest = DyeColor.WHITE;
        double smallestDistance = Double.MAX_VALUE;
        for (DyeColor candidate : DyeColor.values()) {
            Color candidateColor = candidate.getColor();
            if (candidateColor == null) {
                continue;
            }
            double distance = colorDistanceSquared(target, candidateColor);
            if (distance < smallestDistance) {
                smallestDistance = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    private static double colorDistanceSquared(Color a, Color b) {
        int dr = a.getRed() - b.getRed();
        int dg = a.getGreen() - b.getGreen();
        int db = a.getBlue() - b.getBlue();
        return (dr * dr) + (dg * dg) + (db * db);
    }

    private static String getString(Object value) {
        return value instanceof String ? ((String) value).trim() : null;
    }

    private static void log(Logger logger, String message) {
        if (logger != null && message != null) {
            logger.warning(message);
        }
    }

    private static String toHexChatCode(String hex) {
        String upper = hex.toUpperCase(Locale.ROOT);
        if (upper.length() != 6) {
            throw new IllegalArgumentException("Invalid hex length");
        }
        StringBuilder builder = new StringBuilder("§x");
        for (char character : upper.toCharArray()) {
            builder.append('§').append(character);
        }
        return builder.toString();
    }
}
