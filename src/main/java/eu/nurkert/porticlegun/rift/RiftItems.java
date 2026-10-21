package eu.nurkert.porticlegun.rift;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.messages.MessageManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Creates and recognises the items of the interdimensional gun: the gun itself, rift shards and portal fluid.
 *
 * <p>Items are recognised by a marker in their persistent data, never by name or lore. The gun also stores its tank
 * charge, its selected destination and a unique id there. The look comes from the PorticleGun resource pack through
 * item models; without the pack they show as their base items.
 */
public final class RiftItems {

    public enum Type {
        INTERDIMENSIONAL_GUN("interdimensional_gun", Material.WARPED_FUNGUS_ON_A_STICK, "interdimensional-gun"),
        RIFT_SHARD("rift_shard", Material.PAPER, "rift-shard"),
        PORTAL_FLUID("portal_fluid", Material.PAPER, "portal-fluid"),
        RIFT_COMPASS("rift_compass", Material.PAPER, "rift-compass");

        private final String id;
        private final Material base;
        private final String messageKey;

        Type(String id, Material base, String messageKey) {
            this.id = id;
            this.base = base;
            this.messageKey = messageKey;
        }

        public String id() {
            return id;
        }

        public Material base() {
            return base;
        }

        public static Type byId(String id) {
            for (Type type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    private RiftItems() {}

    private static NamespacedKey key(String name) {
        return new NamespacedKey(PorticleGun.getInstance(), name);
    }

    private static NamespacedKey typeKey() {
        return key("rift_item");
    }

    private static NamespacedKey chargesKey() {
        return key("rift_charges");
    }

    private static NamespacedKey destinationKey() {
        return key("rift_destination");
    }

    private static NamespacedKey attunedKey() {
        return key("rift_attuned_until");
    }

    private static NamespacedKey gunIdKey() {
        return key("rift_gun_id");
    }

    /**
     * @return the kind of rift item, or {@code null} for any other item
     */
    public static Type type(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        Type type =
                Type.byId(item.getItemMeta().getPersistentDataContainer().get(typeKey(), PersistentDataType.STRING));
        return type != null && type.base() == item.getType() ? type : null;
    }

    public static boolean isGun(ItemStack item) {
        return type(item) == Type.INTERDIMENSIONAL_GUN;
    }

    public static ItemStack create(Type type, int amount, RiftSettings settings) {
        ItemStack item = new ItemStack(type.base(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(typeKey(), PersistentDataType.STRING, type.id());
        meta.setItemModel(key(type.id()));
        meta.setDisplayName(MessageManager.getMessage("items." + type.messageKey + ".name"));
        if (type == Type.INTERDIMENSIONAL_GUN) {
            data.set(gunIdKey(), PersistentDataType.STRING, UUID.randomUUID().toString());
            data.set(chargesKey(), PersistentDataType.INTEGER, settings.startingCharges());
        } else {
            meta.setLore(lines(MessageManager.getMessage("items." + type.messageKey + ".lore")));
        }
        item.setItemMeta(meta);
        if (type == Type.INTERDIMENSIONAL_GUN) {
            refresh(item, settings);
        }
        return item;
    }

    public static String gunId(ItemStack gun) {
        return gun.getItemMeta().getPersistentDataContainer().get(gunIdKey(), PersistentDataType.STRING);
    }

    public static int charges(ItemStack gun) {
        Integer charges = gun.getItemMeta().getPersistentDataContainer().get(chargesKey(), PersistentDataType.INTEGER);
        return charges == null ? 0 : charges;
    }

    public static RiftDestination destination(ItemStack gun) {
        return RiftDestination.parse(
                gun.getItemMeta().getPersistentDataContainer().get(destinationKey(), PersistentDataType.STRING));
    }

    /**
     * @return until when (epoch milliseconds) a rift compass attunes the gun, 0 if it never was
     */
    public static long attunedUntil(ItemStack gun) {
        Long until = gun.getItemMeta().getPersistentDataContainer().get(attunedKey(), PersistentDataType.LONG);
        return until == null ? 0 : until;
    }

    public static boolean isAttuned(ItemStack gun) {
        return attunedUntil(gun) > System.currentTimeMillis();
    }

    public static void attune(ItemStack gun, long until, RiftSettings settings) {
        ItemMeta meta = gun.getItemMeta();
        meta.getPersistentDataContainer().set(attunedKey(), PersistentDataType.LONG, until);
        gun.setItemMeta(meta);
        refresh(gun, settings);
    }

    /**
     * @return the remaining attunement as m:ss
     */
    static String remaining(ItemStack gun) {
        long seconds = Math.max(0, (attunedUntil(gun) - System.currentTimeMillis()) / 1000);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public static void setCharges(ItemStack gun, int charges, RiftSettings settings) {
        ItemMeta meta = gun.getItemMeta();
        meta.getPersistentDataContainer()
                .set(chargesKey(), PersistentDataType.INTEGER, Math.max(0, Math.min(settings.tankCapacity(), charges)));
        gun.setItemMeta(meta);
        refresh(gun, settings);
    }

    public static void setDestination(ItemStack gun, RiftDestination destination, RiftSettings settings) {
        ItemMeta meta = gun.getItemMeta();
        meta.getPersistentDataContainer().set(destinationKey(), PersistentDataType.STRING, destination.serialize());
        gun.setItemMeta(meta);
        refresh(gun, settings);
    }

    /**
     * Updates the tank level shown by the model and the lore (charges and destination).
     *
     * @return whether the item changed
     */
    public static boolean refresh(ItemStack gun, RiftSettings settings) {
        ItemMeta meta = gun.getItemMeta();
        int charges = charges(gun);
        CustomModelDataComponent modelData = meta.getCustomModelDataComponent();
        List<Float> level = List.of((float) settings.tankLevel(charges));
        boolean changed = !level.equals(modelData.getFloats());
        modelData.setFloats(level);
        meta.setCustomModelDataComponent(modelData);

        RiftDestination destination = destination(gun);
        List<String> lore = new ArrayList<>(lines(MessageManager.getMessage("items.interdimensional-gun.lore")));
        lore.add(MessageManager.getMessage(
                "items.interdimensional-gun.tank",
                Map.of(
                        "bar",
                        tankBar(charges, settings.tankCapacity()),
                        "charges",
                        String.valueOf(charges),
                        "capacity",
                        String.valueOf(settings.tankCapacity()))));
        lore.add(MessageManager.getMessage(
                "items.interdimensional-gun.destination",
                Map.of(
                        "destination",
                        destination == null
                                ? MessageManager.getMessage("rift.destination.none")
                                : RiftNames.describe(destination, null))));
        if (isAttuned(gun)) {
            lore.add(MessageManager.getMessage("items.interdimensional-gun.attuned", Map.of("time", remaining(gun))));
        }
        changed |= !lore.equals(meta.getLore());
        meta.setLore(lore);
        gun.setItemMeta(meta);
        return changed;
    }

    static String tankBar(int charges, int capacity) {
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < capacity; i++) {
            bar.append(i < charges ? "§a■" : "§8■");
        }
        return bar.toString();
    }

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }
}
