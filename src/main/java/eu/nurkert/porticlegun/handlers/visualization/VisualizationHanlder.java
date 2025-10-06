package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.handlers.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.*;
import org.bukkit.entity.Player;
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
                    double radians = Math.toRadians(System.currentTimeMillis() / 5);
                    Vector[] locs = {portal.getParticleLocation(radians) , portal.getParticleLocation(radians + Math.PI)};
                    Color color = GunColorHandler.getColors(portal.getGunID()).get(portal.getType()).getBukkitColor();
                    for(Player player : Bukkit.getOnlinePlayers()) {
                        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1);
                        for(Vector loc : locs) {
                            player.spawnParticle(Particle.DUST, loc.getX(), loc.getY(), loc.getZ(), 1, dustOptions);
                        }
                        //player.getWorld().spawnParticle(Particle.NOTE, player.getLocation(), 1, null);


                    }

                }
            }
        }.runTaskTimer(PorticleGun.getInstance(), 0, 1);
    }
}
