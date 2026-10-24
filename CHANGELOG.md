# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [semantic versioning](https://semver.org).

## [Unreleased]

## [1.6.17] - 2026-10-24

### Fixed
- **Gravity gun lost and dropped chest contents**: picking up one half of a double chest took the contents of both
  halves, and when it was put down everything that did not fit spilled out, while the chest stayed a broken half.
  Double chests (and beds, doors and tall plants) are now lifted and put down as one piece, each half with its own
  contents.
- **Carried blocks broke into items** when they came down on a slab, a torch, a carpet or inside another block, and
  when they were held against a wall or the floor. The plugin now places carried blocks itself, on the nearest free spot
  where they can stand; only without any room nearby do they drop, with everything inside.
- A door or bed picked up at one half could drop an extra item for the other half.

### Changed
- Carried blocks keep their complete state: besides container contents and spawner types also spawner settings, sign
  text, banner patterns, custom names, lecterns, bookshelves, decorated pots, jukeboxes and campfires. On Paper they
  survive a crash with everything.
- Blocks that cannot be mined in survival (bedrock, barriers, end portal frames, command blocks) can only be picked up
  in creative mode; fire, portals and moving pistons not at all. An extended piston is carried retracted.
- While held, a block stays in front of walls and floors. If the server stops while one is carried, it is put on the
  ground below.

## [1.6.16] - 2026-10-24

### Changed
- **Connection switch** in the settings menu: drawn as a vertical switch in the black of the colour sliders instead of a
  white banner with a stripe. A grey groove, the knob up and lime while the portals are connected, down and red while
  they are blocked, shaded towards the groove.

## [1.6.15] - 2026-10-24

### Added
- **Blocking portals**: the free slot at the top of the settings menu is a switch between connected and blocked. Blocked
  portals stay where they are, show a flickering force field instead of their swirl, and push players, mobs and items
  gently back.
- **New /porticlegun menu**: items are taken like from a creative inventory (click, shift-click, number key, drop key,
  as often as wanted) instead of being handed out on click; a button below each item shows its recipe on a crafting
  table with a back button, and admins can take the ingredients and the result there. Drawn in the vanilla style for
  Java players with the resource pack, glass panes for everyone else.
- **Admin menu**: every gun with open portals, by gun (with who used it last), by world or by distance; a page per gun
  travels to its portals, blocks or opens them and closes them.

### Changed
- **Portal particles**: a closed, glowing rim in the portal colour, two bright sparks with a tail running around it, a
  darker swirl turning inwards and glints flying off the surface; a portal still waiting for its partner breathes
  dimmed, and a burst rings both portals when something goes through. Bedrock players get fewer particles.

### Fixed
- The banners in the drawn settings menu could be taken out: the menu was recognised by its title, which Paper hands
  back changed once it carries the background glyphs. It is now recognised by its inventory holder, and shift-clicks,
  double-click collecting and dragging into it are blocked too.

## [1.6.14] - 2026-10-24

The tag v1.6.13 failed its build check (formatting) and was never published; 1.6.14 is the same release.

### Changed
- **Settings menu with sliders**: with the resource pack on 26.3+ the dropper drops its nine slots for plain vanilla
  grey; each colour column (up arrow, colour, down arrow) sits in one long recessed bar, so it reads as a slider, and
  the two buttons in the middle get a socket each. The flat banners are now exactly 8x16 pixels so they fit the bars.
  The title stays centred: the background no longer depends on the title's width. Older clients and Bedrock keep
  the vanilla dropper.

## [1.6.12] - 2026-10-23

### Changed
- **Settings menu back to vanilla**: the drawn background of 1.6.11 is gone (the dropper centres its title, which
  pushed the drawing to the right). With the resource pack on 26.3+ its banners now show flat and from the front,
  without pole, like a wall banner filling the slot; nothing hints at portal colours.
- **Tank menu in the vanilla style**: light grey with vanilla slots and rim instead of a dark panel, green accents.

### Fixed
- Log files of a local check had slipped into the repository with 1.6.9 to 1.6.11; they are removed and ignored.

## [1.6.11] - 2026-10-23

### Added
- **Drawn settings menu**: with the resource pack, the PorticleGun's settings menu gets its own background behind the
  unchanged grid: the primary portal and the left mouse button on the left, the secondary ones on the right, coloured
  frames for the colour columns and the gun's emblem in the top middle. Bedrock and players without the pack keep the
  current look. Images of both drawn menus are in the README and docs/resourcepack.md.

### Changed
- Tank menu: the back button sits inset in the top left corner, slot frames no longer reach into their neighbours,
  and the gauge has the same margin above and below.
- Java dialogs show no pictographs (arrows, ticks, house and skull signs) and no grey text anymore, also not in the
  rename window; the current destination is marked in bold green. Bedrock forms show no grey either.

## [1.6.10] - 2026-10-23

### Added
- **Named homes**: every home of the interdimensional gun can be renamed, in a text field on Java (dialog) and
  Bedrock (form), in chat in the chest menu. Names are stored on the gun and show in the menus, the action bar and
  the gun's tooltip.
- **Drawn tank menu**: with the resource pack, "Fill tank" is a drawn machine instead of a chest with glass panes:
  input slot, load button, the gun on a pedestal and a glass tube that fills with the tank. Only this chest changes;
  Bedrock and players without the pack keep the pane version.

### Changed
- The native windows use vanilla colours: white titles and button labels (the current destination in green), and on
  Bedrock the form's own text colour instead of light grey.

## [1.6.9] - 2026-10-23

### Fixed
- The Java resource pack of 1.6.8 was rejected by 1.21.9+ clients (its overlay declaration is not allowed there), so
  every gun showed as a purple and black cube. The pack loads again on old and new clients.
- Choosing something in the native rift menu no longer shows "waiting for response" for several seconds: the window
  stays until the next one replaces it or it closes.

### Changed
- The native rift menu is no longer one long list: the main window has five entries (worlds, journeys, rift atlas,
  homes, fill tank), each opening its own small window.

## [1.6.8] - 2026-10-23

### Added
- **Native rift menu**: the interdimensional gun's menu is a real window now instead of a chest. Java players on
  Paper 1.21.6+ get a dialog with the gun, its destination, tank and compass at the top and the destinations as
  buttons (with icons on 26.x servers); Bedrock players with Floodgate get a form with big icon buttons made for
  touch and controllers. Homes, delete confirmation and the rift atlas are windows too. Only filling the tank stays a
  small chest, because only a chest has a slot to put items in. `native-menus: false` brings back the chest menu,
  which Spigot and older servers keep using.

### Changed
- **New PorticleGun model**: the rear pod is open at the back, a dark lined opening for the hand with a glow in the
  secondary colour inside. The front now has three identical curved claws 120° apart around the barrel, one on top
  and two below, so they form a perfect triangle seen from the front. Java 1.21.11 and newer get the exact angles
  through a pack overlay, older clients the same claws at 22.5°; Bedrock always gets the exact model. All renders of
  the gun (README, website, recipes, turntable) are updated.

## [1.6.7] - 2026-10-22

### Changed
- **Homes belong to the gun**: the interdimensional gun's homes are stored on the gun item instead of per player.
  Every gun has its own homes, and they go with the gun when it changes hands. Existing guns keep working: the first
  gun a player uses (opening its menu or a rift to a home) takes over the homes that player had, so none are lost.

## [1.6.6] - 2026-10-22

### Added
- **Charging rifts**: the entrance shows up the moment the gun fires, as a small, pulsing orange rift that cannot be
  walked through yet. Once the destination is found and its chunk is loaded it grows into the green rift; if no
  destination is found it collapses. Nobody walks into a rift that does nothing anymore.
- The rift menu shows the current destination in its first slot, and the selected button glows, gets a green tick
  and says "Current destination" first.

### Changed
- Rifts open where the player looks: in front of the wall they aim at, on the spot of ground they aim at (up to six
  blocks away), or floating three blocks ahead when they look into the open. They still snap to the block grid and
  face a compass direction, so their hitbox stays two blocks wide and axis aligned.
- Rift pairs close when their opener is more than 7 blocks from both portals for three seconds (`close-distance`;
  existing configs keep their value).

## [1.6.5] - 2026-10-22

### Fixed
- Scrolling backwards through the portal colours now visits exactly the same colours as scrolling forwards, in
  reverse. After `/porticlegun reload` (or with custom colours) a gun's colour was no longer found in the colour list,
  and both arrows jumped to the first colour instead of moving one step.

## [1.6.4] - 2026-10-22

### Changed
- Settings menu arrows are sharp triangles without a protruding bar, pointing up above and down below the colour
  banners, on a plain background. When a colour changes, they slide in with their new colour, in step with the
  banner filling.
- Colour names in the settings menu are translated (`colors.*` in messages.yml); custom colours without a
  translation show their name in readable form.

## [1.6.3] - 2026-10-22

### Changed
- **PorticleGun settings menu**: the arrows show the colour they switch to, the colour banners are filled with the
  portal's colour instead of a thin frame, and a new colour fills its banner in a short animation, from below after
  the down arrow and from above after the up arrow. (The old arrow banners showed up as plain white on current
  versions.)
- Rifts draw every tick with half of their particles each, instead of all of them every other tick, so they no
  longer pulse; their spiral arms flow steadily into the core.
- Ready rift destinations no longer keep their chunks loaded: they are generated once and loaded back from disk (in
  the background on Paper) when a rift opens there.

### Fixed
- Worlds loaded after the server start also get the chunk that keeps the gun menus from freezing the server.

## [1.6.2] - 2026-10-22

### Added
- **Load slot in the rift menu**: the menu's only free slot takes portal fluid or a rift compass, the banner button
  next to it loads one per click. A single bottle turns into an empty bottle in the slot, from a stack the empty
  bottle goes to the inventory (or drops when it is full); whatever is left goes back when the menu closes.
- **Ready destinations**: every world keeps two safe landing spots searched in the background, with their chunks
  loaded, so a rift to a random spot opens at once. A search that is still needed gives up after 20 seconds instead of
  leaving the gun stuck on "Calculating coordinates".

### Changed
- **Rifts redesigned**: a crisp lime double rim, a dark green band and spiral body, and light streams spiralling
  into the core (trail particles with a fixed duration). Bedrock players get the same shape from fewer, bigger dust
  particles.
- Rifts stand on the block grid: they face a compass direction and fill exactly two blocks, centred between them.
- Rift pairs stay open until their opener is more than 24 blocks (was 12) from both portals for five seconds (was
  two).
- Collision checks use a grid of the blocks portals cover: a player's move is checked with one lookup instead of a
  loop over all portals (PorticleGun portals and rifts).
- Structure searches of the rift atlas are cached for ten minutes per area.
- Banner buttons in the rift menu (load, close, back), like in the PorticleGun settings.
- Rift shard drops follow vanilla rules: they need the right tool, respect the `doTileDrops` and `doMobLoot` game
  rules, and the Luck effect raises the chance (a quarter per level, Bad Luck lowers it).

## [1.6.1] - 2026-10-22

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
