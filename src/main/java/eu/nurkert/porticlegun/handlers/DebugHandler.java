package eu.nurkert.porticlegun.handlers;

import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class DebugHandler implements Listener {

    @EventHandler
    public void on(EntityDamageEvent event) {
        Bukkit.broadcastMessage("test");
    }
}
