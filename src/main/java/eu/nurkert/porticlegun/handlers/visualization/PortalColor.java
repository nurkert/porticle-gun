package eu.nurkert.porticlegun.handlers.visualization;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Particle;

public enum PortalColor {
    BLACK(ChatColor.BLACK, Color.BLACK, DyeColor.BLACK),
    DARK_BLUE(ChatColor.DARK_BLUE, Color.fromBGR(170, 0, 0), DyeColor.BLUE),
    DARK_GREEN(ChatColor.DARK_GREEN, Color.GREEN, DyeColor.GREEN),
    DARK_AQUA(ChatColor.DARK_AQUA, Color.AQUA, DyeColor.CYAN),
    DARK_RED(ChatColor.DARK_RED, Color.RED , DyeColor.RED),
    DARK_PURPLE(ChatColor.DARK_PURPLE, Color.PURPLE, DyeColor.PURPLE),
    GOLD(ChatColor.GOLD, Color.ORANGE, DyeColor.ORANGE),
    GRAY(ChatColor.GRAY, Color.fromBGR(170, 170, 170), DyeColor.LIGHT_GRAY),
    DARK_GRAY(ChatColor.DARK_GRAY, Color.fromBGR(85, 85, 85), DyeColor.GRAY),
    BLUE(ChatColor.BLUE, Color.BLUE, DyeColor.LIGHT_BLUE),
    GREEN(ChatColor.GREEN, Color.LIME, DyeColor.LIME),
    AQUA(ChatColor.AQUA, Color.TEAL, DyeColor.LIGHT_BLUE),
    RED(ChatColor.RED, Color.MAROON, DyeColor.PINK),
    LIGHT_PURPLE(ChatColor.LIGHT_PURPLE, Color.FUCHSIA, DyeColor.MAGENTA),
    YELLOW(ChatColor.YELLOW, Color.YELLOW, DyeColor.YELLOW),
    WHITE(ChatColor.WHITE, Color.WHITE, DyeColor.WHITE);

    ChatColor color;
    Color bukkitColor;

    DyeColor dyeColor;
    private final Particle.DustOptions dustOptions;

    PortalColor(ChatColor color, Color bukkitColor, DyeColor dyeColor) {
        this.color = color;
        this.bukkitColor = bukkitColor;
        this.dyeColor = dyeColor;
        this.dustOptions = new Particle.DustOptions(bukkitColor, 1);
    }

    public ChatColor getChatColor() {
        return color;
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
        return values()[(ordinal() + 1) % values().length];
    }

    public PortalColor previous() {
        return values()[(ordinal() - 1 + values().length) % values().length];
    }
}
