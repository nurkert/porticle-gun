package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.handlers.AudioHandler;
import eu.nurkert.porticlegun.handlers.PersitentHandler;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.handlers.visualization.GunColorHandler;
import eu.nurkert.porticlegun.handlers.visualization.PortalColor;
import eu.nurkert.porticlegun.handlers.visualization.PortalCreationAnimation;
import eu.nurkert.porticlegun.handlers.visualization.PortalShotAnimation;
import eu.nurkert.porticlegun.handlers.visualization.TitleHandler;
import eu.nurkert.porticlegun.handlers.visualization.concrete.PortalVisualizationType;
import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.portals.PortalTracing;
import eu.nurkert.porticlegun.portals.PotentialPortal;
import eu.nurkert.porticlegun.util.WorldGuardIntegration;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

public class PortalOpenHandler implements Listener {

    @EventHandler
    public void on(PlayerInteractEvent event) {
        // check if it is possible to place portal
        if(event.getItem() == null || event.getPlayer().isSneaking()) {
            return;
        }

        String gunID = ItemHandler.isValidGun(event.getItem());
        if(gunID == null) {
            return;
        }

        Action action = event.getAction();
        if(action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        PotentialPortal traced = PortalTracing.tracePortal(player);
        PotentialPortal snapshot = clonePotential(traced);

        Portal.PortalType type = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)
                ? Portal.PortalType.PRIMARY : Portal.PortalType.SECONDARY;
        PortalColor color = GunColorHandler.getColors(gunID).get(type);

        PortalShotAnimation.play(player, snapshot, color, () ->
                attemptPortalPlacement(player, gunID, type, snapshot));
    }



    @EventHandler
    public void on(BlockPlaceEvent event) {
        if(ItemHandler.isValidGun(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void on(BlockBreakEvent event) {
        if(ItemHandler.isValidGun(event.getPlayer().getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    private boolean hasRequiredBackingBlocks(PotentialPortal potential) {
        Location portalBase = potential.getLocation();
        Block lowerBacking = portalBase.clone().subtract(potential.getDirection()).getBlock();
        if (!lowerBacking.getType().isOccluding()) {
            return false;
        }

        if (Math.abs(potential.getDirection().getY()) < 0.5) {
            Location upperPortalLocation = portalBase.clone().add(0, 1, 0);
            Block upperBacking = upperPortalLocation.subtract(potential.getDirection()).getBlock();
            if (!upperBacking.getType().isOccluding()) {
                return false;
            }
        }

        return true;
    }

    private PotentialPortal clonePotential(PotentialPortal potential) {
        if (potential == null) {
            return null;
        }

        Location location = potential.getLocation().clone();
        Vector direction = potential.getDirection() != null
                ? potential.getDirection().clone()
                : location.getDirection().clone();
        return new PotentialPortal(location, direction);
    }

    private boolean attemptPortalPlacement(Player player, String gunID, Portal.PortalType type, PotentialPortal potential) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        if (potential == null) {
            AudioHandler.playSound(player, AudioHandler.PortalSound.DENY);
            return false;
        }

        if (!hasRequiredBackingBlocks(potential)) {
            AudioHandler.playSound(player, AudioHandler.PortalSound.DENY);
            return false;
        }

        if (potential.getLocation().getBlock().getType().isSolid()) {
            AudioHandler.playSound(player, AudioHandler.PortalSound.DENY);
            return false;
        }

        Location playersHight = potential.getLocation().clone().add(0,
                potential.getDirection().getY() != 0.0 ? potential.getDirection().getY() : 1, 0);
        if (playersHight.getBlock().getType().isSolid()) {
            AudioHandler.playSound(player, AudioHandler.PortalSound.DENY);
            return false;
        }

        if (PorticleGun.isWorldGuardEnabled()
                && !WorldGuardIntegration.canCreatePortal(player, potential.getLocation(), potential.getDirection())) {
            AudioHandler.playSound(player, AudioHandler.PortalSound.DENY);
            return false;
        }

        PortalVisualizationType visualizationType = PortalVisualizationType.fromString(
                PersitentHandler.get("porticleguns." + ItemHandler.saveable(gunID) + ".shape"));
        Portal portal = new Portal(potential, gunID, type, visualizationType);
        if (type == Portal.PortalType.PRIMARY) {
            ActivePortalsHandler.setPrimaryPortal(gunID, portal);
        } else {
            ActivePortalsHandler.setSecondaryPortal(gunID, portal);
        }
        portal.saveAll();
        PortalCreationAnimation.play(portal);
        TitleHandler.sendPortalStatus(player, gunID);
        AudioHandler.playSound(player, AudioHandler.PortalSound.PORTAL_OPEN);
        return true;
    }
}
