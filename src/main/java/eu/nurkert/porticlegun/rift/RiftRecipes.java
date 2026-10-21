package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.item.ItemHandler;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * Recipes of the rift items.
 *
 * <ul>
 *   <li>Portal fluid: three rift shards, a glass bottle and glowstone dust.
 *   <li>Interdimensional gun: an upgrade of the PorticleGun with a nether star, echo shards, iron blocks, rift shards
 *       and a first tank of portal fluid.
 * </ul>
 *
 * <p>Rift shards and portal fluid are paper underneath, so recipes only match paper in general; the real ingredients
 * are checked when the result is prepared. They can never be used as paper in vanilla recipes.
 */
public class RiftRecipes implements Listener {

    private final RiftModule module;
    private final NamespacedKey fluidKey;
    private final NamespacedKey gunKey;

    RiftRecipes(RiftModule module) {
        this.module = module;
        this.fluidKey = new NamespacedKey(module.plugin(), "portal_fluid");
        this.gunKey = new NamespacedKey(module.plugin(), "interdimensional_gun");
    }

    void register() {
        RiftSettings settings = module.settings();
        ShapelessRecipe fluid =
                new ShapelessRecipe(fluidKey, RiftItems.create(RiftItems.Type.PORTAL_FLUID, 1, settings));
        fluid.addIngredient(3, Material.PAPER);
        fluid.addIngredient(Material.GLASS_BOTTLE);
        fluid.addIngredient(Material.GLOWSTONE_DUST);
        Bukkit.addRecipe(fluid);

        ShapedRecipe gun = new ShapedRecipe(gunKey, RiftItems.create(RiftItems.Type.INTERDIMENSIONAL_GUN, 1, settings));
        gun.shape("SNS", "EGE", "IFI");
        gun.setIngredient('S', Material.PAPER);
        gun.setIngredient('F', Material.PAPER);
        gun.setIngredient('N', Material.NETHER_STAR);
        gun.setIngredient('E', Material.ECHO_SHARD);
        gun.setIngredient('G', ItemHandler.PORTICLE_GUN_TYPE);
        gun.setIngredient('I', Material.IRON_BLOCK);
        Bukkit.addRecipe(gun);
    }

    void unregister() {
        Bukkit.removeRecipe(fluidKey);
        Bukkit.removeRecipe(gunKey);
    }

    @EventHandler
    public void on(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipes(List.of(fluidKey, gunKey));
    }

    @EventHandler
    public void on(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        NamespacedKey key = event.getRecipe() instanceof Keyed keyed ? keyed.getKey() : null;
        HumanEntity crafter = event.getView().getPlayer();
        RiftSettings settings = module.settings();

        if (fluidKey.equals(key)) {
            inventory.setResult(
                    isFluidRecipe(matrix) ? RiftItems.create(RiftItems.Type.PORTAL_FLUID, 1, settings) : null);
        } else if (gunKey.equals(key)) {
            boolean valid = isGunRecipe(matrix) && crafter.hasPermission(RiftGunListener.PERMISSION);
            inventory.setResult(valid ? RiftItems.create(RiftItems.Type.INTERDIMENSIONAL_GUN, 1, settings) : null);
        } else if (containsRiftItem(matrix)) {
            inventory.setResult(null); // shards and fluid are no paper for vanilla recipes
        }
    }

    @EventHandler
    public void on(CraftItemEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !gunKey.equals(keyed.getKey())) {
            return;
        }
        // the PorticleGun used for the upgrade is gone, so are its portals
        String oldGun = ItemHandler.isValidGun(event.getInventory().getMatrix()[4]);
        if (oldGun != null) {
            module.plugin().getActivePortals().closePortals(oldGun);
        }
    }

    static boolean isFluidRecipe(ItemStack[] matrix) {
        int shards = 0;
        for (ItemStack item : matrix) {
            if (item != null && item.getType() == Material.PAPER) {
                if (RiftItems.type(item) != RiftItems.Type.RIFT_SHARD) {
                    return false;
                }
                shards++;
            }
        }
        return shards == 3;
    }

    boolean isGunRecipe(ItemStack[] matrix) {
        return matrix.length == 9
                && RiftItems.type(matrix[0]) == RiftItems.Type.RIFT_SHARD
                && RiftItems.type(matrix[2]) == RiftItems.Type.RIFT_SHARD
                && RiftItems.type(matrix[7]) == RiftItems.Type.PORTAL_FLUID
                && ItemHandler.isValidGun(matrix[4]) != null;
    }

    private static boolean containsRiftItem(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (RiftItems.type(item) != null) {
                return true;
            }
        }
        return false;
    }
}
