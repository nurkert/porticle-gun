package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.messages.MessageManager;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

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
        return describe(destination, viewer, null);
    }

    /** Like {@link #describe(RiftDestination, CommandSender)}, with the names the gun's homes were given. */
    public static String describe(RiftDestination destination, CommandSender viewer, ItemStack gun) {
        if (destination.type() == RiftDestination.Type.HOME && gun != null) {
            return home(gun, destination.home(), viewer);
        }
        return switch (destination.type()) {
            case WORLD -> message(
                    viewer, "rift.destination.random", Map.of("world", world(destination.world(), viewer)));
            case SPAWN -> message(
                    viewer, "rift.destination.spawn", Map.of("world", world(destination.world(), viewer)));
            case HOME -> message(
                    viewer, "rift.destination.home", Map.of("home", String.valueOf(destination.home() + 1)));
            case BACK -> message(viewer, "rift.destination.back", Map.of());
            case DEATH -> message(viewer, "rift.destination.death", Map.of());
            case STRUCTURE -> message(
                    viewer, "rift.destination.structure", Map.of("structure", structure(destination.world(), viewer)));
        };
    }

    /** @return the home's given name, or "Home 2" and the like */
    public static String home(ItemStack gun, int index, CommandSender viewer) {
        String name = RiftItems.homeName(gun, index);
        return name != null
                ? name
                : message(viewer, "rift.destination.home", Map.of("home", String.valueOf(index + 1)));
    }

    public static String structure(String id, CommandSender viewer) {
        return message(viewer, "rift.structure." + id, Map.of());
    }

    private static String message(CommandSender viewer, String key, Map<String, String> placeholders) {
        return viewer == null
                ? MessageManager.getMessage(key, placeholders)
                : MessageManager.getMessage(viewer, key, placeholders);
    }
}
