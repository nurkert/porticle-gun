# Gravity gun

The PorticleGun doubles as a gravity gun. With the gun in hand, the drop key (Q, on Bedrock the drop button) picks up
the block or mob you look at; press it again, sneak or switch items to let go. Pressing the drop key twice quickly
really drops the gun.

## What travels with a block

A block is lifted with its complete state and comes back exactly as it was:

- the contents of chests, barrels, shulker boxes, hoppers, furnaces, brewing stands, droppers, crafters, lecterns,
  chiseled bookshelves, decorated pots, jukeboxes and campfires
- spawner settings (mob, delay, range), sign text, banner patterns, heads, custom names and locks
- double chests, beds, doors and tall plants as one piece: both halves are lifted, carried side by side and put down
  together

Nothing inside is lost or doubled. The block is emptied in the world before it is removed, and every carried block is
put back exactly once.

## Putting blocks down

Vanilla falling blocks break into an item as soon as they land on a slab, a torch, a carpet or inside another block.
Carried blocks do not: the plugin places them itself, where they come down or, if that spot is taken or the block
cannot stand there (a door on a carpet), on the nearest free spot above or beside it. Only if there is no room at all
within two blocks does the block drop as an item, together with everything that was inside.

While it is held, a block stays in front of walls and floors instead of being pushed into them, and holding it against
something never makes it fall (Paper).

## Restarts and crashes

If the server stops while a block is carried, the block is put on the ground below. After a crash, carried blocks are
saved with their chunk: on Paper with their complete state, on Spigot with their contents and spawner type, and they
land as soon as the chunk loads again.

## Limits

- Blocks that cannot be mined in survival (bedrock, barriers, end portal frames, command blocks, ...) can only be picked
  up in creative mode. Fire, portals, bubble columns and moving pistons cannot be picked up at all.
- An extended piston is carried retracted; its head stays behind and disappears.
- Double chests, beds and doors do not go through portals while carried, so their halves cannot come apart.
- Blocks that need support (torches, flowers, rails, ...) are only put down where they can stand.

See [configuration.md](configuration.md#gravity-gun) for the settings and [worldguard.md](worldguard.md) for the
`porticlegun-gravity-gun` flag.
