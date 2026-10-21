package eu.nurkert.porticlegun.rift;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Rift shards are left behind where the end frays: a rare drop when mining end stone (more with fortune) and when
 * killing endermen (more with looting).
 */
public class RiftDrops implements Listener {

    private final RiftModule module;

    RiftDrops(RiftModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (block.getType() != Material.END_STONE
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        RiftSettings settings = module.settings();
        int fortune = player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.FORTUNE);
        if (roll(settings.endStoneChance() + fortune * settings.endStoneFortuneBonus())) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), shard());
        }
    }

    @EventHandler
    public void on(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (event.getEntityType() != EntityType.ENDERMAN || killer == null) {
            return;
        }
        RiftSettings settings = module.settings();
        int looting = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        if (roll(settings.endermanChance() + looting * settings.endermanLootingBonus())) {
            event.getDrops().add(shard());
        }
    }

    private ItemStack shard() {
        return RiftItems.create(RiftItems.Type.RIFT_SHARD, 1, module.settings());
    }

    private static boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }
}
