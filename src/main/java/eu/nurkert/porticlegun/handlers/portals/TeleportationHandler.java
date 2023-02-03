package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.handlers.AudioHandler;
import eu.nurkert.porticlegun.portals.PorticlePortal;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import javax.xml.bind.helpers.ValidationEventLocatorImpl;
import java.util.ArrayList;

public class TeleportationHandler implements Listener {

    @EventHandler
    public void on(PlayerMoveEvent event) {
        // check if the player moved to a different block
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockY() != event.getTo().getBlockY() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            Player player = event.getPlayer();
            ArrayList<PorticlePortal> portals = OpenedPortalsHandler.getRelevantPortals(player);
            for (PorticlePortal portal : portals) {
                if (portal.getLocation().equals(event.getTo().getBlock().getLocation()) || (portal.getDirection().getY() == -1.0 && portal.getLocation().equals(event.getTo().getBlock().getLocation().add(0, 1, 0)))) {
                    Location destination = portal.getLinkedPortal().getLocation().clone().add(0.5, portal.getLinkedPortal().getDirection().getY() == -1.0 ? -1 : 0, 0.5);
                    destination.setDirection(portal.getLinkedPortal().getDirection());
                    Vector velocity = player.getVelocity();
                    player.teleport(destination);
                    player.setVelocity(destination.getDirection().multiply(velocity.length()));
                    break;
                }
            }
        }

    }


}
