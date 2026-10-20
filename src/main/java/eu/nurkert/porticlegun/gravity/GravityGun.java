package eu.nurkert.porticlegun.gravity;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.audio.AudioHandler;
import eu.nurkert.porticlegun.item.ItemHandler;
import eu.nurkert.porticlegun.util.WorldGuardIntegration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class GravityGun implements Listener {

    private static GravityGun instance;

    // player -> the entity they are currently holding
    private final HashMap<Player, Entity> players;
    // held entity -> the location it is pulled towards
    private final HashMap<Entity, Location> entitys;
    private final HashMap<Entity, BlockDisplay> visualDisplays;
    private BukkitTask task;

    private final Set<Material> blacklist;
    private boolean allowPlayerCapture;

    /*
     * Everything that must survive a restart or crash is kept in the falling block's persistent data container,
     * because the entity itself is saved with its chunk.
     */
    private final NamespacedKey keyCarried;
    private final NamespacedKey keySpawnerType;
    private final NamespacedKey keyContainerItems;

    private static final Set<Material> VISUAL_FALLBACK_BLOCKS =
            EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST);
    private static final int BLOCK_PLACEMENT_SEARCH_HEIGHT = 4;

    public GravityGun(Collection<Material> blockBlacklist, boolean allowPlayerCapture) {
        instance = this;
        entitys = new HashMap<>();
        players = new HashMap<>();
        visualDisplays = new HashMap<>();
        blacklist = new HashSet<>();
        keyCarried = new NamespacedKey(PorticleGun.getInstance(), "gravity_carried");
        keySpawnerType = new NamespacedKey(PorticleGun.getInstance(), "gravity_spawner_type");
        keyContainerItems = new NamespacedKey(PorticleGun.getInstance(), "gravity_container_items");
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
                    entity.setVelocity(direction);
                    updateVisualDisplay(entity);
                }

                updateRemainingVisualDisplays();
            }
        }.runTaskTimer(PorticleGun.getInstance(), 0, 1);
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
            if (entity instanceof FallingBlock && entity.isValid()) {
                placeCarriedBlock((FallingBlock) entity);
            }
        }

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

    private boolean releaseEntity(Entity entity, boolean playSound) {
        if (entity == null || !entitys.containsKey(entity)) {
            return false;
        }

        entitys.remove(entity);
        entity.setFallDistance(0F);
        if (entity instanceof FallingBlock) {
            entity.setGravity(true);
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
            player.removeMetadata("portalgun-drop_bypass", PorticleGun.getInstance());
            return;
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

        if (PorticleGun.isWorldGuardEnabled() && !WorldGuardIntegration.canUseGravityGun(player, block.getLocation())) {
            AudioHandler.playSound(player, AudioHandler.PortalSound.DENY);
            return;
        }

        if (blacklist.contains(block.getType()) || block.isLiquid()) {
            AudioHandler.playSound(player, AudioHandler.PortalSound.DENY);
            return;
        }

        AudioHandler.playSound(player.getLocation(), AudioHandler.PortalSound.GRAB_BLOCK);
        FallingBlock fallingblock =
                block.getWorld().spawnFallingBlock(block.getLocation().add(0.5, 0, 0.5), block.getBlockData());
        // without gravity the Bedrock client does not simulate the block falling and landing on its own
        fallingblock.setGravity(false);
        fallingblock.setDropItem(true);

        PersistentDataContainer data = fallingblock.getPersistentDataContainer();
        data.set(keyCarried, PersistentDataType.BYTE, (byte) 1);
        if (block.getState() instanceof CreatureSpawner spawnerState && spawnerState.getSpawnedType() != null) {
            data.set(
                    keySpawnerType,
                    PersistentDataType.STRING,
                    spawnerState.getSpawnedType().name());
        }

        createVisualDisplayIfNeeded(block, fallingblock);
        storeContainerInventory(block, fallingblock);

        block.setType(Material.AIR);
        entitys.put(fallingblock, playersLook(player));
        players.put(player, fallingblock);
    }

    @EventHandler
    public void on(InventoryClickEvent event) {
        if (event.getCursor() != null && ItemHandler.isValidGun(event.getCursor()) != null) {
            if (event.getRawSlot() == -999) {
                ((Player) event.getWhoClicked())
                        .setMetadata("portalgun-drop_bypass", new FixedMetadataValue(PorticleGun.getInstance(), ""));
            }
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

    @EventHandler
    public void on(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock) || !isCarriedBlock(fallingBlock)) {
            return;
        }

        // the block landed
        releaseEntity(fallingBlock, true);
        removeVisualDisplay(fallingBlock);

        Block block = event.getBlock();
        EntityType spawnedType = getSpawnerType(fallingBlock);
        if (spawnedType != null) {
            event.setCancelled(true);
            block.setType(Material.SPAWNER);
            applySpawnerType(block, spawnedType);
        }

        // the block is only placed after this event, so fill the container one tick later
        ItemStack[] storedItems = getStoredContainerItems(fallingBlock);
        fallingBlock.getPersistentDataContainer().remove(keyContainerItems);
        if (storedItems != null) {
            Bukkit.getScheduler().runTask(PorticleGun.getInstance(), () -> fillContainerOrDrop(block, storedItems));
        }
    }

    @EventHandler
    public void on(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock) || !isCarriedBlock(fallingBlock)) {
            return;
        }

        // the block could not be placed where it landed and breaks into an item: keep the contents with it
        releaseEntity(fallingBlock, false);
        removeVisualDisplay(fallingBlock);
        dropContainerItems(fallingBlock);
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

    private Location playersLook(Player player) {
        return player.getEyeLocation()
                .add(player.getEyeLocation()
                        .add(0, 0.75, 0)
                        .getDirection()
                        .normalize()
                        .multiply(3.0D))
                .subtract(0, 0.2, 0);
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
        if (entity instanceof FallingBlock && isCarriedBlock(entity) && !entitys.containsKey(entity)) {
            entity.setGravity(true);
        }
    }

    /**
     * Puts a carried block back into the world at (or slightly above) its current position, including spawner type
     * and container contents. Drops it as items if there is no room.
     */
    private void placeCarriedBlock(FallingBlock fallingBlock) {
        Block target = fallingBlock.getLocation().getBlock();
        for (int i = 0; i < BLOCK_PLACEMENT_SEARCH_HEIGHT && !canPlaceInto(target); i++) {
            target = target.getRelative(0, 1, 0);
        }

        if (canPlaceInto(target)) {
            target.setBlockData(fallingBlock.getBlockData());
            EntityType spawnedType = getSpawnerType(fallingBlock);
            if (spawnedType != null) {
                applySpawnerType(target, spawnedType);
            }
            restoreContainerInventory(target, fallingBlock);
        } else {
            Location location = fallingBlock.getLocation();
            dropItems(
                    location,
                    new ItemStack[] {new ItemStack(fallingBlock.getBlockData().getPlacementMaterial())});
            dropContainerItems(fallingBlock);
        }

        removeVisualDisplay(fallingBlock);
        fallingBlock.remove();
    }

    private boolean canPlaceInto(Block block) {
        return block.getType().isAir() || (Tag.REPLACEABLE.isTagged(block.getType()) && !block.isLiquid());
    }

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

    private void storeContainerInventory(Block block, FallingBlock fallingBlock) {
        if (!(block.getState() instanceof Container container)) {
            return;
        }

        ItemStack[] contents = container.getInventory().getContents();

        boolean hasItems = false;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                hasItems = true;
                break;
            }
        }

        if (!hasItems) {
            return;
        }

        fallingBlock
                .getPersistentDataContainer()
                .set(keyContainerItems, PersistentDataType.STRING, serializeItems(contents));
        container.getInventory().clear();
        container.update(true);
    }

    private void restoreContainerInventory(Block block, Entity entity) {
        ItemStack[] storedItems = getStoredContainerItems(entity);
        entity.getPersistentDataContainer().remove(keyContainerItems);
        fillContainerOrDrop(block, storedItems);
    }

    private void fillContainerOrDrop(Block block, ItemStack[] storedItems) {
        if (storedItems == null) {
            return;
        }

        if (block.getState() instanceof Container container) {
            ItemStack[] destination = new ItemStack[container.getInventory().getSize()];
            int limit = Math.min(destination.length, storedItems.length);
            System.arraycopy(storedItems, 0, destination, 0, limit);

            if (storedItems.length > destination.length) {
                ItemStack[] overflow = Arrays.copyOfRange(storedItems, destination.length, storedItems.length);
                dropItems(block.getLocation().add(0.5, 0.5, 0.5), overflow);
            }

            container.getInventory().setContents(destination);
            container.update(true);
        } else {
            dropItems(block.getLocation().add(0.5, 0.5, 0.5), storedItems);
        }
    }

    private void handleEntityRemoval(Entity entity) {
        if (entity == null) {
            return;
        }

        removeVisualDisplay(entity);

        Player owner = getOwner(entity);
        if (owner != null) {
            players.remove(owner);
        }
    }

    private void createVisualDisplayIfNeeded(Block block, FallingBlock fallingBlock) {
        if (!VISUAL_FALLBACK_BLOCKS.contains(block.getType())) {
            return;
        }

        BlockDisplay display = block.getWorld()
                .spawn(block.getLocation().add(0.5, 0, 0.5), BlockDisplay.class, spawned -> {
                    spawned.setBlock(block.getBlockData());
                    spawned.setGravity(false);
                    spawned.setInvulnerable(true);
                    spawned.setPersistent(false);
                });

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

    private void dropContainerItems(Entity entity) {
        ItemStack[] storedItems = getStoredContainerItems(entity);
        entity.getPersistentDataContainer().remove(keyContainerItems);
        dropItems(entity.getLocation(), storedItems);
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
