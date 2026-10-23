package eu.nurkert.porticlegun.rift;

import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 * One page of the native rift menus, independent of how it is shown: a Java dialog on Paper, a Bedrock form through
 * Floodgate. Texts use legacy colour codes, as everywhere in the plugin.
 *
 * @param title the window title
 * @param item an item shown above the text (Java only), or {@code null}
 * @param body the lines below the title
 * @param buttons the buttons, in reading order
 * @param columns how many buttons share a row in the Java dialog
 * @param exit the button at the bottom (back or close), or {@code null}
 */
record RiftScreen(String title, ItemStack item, List<String> body, List<Button> buttons, int columns, Button exit) {

    /**
     * @param label the button text
     * @param detail a second, smaller line (Bedrock shows it under the label), or {@code null}
     * @param tooltip the lines shown when hovering the button (Java)
     * @param icon the icon, or {@code null}
     * @param selected whether this is the gun's current destination, which is highlighted
     * @param action what a click does; it runs on the main thread
     */
    record Button(String label, String detail, List<String> tooltip, RiftIcon icon, boolean selected, Runnable action) {

        static Button of(String label, RiftIcon icon, Runnable action) {
            return new Button(label, null, List.of(), icon, false, action);
        }
    }
}
