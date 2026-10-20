package eu.nurkert.porticlegun.commands.sub;

import eu.nurkert.porticlegun.commands.SubCommand;
import eu.nurkert.porticlegun.config.ConfigManager;
import eu.nurkert.porticlegun.messages.MessageManager;
import eu.nurkert.porticlegun.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.portals.Portal.PortalType;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * {@code /porticlegun removeportal [linked]}: closes the portal the player looks at.
 */
public class RemovePortalCommand implements SubCommand {

    private final ActivePortalsHandler activePortals;
    private final ConfigManager config;

    public RemovePortalCommand(ActivePortalsHandler activePortals, ConfigManager config) {
        this.activePortals = activePortals;
        this.config = config;
    }

    @Override
    public String getName() {
        return "removeportal";
    }

    @Override
    public boolean isAdminOnly() {
        return false;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageManager.getMessage(sender, "commands.general.players-only"));
            return;
        }

        boolean includeLinked = false;
        if (args.length >= 1) {
            if (isLinkedRemovalOption(args[0])) {
                includeLinked = true;
            } else {
                player.sendMessage(
                        MessageManager.getMessage(player, "commands.usage.removeportal", Map.of("label", label)));
                return;
            }
        }

        Portal portal = findPortalPlayerLooksAt(player);
        if (portal == null) {
            player.sendMessage(MessageManager.getMessage(player, "commands.removeportal.no-target"));
            return;
        }

        Portal linkedPortal = portal.getLinkedPortal();
        PortalType type = portal.getType();
        activePortals.closePortal(portal);

        if (!includeLinked) {
            player.sendMessage(MessageManager.getMessage(
                    player, "commands.removeportal.removed-single", Map.of("type", getPortalTypeName(player, type))));
        } else if (linkedPortal != null) {
            PortalType linkedType = linkedPortal.getType();
            activePortals.closePortal(linkedPortal);
            player.sendMessage(MessageManager.getMessage(
                    player,
                    "commands.removeportal.removed-linked",
                    Map.of(
                            "type", getPortalTypeName(player, type),
                            "linked_type", getPortalTypeName(player, linkedType))));
        } else {
            player.sendMessage(MessageManager.getMessage(
                    player, "commands.removeportal.linked-not-found", Map.of("type", getPortalTypeName(player, type))));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Stream.of("linked", "both", "all")
                .filter(option -> option.startsWith(prefix))
                .collect(Collectors.toList());
    }

    private Portal findPortalPlayerLooksAt(Player player) {
        Location eyeLocation = player.getEyeLocation();
        Vector viewDirection = eyeLocation.getDirection().normalize();
        double maxDistance = config.getPortalMaxTargetDistance();

        Portal bestPortal = null;
        double bestDistance = Double.MAX_VALUE;

        for (Portal portal : activePortals.getRelevantPortals(player)) {
            if (portal == null
                    || portal.getLocation() == null
                    || portal.getLocation().getWorld() == null) {
                continue;
            }
            if (!portal.getLocation().getWorld().equals(eyeLocation.getWorld())) {
                continue;
            }

            Vector toPortal = portal.getLocation().toVector().subtract(eyeLocation.toVector());
            double distance = toPortal.length();
            if (distance == 0 || distance > maxDistance) {
                continue;
            }

            Vector directionToPortal = toPortal.clone().normalize();
            double alignment = viewDirection.dot(directionToPortal);
            if (alignment < 0.995D) {
                continue;
            }

            if (distance < bestDistance) {
                bestDistance = distance;
                bestPortal = portal;
            }
        }

        return bestPortal;
    }

    private String getPortalTypeName(CommandSender sender, PortalType type) {
        String key = type == PortalType.PRIMARY
                ? "commands.removeportal.type.primary"
                : "commands.removeportal.type.secondary";
        return MessageManager.getMessage(sender, key);
    }

    private boolean isLinkedRemovalOption(String argument) {
        String normalized = argument.toLowerCase(Locale.ROOT);
        return normalized.equals("linked")
                || normalized.equals("both")
                || normalized.equals("all")
                || normalized.equals("verlinkt")
                || normalized.equals("beide");
    }
}
