package eu.nurkert.porticlegun.item;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.builders.ItemBuilder;
import eu.nurkert.porticlegun.messages.MessageManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Creates gun items and recognises them. Every gun has a random id that links it to its portals.
 */
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
        builder.setLore(MessageManager.getMessage("items.porticlegun.lore"));

        ItemStack gun = builder.build();
        ItemMeta meta = gun.getItemMeta();
        meta.getPersistentDataContainer().set(gunIdKey(), PersistentDataType.STRING, id);
        gun.setItemMeta(meta);
        GunAppearance.apply(gun, id);
        return gun;
    }

    private static NamespacedKey gunIdKey() {
        return new NamespacedKey(PorticleGun.getInstance(), "gun_id");
    }

    /**
     * Rewrites the name and lore of a gun created while its texts were missing from an outdated messages.yml.
     *
     * @return whether the item changed
     */
    public static boolean repairTexts(ItemStack gun) {
        ItemMeta meta = gun.getItemMeta();
        boolean changed = false;
        if (MessageManager.isMissing(meta.getDisplayName())) {
            meta.setDisplayName(getPorticleGunName());
            changed = true;
        }
        if (meta.hasLore() && meta.getLore().stream().anyMatch(MessageManager::isMissing)) {
            meta.setLore(List.of(MessageManager.getMessage("items.porticlegun.lore")));
            changed = true;
        }
        if (changed) {
            gun.setItemMeta(meta);
        }
        return changed;
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
        String id = meta.getPersistentDataContainer().get(gunIdKey(), PersistentDataType.STRING);
        if (id == null) {
            id = getLegacyID(meta);
        }
        if (id == null || !isValidGunID(id)) return null;
        return id;
    }

    /**
     * Guns created before 1.3 kept their id hidden in the first lore line. Bedrock clients render that line as
     * garbage, so the id now lives in the persistent data container.
     */
    private static String getLegacyID(ItemMeta meta) {
        if (!meta.hasLore() || meta.getLore().isEmpty()) return null;
        String id = revealID(meta.getLore().get(0));
        return isValidGunID(id) ? id : null;
    }

    /**
     * Moves the id of a gun created before 1.3 from its lore into the persistent data container.
     *
     * @return whether the item was changed
     */
    public static boolean migrateLegacyGun(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != PORTICLE_GUN_TYPE || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta.getPersistentDataContainer().has(gunIdKey(), PersistentDataType.STRING)) {
            return false;
        }
        String id = getLegacyID(meta);
        if (id == null) {
            return false;
        }
        meta.getPersistentDataContainer().set(gunIdKey(), PersistentDataType.STRING, id);
        List<String> lore = new ArrayList<>(meta.getLore());
        lore.remove(0);
        meta.setLore(lore);
        itemStack.setItemMeta(meta);
        return true;
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
                PorticleGun.getInstance()
                        .getLogger()
                        .warning("Encountered unknown character '" + c + "' while encoding gun id");
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
                PorticleGun.getInstance()
                        .getLogger()
                        .warning("Encountered unknown character '" + c + "' while decoding gun id");
                return null;
            }
            decoded.append(CHAR_SET[index]);
        }
        return decoded.toString();
    }
}
