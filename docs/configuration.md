# Configuration

`plugins/PorticleGun/config.yml` is created on the first start. Apply changes with `/porticlegun reload` (resource
pack and Bedrock options take effect on the next restart or join).

## Portals

| Key | Default | Description |
|---|---|---|
| `portal.max-target-distance` | `128` | How far (blocks) the gun looks for a block to place a portal on. Also the range of `/porticlegun removeportal`. |
| `portal.max-player-distance` | `100` | Portals are not placed further away than this from the shooter. |
| `portal.max-block-trace` | `100` | How many blocks are traced to find out which side of a block was hit. |
| `portal.custom-colors` | built-in palette | The portal colours players can cycle through, see below. |

### Portal colours

Each entry under `portal.custom-colors` defines one colour:

```yaml
portal:
  custom-colors:
    - name: cosmic_blue          # unique name, stored with the gun
      particle-color: "#3366FF"  # required: hex (#RRGGBB) or a DyeColor name
      chat-color: "#3366FF"      # optional, defaults to the particle colour
      banner-dye: BLUE           # optional DyeColor for the menu preview, defaults to the nearest one
```

The shipped list contains the default palette; edit it to change the defaults or append entries. If a gun was saved
with a colour that no longer exists, it falls back to the default colour and a warning is logged.

## Interdimensional gun

| Key | Default | Description |
|---|---|---|
| `interdimensional-gun.enabled` | `true` | `false` removes the gun, its items and recipes (restart required). |
| `interdimensional-gun.tank-capacity` | `8` | Rifts a full tank holds. |
| `interdimensional-gun.rifts-per-fluid` | `4` | Rifts one bottle of portal fluid adds. |
| `interdimensional-gun.starting-charges` | `4` | Rifts a freshly crafted gun has. |
| `interdimensional-gun.cooldown-seconds` | `3` | Seconds between two rifts. |
| `interdimensional-gun.close-distance` | `7` | A rift pair closes when its opener is farther than this from both portals for three seconds. |
| `interdimensional-gun.portal-seconds` | `30` | A rift pair closes after this many seconds at the latest. |
| `interdimensional-gun.homes` | `5` | Home slots per gun (0-7). |
| `interdimensional-gun.native-menus` | `true` | The gun's menu as a native window: a dialog on Java (Paper 1.21.6+), a form on Bedrock (Floodgate); only filling the tank is a chest. `false` keeps the chest menu for everything (always the case on Spigot and older servers). |
| `interdimensional-gun.random-radius` | `3000` | Radius around the world spawn for random destinations (overworld and other worlds). |
| `interdimensional-gun.nether-radius` | `1000` | The same for the nether. |
| `interdimensional-gun.end-radius` | `1500` | The same for the end (around 0, 0); the main island is the fallback. |
| `interdimensional-gun.search-attempts` | `40` | Candidate spots checked, one per tick, before giving up. |
| `interdimensional-gun.disabled-worlds` | `[]` | Worlds that never show up as destinations. |
| `interdimensional-gun.compass-minutes` | `10` | Minutes a rift compass attunes the gun. |
| `interdimensional-gun.attuned-free-rifts` | `true` | Rifts cost no portal fluid while the gun is attuned. |
| `interdimensional-gun.structure-search-radius` | `100` | How far (chunks) the rift atlas looks for structures. |
| `interdimensional-gun.drops.end-stone-chance` | `0.03` | Chance for a rift shard per mined end stone. |
| `interdimensional-gun.drops.end-stone-fortune-bonus` | `0.01` | Added per fortune level. |
| `interdimensional-gun.drops.enderman-chance` | `0.08` | Chance for a rift shard per enderman killed by a player. |
| `interdimensional-gun.drops.enderman-looting-bonus` | `0.03` | Added per looting level. |

See [interdimensional-gun.md](interdimensional-gun.md).

## Gravity gun

| Key | Default | Description |
|---|---|---|
| `gravity-gun.enabled` | `true` | `false` disables the gravity gun completely. |
| `gravity-gun.allow-player-capture` | `false` | Whether players can pick up other players. |
| `gravity-gun.allow-chest-capture` | `true` | Whether chests can be picked up. Their contents travel with them and survive restarts. |
| `gravity-gun.block-blacklist` | `[AIR]` | Blocks that cannot be picked up (Bukkit material names). |

## Portal status overlay

| Key | Default | Description |
|---|---|---|
| `hud.overlay` | `true` | Two rings around the crosshair in the portal colours for Java players who loaded the resource pack. `false` (or no pack) shows the classic title. |
| `hud.bedrock-overlay` | `false` | The same rings for Bedrock players, see [bedrock.md](bedrock.md). |

## Resource pack (Java)

| Key | Default | Description |
|---|---|---|
| `resource-pack.enabled` | `true` | Send the 3D gun model to Java players. |
| `resource-pack.required` | `false` | Players must accept the pack to join. |
| `resource-pack.url` | `""` | Empty: download from the GitHub release of this version. Set a URL to host the pack yourself. |
| `resource-pack.sha1` | `""` | SHA-1 of the pack at `url`. Only used together with `url`. |
| `resource-pack.prompt` | `""` | Optional text in the download prompt. |

Details in [resourcepack.md](resourcepack.md).

## Bedrock

| Key | Default | Description |
|---|---|---|
| `bedrock.install-pack` | `true` | Copy the Bedrock pack and Geyser item mapping into `plugins/Geyser-Spigot` on startup. |

Details in [bedrock.md](bedrock.md).

## Data files

- `rift.yml` stores the back and death locations of the interdimensional gun per player. Homes are stored on each gun
  item; homes from older versions stay here until the player's first gun takes them over.
- `portals.yml` stores every gun's portals, colours and shape. It is written shortly after changes and on shutdown;
  edit it only while the server is stopped.
- `messages.yml` holds all texts, see [localization.md](localization.md).
