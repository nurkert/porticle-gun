package eu.nurkert.porticlegun.item;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.rift.RiftItems;
import eu.nurkert.porticlegun.rift.RiftModule;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Turns bare copies of PorticleGun items into working ones. Bedrock players can take the items from their creative
 * inventory; Geyser then hands the server only the base item with the PorticleGun model, without the plugin's data.
 */
public final class ItemRestorer {

    private ItemRestorer() {}

    /**
     * Restores the item in place.
     *
     * @return whether the item was changed
     */
    public static boolean restore(ItemStack item) {
        if (item == null
                || !item.hasItemMeta()
                || ItemHandler.isValidGun(item) != null
                || RiftItems.type(item) != null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        NamespacedKey model = meta.hasItemModel() ? meta.getItemModel() : null;
        if (model == null
                || !model.getNamespace().equals(GunAppearance.modelKey().getNamespace())) {
            return false;
        }
        ItemStack fresh = null;
        if (model.equals(GunAppearance.modelKey())) {
            fresh = ItemHandler.generateNewGun();
        } else {
            RiftModule rift = PorticleGun.getInstance().getRiftModule();
            RiftItems.Type type = RiftItems.Type.byId(model.getKey());
            if (rift != null && type != null) {
                fresh = RiftItems.create(type, 1, rift.settings());
            }
        }
        if (fresh == null || fresh.getType() != item.getType()) {
            return false;
        }
        item.setItemMeta(fresh.getItemMeta());
        return true;
    }
}
