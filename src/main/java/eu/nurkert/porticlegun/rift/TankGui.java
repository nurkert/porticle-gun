package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.PorticleGun;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The drawn tank menu for Java players with the resource pack: glyphs in the chest title paint its background and
 * the tank gauge, and the clickable spots are items that draw nothing. Keep in sync with the "tank menu" section of
 * scripts/generate_resourcepack.py.
 */
final class TankGui {

    // chest slots of the drawn spots (three rows of nine)
    static final int INPUT = 10;
    static final int LOAD = 12;
    static final int LOAD_RIGHT = 13;
    static final int GUN = 15;
    static final int BACK = 18;
    static final int COMPASS = 24;
    static final int[] GAUGE = {8, 17, 26};

    private static final char BACKGROUND = '\ue9b0';
    private static final char GAUGE_EMPTY = '\ue9b1';
    private static final int GAUGE_LEVELS = 12;
    private static final char SHIFT_START = '\ue9c0';
    private static final char SHIFT_GAUGE = '\ue9c1';
    private static final char SHIFT_TEXT = '\ue9c2';

    private TankGui() {}

    /**
     * @return the chest title: the background and the gauge filled to the tank's level (white, so the glyphs keep
     *     their colours), then the title text in the usual colour
     */
    static String title(String text, int charges, int capacity) {
        int level = capacity <= 0
                ? 0
                : Math.max(0, Math.min(GAUGE_LEVELS, Math.round((float) GAUGE_LEVELS * charges / capacity)));
        if (charges > 0 && level == 0) {
            level = 1; // never look empty while a rift is left
        }
        return "§f" + SHIFT_START + BACKGROUND + SHIFT_GAUGE + (char) (GAUGE_EMPTY + level) + SHIFT_TEXT + "§r" + text;
    }

    /** An item that draws nothing: with a name it is a button, without one a part of the frame without tooltip. */
    static ItemStack blank(String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey(PorticleGun.getInstance(), "gui_blank"));
        if (name == null) {
            meta.setHideTooltip(true);
        } else {
            meta.setDisplayName(name);
            meta.setLore(lore);
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }
}
