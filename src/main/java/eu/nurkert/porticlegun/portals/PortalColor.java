package eu.nurkert.porticlegun.portals;

import org.bukkit.ChatColor;
import org.bukkit.Color;

public enum PortalColor {
    BLACK(ChatColor.BLACK, Color.BLACK),
    DARK_BLUE(ChatColor.DARK_BLUE, Color.fromBGR(170, 0, 0)),
    DARK_GREEN(ChatColor.DARK_GREEN, Color.GREEN),
    DARK_AQUA(ChatColor.DARK_AQUA, Color.AQUA),
    DARK_RED(ChatColor.DARK_RED, Color.RED),
    DARK_PURPLE(ChatColor.DARK_PURPLE, Color.PURPLE),
    GOLD(ChatColor.GOLD, Color.ORANGE),
    GRAY(ChatColor.GRAY, Color.fromBGR(170, 170, 170)),
    DARK_GRAY(ChatColor.DARK_GRAY, Color.fromBGR(85, 85, 85)),
    BLUE(ChatColor.BLUE, Color.BLUE),
    GREEN(ChatColor.GREEN, Color.LIME),
    AQUA(ChatColor.AQUA, Color.TEAL),
    RED(ChatColor.RED, Color.MAROON),
    LIGHT_PURPLE(ChatColor.LIGHT_PURPLE, Color.FUCHSIA),
    YELLOW(ChatColor.YELLOW, Color.YELLOW),
    WHITE(ChatColor.WHITE, Color.WHITE);

    ChatColor color;
    Color bukkitColor;
    PortalColor(ChatColor color, Color bukkitColor) {
        this.color = color;
        this.bukkitColor = bukkitColor;
    }

    public ChatColor getChatColor() {
        return color;
    }

    public Color getColor() {
        return bukkitColor;
    }
}
