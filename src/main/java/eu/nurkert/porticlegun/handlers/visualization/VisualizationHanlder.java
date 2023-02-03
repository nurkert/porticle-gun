package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.handlers.portals.OpenedPortalsHandler;
import eu.nurkert.porticlegun.portals.PorticlePortal;
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
                ArrayList<PorticlePortal> portals = OpenedPortalsHandler.getAllPortal();
                for (int i = 0; i < portals.size(); i++) {
                    PorticlePortal portal = portals.get(i);
                    double radians = Math.toRadians(System.currentTimeMillis() / 5);
                    Vector[] locs = {portal.nextParticleLocation(radians) , portal.nextParticleLocation(radians + Math.PI)};
                    Color color = portal.getColor().getBukkitColor();
                    for(Player player : Bukkit.getOnlinePlayers()) {
                        Particle.DustOptions dustOptions = new Particle.DustOptions(portal.getColor().getBukkitColor(), 1);
                        for(Vector loc : locs) {
                            player.spawnParticle(Particle.REDSTONE, loc.getX(), loc.getY(), loc.getZ(), 1, dustOptions);
                        }
                        //player.getWorld().spawnParticle(Particle.NOTE, player.getLocation(), 1, null);


                    }

                }
            }
        }.runTaskTimer(PorticleGun.getInstance(), 0, 1);
    }
}
