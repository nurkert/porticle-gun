# Bedrock players (Geyser)

PorticleGun works for Bedrock players connected through [Geyser](https://geysermc.org) and
[Floodgate](https://geysermc.org/wiki/floodgate/).

## Setup

Install Geyser-Spigot and Floodgate on the same server as PorticleGun. On startup PorticleGun

- detects Floodgate and logs `Floodgate detected - Bedrock support enabled.`
- copies `porticlegun.mcpack` into `plugins/Geyser-Spigot/packs/` and `porticlegun_mappings.json` into
  `plugins/Geyser-Spigot/custom_mappings/`. PorticleGun loads before Geyser, so this works on the very first start.

Geyser's `enable-custom-content` and `force-resource-packs` must be `true` (the defaults); PorticleGun warns on startup
if either is turned off, because Bedrock players could not see the gun then. Turn the automatic installation off with
`bedrock.install-pack: false`.

### Geyser on a proxy

If Geyser runs on Velocity or BungeeCord, PorticleGun notices that Floodgate is installed without Geyser and puts both
files into `plugins/PorticleGun/bedrock/`. Copy `porticlegun.mcpack` into Geyser's `packs` folder and
`porticlegun_mappings.json` into its `custom_mappings` folder on the proxy, then restart it. The files are also attached
to every [release](https://github.com/nurkert/porticle-gun/releases).

## What Bedrock players get

- The 3D gun model in hand and a gun icon in the inventory. Bedrock has no item tints, so the claw tips are always
  orange and the windows blue instead of the gun's portal colours.
- On a controller the right trigger attacks (primary portal) and the left trigger uses (secondary portal). The portal
  status title is mirrored for controller players so its left marker belongs to the left trigger.
- Guns created before 1.3 hid their id in a lore line that Bedrock displayed as garbage; they are converted
  automatically when they show up in an inventory.

## Interdimensional gun on Bedrock

The interdimensional gun, the rift shard and the portal fluid are in the Bedrock pack as 3D attachables with inventory
icons. The gun's menu is a normal chest menu with single-click actions, so it works with controllers and touch screens.
Rifts show as green particles for Bedrock players; the wobbling display entity is hidden from them. The tank level is
shown in the item's lore and the action bar (the dome on the Bedrock model always looks full).

## Portal status overlay on Bedrock

Java players see rings around the crosshair while holding the gun. The Bedrock pack contains the same glyphs
(`font/glyph_E9.png`), but whether Bedrock colours them and where it places them could not be checked without a
Bedrock client, so Bedrock players keep the classic title by default. To try the overlay, set `hud.bedrock-overlay:
true` in `config.yml` and run `/porticlegun reload`. If it looks wrong (white rings, boxes), set it back to `false`;
nothing else changes.

## How the Bedrock pack is kept working

The attachable, geometry and animations reproduce what [java2bedrock](https://github.com/Kas-tle/java2bedrock.sh), the
converter most Geyser servers use for 3D items, generates. `scripts/validate_bedrock_pack.py` checks every reference
Bedrock and Geyser resolve at runtime (icon, attachable, geometry, animations, bones, textures, UVs) and validates the
files against the [Bedrock JSON schemas](https://github.com/Blockception/Minecraft-bedrock-json-schemas); CI runs it on
every change. A broken reference produces no error anywhere, the item is just invisible, which is what happened in
1.3.0-beta.1.

## Known limitations and open checks

These need testing with a real Bedrock client (`scripts/dev-server.sh` starts a local server with Geyser on port
19132):

- whether shooting the primary portal (left click into the air) works on touch devices
- position and size of the 3D model in first and third person (derived from the Java display values in
  `scripts/generate_resourcepack.py`)
- particle colours, block displays of carried chests and teleport smoothness

The settings menu is the Java inventory menu for everyone; a native Bedrock form is not planned without further
discussion.
