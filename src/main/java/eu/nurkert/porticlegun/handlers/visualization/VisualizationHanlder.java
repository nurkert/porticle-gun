package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.handlers.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;

public class VisualizationHanlder implements Listener {

    public VisualizationHanlder() {
         visualizePortals();
    }


    private static void visualizePortals() {
        new BukkitRunnable() {

            @Override
            public void run() {
                Collection<Portal> portals = ActivePortalsHandler.getAllPortals();
                if (portals.isEmpty()) {
                    return;
                }

                Collection<? extends Player> players = Bukkit.getOnlinePlayers();
                if (players.isEmpty()) {
                    return;
                }

                double radians = Math.toRadians(System.currentTimeMillis() * 0.2d);

                for (Portal portal : portals) {
                    Vector first = portal.getParticleLocation(radians);
                    Vector second = portal.getParticleLocation(radians + Math.PI);
                    if (first == null || second == null) {
                        continue;
                    }

                    GunColors gunColors = GunColorHandler.getColors(portal.getGunID());
                    PortalColor portalColor = gunColors.get(portal.getType());
                    Particle.DustOptions dustOptions = portalColor.getDustOptions();

                    World portalWorld = portal.getLocation().getWorld();
                    for (Player player : players) {
                        if (portalWorld != null && !player.getWorld().equals(portalWorld)) {
                            continue;
                        }
                        player.spawnParticle(Particle.REDSTONE, first.getX(), first.getY(), first.getZ(), 1, dustOptions);
                        player.spawnParticle(Particle.REDSTONE, second.getX(), second.getY(), second.getZ(), 1, dustOptions);
                    }
                }
            }
        }.runTaskTimer(PorticleGun.getInstance(), 0, 1);
    }
}
