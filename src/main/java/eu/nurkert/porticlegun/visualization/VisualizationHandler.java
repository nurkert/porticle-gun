package eu.nurkert.porticlegun.visualization;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.portals.Portal;
import java.util.ArrayList;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Draws the particle outline of every open portal near players.
 */
public class VisualizationHandler implements Listener {

    // particles farther away than this are not shown by the client anyway
    private static final double VIEW_DISTANCE = 48;

    private final ActivePortalsHandler activePortals;
    private final GunColorHandler gunColors;

    public VisualizationHandler(ActivePortalsHandler activePortals, GunColorHandler gunColors) {
        this.activePortals = activePortals;
        this.gunColors = gunColors;
        visualizePortals();
    }

    private void visualizePortals() {
        new BukkitRunnable() {

            @Override
            public void run() {
                ArrayList<Portal> portals = activePortals.getAllPortal();
                double radians = Math.toRadians(System.currentTimeMillis() / 5);
                for (int i = 0; i < portals.size(); i++) {
                    Portal portal = portals.get(i);
                    if (!portal.isVisualizationReady()) {
                        continue;
                    }

                    Location portalLocation = portal.getLocation();
                    // skip portals in unloaded chunks or without anyone near enough to see their particles
                    if (portalLocation == null
                            || portalLocation.getWorld() == null
                            || !portalLocation
                                    .getWorld()
                                    .isChunkLoaded(portalLocation.getBlockX() >> 4, portalLocation.getBlockZ() >> 4)
                            || !viewerNearby(portalLocation)) {
                        continue;
                    }

                    Vector[] locs = {portal.getParticleLocation(radians), portal.getParticleLocation(radians + Math.PI)
                    };
                    Color color = gunColors
                            .getColors(portal.getGunID())
                            .get(portal.getType())
                            .getBukkitColor();
                    Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1);

                    for (Vector loc : locs) {
                        if (loc == null) {
                            continue;
                        }

                        portalLocation
                                .getWorld()
                                .spawnParticle(Particle.DUST, loc.getX(), loc.getY(), loc.getZ(), 1, dustOptions);
                    }
                }
            }
        }.runTaskTimer(PorticleGun.getInstance(), 0, 1);
    }

    private static boolean viewerNearby(Location location) {
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) < VIEW_DISTANCE * VIEW_DISTANCE) {
                return true;
            }
        }
        return false;
    }
}
