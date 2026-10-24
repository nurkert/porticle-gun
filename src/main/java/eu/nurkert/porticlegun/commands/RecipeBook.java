package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.rift.RiftItems;
import eu.nurkert.porticlegun.rift.RiftModule;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The plugin's items with their crafting recipes as the menu shows them: the real ingredients (rift shards and portal
 * fluid instead of the paper the registered recipes match). Keep in sync with RecipeHandler and RiftRecipes.
 */
final class RecipeBook {

    /**
     * @param item a fresh copy of the item, as handed out (a new gun id every time)
     * @param grid the crafting grid, row by row; {@code null} for an empty field
     * @param shapeless whether the ingredients may lie anywhere
     * @param freeForAll whether players without the admin permission may take it (only the PorticleGun)
     */
    record Entry(Supplier<ItemStack> item, List<Supplier<ItemStack>> grid, boolean shapeless, boolean freeForAll) {}

    private RecipeBook() {}

    /** @return the PorticleGun, and with the rift module the interdimensional gun and its items */
    static List<Entry> entries(RiftModule rift) {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(
                ItemHandler::generateNewGun,
                grid(
                        Material.OBSIDIAN,
                        Material.BEACON,
                        Material.OBSIDIAN,
                        Material.QUARTZ_BLOCK,
                        Material.ENDER_PEARL,
                        Material.QUARTZ_BLOCK,
                        Material.QUARTZ_BLOCK,
                        Material.CROSSBOW,
                        Material.QUARTZ_BLOCK),
                false,
                true));
        if (rift == null) {
            return entries;
        }
        Supplier<ItemStack> shard = () -> RiftItems.create(RiftItems.Type.RIFT_SHARD, 1, rift.settings());
        Supplier<ItemStack> fluid = () -> RiftItems.create(RiftItems.Type.PORTAL_FLUID, 1, rift.settings());
        Supplier<ItemStack> netherStar = () -> new ItemStack(Material.NETHER_STAR);
        Supplier<ItemStack> echo = () -> new ItemStack(Material.ECHO_SHARD);
        Supplier<ItemStack> iron = () -> new ItemStack(Material.IRON_BLOCK);
        entries.add(new Entry(
                () -> RiftItems.create(RiftItems.Type.INTERDIMENSIONAL_GUN, 1, rift.settings()),
                List.of(shard, netherStar, shard, echo, ItemHandler::generateNewGun, echo, iron, fluid, iron),
                false,
                false));
        Supplier<ItemStack> compass = () -> new ItemStack(Material.COMPASS);
        Supplier<ItemStack> eye = () -> new ItemStack(Material.ENDER_EYE);
        entries.add(new Entry(
                () -> RiftItems.create(RiftItems.Type.RIFT_COMPASS, 1, rift.settings()),
                list(null, shard, null, shard, compass, shard, null, eye, null),
                false,
                false));
        Supplier<ItemStack> bottle = () -> new ItemStack(Material.GLASS_BOTTLE);
        Supplier<ItemStack> glowstone = () -> new ItemStack(Material.GLOWSTONE_DUST);
        entries.add(new Entry(
                () -> RiftItems.create(RiftItems.Type.PORTAL_FLUID, 4, rift.settings()),
                list(shard, shard, shard, null, bottle, null, null, glowstone, null),
                true,
                false));
        // rift shards drop from blocks and mobs; they have no recipe
        entries.add(
                new Entry(() -> RiftItems.create(RiftItems.Type.RIFT_SHARD, 16, rift.settings()), null, false, false));
        return entries;
    }

    private static List<Supplier<ItemStack>> grid(Material... materials) {
        List<Supplier<ItemStack>> grid = new ArrayList<>();
        for (Material material : materials) {
            grid.add(material == null ? null : () -> new ItemStack(material));
        }
        return grid;
    }

    @SafeVarargs
    private static List<Supplier<ItemStack>> list(Supplier<ItemStack>... fields) {
        List<Supplier<ItemStack>> grid = new ArrayList<>();
        for (Supplier<ItemStack> field : fields) {
            grid.add(field);
        }
        return grid;
    }
}
