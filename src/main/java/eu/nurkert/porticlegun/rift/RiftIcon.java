package eu.nurkert.porticlegun.rift;

import org.bukkit.World;

/**
 * An icon for a button of the native menus, as a vanilla texture on both editions: a sprite of the Java client's block
 * or item atlas ({@code block/...} or {@code item/...}) and a Bedrock texture path. Only vanilla textures (or ones from
 * the plugin's Bedrock pack, which Geyser always sends) are used, so nothing shows up missing without the Java pack.
 */
record RiftIcon(String javaSprite, String bedrockPath) {

    static final RiftIcon OVERWORLD = new RiftIcon("block/grass_block_side", "textures/blocks/grass_side_carried");
    static final RiftIcon NETHER = new RiftIcon("block/netherrack", "textures/blocks/netherrack");
    static final RiftIcon END = new RiftIcon("block/end_stone", "textures/blocks/end_stone");
    static final RiftIcon SPAWN = new RiftIcon("block/lodestone_top", "textures/blocks/lodestone_top");
    static final RiftIcon BACK = new RiftIcon("item/recovery_compass_16", "textures/items/recovery_compass_item");
    static final RiftIcon DEATH = new RiftIcon("item/bone", "textures/items/bone");
    static final RiftIcon ATLAS = new RiftIcon("item/filled_map", "textures/items/map_filled");
    static final RiftIcon ATLAS_LOCKED = new RiftIcon("item/map", "textures/items/map_empty");
    static final RiftIcon HOMES = new RiftIcon("item/oak_door", "textures/items/door_wood");
    static final RiftIcon HOME = new RiftIcon("item/oak_door", "textures/items/door_wood");
    static final RiftIcon HOME_EMPTY = new RiftIcon("item/light_gray_dye", "textures/items/dye_powder_silver");
    static final RiftIcon TRAVEL = new RiftIcon("item/ender_pearl", "textures/items/ender_pearl");
    static final RiftIcon SET_HERE = new RiftIcon("item/compass_16", "textures/items/compass_item");
    static final RiftIcon DELETE = new RiftIcon("item/lava_bucket", "textures/items/bucket_lava");
    static final RiftIcon KEEP = new RiftIcon("item/lime_dye", "textures/items/dye_powder_lime");
    static final RiftIcon TANK = new RiftIcon("item/experience_bottle", "textures/items/portal_fluid");
    static final RiftIcon GUIDE = new RiftIcon("item/knowledge_book", "textures/items/book_enchanted");

    static RiftIcon world(World.Environment environment) {
        return switch (environment) {
            case NETHER -> NETHER;
            case THE_END -> END;
            default -> OVERWORLD;
        };
    }

    static RiftIcon structure(RiftStructure structure) {
        return switch (structure) {
            case VILLAGE -> new RiftIcon("item/bell", "textures/blocks/bell_side");
            case PILLAGER_OUTPOST -> new RiftIcon("item/crossbow_standby", "textures/items/crossbow_standby");
            case DESERT_PYRAMID -> new RiftIcon("block/chiseled_sandstone", "textures/blocks/sandstone_carved");
            case JUNGLE_PYRAMID -> new RiftIcon("block/mossy_cobblestone", "textures/blocks/cobblestone_mossy");
            case SWAMP_HUT -> new RiftIcon("item/cauldron", "textures/items/cauldron");
            case IGLOO -> new RiftIcon("block/snow", "textures/blocks/snow");
            case MANSION -> new RiftIcon("block/dark_oak_log", "textures/blocks/log_big_oak");
            case TRAIL_RUINS -> new RiftIcon("block/suspicious_gravel_0", "textures/blocks/suspicious_gravel_0");
            case RUINED_PORTAL -> new RiftIcon("block/crying_obsidian", "textures/blocks/crying_obsidian");
            case ANCIENT_CITY -> new RiftIcon("block/sculk_shrieker_top", "textures/blocks/sculk_shrieker_top");
            case TRIAL_CHAMBERS -> new RiftIcon("item/trial_key", "textures/items/trial_key");
            case STRONGHOLD -> new RiftIcon("block/end_portal_frame_top", "textures/blocks/endframe_top");
            case FORTRESS -> new RiftIcon("block/nether_bricks", "textures/blocks/nether_brick");
            case BASTION -> new RiftIcon("block/gilded_blackstone", "textures/blocks/gilded_blackstone");
            case END_CITY -> new RiftIcon("block/purpur_block", "textures/blocks/purpur_block");
        };
    }

    /** @return the Java atlas the sprite lives in */
    String javaAtlas() {
        return javaSprite.startsWith("block/") ? "blocks" : "items";
    }
}
