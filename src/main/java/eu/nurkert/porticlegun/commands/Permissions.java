package eu.nurkert.porticlegun.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Permission nodes of the plugin and helpers to check them.
 */
public final class Permissions {

    public static final String COMMAND = "porticlegun.command";
    public static final String ADMIN = "porticlegun.admin";

    private Permissions() {}

    /**
     * @return whether the sender may use admin commands; the console always may
     */
    public static boolean isAdmin(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission(ADMIN);
    }

    /**
     * @return whether the sender is a player allowed to use the general commands
     */
    public static boolean canUseGeneral(CommandSender sender) {
        return sender instanceof Player && sender.hasPermission(COMMAND);
    }
}
