package eu.nurkert.porticlegun.visualization;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.portals.ActivePortalsHandler;
import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.util.BedrockSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Draws every open portal near players, every tick, in particles only (so it looks the same with or without the
 * resource pack, on Java and Bedrock):
 *
 * <ul>
 *   <li>a closed rim in the portal's colour, kept dense by a few new particles each tick;
 *   <li>two bright sparks with a short tail running around it;
 *   <li>while linked: a swirl of darker particles turning inwards and the odd glint flying off the surface;
 *   <li>alone (the other portal is not placed yet): only a dimmed rim, slowly breathing;
 *   <li>blocked: a flickering force field across the surface, and the rim tenses.
 * </ul>
 *
 * Bedrock clients draw particles at a higher cost, so they get fewer of each.
 */
public class VisualizationHandler implements Listener {

    // particles farther away than this are not shown by the client anyway
    private static final double VIEW_DISTANCE = 48;
    // golden ratio: spreads each tick's rim particles evenly over the gaps the earlier ticks left
    private static final double GOLDEN = 0.6180339887;
    private static final Color FIELD = Color.fromRGB(0xE6E6E6);

    private final ActivePortalsHandler activePortals;
    private final GunColorHandler gunColors;
    private long tick;

    public VisualizationHandler(ActivePortalsHandler activePortals, GunColorHandler gunColors) {
        this.activePortals = activePortals;
        this.gunColors = gunColors;
        new BukkitRunnable() {
            @Override
            public void run() {
                tick++;
                for (Portal portal : activePortals.getAllPortal()) {
                    draw(portal);
                }
            }
        }.runTaskTimer(PorticleGun.getInstance(), 0, 1);
    }

    private void draw(Portal portal) {
        if (!portal.isVisualizationReady()) {
            return;
        }
        Location location = portal.getLocation();
        if (location == null
                || location.getWorld() == null
                || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        List<Player> viewers = viewers(location);
        if (viewers.isEmpty()) {
            return;
        }
        PortalFrame frame = PortalFrame.of(portal);
        Color color = colour(portal);
        boolean linked = portal.getLinkedPortal() != null;
        boolean blocked = linked && activePortals.isBlocked(portal.getGunID());
        // each portal starts somewhere else on its rim, so a pair never runs in lockstep
        double offset = (portal.getGunID().hashCode() & 0xFF) / 256.0
                + (portal.getType() == Portal.PortalType.PRIMARY ? 0 : 0.5);
        for (Player viewer : viewers) {
            Painter painter = new Painter(viewer, BedrockSupport.isBedrock(viewer));
            if (!linked) {
                drawAlone(painter, frame, color, offset);
            } else {
                drawLinked(painter, frame, color, offset, blocked);
            }
        }
    }

    private void drawLinked(Painter painter, PortalFrame frame, Color color, double offset, boolean blocked) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double time = tick / 20.0;

        // the rim: tense and pulsing while blocked
        float rimSize = blocked ? (float) (0.75 + 0.25 * Math.sin(time * 9)) : 0.8f;
        int rim = painter.bedrock ? 4 : 10;
        for (int i = 0; i < rim; i++) {
            painter.dust(frame.rim(tick * GOLDEN + (double) i / rim), color, rimSize);
        }

        // two sparks chasing each other around the rim, with a tail
        double speed = blocked ? 0.12 : 0.45;
        Color bright = mix(color, Color.WHITE, 0.45);
        for (int spark = 0; spark < 2; spark++) {
            double t = offset + time * speed + spark * 0.5;
            painter.dust(frame.rim(t), bright, 1.3f);
            if (!painter.bedrock) {
                for (int tail = 1; tail <= 3; tail++) {
                    painter.dust(
                            frame.rim(t - tail * 0.018),
                            mix(color, Color.WHITE, 0.3 - tail * 0.08),
                            1.1f - tail * 0.2f);
                }
            }
        }

        if (blocked) {
            // a force field: horizontal bars across the surface, flickering
            int field = painter.bedrock ? 2 : 6;
            for (int i = 0; i < field; i++) {
                int bar = random.nextInt(5);
                double v = -0.7 + bar * 0.35;
                double width = frame.halfWidthAt(v) * 0.85;
                painter.dust(frame.at(random.nextDouble(-width, width), v), FIELD, 0.45f);
            }
            return;
        }

        // the swirl: three spiral arms turning inwards, darker than the rim so the portal gets depth
        Color deep = mix(color, Color.BLACK, 0.35);
        int swirl = painter.bedrock ? 1 : 4;
        double turn = time * 2.2;
        for (int i = 0; i < swirl; i++) {
            double radius = 0.15 + random.nextDouble() * 0.75;
            double angle = turn + random.nextInt(3) * (2 * Math.PI / 3) + radius * 2.6;
            painter.dust(frame.at(Math.cos(angle) * radius, Math.sin(angle) * radius), deep, 0.6f);
        }

        // now and then a glint flies off the surface
        if (!painter.bedrock && random.nextInt(8) == 0) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = random.nextDouble() * 0.8;
            Vector from = frame.at(Math.cos(angle) * radius, Math.sin(angle) * radius);
            painter.glint(from, frame.normal());
        }
    }

    private void drawAlone(Painter painter, PortalFrame frame, Color color, double offset) {
        double time = tick / 20.0;
        // dimmed and breathing slowly: open, but waiting for its other half
        double breath = 0.5 + 0.5 * Math.sin(time * 2.5);
        Color dim = mix(color, Color.fromRGB(0x808080), 0.35 + 0.25 * breath);
        int rim = painter.bedrock ? 2 : 5;
        for (int i = 0; i < rim; i++) {
            painter.dust(frame.rim(tick * GOLDEN + (double) i / rim), dim, 0.6f + 0.2f * (float) breath);
        }
        painter.dust(frame.rim(offset + time * 0.2), mix(color, Color.WHITE, 0.3), 1.0f);
    }

    private Color colour(Portal portal) {
        return gunColors.getColors(portal.getGunID()).get(portal.getType()).getBukkitColor();
    }

    private static List<Player> viewers(Location location) {
        List<Player> viewers = new ArrayList<>(2);
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) < VIEW_DISTANCE * VIEW_DISTANCE) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    static Color mix(Color from, Color to, double amount) {
        amount = Math.max(0, Math.min(1, amount));
        return Color.fromRGB(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }

    /**
     * A burst when something goes through a portal pair: a ring of the portal's colour around both openings, and a
     * few glints out of them. Played by the teleport.
     */
    public static void passThrough(Portal from, Portal to, Color fromColour, Color toColour) {
        burst(from, fromColour);
        burst(to, toColour);
    }

    private static void burst(Portal portal, Color color) {
        Location location = portal.getLocation();
        World world = location == null ? null : location.getWorld();
        if (world == null) {
            return;
        }
        PortalFrame frame = PortalFrame.of(portal);
        Color bright = mix(color, Color.WHITE, 0.4);
        for (Player viewer : viewers(location)) {
            Painter painter = new Painter(viewer, BedrockSupport.isBedrock(viewer));
            int points = painter.bedrock ? 8 : 20;
            for (int i = 0; i < points; i++) {
                Vector rim = frame.rim((double) i / points);
                Vector inner = frame.centre()
                        .clone()
                        .add(rim.clone().subtract(frame.centre()).multiply(0.6));
                painter.dust(rim, bright, 1.2f);
                if (!painter.bedrock) {
                    painter.dust(inner, color, 0.9f);
                }
            }
            if (!painter.bedrock) {
                for (int i = 0; i < 5; i++) {
                    painter.glint(frame.at(0, 0), frame.normal());
                }
            }
        }
    }

    /** A blocked portal pushes something back: the force field flashes up across the whole surface. */
    public static void repelled(Portal portal) {
        Location location = portal.getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        PortalFrame frame = PortalFrame.of(portal);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player viewer : viewers(location)) {
            Painter painter = new Painter(viewer, BedrockSupport.isBedrock(viewer));
            int points = painter.bedrock ? 5 : 16;
            for (int i = 0; i < points; i++) {
                double v = -0.7 + random.nextInt(5) * 0.35;
                double width = frame.halfWidthAt(v) * 0.85;
                painter.dust(frame.at(random.nextDouble(-width, width), v), FIELD, 0.8f);
            }
        }
    }

    /** Spawns the particles for one player only, so Java and Bedrock players near the same portal can differ. */
    private record Painter(Player player, boolean bedrock) {

        void dust(Vector at, Color color, float size) {
            player.spawnParticle(
                    Particle.DUST,
                    at.getX(),
                    at.getY(),
                    at.getZ(),
                    1,
                    0,
                    0,
                    0,
                    0,
                    new Particle.DustOptions(color, size));
        }

        /** A white glint drifting off the surface (no count: the offset is its motion). */
        void glint(Vector at, Vector normal) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Vector motion = normal.clone()
                    .add(new Vector(
                            random.nextDouble(-0.4, 0.4), random.nextDouble(-0.4, 0.4), random.nextDouble(-0.4, 0.4)));
            player.spawnParticle(
                    Particle.END_ROD,
                    at.getX(),
                    at.getY(),
                    at.getZ(),
                    0,
                    motion.getX(),
                    motion.getY(),
                    motion.getZ(),
                    0.04);
        }
    }
}
