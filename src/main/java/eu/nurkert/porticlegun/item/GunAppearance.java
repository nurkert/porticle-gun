package eu.nurkert.porticlegun.item;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.visualization.GunColors;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

/**
 * Points gun items at the 3D model from the PorticleGun resource pack ({@code porticlegun:portal_gun}) and passes the
 * gun's portal colours to it, which tint the lights of the model. Players without the pack still see a brewing stand.
 */
public final class GunAppearance {

    private GunAppearance() {}

    public static NamespacedKey modelKey() {
        return new NamespacedKey(PorticleGun.getInstance(), "portal_gun");
    }

    /**
     * @return whether the item was changed
     */
    public static boolean apply(ItemStack gun, String gunID) {
        if (gun == null || gunID == null || !gun.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = gun.getItemMeta();
        boolean changed = false;

        NamespacedKey model = modelKey();
        if (!model.equals(meta.getItemModel())) {
            meta.setItemModel(model);
            changed = true;
        }

        GunColors colors = PorticleGun.getInstance().getGunColors().getColors(gunID);
        List<Color> tints = List.of(
                colors.getPrimary().getBukkitColor(), colors.getSecondary().getBukkitColor());
        CustomModelDataComponent modelData = meta.getCustomModelDataComponent();
        if (!tints.equals(modelData.getColors())) {
            modelData.setColors(tints);
            meta.setCustomModelDataComponent(modelData);
            changed = true;
        }

        if (changed) {
            gun.setItemMeta(meta);
        }
        return changed;
    }
}
