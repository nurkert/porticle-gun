package eu.nurkert.porticlegun.handlers.gravity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.nurkert.porticlegun.PorticleGun;
import eu.nurkert.porticlegun.handlers.AudioHandler;
import eu.nurkert.porticlegun.handlers.item.ItemHandler;
import eu.nurkert.porticlegun.util.WorldGuardIntegration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class GravityGun implements Listener {

        private static GravityGun instance;

        HashMap<Player, Entity> players;
        HashMap<Entity, Location> entitys;
        HashMap<Entity, EntityType> spawner;
        BukkitTask task;

        private final Set<Material> blacklist;
        private boolean allowPlayerCapture;

        private static final String METADATA_BINDED_UUID = "binded_uuid";
        private static final String METADATA_SPAWNER_TYPE = "spawner_type";
        private static final String METADATA_CONTAINER_ITEMS = "gravity_gun_container_items";

        public GravityGun(Collection<Material> blockBlacklist, boolean allowPlayerCapture) {
                instance = this;
                entitys = new HashMap<Entity, Location>();
                players = new HashMap<Player, Entity>();
                spawner = new HashMap<Entity, EntityType>();
                blacklist = new HashSet<>();
                this.allowPlayerCapture = allowPlayerCapture;
                updateBlockBlacklist(blockBlacklist);
                init();
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
                                Iterator<Map.Entry<Entity, Location>> iterator = entitys.entrySet().iterator();
                                while (iterator.hasNext()) {
                                        Map.Entry<Entity, Location> entry = iterator.next();
                                        Entity entity = entry.getKey();

                                        if (entity == null || entity.isDead() || !entity.isValid()) {
                                                handleEntityRemoval(entity);
                                                iterator.remove();
                                                continue;
                                        }

                                        Vector plV = entry.getValue().toVector();
                                        Vector spV = entity.getLocation().toVector();
                                        Vector direction = plV.subtract(spV).normalize()
                                                        .multiply(entry.getValue().distance(entity.getLocation()) / 2);
                                        entity.setVelocity(direction);
                                }
                        }
                }.runTaskTimer(PorticleGun.getInstance(), 0, 1);
        }

        public void shutdown() {
                if (task != null) {
                        task.cancel();
                }
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

                Player owner = null;
                for (Map.Entry<Player, Entity> entry : players.entrySet()) {
                        if (entry.getValue().equals(entity)) {
                                owner = entry.getKey();
                                break;
                        }
                }

                entitys.remove(entity);
                entity.setFallDistance(0F);

                if (owner != null) {
                        players.remove(owner);
                        if (playSound) {
                                AudioHandler.playSound(owner.getLocation(), AudioHandler.PortalSound.LET_BLOCK);
                        }
                        spawner.remove(owner.getUniqueId().toString());
                }

                return true;
        }

        @EventHandler
        public void on(PlayerDropItemEvent event) {
		Player player = event.getPlayer();

		if (player.hasMetadata("portalgun-drop_bypass")) {
			player.removeMetadata("portalgun-drop_bypass", PorticleGun.getInstance());
			return;
		}

		ItemStack item = event.getItemDrop().getItemStack();
		if (item.getType() == Material.BREWING_STAND) {
			if (ItemHandler.isValidGun(item) != null) {

				if (player.hasMetadata("portalgun-drop") && player.getMetadata("portalgun-drop").size() > 0) {
					long time = Long.valueOf(player.getMetadata("portalgun-drop").get(0).asString());
					if (System.currentTimeMillis() - time < 500) {
						if (players.containsKey(player)) {
							AudioHandler.playSound(player.getLocation(), AudioHandler.PortalSound.LET_BLOCK);
							entitys.remove(players.get(player));
							players.remove(player);
						}
						return;
					}
					player.removeMetadata("portalgun-drop", PorticleGun.getInstance());

				}

				event.setCancelled(true);
				player.setMetadata("portalgun-drop",
						new FixedMetadataValue(PorticleGun.getInstance(), System.currentTimeMillis() + ""));

				if (players.containsKey(player)) {
					AudioHandler.playSound(player.getLocation(), AudioHandler.PortalSound.LET_BLOCK);
//
//					Location loc = entitys.get(players.get(player));
//					FallingBlock fallingblock = (FallingBlock) players.get(player);
//					loc.getBlock().setType(fallingblock.getMaterial());

					entitys.remove(players.get(player));
//					fallingblock.remove();
					players.get(player).setFallDistance(0F);
					players.remove(player);
				} else {
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

                                        if (PorticleGun.isWorldGuardEnabled()
                                                        && !WorldGuardIntegration.canUseGravityGun(player, block.getLocation())) {
                                                AudioHandler.playSound(event.getPlayer(), AudioHandler.PortalSound.DENY);
                                                return;
                                        }

                                        if (blacklist.contains(block.getType()) || block.isLiquid()) {
                                                AudioHandler.playSound(event.getPlayer(), AudioHandler.PortalSound.DENY);
                                                return;
                                        }

					AudioHandler.playSound(player.getLocation(), AudioHandler.PortalSound.GRAB_BLOCK);
                                        FallingBlock fallingblock = (FallingBlock) block.getLocation().getWorld()
                                                        .spawnFallingBlock(block.getLocation().add(0.5, 0, 0.5), block.getBlockData());

					storeContainerInventory(block, fallingblock);

					if (block.getType() == Material.SPAWNER) {
//						spawner.put(player.getUniqueId().toString(),
//								((CreatureSpawner) block.getState()).getSpawnedType());
						fallingblock.setMetadata(METADATA_SPAWNER_TYPE, new FixedMetadataValue(PorticleGun.getInstance(),
								((CreatureSpawner) block.getState()).getSpawnedType().toString()));
					}

					((Entity) fallingblock).setMetadata(METADATA_BINDED_UUID,
							new FixedMetadataValue(PorticleGun.getInstance(), player.getUniqueId().toString()));
					block.setType(Material.AIR);
					entitys.put((Entity) fallingblock, playersLook(player));
					players.put(player, (Entity) fallingblock);
				}

			}
		}
	}

	@EventHandler
	public void on(InventoryClickEvent event) {
		if (event.getCursor() != null && ItemHandler.isValidGun(event.getCursor()) != null) {
			if (event.getRawSlot() == -999) {
				((Player) event.getWhoClicked()).setMetadata("portalgun-drop_bypass",
						new FixedMetadataValue(PorticleGun.getInstance(), ""));
			}
		}
	}

	@EventHandler
        public void on(PlayerTeleportEvent event) {
                Player player = event.getPlayer();
                if (players.containsKey(player)) {
                        entitys.remove(players.get(player));
                        players.remove(player);
                        if (spawner.containsKey(player.getUniqueId().toString()))
                                spawner.remove(player.getUniqueId().toString());
                } else if (entitys.containsKey(player)) {
                        releaseEntitySilently(player);
                }
        }

	@EventHandler
	public void on(EntityDamageEvent event) {
		if (entitys.containsKey(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void on(EntityChangeBlockEvent event) {
		if (event.getEntity() instanceof FallingBlock) {
			if (event.getEntity().hasMetadata(METADATA_BINDED_UUID)
					&& event.getEntity().getMetadata(METADATA_BINDED_UUID).size() > 0) {

				for (Player player : players.keySet())
					if (player.getUniqueId().toString()
							.equals(event.getEntity().getMetadata(METADATA_BINDED_UUID).get(0).asString())) {
						AudioHandler.playSound(player.getLocation(), AudioHandler.PortalSound.LET_BLOCK);
						entitys.remove(event.getEntity());
						players.remove(player);
						break;
					}
			} 
			
			if (event.getEntity().hasMetadata(METADATA_SPAWNER_TYPE)
					&& event.getEntity().getMetadata(METADATA_SPAWNER_TYPE).size() > 0) {
//				System.out.println("test");
//				event.setCancelled(true);
				
//				SoundHandler.playSound(event.getBlock().getLocation(), APGSound.LET_BLOCK);
				event.setCancelled(true);
				Block block = event.getBlock();
				block.setType(Material.SPAWNER);
				EntityType type = EntityType.valueOf(event.getEntity().getMetadata(METADATA_SPAWNER_TYPE).get(0).asString());
                                CreatureSpawner spawner = ((CreatureSpawner) block.getState());
                                spawner.setSpawnedType(type);
                                spawner.update();
                        }

                        if (event.getEntity().hasMetadata(METADATA_CONTAINER_ITEMS)) {
                                restoreContainerInventory(event.getBlock(), event.getEntity());
                        }
                }
        }

	@EventHandler
	public void on(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();
		if (players.containsKey(player) && player.getInventory().getItem(event.getPreviousSlot()) != null) {
			if (ItemHandler.isValidGun(player.getInventory().getItem(event.getPreviousSlot())) != null) {
				AudioHandler.playSound(player.getLocation(), AudioHandler.PortalSound.LET_BLOCK);
				entitys.remove(players.get(player));
				players.remove(player);
			} else {
			}
		}
	}

	@EventHandler
        public void on(PlayerQuitEvent event) {
                Player player = event.getPlayer();
                if (players.containsKey(player)) {
                        entitys.remove(players.get(player));
                        players.remove(player);
                        if (spawner.containsKey(player.getUniqueId().toString()))
                                spawner.remove(player.getUniqueId().toString());
                } else if (entitys.containsKey(player)) {
                        releaseEntitySilently(player);
                }
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

        private Location playersLook(Player player) {
                return player.getEyeLocation()
                                .add(player.getEyeLocation().add(0, 0.75, 0).getDirection().normalize().multiply(3.0D))
                                .subtract(0, 0.2, 0);
        }

//	@EventHandler
//	public void on(PlayerInteractAtEntityEvent event) {
//		event.
//	}


	private void storeContainerInventory(Block block, FallingBlock fallingBlock) {
		if (!(block.getState() instanceof Container)) {
			return;
		}

		Container container = (Container) block.getState();
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

		ItemStack[] clonedContents = cloneItemStackArray(contents);
		fallingBlock.setMetadata(METADATA_CONTAINER_ITEMS,
				new FixedMetadataValue(PorticleGun.getInstance(), clonedContents));
		container.getInventory().clear();
		container.update(true);
	}

	private void restoreContainerInventory(Block block, Entity entity) {
		ItemStack[] storedItems = getStoredContainerItems(entity);
		entity.removeMetadata(METADATA_CONTAINER_ITEMS, PorticleGun.getInstance());

		if (storedItems == null) {
			return;
		}

		if (block.getState() instanceof Container) {
			Container container = (Container) block.getState();
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

		dropContainerItems(entity);

		Player owner = null;
		for (Map.Entry<Player, Entity> entry : new ArrayList<>(players.entrySet())) {
			if (entry.getValue().equals(entity)) {
				owner = entry.getKey();
				break;
			}
		}

		if (owner != null) {
			players.remove(owner);
			spawner.remove(owner.getUniqueId().toString());
		}
	}

	private void dropContainerItems(Entity entity) {
		ItemStack[] storedItems = getStoredContainerItems(entity);
		if (storedItems == null) {
			return;
		}

		dropItems(entity.getLocation(), storedItems);
		entity.removeMetadata(METADATA_CONTAINER_ITEMS, PorticleGun.getInstance());
	}

	private ItemStack[] getStoredContainerItems(Entity entity) {
		if (entity == null || !entity.hasMetadata(METADATA_CONTAINER_ITEMS)) {
			return null;
		}

		List<MetadataValue> metadataValues = entity.getMetadata(METADATA_CONTAINER_ITEMS);
		if (metadataValues.isEmpty()) {
			return null;
		}

		Object value = metadataValues.get(0).value();
		if (!(value instanceof ItemStack[])) {
			return null;
		}

		ItemStack[] contents = (ItemStack[]) value;
		ItemStack[] clone = new ItemStack[contents.length];
		boolean hasItems = false;
		for (int i = 0; i < contents.length; i++) {
			if (contents[i] != null && contents[i].getType() != Material.AIR) {
				clone[i] = contents[i].clone();
				hasItems = true;
			} else {
				clone[i] = null;
			}
		}

		return hasItems ? clone : null;
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

	private ItemStack[] cloneItemStackArray(ItemStack[] original) {
		ItemStack[] clone = new ItemStack[original.length];
		for (int i = 0; i < original.length; i++) {
			if (original[i] != null) {
				clone[i] = original[i].clone();
			} else {
				clone[i] = null;
			}
		}
		return clone;
	}

}
