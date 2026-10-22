package eu.nurkert.porticlegun.rift;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
        ItemStack tool = player.getInventory().getItemInMainHand();
        // like vanilla drops: only when the block drops anything at all (a pickaxe, block drops on, not cancelled)
        if (!event.isDropItems()
                || !Boolean.TRUE.equals(block.getWorld().getGameRuleValue(GameRule.DO_TILE_DROPS))
                || block.getDrops(tool, player).isEmpty()) {
            return;
        }
        RiftSettings settings = module.settings();
        int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        if (roll(player, settings.endStoneChance() + fortune * settings.endStoneFortuneBonus())) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), shard());
        }
    }

    @EventHandler
    public void on(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (event.getEntityType() != EntityType.ENDERMAN
                || killer == null
                || !Boolean.TRUE.equals(event.getEntity().getWorld().getGameRuleValue(GameRule.DO_MOB_LOOT))) {
            return;
        }
        RiftSettings settings = module.settings();
        int looting = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        if (roll(killer, settings.endermanChance() + looting * settings.endermanLootingBonus())) {
            event.getDrops().add(shard());
        }
    }

    private ItemStack shard() {
        return RiftItems.create(RiftItems.Type.RIFT_SHARD, 1, module.settings());
    }

    /**
     * Rolls a drop. The player's luck (the attribute behind the Luck and Bad Luck effects, which vanilla loot uses too)
     * scales the chance: every point of luck adds a quarter, bad luck takes it away.
     */
    private static boolean roll(Player player, double chance) {
        AttributeInstance luck = player.getAttribute(Attribute.LUCK);
        double factor = luck == null ? 1 : Math.max(0, 1 + 0.25 * luck.getValue());
        return ThreadLocalRandom.current().nextDouble() < chance * factor;
    }
}
