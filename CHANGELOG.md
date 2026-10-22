# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [semantic versioning](https://semver.org).

## [Unreleased]

### Changed
- **Interdimensional gun controls**: left click (attack) opens a rift, right click (use) opens the menu, like the
  PorticleGun. Attacking without a destination opens the menu, and an empty tank refills itself from portal fluid in
  the inventory. Attacking with the gun never breaks blocks.
- **Rift menu**: rebuilt with a clear header (rift compass, the gun with its tank, portal fluid) and one row per kind
  of destination. Choosing a destination closes the menu and confirms it in the action bar; homes show their number,
  frames have no tooltips, and the rift atlas is sorted by dimension. A rift compass in the inventory unlocks the atlas
  with one click.
- **Rifts look like the real thing**: a thick, wobbling lime rim with white bubbles, a dark green body and light
  spiral arms turning into a bright core.
- Floor and ceiling portals stacked above each other make an endless fall: players keep their speed, are teleported
  even when they land on a floor portal, and never take fall damage from it.

### Fixed
- Servers updated from an older version showed "Missing message" instead of texts (item names, the rift action bar,
  menus), because new texts were missing from the existing `messages.yml`. Missing texts now come from the plugin
  itself, and items that were created with a missing name are repaired.
- Searching a random rift destination generated chunks on the main thread and froze the server for seconds. On Paper
  chunks are now loaded and generated in the background, and the chunks of an open rift stay loaded until it closes.
- Dropping a gun out of the open inventory (dragged out, or the drop key over its slot) now really drops it instead of
  starting the gravity gun, on Java and Bedrock.

## [1.6.0] - 2026-10-21

### Added
- **Rift compass**: attunes the interdimensional gun for ten minutes. Rifts cost no portal fluid and the **rift
  atlas** unlocks: rifts to the nearest village, outpost, temple, witch hut, igloo, mansion, trail ruins, ruined
  portal, ancient city, trial chambers, stronghold, nether fortress, bastion or end city, with safe landing next to
  it (inside caves for underground structures). Recipe: three rift shards, a compass and an eye of ender.
- `/porticlegun` (alias `/pg`) opens a chest with every PorticleGun item; one click puts it into the inventory.
  Admins also get the interdimensional gun, rift shards, portal fluid and the rift compass there.
- Bedrock players find all PorticleGun items in their creative inventory; taken from there they work right away.
- A website on GitHub Pages (English and German) with the latest download, guides and recipes.
- Graphical crafting recipes for portal fluid, the interdimensional gun and the rift compass in the README and docs.
- Animated README images: turning items at a fixed scale, the filling tank and the inventory icons, all
  with a transparent background (`scripts/render_gifs.py`).

### Changed
- Both guns are shown side by side at the top of the README.
- README renders use a depth buffer, so overlapping parts are drawn correctly.
- Rifts are made of particles only: a wobbling oval rim with white speckles and three turning spiral arms in light and
  dark green, growing open and shrinking shut. Java and Bedrock players see the same, without a resource pack.
- Portal and rift particles are only sent while a player is near enough to see them.
- The gameplay GIFs in the README are cut to the scene, their texts moved into the README.

### Fixed
- The back crystal of the rift shard no longer pokes into the main crystal.
- Opening a gun menu could freeze newer servers (26.x) for many seconds while they generated chunk 0 0.

### Removed
- The "rename plugin jar" button in the menu.

## [1.5.0] - 2026-10-21

### Added
- **Interdimensional gun**: opens wobbling green rift pairs (in front of the player and at the destination), walkable
  both ways, to a random safe spot or the spawn of any world, homes, back and the last death. Chest menu with
  single-click actions that work on Bedrock too.
- Smart rift placement: the entrance stands on the ground in front of the player (closer to walls, floating at edges),
  the exit faces open space, built-over stored places move to the nearest safe spot. Rifts close when the opener walks
  away or after `portal-seconds`.
- Safe random destinations: searched one candidate per tick, generated chunks first, no lava, hazards, water, nether
  roof or void; the end's main island as fallback.
- **Rift shards** (end stone and enderman drops) and **portal fluid** (fuel, shown as the tank level in the gun's
  dome). Recipes for portal fluid and for the gun as an upgrade of the PorticleGun.
- 3D models for the interdimensional gun, rift shards and portal fluid (Java and Bedrock); animated vanilla-style
  inventory icons on Java.
- `/porticlegun give <item> [player] [amount]`, `porticlegun.interdimensional` permission, `interdimensional-gun`
  config section, `rift.yml`.

## [1.4.1] - 2026-10-21

### Changed
- Overlay rings are now clearly different: an outlined ring while a portal is closed, a filled ring while it is
  open (drawn at double resolution).

## [1.4.0] - 2026-10-21

### Added
- Portal status overlay: while holding the gun, two rings around the crosshair show which portals are open, in the
  portal colours. Java players who loaded the resource pack get it automatically; everyone else keeps the classic
  title. Bedrock players can be switched over with `hud.bedrock-overlay` once checked on a Bedrock client.
- README redesign with colour-variant renders of the gun.

### Changed
- The resource pack validator also checks the overlay glyphs in the Java font, the Bedrock glyph sheet and the plugin.

## [1.3.2] - 2026-10-21

### Fixed
- The 3D gun pointed at the player in first and third person (Java and Bedrock); it now points forward.

### Changed
- The crafting recipe image shows the 3D portal gun as the result.

## [1.3.1] - 2026-10-21

### Fixed
- Existing guns also get the 3D model and their colours when they are picked up or used, not only on join, slot
  changes and when inventories are opened or closed.

## [1.3.0] - 2026-10-21

### Added
- New voxel portal gun model (rear pod, glowing barrel windows, curved claws).
- Bedrock pack validation (references and JSON schemas) in CI; warnings when Geyser's settings would hide the gun;
  the Bedrock files are exported for copying when Geyser runs on a proxy.
- 3D portal gun model for Java and Bedrock players; its lights glow in the gun's portal colours (Java). The Java pack
  is sent in addition to other packs, the Bedrock pack is installed into Geyser automatically.
- Bedrock support through Floodgate: the portal status title is mirrored for controller players, whose left trigger
  fires the secondary portal.
- Portals in worlds that load after the plugin (e.g. Multiverse) are activated once their world loads.
- `resource-pack.*` and `bedrock.install-pack` options; the portal distance options are now listed in `config.yml`.
- GitHub Actions: build, tests, formatting check and smoke tests on Paper (with and without Geyser) for every change;
  releases with jar and resource packs for every `v*` tag. Dependabot.
- Documentation in `docs/`, GPL-3.0 license.

### Changed
- Requires Minecraft 1.21.4 or newer and Java 21; the plugin disables itself with a clear message on older servers.
- `portals.yml` is written asynchronously shortly after changes instead of synchronously on every change.
- The gun id is stored in the item's persistent data instead of hidden lore characters. Existing guns keep working
  and are converted automatically.
- Code restructured into feature packages, `/porticlegun` split into sub commands, formatting enforced with Spotless.

### Fixed
- Bedrock: the gun was invisible because Geyser looked for its icon under a different name; the attachable now uses
  the bone layout of the common Java-to-Bedrock converter (reported in 1.3.0-beta.1).
- The Geyser config check never fired on Geyser 2.9+, whose settings moved into sections.
- Gravity gun: chest contents were lost when the server stopped or crashed while a chest was carried.
- Gravity gun: chest contents were dropped on the ground instead of put back into the chest when it landed.
- Gravity gun: carried blocks disappeared after 30 seconds. Carried blocks no longer have gravity, so Bedrock clients
  should no longer drop them locally (reported by players, still to be confirmed on Bedrock).
- Gravity gun: carried blocks are put back into the world when the plugin is disabled.
- Closing both portals in the settings menu was not saved, so they came back after a reload or restart.
- One stored gun with an unknown colour stopped all following guns from loading.
- A secondary portal without a primary portal was not loaded.

### Removed
- Developer-only debug listener and an empty, unused class.

## [1.2.2] and earlier

See the [Spigot page](https://www.spigotmc.org/resources/porticlegun-1-9.107796/updates).
