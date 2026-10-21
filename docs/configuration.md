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

- `portals.yml` stores every gun's portals, colours and shape. It is written shortly after changes and on shutdown;
  edit it only while the server is stopped.
- `messages.yml` holds all texts, see [localization.md](localization.md).
