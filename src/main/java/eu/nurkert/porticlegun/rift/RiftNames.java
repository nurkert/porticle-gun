package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.messages.MessageManager;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

/**
 * Player-facing names of worlds and rift destinations.
 */
public final class RiftNames {

    private RiftNames() {}

    /**
     * The main world is "Overworld", its nether and end are "Nether" and "The End", every other world keeps its name.
     */
    public static String world(String worldName, CommandSender viewer) {
        World world = Bukkit.getWorld(worldName);
        List<World> worlds = Bukkit.getWorlds();
        String main = worlds.isEmpty() ? "" : worlds.get(0).getName();
        if (world != null && world.getName().equals(main)) {
            return message(viewer, "rift.world.overworld", Map.of());
        }
        if (worldName.equals(main + "_nether")) {
            return message(viewer, "rift.world.nether", Map.of());
        }
        if (worldName.equals(main + "_the_end")) {
            return message(viewer, "rift.world.end", Map.of());
        }
        return worldName;
    }

    public static String describe(RiftDestination destination, CommandSender viewer) {
        return switch (destination.type()) {
            case WORLD -> message(
                    viewer, "rift.destination.random", Map.of("world", world(destination.world(), viewer)));
            case SPAWN -> message(
                    viewer, "rift.destination.spawn", Map.of("world", world(destination.world(), viewer)));
            case HOME -> message(
                    viewer, "rift.destination.home", Map.of("home", String.valueOf(destination.home() + 1)));
            case BACK -> message(viewer, "rift.destination.back", Map.of());
            case DEATH -> message(viewer, "rift.destination.death", Map.of());
        };
    }

    private static String message(CommandSender viewer, String key, Map<String, String> placeholders) {
        return viewer == null
                ? MessageManager.getMessage(key, placeholders)
                : MessageManager.getMessage(viewer, key, placeholders);
    }
}
