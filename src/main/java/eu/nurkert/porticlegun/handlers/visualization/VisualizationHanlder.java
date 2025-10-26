package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.handlers.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.handlers.visualization.GunColorHandler;
import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;

public class VisualizationHanlder implements Listener {

    public VisualizationHanlder() {
         visualizePortals();
    }


    private static void visualizePortals() {
        new BukkitRunnable() {

            @Override
            public void run() {
                ArrayList<Portal> portals = ActivePortalsHandler.getAllPortal();
                for (int i = 0; i < portals.size(); i++) {
                    Portal portal = portals.get(i);
                    if (!portal.isVisualizationReady()) {
                        continue;
                    }

                    Location portalLocation = portal.getLocation();
                    if (portalLocation == null || portalLocation.getWorld() == null) {
                        continue;
                    }

                    double radians = Math.toRadians(System.currentTimeMillis() / 5);
                    Vector[] locs = {portal.getParticleLocation(radians), portal.getParticleLocation(radians + Math.PI)};
                    Color color = GunColorHandler.getColors(portal.getGunID()).get(portal.getType()).getBukkitColor();
                    Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1);

                    for (Vector loc : locs) {
                        if (loc == null) {
                            continue;
                        }

                        portalLocation.getWorld().spawnParticle(
                                Particle.DUST,
                                loc.getX(),
                                loc.getY(),
                                loc.getZ(),
                                1,
                                dustOptions
                        );
                    }
                }
            }
        }.runTaskTimer(PorticleGun.getInstance(), 0, 1);
    }
}
