package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.Collection;

public class TeleportationHandler implements Listener {

    @EventHandler
    public void on(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (event.getFrom().getBlockX() == to.getBlockX()
                && event.getFrom().getBlockY() == to.getBlockY()
                && event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        World playerWorld = to.getWorld();
        if (playerWorld == null) {
            return;
        }

        Collection<Portal> portals = ActivePortalsHandler.getAllPortals();
        if (portals.isEmpty()) {
            return;
        }

        Location above = null;
        for (Portal portal : portals) {
            Location portalLocation = portal.getLocation();
            if (portalLocation == null || portalLocation.getWorld() != playerWorld) {
                continue;
            }

            boolean inPortal = portal.isInPortal(to);
            if (!inPortal && portal.getDirection().getY() == -1.0) {
                if (above == null) {
                    above = to.clone().add(0, 1, 0);
                }
                inPortal = portal.isInPortal(above);
            }

            if (!inPortal) {
                continue;
            }

            Portal linked = portal.getLinkedPortal();
            if (linked == null) {
                continue;
            }

            Location destination = linked.getLocation().clone().add(0.5, linked.getDirection().getY() == -1.0 ? -1 : 0, 0.5);
            destination.setDirection(linked.getDirection());

            Vector velocity = player.getVelocity();
            double speed = velocity.length();
            player.teleport(destination);
            player.setVelocity(destination.getDirection().multiply(speed));
            break;
        }
    }
}
