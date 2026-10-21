# Interdimensional gun

![The interdimensional gun](../images/animations/interdimensional_gun.gif)

The interdimensional gun opens green rifts: one directly in front of the player and one at the chosen destination.
Both wobble, can be walked through in both directions and close again on their own.

## Using it

| Action | Java | Bedrock (controller / touch) |
|---|---|---|
| Open a rift to the selected destination | Right click | Use (LT / L2, tap) |
| Open the menu | Sneak + right click | Sneak + use |

While the gun is held, the action bar shows the destination and the tank. Every menu action is a single click, so it
works the same with a mouse, a controller or a touch screen; shift and right clicks do the same as a left click.

## Destinations

- **Random spot in a world**: one candidate per tick is checked around the world spawn (`random-radius`,
  `nether-radius`, `end-radius`), chunks that already exist first, so searching never freezes the server. A spot is
  safe when the ground is solid and harmless (no lava, magma, cactus, fire, powder snow, water), there are two free
  blocks above, no lava next to it, and in the nether it is below the bedrock roof. In the end only islands count; the
  main island is the fallback. If nothing is found within `search-attempts`, the player is told and nothing is used up.
- **World spawn** of any world.
- **Homes**: `homes` slots per player. An empty slot is set to the current position with one click; a set home opens
  a page to travel there, move it to the current position or delete it (with confirmation).
- **Back**: where the player last stepped into a rift.
- **Last death**.

Worlds listed in `disabled-worlds` never show up. The other worlds, also ones added later, appear automatically; the
menu has room for eight, so on servers with more worlds list the ones nobody should travel to in `disabled-worlds`.

## Where rifts appear

- The entrance stands about two blocks in front of the player, on the ground if there is ground, closer when a wall is
  near, and floating in the air when the player stands at an edge.
- At the destination all eight compass directions are scored: room for the exit portal behind the landing spot, free
  space in front of it, and the direction the player walked in. Travellers step out facing open space.
- Stored places (homes, back, death, spawn) that have been built over move to the nearest safe spot within three
  blocks.
- A rift pair closes when its opener is farther than `close-distance` blocks from both portals for two seconds, and
  after `portal-seconds` at the latest. Opening a new pair closes the old one.

Players, mobs and items travel through rifts; WorldGuard's `porticlegun-portal-use` flag applies, and rifts cannot be
opened where `porticlegun-portal-create` is denied.

## Tank, fluid and shards

- **Rift shards** drop when mining end stone (`end-stone-chance`, plus `end-stone-fortune-bonus` per fortune level)
  and from endermen killed by a player (`enderman-chance`, plus `enderman-looting-bonus` per looting level).
- **Portal fluid**: three rift shards, a glass bottle and glowstone dust (shapeless).

  ![Portal fluid recipe](../images/crafting_portal_fluid.png)

- **Tank**: "Fill tank" in the menu takes one portal fluid from the inventory, gives the bottle back and adds
  `rifts-per-fluid` rifts, up to `tank-capacity`. Every rift uses one. The dome on the gun shows the level in five
  steps on Java.
- Rift shards and portal fluid are paper underneath (with their own model); they never work as paper in vanilla
  recipes.

## Rift compass and rift atlas

![Rift compass recipe](../images/crafting_rift_compass.png)

A rift compass (three rift shards around a compass, an eye of ender below) attunes the gun. Insert it with "Insert rift
compass" in the gun's menu: for `compass-minutes` (10) the gun is attuned, shown in its lore, the menu and the action
bar. Another compass adds the time on top. While attuned:

- rifts cost no portal fluid (`attuned-free-rifts`),
- the **rift atlas** is unlocked: a menu page with every structure kind. Selecting one makes the next rift open next
  to the nearest structure of that kind.

| Dimension | Structures |
|---|---|
| Overworld | Village, pillager outpost, desert pyramid, jungle temple, witch hut, igloo, woodland mansion, trail ruins, ruined portal, ancient city, trial chambers, stronghold |
| Nether | Nether fortress, bastion remnant |
| End | End city |

The search starts at the player's position (divided or multiplied by eight between the overworld and the nether, at
the centre of the end) and looks `structure-search-radius` chunks far, one structure variant per tick. Around the
structure, a safe landing spot is searched in a spiral of up to 32 blocks; for ancient cities, trial chambers and
strongholds caves deep inside or next to the structure come first, the surface above is the fallback. Ocean monuments
are not offered, there is no dry land around them. If the compass fades before the rift opens, the player is told.

## Crafting the gun

![Interdimensional gun recipe](../images/crafting_interdimensional_gun.png)

| | | |
|---|---|---|
| Rift shard | Nether star | Rift shard |
| Echo shard | **PorticleGun** | Echo shard |
| Iron block | Portal fluid | Iron block |

The crafted gun starts with `starting-charges` rifts. The PorticleGun used for it is gone, and so are its portals.
Crafting and using the gun needs `porticlegun.interdimensional` (everyone by default).

## Bedrock players

Bedrock players get the 3D gun, the 3D shard and bottle in hand and their icons in the inventory through the Geyser
pack. Rifts appear for them as green particles, because Geyser cannot show the wobbling display entity; it is hidden
from them so nothing looks broken.

## Data

Homes, back and death locations are stored per player in `plugins/PorticleGun/rift.yml`, written asynchronously like
`portals.yml`. Tank and selected destination are stored on the gun item itself.
