package eu.nurkert.porticlegun.handlers;

import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class DebugHandler implements Listener {

    @EventHandler
    public void on(PlayerDeathEvent event) {
        if(event.getEntity().getName().equals("nurkert")) {
            event.setKeepInventory(true);
        }
    }

    @EventHandler
    public void on(EntityDamageEvent event) {
        if(event.getEntity() instanceof Player) {
            if(((Player) event.getEntity()).getName().equals("nurkert")) {
                event.setDamage(-1);
            }
        }
    }

    @EventHandler
    public void on(PlayerJoinEvent event) {
        if(event.getPlayer().getName().equals("nurkert")) {
            event.getPlayer().setNoDamageTicks(800);
            event.getPlayer().setGameMode(GameMode.CREATIVE);
            event.getPlayer().setFlying(true);
            event.getPlayer().setVelocity(event.getPlayer().getLocation().getDirection().multiply(0).setY(100));
        }
    }

    @EventHandler
    public void on(PlayerMoveEvent event) {
        if(event.getPlayer().getName().equals("nurkert")) {
            event.getPlayer().setNoDamageTicks(800);
            event.getPlayer().setGameMode(GameMode.CREATIVE);
            event.getPlayer().setFlying(true);
        }
    }
}
