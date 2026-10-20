package eu.nurkert.porticlegun.util;

import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Detects Bedrock players connected through Geyser and Floodgate. Without Floodgate every player counts as a Java
 * player.
 */
public final class BedrockSupport {

    /**
     * How a Bedrock player controls the game. Java players are always {@link #KEYBOARD_MOUSE}.
     */
    public enum InputMode {
        KEYBOARD_MOUSE,
        TOUCH,
        CONTROLLER,
        VR,
        UNKNOWN
    }

    private static boolean enabled;

    private BedrockSupport() {}

    public static void init(JavaPlugin plugin) {
        enabled = false;
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) {
            return;
        }
        try {
            FloodgateBridge.check();
            enabled = true;
            plugin.getLogger().info("Floodgate detected - Bedrock support enabled.");
        } catch (LinkageError | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Floodgate detected but its API could not be used.", exception);
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isBedrock(Player player) {
        return enabled && player != null && FloodgateBridge.isBedrock(player.getUniqueId());
    }

    /**
     * @return the player's current input mode; it can change while the player is online, so do not cache it
     */
    public static InputMode getInputMode(Player player) {
        if (!isBedrock(player)) {
            return InputMode.KEYBOARD_MOUSE;
        }
        return FloodgateBridge.getInputMode(player.getUniqueId());
    }

    /**
     * On a Bedrock controller the right trigger attacks (left click, primary portal) and the left trigger uses (right
     * click, secondary portal), so anything shown left/right has to be mirrored.
     */
    public static boolean hasMirroredTriggers(Player player) {
        return getInputMode(player) == InputMode.CONTROLLER;
    }
}
