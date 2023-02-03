package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.handlers.AudioHandler;
import eu.nurkert.porticlegun.handlers.gun.GunColorHandler;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.portals.PortalColor;
import eu.nurkert.porticlegun.portals.PortalTracing;
import eu.nurkert.porticlegun.portals.PorticlePortal;
import eu.nurkert.porticlegun.portals.PotentialPortal;
import org.bukkit.Location;
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
                if(potential != null) {
                    // check if the portal is not placed in a solid block
                    if(!potential.getLocation().getBlock().getType().isSolid()) {
                        // so player can stand where he wants to place portal
                        Location playersHight = potential.getLocation().clone().add(0, potential.getDirection().getY() != 0.0 ? potential.getDirection().getY() : 1, 0);
                        if(!playersHight.getBlock().getType().isSolid()) {
                            Action action = event.getAction();

                            if(action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                                PortalColor color = GunColorHandler.getColors(gunID).getPrimary();
                                PorticlePortal primary = new PorticlePortal(potential, gunID, color);
                                OpenedPortalsHandler.setPrimaryPortal(gunID, primary);
                            } else if(action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                                PortalColor color = GunColorHandler.getColors(gunID).getSecondary();
                                PorticlePortal secondary = new PorticlePortal(potential, gunID, color);
                                OpenedPortalsHandler.setSecondaryPortal(gunID, secondary);
                            }
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
}
