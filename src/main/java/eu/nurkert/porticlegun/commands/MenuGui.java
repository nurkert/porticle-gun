package eu.nurkert.porticlegun.commands;

import eu.nurkert.porticlegun.PorticleGun;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The drawn /porticlegun menus: background glyphs and the slots they draw. Keep in sync with the "command menus"
 * section of scripts/generate_resourcepack.py.
 */
final class MenuGui {

    static final char OVERVIEW = '\ue9e0';
    static final char OVERVIEW_ADMIN = '\ue9e1';
    static final char SINGLE = '\ue9e2';
    static final char SINGLE_ADMIN = '\ue9e3';
    static final char RECIPE = '\ue9e4';

    // overview: the items in the top row, a free column between them, their recipe buttons below
    static final int[] COLUMNS = {0, 2, 4, 6, 8};
    static final int ADMIN_SLOT = 26;

    // recipe: back button, the 3x3 grid and the large result slot
    static final int BACK = 0;
    static final int[] GRID = {1, 2, 3, 10, 11, 12, 19, 20, 21};
    static final int RESULT = 15;

    private MenuGui() {}

    /** A button that draws nothing, so only the background's button shows. */
    static ItemStack blank(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey(PorticleGun.getInstance(), "gui_blank"));
        meta.setDisplayName(name);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }
}
