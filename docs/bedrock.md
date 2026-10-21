# Bedrock players (Geyser)

PorticleGun works for Bedrock players connected through [Geyser](https://geysermc.org) and
[Floodgate](https://geysermc.org/wiki/floodgate/).

## Setup

Install Geyser-Spigot and Floodgate on the same server as PorticleGun. On startup PorticleGun

- detects Floodgate and logs `Floodgate detected - Bedrock support enabled.`
- copies `porticlegun.mcpack` into `plugins/Geyser-Spigot/packs/` and `porticlegun_mappings.json` into
  `plugins/Geyser-Spigot/custom_mappings/`. PorticleGun loads before Geyser, so this works on the very first start.

Geyser's `enable-custom-content` must be `true` (the default); PorticleGun warns if it is not. Turn the automatic
installation off with `bedrock.install-pack: false`.

### Geyser on a proxy

If Geyser runs on Velocity or BungeeCord, copy the two files from the
[release](https://github.com/nurkert/porticle-gun/releases) (`PorticleGun-bedrock-<version>.mcpack` and
`porticlegun_mappings.json`) into Geyser's `packs` and `custom_mappings` folders on the proxy and restart it.

## What Bedrock players get

- The 3D gun model in hand and a gun icon in the inventory. Bedrock has no item tints, so the claw tips are always
  orange and the windows blue instead of the gun's portal colours.
- On a controller the right trigger attacks (primary portal) and the left trigger uses (secondary portal). The portal
  status title is mirrored for controller players so its left marker belongs to the left trigger.
- Guns created before 1.3 hid their id in a lore line that Bedrock displayed as garbage; they are converted
  automatically when they show up in an inventory.

## Known limitations and open checks

These need testing with a real Bedrock client (`scripts/dev-server.sh` starts a local server with Geyser on port
19132):

- whether shooting the primary portal (left click into the air) works on touch devices
- position and size of the 3D model in first and third person (derived from the Java display values in
  `scripts/generate_resourcepack.py`)
- particle colours, block displays of carried chests and teleport smoothness

The settings menu is the Java inventory menu for everyone; a native Bedrock form is not planned without further
discussion.
