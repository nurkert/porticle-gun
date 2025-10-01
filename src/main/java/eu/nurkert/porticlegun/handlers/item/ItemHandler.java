package eu.nurkert.porticlegun.handlers.item;

import eu.nurkert.porticlegun.builders.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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
        builder.setLore(vanishID(id), PORTICLE_GUN_LORE);

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
        if (meta == null || !meta.hasLore()) return null;
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) return null;
        String id = revealID(lore.get(0));
        if(!isValidGunID(id)) return null;
        return id;
    }

    // Charset for generating portal IDs
    private static final String CHAR_SET = "ᵃᵇᶜᵈᵉᶠᵍʰᶤʲᵏˡᵐᶰᵒᵖᵠʳˢᵗᵘᵛʷˣʸᶻᴬᴮᶜᴰᴱᶠᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᵠᴿˢᵀᵁᵛᵂᵡᵞᶻ⁰¹²³⁴⁵⁶⁷⁸⁹";
    private static final String CHAR_SET_STORAGE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final char[] HIDDEN_ID_CHARACTERS = CHAR_SET.toCharArray();
    private static final char[] STORAGE_ID_CHARACTERS = CHAR_SET_STORAGE.toCharArray();
    private static final int GUN_ID_LENGTH = 10;
    private static final Set<Character> VALID_GUN_ID_CHARACTERS;

    static {
        Set<Character> characters = new HashSet<>();
        for (char character : HIDDEN_ID_CHARACTERS) {
            characters.add(character);
        }
        VALID_GUN_ID_CHARACTERS = Collections.unmodifiableSet(characters);
    }

    public static String generateGunID() {
        StringBuilder id = new StringBuilder(GUN_ID_LENGTH);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < GUN_ID_LENGTH; i++) {
            id.append(HIDDEN_ID_CHARACTERS[random.nextInt(HIDDEN_ID_CHARACTERS.length)]);
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
        if (id == null || id.length() != GUN_ID_LENGTH) return false;
        for (char c : id.toCharArray()) {
            if (!VALID_GUN_ID_CHARACTERS.contains(c)) return false;
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
        return id == null ? null : id.replace("§", "");
    }

    public static String saveable(String id) {
        return translateCharacters(id, HIDDEN_ID_CHARACTERS, STORAGE_ID_CHARACTERS);
    }

    public static String useable(String id) {
        return translateCharacters(id, STORAGE_ID_CHARACTERS, HIDDEN_ID_CHARACTERS);
    }

    private static String translateCharacters(String value, char[] source, char[] target) {
        if (value == null) {
            throw new IllegalArgumentException("Value may not be null");
        }
        if (source.length != target.length) {
            throw new IllegalStateException("Source and target character sets must have the same length");
        }
        StringBuilder encoded = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            int index = indexOf(source, c);
            if (index < 0) {
                throw new IllegalArgumentException("Unexpected character '" + c + "' for translation");
            }
            encoded.append(target[index]);
        }
        return encoded.toString();
    }

    private static int indexOf(char[] array, char value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }
}
