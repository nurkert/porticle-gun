package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.config.ConfigManager;
import eu.nurkert.porticlegun.handlers.AudioHandler;
import eu.nurkert.porticlegun.handlers.PersitentHandler;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.handlers.visualization.GunColorHandler;
import eu.nurkert.porticlegun.handlers.visualization.PortalCreationAnimation;
import eu.nurkert.porticlegun.handlers.visualization.PortalShotAnimation;
import eu.nurkert.porticlegun.handlers.visualization.TitleHandler;
import eu.nurkert.porticlegun.handlers.visualization.concrete.PortalVisualizationType;
import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.portals.PortalTracing;
import eu.nurkert.porticlegun.portals.PotentialPortal;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class PortalOpenHandler implements Listener {

    @EventHandler
    public void on(PlayerInteractEvent event) {
        if(event.getItem() == null || event.getPlayer().isSneaking()) {
            return;
        }

        String gunID = ItemHandler.isValidGun(event.getItem());
        if(gunID == null) {
            return;
        }

        Player player = event.getPlayer();
        Action action = event.getAction();
        if(!isPortalShotAction(action)) {
            return;
        }

        Portal.PortalType portalType = isPrimaryAction(action) ? Portal.PortalType.PRIMARY : Portal.PortalType.SECONDARY;
        Color beamColor = GunColorHandler.getColors(gunID).get(portalType).getBukkitColor();

        PotentialPortal traced = PortalTracing.tracePortal(player);
        PotentialPortal targetPortal = traced != null ? clonePotentialPortal(traced) : null;
        Location targetLocation = targetPortal != null ? PortalCreationAnimation.computePortalCenter(targetPortal) : computeFallbackTarget(player);

        Runnable failureAction = () -> AudioHandler.playSound(player, AudioHandler.PortalSound.DENY);

        if (targetPortal == null || targetLocation == null) {
            PortalShotAnimation.play(player, targetLocation, beamColor, false, null, failureAction);
            return;
        }

        Runnable successAction = () -> {
            if (!canPlacePortal(targetPortal)) {
                failureAction.run();
                return;
            }
            PortalVisualizationType visualizationType = PortalVisualizationType.fromString(PersitentHandler.get("porticleguns." + ItemHandler.saveable(gunID) + ".shape"));
            Portal portal = new Portal(targetPortal, gunID, portalType, visualizationType);
            if (portalType == Portal.PortalType.PRIMARY) {
                ActivePortalsHandler.setPrimaryPortal(gunID, portal);
            } else {
                ActivePortalsHandler.setSecondaryPortal(gunID, portal);
            }
            portal.saveAll();
            PortalCreationAnimation.play(portal);
            TitleHandler.sendPortalStatus(player, gunID);
            AudioHandler.playSound(player, AudioHandler.PortalSound.PORTAL_OPEN);
        };

        boolean canPlace = canPlacePortal(targetPortal);
        PortalShotAnimation.play(player, targetLocation, beamColor, canPlace, successAction, failureAction);
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

    private boolean canPlacePortal(PotentialPortal potential) {
        if (potential == null) {
            return false;
        }
        if (!hasRequiredBackingBlocks(potential)) {
            return false;
        }
        if (potential.getLocation().getBlock().getType().isSolid()) {
            return false;
        }
        Location playersHeight = potential.getLocation().clone().add(0,
                potential.getDirection().getY() != 0.0 ? potential.getDirection().getY() : 1, 0);
        return !playersHeight.getBlock().getType().isSolid();
    }

    private PotentialPortal clonePotentialPortal(PotentialPortal original) {
        Location location = original.getLocation().clone();
        return new PotentialPortal(location, original.getDirection().clone());
    }

    private boolean isPortalShotAction(Action action) {
        return action == Action.LEFT_CLICK_AIR
                || action == Action.LEFT_CLICK_BLOCK
                || action == Action.RIGHT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean isPrimaryAction(Action action) {
        return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
    }

    private Location computeFallbackTarget(Player player) {
        Location eye = player.getEyeLocation().clone();
        double maxDistance = ConfigManager.getPortalMaxTargetDistance();
        return eye.add(eye.getDirection().normalize().multiply(maxDistance));
    }
}
