package eu.nurkert.porticlegun.handlers.item;

import eu.nurkert.porticlegun.PorticleGun;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

public class RecipeHandler implements Listener {

    NamespacedKey key;
    ShapedRecipe recipe;

    public RecipeHandler() {
        key = new NamespacedKey(PorticleGun.getInstance(), "porticle_gun");

        ItemStack preview = ItemHandler.generateNewGun();

        recipe = new ShapedRecipe(key, preview);
        recipe.shape("OBO", "QEQ", "QCQ");
        recipe.setIngredient('O', Material.OBSIDIAN);
        recipe.setIngredient('B', Material.BEACON);
        recipe.setIngredient('Q', Material.QUARTZ_BLOCK);
        recipe.setIngredient('E', Material.ENDER_PEARL);
        recipe.setIngredient('C', Material.CROSSBOW);

        if(PorticleGun.getInstance().isEnabled() && Bukkit.getServer().getRecipe(key) == null)
            PorticleGun.getInstance().getServer().addRecipe(recipe);
    }

    @EventHandler
    public void on(CraftItemEvent event) {
        ItemStack crafted = event.getCurrentItem();
        if (crafted.getType() == Material.BREWING_STAND && crafted.hasItemMeta() &&
                crafted.getItemMeta().getDisplayName().equals(ItemHandler.PORTICLE_GUN_NAME)) {
            // so not ever portal has the same id
            event.setCurrentItem(ItemHandler.generateNewGun());
        }
    }
}
