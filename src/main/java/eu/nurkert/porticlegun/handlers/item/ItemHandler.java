package eu.nurkert.porticlegun.handlers.item;

import eu.nurkert.porticlegun.builders.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;

public class ItemHandler implements Listener {

    // uniform name for the PorticleGun
    public static final String PORTICLE_GUN_NAME = "§5PorticleGun";
    // uniform description lore for the PorticleGun
    public static final String PORTICLE_GUN_LORE = "§7Device that creates portals.";

    // uniform type for the PorticleGun
    public static final Material PORTICLE_GUN_TYPE = Material.BREWING_STAND;

    /**
     *
     * @return Item that represents the PorticleGun
     */
    public static ItemStack generateNewGun() {
        // generate a random id for the gun
        String id = generateGunID();

        ItemBuilder builder = new ItemBuilder(PORTICLE_GUN_TYPE);
        builder.setName(PORTICLE_GUN_NAME);
        builder.setLore(encodeID(id), PORTICLE_GUN_LORE);

        return builder.build();
    }

    /**
     * Checks if the itemStack is a PorticleGun
     *
     * @param itemStack the item to check
     * @return true if the itemStack is a PorticleGun
     */
    public static String isValidGun(ItemStack itemStack) {
        if (itemStack == null) return null;
        if (itemStack.getType() != PORTICLE_GUN_TYPE) return null;
        if (!itemStack.hasItemMeta()) return null;
        ItemMeta meta = itemStack.getItemMeta();
        assert meta != null;
        if(!meta.hasLore()) return null;
        String id = decodeID(meta.getLore().get(0));
        if(!isValidGunID(id)) return null;
        return id;
    }

    // Charset for generating portal IDs
    private static final String charSet = "ᵃᵇᶜᵈᵉᶠᵍʰᶤʲᵏˡᵐᶰᵒᵖᵠʳˢᵗᵘᵛʷˣʸᶻᴬᴮᶜᴰᴱᶠᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᵠᴿˢᵀᵁᵛᵂᵡᵞᶻ⁰¹²³⁴⁵⁶⁷⁸⁹";

    public static String generateGunID() {
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            id.append(charSet.toCharArray()[new Random().nextInt(charSet.length())]);
        }
        return id.toString();
    }

    /**
     * Checks if the id is valid
     *
     * @param id the id to check
     * @return true if the id is valid
     */
    public static boolean isValidGunID(String id) {
        // check if the id is 10 chars long and only contains chars from the charset
        if (id.length() != 10) return false;
        for (char c : id.toCharArray()) {
            if (!charSet.contains(String.valueOf(c))) return false;
        }
        return true;
    }

    /**
     * Using the § char to hide the id in the lore of the PorticleGun
     *
     * @param id the id to encode
     * @return the id with '§' between every char
     */
    public static String encodeID(String id) {
        // add '§' between every char frrom the id to hide it in the lore
        String[] idArray = id.split("");
        StringBuilder idBuilder = new StringBuilder();
        for (String s : idArray) {
            idBuilder.append("§").append(s);
        }
        return idBuilder.toString();
    }

    /**
     * Decodes the id from the lore of the PorticleGun
     *
     * @param id the id with '§' between every char
     * @return the id without '§'
     */
    public static String decodeID(String id) {
        // removes '§' from the id
        return id.replaceAll("§", "");
    }
}
