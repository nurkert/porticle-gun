package eu.nurkert.porticlegun.gravity;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.audio.AudioHandler;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.util.WorldGuardIntegration;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Campfire;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.DecoratedPot;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Lectern;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Piston;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * The gravity gun: with the gun in hand, the drop key picks up the targeted block or mob and lets it go again.
 *
 * <p>Carried blocks are falling block entities pulled towards the player's crosshair every tick. The block is taken
 * with its complete state (contents, spawner settings, sign text, names, ...) as a snapshot, and the plugin puts it back
 * itself wherever it comes down: vanilla would break a falling block into an item as soon as it lands on a slab, a
 * torch or in a full block, and lose everything inside. Double chests, beds, doors and tall plants travel as one piece.
 *
 * <p>Nothing inside a block may be lost or doubled: the snapshot is taken before the block is removed, the live block
 * is emptied before it is removed, and every carried block is placed (or, without any room nearby, dropped with all its
 * contents) exactly once.
 */
public class GravityGun implements Listener {

    private static GravityGun instance;

    // player -> the entity they are currently holding
    private final HashMap<Player, Entity> players;
    // held entity -> the location it is pulled towards
    private final HashMap<Entity, Location> entitys;
    private final HashMap<Entity, BlockDisplay> visualDisplays;
    // held block -> the other parts of its double chest, bed or door, which follow it
    private final HashMap<Entity, List<FallingBlock>> followers = new HashMap<>();
    // every carried block entity that still has to be placed, held or on its way down
    private final Set<FallingBlock> carried = new LinkedHashSet<>();
    // carried block -> the state it was picked up with, contents included
    private final Map<UUID, BlockState> states = new HashMap<>();
    // carried blocks already put back, so no event places one twice
    private final Set<UUID> placed = new HashSet<>();
    private BukkitTask task;

    private final Set<Material> blacklist;
    private boolean allowPlayerCapture;

    /*
     * Everything that must survive a restart or crash is kept with the falling block itself, because the entity is
     * saved with its chunk: on Paper the complete block state (FallingBlock#setBlockState), elsewhere the spawner type
     * and the container contents in its persistent data container.
     */
    private final NamespacedKey keyCarried;
    private final NamespacedKey keySpawnerType;
    private final NamespacedKey keyContainerItems;
    private final NamespacedKey keyGroup;
    private final NamespacedKey keyOffset;

    // Paper only, looked up once: the falling block's complete state and entities without collisions
    private static final Method SET_BLOCK_STATE = method(FallingBlock.class, "setBlockState", BlockState.class);
    private static final Method GET_BLOCK_STATE = method(FallingBlock.class, "getBlockState");
    private static final Method SET_NO_PHYSICS = method(Entity.class, "setNoPhysics", boolean.class);

    // blocks nobody should move: technical blocks and the inside of a moving piston
    private static final Set<Material> NEVER = EnumSet.of(
            Material.MOVING_PISTON,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.BUBBLE_COLUMN,
            Material.NETHER_PORTAL,
            Material.END_PORTAL,
            Material.END_GATEWAY);
    // where a carried block may go when the spot it comes down on is taken: straight up first, then around it
    private static final List<int[]> SEARCH = searchOrder();

    public GravityGun(Collection<Material> blockBlacklist, boolean allowPlayerCapture) {
        instance = this;
        entitys = new HashMap<>();
        players = new HashMap<>();
        visualDisplays = new HashMap<>();
        blacklist = new HashSet<>();
        keyCarried = new NamespacedKey(PorticleGun.getInstance(), "gravity_carried");
        keySpawnerType = new NamespacedKey(PorticleGun.getInstance(), "gravity_spawner_type");
        keyContainerItems = new NamespacedKey(PorticleGun.getInstance(), "gravity_container_items");
        keyGroup = new NamespacedKey(PorticleGun.getInstance(), "gravity_group");
        keyOffset = new NamespacedKey(PorticleGun.getInstance(), "gravity_offset");
        this.allowPlayerCapture = allowPlayerCapture;
        updateBlockBlacklist(blockBlacklist);
        init();
        recoverOrphanedBlocks();
    }

    public static GravityGun getInstance() {
        return instance;
    }

    public void updateBlockBlacklist(Collection<Material> blockBlacklist) {
        blacklist.clear();
        if (blockBlacklist != null) {
            for (Material material : blockBlacklist) {
                if (material != null) {
                    blacklist.add(material);
                }
            }
        }
    }

    public void setAllowPlayerCapture(boolean allowPlayerCapture) {
        this.allowPlayerCapture = allowPlayerCapture;
    }

    private void init() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<Entity, Location>> iterator =
                        entitys.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Entity, Location> entry = iterator.next();
                    Entity entity = entry.getKey();

                    if (entity == null || entity.isDead() || !entity.isValid()) {
                        iterator.remove();
                        handleEntityRemoval(entity);
                        continue;
                    }

                    if (entity instanceof FallingBlock) {
                        // vanilla discards falling blocks that have not landed after 600 ticks
                        entity.setTicksLived(1);
                    }

                    Vector plV = entry.getValue().toVector();
                    Vector spV = entity.getLocation().toVector();
                    Vector direction = plV.subtract(spV)
                            .normalize()
                            .multiply(entry.getValue().distance(entity.getLocation()) / 2);
                    if (Double.isFinite(direction.getX())) {
                        entity.setVelocity(direction);
                    }
                    pullFollowers(entity, direction);
                    updateVisualDisplay(entity);
                }

                updateRemainingVisualDisplays();
            }
        }.runTaskTimer(PorticleGun.getInstance(), 0, 1);
    }

    /** The other parts of a carried double chest, bed or door keep their place next to the held one. */
    private void pullFollowers(Entity leader, Vector leaderVelocity) {
        List<FallingBlock> parts = followers.get(leader);
        if (parts == null) {
            return;
        }
        Vector next = leader.getLocation().toVector();
        if (Double.isFinite(leaderVelocity.getX())) {
            next.add(leaderVelocity);
        }
        for (FallingBlock part : parts) {
            if (!part.isValid()) {
                continue;
            }
            part.setTicksLived(1);
            int[] offset = offset(part);
            Vector target = next.clone().add(new Vector(offset[0], offset[1], offset[2]));
            part.setVelocity(target.subtract(part.getLocation().toVector()));
        }
    }

    /**
     * Stops the gravity gun and puts every carried block back into the world, so nothing is lost when the plugin is
     * disabled or the server stops.
     */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        for (Entity entity : new ArrayList<>(entitys.keySet())) {
            releaseEntity(entity, false);
        }
        for (FallingBlock block : new ArrayList<>(carried)) {
            if (block.isValid() && !placed.contains(block.getUniqueId())) {
                // no time left to let it fall: put it on the ground below
                land(block, groundBelow(block.getLocation().getBlock()));
                block.remove();
            }
        }
        carried.clear();

        for (BlockDisplay display : visualDisplays.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        visualDisplays.clear();

        if (instance == this) {
            instance = null;
        }
    }

    public boolean releaseEntity(Entity entity) {
        return releaseEntity(entity, true);
    }

    public boolean releaseEntitySilently(Entity entity) {
        return releaseEntity(entity, false);
    }

    /** Whether the entity is one part of a carried double chest, bed or door, which must not be separated. */
    public boolean isMultiBlockPart(Entity entity) {
        return entity instanceof FallingBlock
                && entity.getPersistentDataContainer().has(keyGroup, PersistentDataType.STRING)
                && !placed.contains(entity.getUniqueId());
    }

    private boolean releaseEntity(Entity entity, boolean playSound) {
        if (entity == null || !entitys.containsKey(entity)) {
            return false;
        }

        entitys.remove(entity);
        entity.setFallDistance(0F);
        if (entity instanceof FallingBlock) {
            letFall(entity);
        }
        List<FallingBlock> parts = followers.remove(entity);
        if (parts != null) {
            for (FallingBlock part : parts) {
                if (part.isValid()) {
                    // the same speed as the held part, so they come down side by side
                    part.setVelocity(entity.getVelocity());
                    letFall(part);
                }
            }
        }

        Player owner = getOwner(entity);
        if (owner != null) {
            players.remove(owner);
            if (playSound) {
                AudioHandler.playSound(owner.getLocation(), AudioHandler.PortalSound.LET_BLOCK);
            }
        }

        return true;
    }

    private void letFall(Entity entity) {
        entity.setGravity(true);
        setNoPhysics(entity, false);
    }

    private Player getOwner(Entity entity) {
        for (Map.Entry<Player, Entity> entry : players.entrySet()) {
            if (entry.getValue().equals(entity)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @EventHandler
    public void on(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (player.hasMetadata("portalgun-drop_bypass")) {
            long clicked = player.getMetadata("portalgun-drop_bypass").get(0).asLong();
            player.removeMetadata("portalgun-drop_bypass", PorticleGun.getInstance());
            if (System.currentTimeMillis() - clicked < 250) {
                return; // dropped from the inventory, not with the drop key in hand
            }
        }

        ItemStack item = event.getItemDrop().getItemStack();
        if (item.getType() != Material.BREWING_STAND || ItemHandler.isValidGun(item) == null) {
            return;
        }

        if (player.hasMetadata("portalgun-drop")
                && player.getMetadata("portalgun-drop").size() > 0) {
            long time =
                    Long.parseLong(player.getMetadata("portalgun-drop").get(0).asString());
            if (System.currentTimeMillis() - time < 500) {
                // second drop in quick succession: really drop the gun
                if (players.containsKey(player)) {
                    releaseEntity(players.get(player), true);
                }
                return;
            }
            player.removeMetadata("portalgun-drop", PorticleGun.getInstance());
        }

        event.setCancelled(true);
        player.setMetadata(
                "portalgun-drop", new FixedMetadataValue(PorticleGun.getInstance(), System.currentTimeMillis() + ""));

        if (players.containsKey(player)) {
            releaseEntity(players.get(player), true);
            return;
        }

        Block block = player.getTargetBlockExact(4);

        if (block == null || block.getType() == Material.AIR) {
            AudioHandler.playSound(player, AudioHandler.PortalSound.DENY);
            return;
        }

        Location loc = playersLook(player);

        for (Entity entity : loc.getChunk().getEntities()) {
            if (!(entity instanceof LivingEntity)) {
                continue;
            }

            if (entity.equals(player) || entitys.containsKey(entity)) {
                continue;
            }

            if (entity.getType() == EntityType.PLAYER && !allowPlayerCapture) {
                continue;
            }

            if (entity.getLocation().distance(loc) < 1.5) {
                if (PorticleGun.isWorldGuardEnabled()
                        && !WorldGuardIntegration.canUseGravityGun(player, entity.getLocation())) {
                    continue;
                }
                entitys.put(entity, playersLook(player));
                players.put(player, entity);
                AudioHandler.playSound(player.getLocation(), AudioHandler.PortalSound.GRAB_BLOCK);
                return;
            }
        }

        if (!pickUp(player, block)) {
            AudioHandler.playSound(player, AudioHandler.PortalSound.DENY);
        }
    }

    /** Lifts the block, together with the other half of a double chest, bed or door, out of the world. */
    private boolean pickUp(Player player, Block block) {
        if (block.getType() == Material.PISTON_HEAD && block.getBlockData() instanceof Directional head) {
            // the head belongs to its piston: take the piston, retracted
            block = block.getRelative(head.getFacing().getOppositeFace());
        }
        if (!canPickUp(player, block)) {
            return false;
        }

        for (Block part : parts(block)) {
            if (PorticleGun.isWorldGuardEnabled()
                    && !WorldGuardIntegration.canUseGravityGun(player, part.getLocation())) {
                return false;
            }
        }

        List<FallingBlock> spawned = lift(block);
        AudioHandler.playSound(player.getLocation(), AudioHandler.PortalSound.GRAB_BLOCK);
        FallingBlock leader = spawned.get(0);
        if (spawned.size() > 1) {
            followers.put(leader, new ArrayList<>(spawned.subList(1, spawned.size())));
        }
        entitys.put(leader, playersLook(player));
        players.put(player, leader);
        return true;
    }

    /** The block and the other half of its double chest, bed or door. */
    private static List<Block> parts(Block block) {
        List<Block> parts = new ArrayList<>();
        parts.add(block);
        Block partner = partner(block);
        if (partner != null) {
            parts.add(partner);
        }
        return parts;
    }

    /** Replaces the block (and its other half) with carried falling blocks; the first one is the block itself. */
    private List<FallingBlock> lift(Block block) {
        List<Block> parts = parts(block);

        // the complete states first, before anything in the world changes
        List<BlockState> snapshots = new ArrayList<>();
        for (Block part : parts) {
            BlockState snapshot = part.getState();
            snapshot.setBlockData(carriedData(snapshot.getBlockData(), parts.size() > 1));
            snapshots.add(snapshot);
        }
        Block pistonHead = null;
        if (block.getBlockData() instanceof Piston piston && piston.isExtended()) {
            pistonHead = block.getRelative(piston.getFacing());
        }

        // empty the live blocks, so nothing inside can drop or be left behind when they are removed
        for (Block part : parts) {
            emptyLive(part);
        }
        /*
         * Removed without block updates first: with updates, the half removed first would make vanilla destroy the
         * other one, and a door's lower half or a bed's head drops its item when destroyed. Then air with updates, so
         * torches on them break off and redstone notices, like after mining.
         */
        List<Block> cells = new ArrayList<>(parts);
        if (pistonHead != null && pistonHead.getType() == Material.PISTON_HEAD) {
            cells.add(pistonHead);
        }
        for (Block cell : cells) {
            cell.setType(Material.STRUCTURE_VOID, false);
        }
        for (Block cell : cells) {
            cell.setType(Material.AIR, true);
        }

        String group = parts.size() > 1 ? UUID.randomUUID().toString() : null;
        List<FallingBlock> spawned = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            Block part = parts.get(i);
            int[] offset = {part.getX() - block.getX(), part.getY() - block.getY(), part.getZ() - block.getZ()};
            spawned.add(spawnCarried(part, snapshots.get(i), group, offset));
        }
        return spawned;
    }

    private boolean canPickUp(Player player, Block block) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid() || blacklist.contains(type) || NEVER.contains(type)) {
            return false;
        }
        // bedrock, barriers, end portal frames, command blocks ... only in creative mode
        return type.getHardness() >= 0 || player.getGameMode() == GameMode.CREATIVE;
    }

    /**
     * The other half of a double chest, bed, door or tall plant, if it is really there; {@code null} for blocks that
     * stand alone.
     */
    private static Block partner(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Chest chest && chest.getType() != Chest.Type.SINGLE) {
            // vanilla: the left half's partner is clockwise of its facing, the right half's counter-clockwise
            BlockFace side = chest.getType() == Chest.Type.LEFT
                    ? clockwise(chest.getFacing())
                    : clockwise(chest.getFacing()).getOppositeFace();
            Block other = block.getRelative(side);
            if (other.getType() == block.getType()
                    && other.getBlockData() instanceof Chest otherChest
                    && otherChest.getFacing() == chest.getFacing()
                    && otherChest.getType() != chest.getType()
                    && otherChest.getType() != Chest.Type.SINGLE) {
                return other;
            }
            return null;
        }
        if (data instanceof Bed bed) {
            BlockFace side = bed.getPart() == Bed.Part.FOOT
                    ? bed.getFacing()
                    : bed.getFacing().getOppositeFace();
            Block other = block.getRelative(side);
            if (other.getType() == block.getType()
                    && other.getBlockData() instanceof Bed otherBed
                    && otherBed.getPart() != bed.getPart()) {
                return other;
            }
            return null;
        }
        // doors and tall plants; stairs and trapdoors are bisected too, but only describe which way up they are
        if (data instanceof Bisected bisected && !(data instanceof Stairs) && !(data instanceof TrapDoor)) {
            Block other = block.getRelative(bisected.getHalf() == Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN);
            if (other.getType() == block.getType()
                    && other.getBlockData() instanceof Bisected otherHalf
                    && otherHalf.getHalf() != bisected.getHalf()) {
                return other;
            }
        }
        return null;
    }

    private static BlockFace clockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> face;
        };
    }

    /**
     * The block as it travels: without the water it stood in (that stays behind), a chest carried alone as a single
     * chest, a piston retracted (its head stays behind and disappears).
     */
    private static BlockData carriedData(BlockData data, boolean wholeGroup) {
        BlockData copy = data.clone();
        if (copy instanceof Waterlogged waterlogged) {
            waterlogged.setWaterlogged(false);
        }
        if (copy instanceof Chest chest && !wholeGroup) {
            chest.setType(Chest.Type.SINGLE);
        }
        if (copy instanceof Piston piston) {
            piston.setExtended(false);
        }
        return copy;
    }

    /** Takes everything out of the block in the world; the snapshot keeps it. */
    private static void emptyLive(Block block) {
        BlockState state = block.getState();
        if (state instanceof org.bukkit.block.Chest chest) {
            // only this half: the inventory of a double chest would be both
            chest.getBlockInventory().clear();
        } else if (state instanceof Container container) {
            container.getInventory().clear();
        } else if (state instanceof Lectern lectern) {
            lectern.getInventory().clear();
        } else if (state instanceof ChiseledBookshelf bookshelf) {
            bookshelf.getInventory().clear();
        } else if (state instanceof DecoratedPot pot) {
            pot.getInventory().clear();
        } else if (state instanceof Jukebox jukebox) {
            jukebox.getInventory().clear();
        } else if (state instanceof Campfire campfire) {
            for (int i = 0; i < 4; i++) {
                campfire.setItem(i, null);
            }
            campfire.update(true, false);
        }
    }

    private FallingBlock spawnCarried(Block origin, BlockState snapshot, String group, int[] offset) {
        FallingBlock fallingBlock =
                origin.getWorld().spawnFallingBlock(origin.getLocation().add(0.5, 0, 0.5), snapshot.getBlockData());
        // without gravity the Bedrock client does not simulate the block falling and landing on its own
        fallingBlock.setGravity(false);
        // without collisions (Paper) a carried block never lands, whatever it is held against
        setNoPhysics(fallingBlock, true);
        fallingBlock.setDropItem(true);
        fallingBlock.setHurtEntities(false);

        PersistentDataContainer data = fallingBlock.getPersistentDataContainer();
        data.set(keyCarried, PersistentDataType.BYTE, (byte) 1);
        if (group != null) {
            data.set(keyGroup, PersistentDataType.STRING, group);
            data.set(keyOffset, PersistentDataType.INTEGER_ARRAY, offset);
        }
        if (!storeStateInEntity(fallingBlock, snapshot)) {
            // without Paper only the spawner type and the contents survive a crash
            if (snapshot instanceof CreatureSpawner spawner && spawner.getSpawnedType() != null) {
                data.set(
                        keySpawnerType,
                        PersistentDataType.STRING,
                        spawner.getSpawnedType().name());
            }
            ItemStack[] contents = contentsOf(snapshot);
            if (snapshot instanceof Container && hasItems(contents)) {
                data.set(keyContainerItems, PersistentDataType.STRING, serializeItems(contents));
            }
        }

        states.put(fallingBlock.getUniqueId(), snapshot);
        carried.add(fallingBlock);
        createVisualDisplayIfNeeded(snapshot.getBlockData(), fallingBlock);
        return fallingBlock;
    }

    /**
     * Dropping the gun out of an open inventory (dragged out of the window, or the drop key over its slot) really drops
     * it; only the drop key with the gun in hand grabs with the gravity gun. Geyser turns Bedrock's inventory drops
     * into the same clicks.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(InventoryClickEvent event) {
        ItemStack dropped =
                switch (event.getAction()) {
                    case DROP_ALL_CURSOR, DROP_ONE_CURSOR -> event.getCursor();
                    case DROP_ALL_SLOT, DROP_ONE_SLOT -> event.getCurrentItem();
                        // creative mode reports a drop out of the window as a click outside it
                    default -> event.getSlotType() == InventoryType.SlotType.OUTSIDE ? event.getCursor() : null;
                };
        if (dropped != null && ItemHandler.isValidGun(dropped) != null) {
            event.getWhoClicked()
                    .setMetadata(
                            "portalgun-drop_bypass",
                            new FixedMetadataValue(PorticleGun.getInstance(), System.currentTimeMillis()));
        }
    }

    @EventHandler
    public void on(PlayerTeleportEvent event) {
        releaseHeldOrSelf(event.getPlayer());
    }

    @EventHandler
    public void on(EntityDamageEvent event) {
        if (entitys.containsKey(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** A carried block came down: the plugin places it, not vanilla. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock) || !isCarriedBlock(fallingBlock)) {
            return;
        }
        event.setCancelled(true);
        land(fallingBlock, event.getBlock());
    }

    /** Vanilla would break a carried block into an item (on a slab, a torch, in a full block): place it instead. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock) || !isCarriedBlock(fallingBlock)) {
            return;
        }
        event.setCancelled(true);
        land(fallingBlock, fallingBlock.getLocation().getBlock());
    }

    /** The last safety net: a carried block that vanishes for any other reason is placed as well. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock) || !isCarriedBlock(fallingBlock)) {
            return;
        }
        switch (event.getCause()) {
            case DESPAWN, DROP, ENTER_BLOCK, EXPLODE, HIT -> {
                if (!placed.contains(fallingBlock.getUniqueId())) {
                    land(fallingBlock, fallingBlock.getLocation().getBlock());
                }
            }
            case UNLOAD -> {
                // saved with its chunk and placed when it comes back; only let go of it here
                releaseEntity(fallingBlock, false);
                carried.remove(fallingBlock);
                removeVisualDisplay(fallingBlock);
            }
            default -> {}
        }
    }

    @EventHandler
    public void on(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            recoverIfOrphaned(entity);
        }
    }

    @EventHandler
    public void on(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack previous = player.getInventory().getItem(event.getPreviousSlot());
        if (players.containsKey(player) && previous != null && ItemHandler.isValidGun(previous) != null) {
            releaseEntity(players.get(player), true);
        }
    }

    @EventHandler
    public void on(PlayerQuitEvent event) {
        releaseHeldOrSelf(event.getPlayer());
    }

    @EventHandler
    public void on(PlayerMoveEvent event) {
        if (players.containsKey(event.getPlayer()))
            entitys.put(players.get(event.getPlayer()), playersLook(event.getPlayer()));
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && entitys.containsKey(event.getPlayer())) {
            releaseEntity(event.getPlayer());
        }
    }

    private void releaseHeldOrSelf(Player player) {
        if (players.containsKey(player)) {
            releaseEntity(players.get(player), false);
        } else if (entitys.containsKey(player)) {
            releaseEntitySilently(player);
        }
    }

    /**
     * Where the held entity is pulled to: three blocks in front of the eyes, but never into a wall or the floor, so a
     * block held against something stays in front of it.
     */
    private Location playersLook(Player player) {
        Location eye = player.getEyeLocation();
        Location target = eye.clone()
                .add(eye.clone().add(0, 0.75, 0).getDirection().normalize().multiply(3.0D))
                .subtract(0, 0.2, 0);
        // the held block hangs from the target point; trace towards its centre
        Vector toCentre = target.toVector().add(new Vector(0, 0.5, 0)).subtract(eye.toVector());
        double length = toCentre.length();
        if (length < 1.0E-3) {
            return target;
        }
        Vector direction = toCentre.clone().multiply(1 / length);
        RayTraceResult hit =
                player.getWorld().rayTraceBlocks(eye, direction, length + 0.6, FluidCollisionMode.NEVER, true);
        if (hit == null) {
            return target;
        }
        double free = hit.getHitPosition().distance(eye.toVector()) - 0.6;
        if (free >= length) {
            return target;
        }
        Vector centre = eye.toVector().add(direction.multiply(Math.max(free, 0.8)));
        return centre.subtract(new Vector(0, 0.5, 0)).toLocation(player.getWorld(), target.getYaw(), target.getPitch());
    }

    private boolean isCarriedBlock(Entity entity) {
        return entity.getPersistentDataContainer().has(keyCarried, PersistentDataType.BYTE);
    }

    /**
     * Carried blocks have no gravity. If the server crashed while a block was carried, it would float forever, so
     * blocks that are not held by anyone get their gravity back and land normally.
     */
    private void recoverOrphanedBlocks() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(FallingBlock.class)) {
                recoverIfOrphaned(entity);
            }
        }
    }

    private void recoverIfOrphaned(Entity entity) {
        if (entity instanceof FallingBlock fallingBlock
                && isCarriedBlock(entity)
                && !entitys.containsKey(entity)
                && !placed.contains(entity.getUniqueId())) {
            letFall(entity);
            carried.add(fallingBlock);
        }
    }

    // --- putting blocks back ----------------------------------------------------------------------------------------

    /**
     * Puts a carried block, with the other parts of its double chest, bed or door, back into the world where it came
     * down, or as close above or beside it as there is room. Without any room it drops, with everything inside.
     */
    private void land(FallingBlock fallingBlock, Block where) {
        if (!placed.add(fallingBlock.getUniqueId())) {
            return;
        }
        Entity holder = leaderOf(fallingBlock);
        if (holder != null) {
            releaseEntity(holder, false);
        }

        List<FallingBlock> group = new ArrayList<>();
        group.add(fallingBlock);
        for (FallingBlock other : groupMembers(fallingBlock)) {
            if (placed.add(other.getUniqueId())) {
                group.add(other);
            }
        }
        boolean whole = !isMultiBlock(fallingBlock) || group.size() > 1;

        int[] own = offset(fallingBlock);
        Block base = where.getRelative(-own[0], -own[1], -own[2]);
        Block spot = whole ? findSpot(base, group) : null;
        boolean loneChestHalf = !whole && dataOf(fallingBlock) instanceof Chest;
        if (loneChestHalf) {
            // its other half got lost (an unloaded chunk): it is still a whole chest of its own
            spot = findSpot(where, List.of(fallingBlock));
            own = new int[3];
        }

        for (FallingBlock part : group) {
            int[] offset = loneChestHalf ? own : offset(part);
            if (spot != null) {
                place(part, spot.getRelative(offset[0], offset[1], offset[2]), group.size() == 1, loneChestHalf);
            } else {
                drop(part, where.getLocation().add(0.5, 0.5, 0.5));
            }
            forget(part);
            if (part != fallingBlock && part.isValid()) {
                part.remove();
            }
        }
    }

    private Block groundBelow(Block block) {
        int lowest = Math.max(block.getWorld().getMinHeight(), block.getY() - 64);
        while (block.getY() > lowest && canPlaceInto(block.getRelative(BlockFace.DOWN))) {
            block = block.getRelative(BlockFace.DOWN);
        }
        return block;
    }

    /** The held entity a carried block belongs to: itself, or the part of its group the player holds. */
    private Entity leaderOf(FallingBlock fallingBlock) {
        if (entitys.containsKey(fallingBlock)) {
            return fallingBlock;
        }
        for (Map.Entry<Entity, List<FallingBlock>> entry : followers.entrySet()) {
            if (entry.getValue().contains(fallingBlock)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean isMultiBlock(FallingBlock fallingBlock) {
        return fallingBlock.getPersistentDataContainer().has(keyGroup, PersistentDataType.STRING);
    }

    /** The other parts of the block's group that are still on their way, found by their group id. */
    private List<FallingBlock> groupMembers(FallingBlock fallingBlock) {
        String group = fallingBlock.getPersistentDataContainer().get(keyGroup, PersistentDataType.STRING);
        List<FallingBlock> members = new ArrayList<>();
        if (group == null) {
            return members;
        }
        for (Entity nearby : fallingBlock.getNearbyEntities(4, 4, 4)) {
            if (nearby instanceof FallingBlock other
                    && nearby.isValid()
                    && !placed.contains(nearby.getUniqueId())
                    && group.equals(nearby.getPersistentDataContainer().get(keyGroup, PersistentDataType.STRING))) {
                members.add(other);
            }
        }
        return members;
    }

    /** The first spot (straight up first, then around) where every part of the group fits and holds. */
    private Block findSpot(Block base, List<FallingBlock> group) {
        World world = base.getWorld();
        for (int[] step : SEARCH) {
            Block candidate = base.getRelative(step[0], step[1], step[2]);
            boolean fits = true;
            for (FallingBlock part : group) {
                int[] offset = group.size() == 1 ? new int[3] : offset(part);
                Block cell = candidate.getRelative(offset[0], offset[1], offset[2]);
                if (cell.getY() < world.getMinHeight()
                        || cell.getY() >= world.getMaxHeight()
                        || !canPlaceInto(cell)
                        || (!(group.size() > 1 && restsOnOtherHalf(dataOf(part)))
                                && !dataOf(part).isSupported(cell))) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                return candidate;
            }
        }
        return null;
    }

    /** The upper half of a door or tall plant only holds on its lower half, which is not there yet when checking. */
    private static boolean restsOnOtherHalf(BlockData data) {
        return data instanceof Bisected bisected
                && bisected.getHalf() == Bisected.Half.TOP
                && !(data instanceof Stairs)
                && !(data instanceof TrapDoor);
    }

    private static List<int[]> searchOrder() {
        List<int[]> order = new ArrayList<>();
        for (int up = 0; up <= 4; up++) {
            order.add(new int[] {0, up, 0});
        }
        for (int radius = 1; radius <= 2; radius++) {
            for (int up = 0; up <= 2; up++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) == radius) {
                            order.add(new int[] {x, up, z});
                        }
                    }
                }
            }
        }
        return order;
    }

    private boolean canPlaceInto(Block block) {
        return block.getType().isAir() || (Tag.REPLACEABLE.isTagged(block.getType()) && !block.isLiquid());
    }

    /**
     * Places the block with its complete state. Parts of a group go in without block updates first, so a door's lower
     * half does not break off before its upper half is there.
     */
    private void place(FallingBlock fallingBlock, Block target, boolean updates, boolean singleChest) {
        BlockState state = states.get(fallingBlock.getUniqueId());
        ItemStack[] legacyContents = getStoredContainerItems(fallingBlock);
        if (state == null && legacyContents == null) {
            state = stateFromEntity(fallingBlock);
        }
        BlockData data = dataOf(fallingBlock).clone();
        if (singleChest && data instanceof Chest chest) {
            chest.setType(Chest.Type.SINGLE);
        }
        if (state != null) {
            BlockState copy = state.copy(target.getLocation());
            if (copy.getType() == data.getMaterial()) {
                copy.setBlockData(data);
            }
            copy.update(true, updates);
            return;
        }
        // without a state (a crash without Paper): the block, the spawner type and the contents
        target.setBlockData(data, updates);
        EntityType spawnedType = getSpawnerType(fallingBlock);
        if (spawnedType != null) {
            applySpawnerType(target, spawnedType);
        }
        fillContainerOrDrop(target, legacyContents);
    }

    /**
     * No room anywhere near: the block drops as an item with everything that was inside. Of a bed, door or tall plant
     * only one part gives the item (the one the player picked up), each half of a double chest is a chest.
     */
    private void drop(FallingBlock fallingBlock, Location location) {
        BlockState state = states.get(fallingBlock.getUniqueId());
        ItemStack[] contents = getStoredContainerItems(fallingBlock);
        if (contents == null) {
            if (state == null) {
                state = stateFromEntity(fallingBlock);
            }
            contents = state == null ? null : contentsOf(state);
        }
        dropItems(location, contents);
        int[] offset = offset(fallingBlock);
        boolean mainPart = !isMultiBlock(fallingBlock)
                || dataOf(fallingBlock) instanceof Chest
                || (offset[0] == 0 && offset[1] == 0 && offset[2] == 0);
        if (mainPart) {
            dropItems(
                    location,
                    new ItemStack[] {new ItemStack(dataOf(fallingBlock).getPlacementMaterial())});
        }
    }

    private void forget(FallingBlock fallingBlock) {
        states.remove(fallingBlock.getUniqueId());
        carried.remove(fallingBlock);
        removeVisualDisplay(fallingBlock);
        fallingBlock.getPersistentDataContainer().remove(keyContainerItems);
    }

    private BlockData dataOf(FallingBlock fallingBlock) {
        BlockState state = states.get(fallingBlock.getUniqueId());
        return state != null ? state.getBlockData() : fallingBlock.getBlockData();
    }

    private int[] offset(FallingBlock fallingBlock) {
        int[] offset = fallingBlock.getPersistentDataContainer().get(keyOffset, PersistentDataType.INTEGER_ARRAY);
        return offset != null && offset.length == 3 ? offset : new int[3];
    }

    /** Everything inside a block state that would be lost if the block dropped as a plain item. */
    private static ItemStack[] contentsOf(BlockState state) {
        if (state instanceof Container container) {
            return container.getSnapshotInventory().getContents();
        }
        Inventory inventory = null;
        if (state instanceof Lectern lectern) {
            inventory = lectern.getSnapshotInventory();
        } else if (state instanceof ChiseledBookshelf bookshelf) {
            inventory = bookshelf.getSnapshotInventory();
        } else if (state instanceof DecoratedPot pot) {
            inventory = pot.getSnapshotInventory();
        } else if (state instanceof Jukebox jukebox) {
            inventory = jukebox.getSnapshotInventory();
        } else if (state instanceof Campfire campfire) {
            ItemStack[] items = new ItemStack[4];
            for (int i = 0; i < items.length; i++) {
                items[i] = campfire.getItem(i);
            }
            return items;
        }
        return inventory == null ? null : inventory.getContents();
    }

    private static boolean hasItems(ItemStack[] items) {
        if (items == null) {
            return false;
        }
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                return true;
            }
        }
        return false;
    }

    // --- Paper ------------------------------------------------------------------------------------------------------

    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static boolean storeStateInEntity(FallingBlock fallingBlock, BlockState state) {
        if (SET_BLOCK_STATE == null) {
            return false;
        }
        try {
            SET_BLOCK_STATE.invoke(fallingBlock, state);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static BlockState stateFromEntity(FallingBlock fallingBlock) {
        if (GET_BLOCK_STATE == null) {
            return null;
        }
        try {
            return (BlockState) GET_BLOCK_STATE.invoke(fallingBlock);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static void setNoPhysics(Entity entity, boolean noPhysics) {
        if (SET_NO_PHYSICS == null) {
            return;
        }
        try {
            SET_NO_PHYSICS.invoke(entity, noPhysics);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Spigot: carried blocks keep their collisions and land when held against something
        }
    }

    // --- legacy data (servers without Paper) ------------------------------------------------------------------------

    private EntityType getSpawnerType(Entity entity) {
        String name = entity.getPersistentDataContainer().get(keySpawnerType, PersistentDataType.STRING);
        if (name == null) {
            return null;
        }
        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void applySpawnerType(Block block, EntityType type) {
        if (block.getState() instanceof CreatureSpawner spawnerState) {
            spawnerState.setSpawnedType(type);
            spawnerState.update();
        }
    }

    private void fillContainerOrDrop(Block block, ItemStack[] storedItems) {
        if (storedItems == null) {
            return;
        }

        if (block.getState() instanceof Container container) {
            Inventory inventory = container instanceof org.bukkit.block.Chest chest
                    ? chest.getBlockInventory()
                    : container.getInventory();
            ItemStack[] destination = new ItemStack[inventory.getSize()];
            int limit = Math.min(destination.length, storedItems.length);
            System.arraycopy(storedItems, 0, destination, 0, limit);

            if (storedItems.length > destination.length) {
                ItemStack[] overflow = Arrays.copyOfRange(storedItems, destination.length, storedItems.length);
                dropItems(block.getLocation().add(0.5, 0.5, 0.5), overflow);
            }

            inventory.setContents(destination);
        } else {
            dropItems(block.getLocation().add(0.5, 0.5, 0.5), storedItems);
        }
    }

    private void handleEntityRemoval(Entity entity) {
        if (entity == null) {
            return;
        }

        removeVisualDisplay(entity);
        List<FallingBlock> parts = followers.remove(entity);
        if (parts != null) {
            for (FallingBlock part : parts) {
                if (part.isValid()) {
                    letFall(part);
                }
            }
        }

        Player owner = getOwner(entity);
        if (owner != null) {
            players.remove(owner);
        }
    }

    // --- stand-ins for blocks a falling block does not show
    // ------------------------------------------------------------

    /**
     * Blocks drawn by the client's block entity renderers (chests, beds, shulker boxes, banners, signs, heads, ...)
     * are invisible as falling blocks on Java; a block display shows them while they are carried.
     */
    private static boolean needsVisualDisplay(Material material) {
        String name = material.name();
        return name.equals("CHEST")
                || name.endsWith("_CHEST")
                || Tag.BEDS.isTagged(material)
                || name.endsWith("SHULKER_BOX")
                || name.endsWith("BANNER")
                || name.endsWith("SIGN")
                || name.endsWith("_SKULL")
                || (name.endsWith("_HEAD") && material != Material.PISTON_HEAD)
                || name.endsWith("GOLEM_STATUE")
                || material == Material.DECORATED_POT
                || material == Material.CONDUIT
                || material == Material.BELL;
    }

    private void createVisualDisplayIfNeeded(BlockData data, FallingBlock fallingBlock) {
        if (!needsVisualDisplay(data.getMaterial())) {
            return;
        }

        BlockDisplay display = fallingBlock
                .getWorld()
                .spawn(fallingBlock.getLocation(), BlockDisplay.class, spawned -> {
                    spawned.setBlock(data);
                    spawned.setGravity(false);
                    spawned.setInvulnerable(true);
                    spawned.setPersistent(false);
                });
        // the display is placed from its corner, the falling block from its bottom centre
        display.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(-0.5f, 0, -0.5f),
                display.getTransformation().getLeftRotation(),
                display.getTransformation().getScale(),
                display.getTransformation().getRightRotation()));

        visualDisplays.put(fallingBlock, display);
    }

    private void updateVisualDisplay(Entity entity) {
        BlockDisplay display = visualDisplays.get(entity);
        if (display == null) {
            return;
        }

        if (entity == null || !entity.isValid() || entity.isDead()) {
            removeVisualDisplay(entity);
            return;
        }

        if (!display.isValid() || display.isDead()) {
            visualDisplays.remove(entity);
            return;
        }

        display.teleport(entity.getLocation());
    }

    private void updateRemainingVisualDisplays() {
        Iterator<Map.Entry<Entity, BlockDisplay>> iterator =
                visualDisplays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Entity, BlockDisplay> entry = iterator.next();
            Entity entity = entry.getKey();
            BlockDisplay display = entry.getValue();

            if (entity == null || !entity.isValid() || entity.isDead()) {
                if (display != null && display.isValid()) {
                    display.remove();
                }
                iterator.remove();
                continue;
            }

            if (display == null || !display.isValid() || display.isDead()) {
                iterator.remove();
                continue;
            }

            display.teleport(entity.getLocation());
        }
    }

    private void removeVisualDisplay(Entity entity) {
        if (entity == null) {
            return;
        }

        BlockDisplay display = visualDisplays.remove(entity);
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    private ItemStack[] getStoredContainerItems(Entity entity) {
        if (entity == null) {
            return null;
        }

        String serialized = entity.getPersistentDataContainer().get(keyContainerItems, PersistentDataType.STRING);
        if (serialized == null) {
            return null;
        }

        return deserializeItems(serialized);
    }

    static String serializeItems(ItemStack[] items) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("size", items.length);
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getType() != Material.AIR) {
                yaml.set("items." + i, items[i]);
            }
        }
        return yaml.saveToString();
    }

    static ItemStack[] deserializeItems(String serialized) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(serialized);
        } catch (InvalidConfigurationException exception) {
            PorticleGun.getInstance()
                    .getLogger()
                    .log(Level.SEVERE, "Could not read the contents of a carried container", exception);
            return null;
        }

        ItemStack[] items = new ItemStack[yaml.getInt("size")];
        boolean hasItems = false;
        for (int i = 0; i < items.length; i++) {
            items[i] = yaml.getItemStack("items." + i);
            hasItems |= items[i] != null;
        }
        return hasItems ? items : null;
    }

    private void dropItems(Location location, ItemStack[] items) {
        if (items == null || items.length == 0 || location == null || location.getWorld() == null) {
            return;
        }

        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            location.getWorld().dropItemNaturally(location, item);
        }
    }
}
