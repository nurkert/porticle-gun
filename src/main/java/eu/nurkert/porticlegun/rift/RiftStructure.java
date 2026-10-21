package eu.nurkert.porticlegun.rift;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.generator.structure.Structure;

/**
 * The structures a gun attuned with a rift compass can open rifts to. Several vanilla variants are searched as one
 * (every village type is a village). Ocean monuments are left out on purpose: there is no dry land around them.
 *
 * <p>Variants are kept as vanilla keys and looked up in the server's registry only when searching, so a structure a
 * server version does not know is simply skipped.
 */
public enum RiftStructure {
    VILLAGE(
            "village",
            Material.BELL,
            World.Environment.NORMAL,
            Landing.SURFACE,
            "village_plains",
            "village_desert",
            "village_savanna",
            "village_snowy",
            "village_taiga"),
    PILLAGER_OUTPOST(
            "pillager_outpost", Material.CROSSBOW, World.Environment.NORMAL, Landing.SURFACE, "pillager_outpost"),
    DESERT_PYRAMID(
            "desert_pyramid", Material.CHISELED_SANDSTONE, World.Environment.NORMAL, Landing.SURFACE, "desert_pyramid"),
    JUNGLE_PYRAMID(
            "jungle_pyramid", Material.MOSSY_COBBLESTONE, World.Environment.NORMAL, Landing.SURFACE, "jungle_pyramid"),
    SWAMP_HUT("swamp_hut", Material.CAULDRON, World.Environment.NORMAL, Landing.SURFACE, "swamp_hut"),
    IGLOO("igloo", Material.SNOW_BLOCK, World.Environment.NORMAL, Landing.SURFACE, "igloo"),
    MANSION("mansion", Material.DARK_OAK_LOG, World.Environment.NORMAL, Landing.SURFACE, "mansion"),
    TRAIL_RUINS("trail_ruins", Material.SUSPICIOUS_GRAVEL, World.Environment.NORMAL, Landing.SURFACE, "trail_ruins"),
    RUINED_PORTAL(
            "ruined_portal",
            Material.CRYING_OBSIDIAN,
            World.Environment.NORMAL,
            Landing.SURFACE,
            "ruined_portal",
            "ruined_portal_desert",
            "ruined_portal_jungle",
            "ruined_portal_swamp",
            "ruined_portal_mountain"),
    ANCIENT_CITY(
            "ancient_city", Material.SCULK_SHRIEKER, World.Environment.NORMAL, Landing.UNDERGROUND, "ancient_city"),
    TRIAL_CHAMBERS(
            "trial_chambers", Material.TRIAL_KEY, World.Environment.NORMAL, Landing.UNDERGROUND, "trial_chambers"),
    STRONGHOLD("stronghold", Material.END_PORTAL_FRAME, World.Environment.NORMAL, Landing.UNDERGROUND, "stronghold"),
    FORTRESS("fortress", Material.NETHER_BRICKS, World.Environment.NETHER, Landing.NETHER, "fortress"),
    BASTION("bastion", Material.GILDED_BLACKSTONE, World.Environment.NETHER, Landing.NETHER, "bastion_remnant"),
    END_CITY("end_city", Material.PURPUR_BLOCK, World.Environment.THE_END, Landing.SURFACE, "end_city");

    /** Where to look for a landing spot around the structure. */
    public enum Landing {
        /** on the ground at the surface */
        SURFACE,
        /** between the nether's floor and roof */
        NETHER,
        /** in a cave inside or next to the structure, the surface above as fallback */
        UNDERGROUND
    }

    private final String id;
    private final Material icon;
    private final World.Environment environment;
    private final Landing landing;
    private final List<String> variants;

    RiftStructure(String id, Material icon, World.Environment environment, Landing landing, String... variants) {
        this.id = id;
        this.icon = icon;
        this.environment = environment;
        this.landing = landing;
        this.variants = List.of(variants);
    }

    public String id() {
        return id;
    }

    public Material icon() {
        return icon;
    }

    public World.Environment environment() {
        return environment;
    }

    public Landing landing() {
        return landing;
    }

    /**
     * @return the vanilla keys of the structures searched for this kind, e.g. {@code village_plains}
     */
    public List<String> variants() {
        return variants;
    }

    /**
     * @return the structures this server knows of this kind
     */
    public List<Structure> structures() {
        List<Structure> structures = new ArrayList<>();
        for (String variant : variants) {
            Structure structure = Registry.STRUCTURE.get(NamespacedKey.minecraft(variant));
            if (structure != null) {
                structures.add(structure);
            }
        }
        return structures;
    }

    public static RiftStructure byId(String id) {
        for (RiftStructure structure : values()) {
            if (structure.id.equals(id)) {
                return structure;
            }
        }
        return null;
    }
}
