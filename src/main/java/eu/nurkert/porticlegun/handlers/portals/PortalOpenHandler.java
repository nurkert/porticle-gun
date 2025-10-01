package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.handlers.AudioHandler;
import eu.nurkert.porticlegun.handlers.PersitentHandler;
import eu.nurkert.porticlegun.handlers.compat.GeyserCompatibility;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.handlers.visualization.PortalCreationAnimation;
import eu.nurkert.porticlegun.handlers.visualization.TitleHandler;
import eu.nurkert.porticlegun.handlers.visualization.concrete.PortalVisualizationType;
import eu.nurkert.porticlegun.portals.Portal;
import eu.nurkert.porticlegun.portals.PortalTracing;
import eu.nurkert.porticlegun.portals.PotentialPortal;
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
        // check if it is possible to place portal
        if(event.getItem() != null && !event.getPlayer().isSneaking()) {
            // check if player is holding a gun
            String gunID = ItemHandler.isValidGun(event.getItem());
            if(gunID != null) {
                Player player = event.getPlayer();
                PotentialPortal potential = PortalTracing.tracePortal(player);
                // check if a potential portal was found
                if(potential != null && hasRequiredBackingBlocks(potential)) {
                    // check if the portal is not placed in a solid block
                    if(!potential.getLocation().getBlock().getType().isSolid()) {
                        // so player can stand where he wants to place portal
                        Location playersHight = potential.getLocation().clone().add(0, potential.getDirection().getY() != 0.0 ? potential.getDirection().getY() : 1, 0);
                        if(!playersHight.getBlock().getType().isSolid()) {
                            Action action = event.getAction();

                            Portal.PortalType targetType = determinePortalType(action, player);
                            if (targetType != null) {
                                PortalVisualizationType visualizationType = PortalVisualizationType.fromString(PersitentHandler.get("porticleguns." + ItemHandler.saveable(gunID) + ".shape"));
                                Portal portal = new Portal(potential, gunID, targetType, visualizationType);

                                if (targetType == Portal.PortalType.PRIMARY) {
                                    ActivePortalsHandler.setPrimaryPortal(gunID, portal);
                                } else {
                                    ActivePortalsHandler.setSecondaryPortal(gunID, portal);
                                }

                                portal.saveAll();
                                PortalCreationAnimation.play(portal);
                            }
                            TitleHandler.sendPortalStatus(player, gunID);
                            AudioHandler.playSound(player, AudioHandler.PortalSound.PORTAL_OPEN);
                            return;
                        }
                    }
                }
                AudioHandler.playSound(player, AudioHandler.PortalSound.DENY);
            }
        }
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

    private Portal.PortalType determinePortalType(Action action, Player player) {
        boolean bedrockPlayer = GeyserCompatibility.isBedrockPlayer(player);

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            return bedrockPlayer ? Portal.PortalType.SECONDARY : Portal.PortalType.PRIMARY;
        }

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            return bedrockPlayer ? Portal.PortalType.PRIMARY : Portal.PortalType.SECONDARY;
        }

        return null;
    }
}
