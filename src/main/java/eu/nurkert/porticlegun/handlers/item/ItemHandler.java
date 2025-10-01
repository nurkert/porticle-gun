package eu.nurkert.porticlegun.handlers.item;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.messages.MessageManager;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;

public class ItemHandler implements Listener {

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
        builder.setName(getPorticleGunName());
        builder.setLore(vanishID(id), MessageManager.getMessage("items.porticlegun.lore"));

        return builder.build();
    }

    public static String getPorticleGunName() {
        return MessageManager.getMessage("items.porticlegun.name");
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
        String id = revealID(meta.getLore().get(0));
        if(!isValidGunID(id)) return null;
        return id;
    }

    // Charset for generating portal IDs
    private static final String charSet = "ᵃᵇᶜᵈᵉᶠᵍʰᶤʲᵏˡᵐᶰᵒᵖᵠʳˢᵗᵘᵛʷˣʸᶻᴬᴮᶜᴰᴱᶠᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᵠᴿˢᵀᵁᵛᵂᵡᵞᶻ⁰¹²³⁴⁵⁶⁷⁸⁹";
    private static final String charSet2 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final char[] CHAR_SET = charSet.toCharArray();
    private static final char[] CHAR_SET_ASCII = charSet2.toCharArray();



    public static String generateGunID() {
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            id.append(CHAR_SET[new Random().nextInt(CHAR_SET.length)]);
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
    public static String vanishID(String id) {
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
    public static String revealID(String id) {
        // removes '§' from the id
        return id.replaceAll("§", "");
    }

    public static String saveable(String id) {
        StringBuilder encoded = new StringBuilder();
        for (char c : id.toCharArray()) {
            int index = charSet.indexOf(c);
            if (index == -1) {
                PorticleGun.getInstance().getLogger().warning("Encountered unknown character '" + c + "' while encoding gun id");
                continue;
            }
            encoded.append(CHAR_SET_ASCII[index]);
        }
        return encoded.toString();
    }

    /**
     * Decodes an id stored in the configuration.
     *
     * @param id the persisted id encoded via {@link #saveable(String)}
     * @return the decoded id or {@code null} if the persisted value contains unsupported characters
     */
    public static String useable(String id) {
        StringBuilder decoded = new StringBuilder();
        for (char c : id.toCharArray()) {
            int index = charSet2.indexOf(c);
            if (index == -1) {
                PorticleGun.getInstance().getLogger().warning("Encountered unknown character '" + c + "' while decoding gun id");
                return null;
            }
            decoded.append(CHAR_SET[index]);
        }
        return decoded.toString();
    }
}
