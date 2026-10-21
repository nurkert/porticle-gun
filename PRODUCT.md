# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Static HTML/CSS without a build step in `site/`, published to GitHub Pages by a GitHub Actions workflow that copies
`site/` and `images/` together.

## Users

Server admins and players, served equally. Admins decide whether to install the plugin: they need to see what it does,
that Java and Bedrock both work, how to set it up, and get the download. Players on a server with the plugin want to
understand the guns: what they do, how to use them, how to craft them.

## Product Purpose

PorticleGun is a Spigot/Paper plugin (Minecraft 1.21.4+, Java 21) that adds a portal gun, an interdimensional gun and
a gravity gun, each with its own 3D model for Java and Bedrock players. The website lets visitors understand at a
glance what it is, how it is used and what it brings, and download the latest release directly.

## Positioning

Real portal physics (momentum and view direction carry over) plus green rifts to homes, worlds, spawns and structures,
with hand-built 3D models that work identically for Java and Bedrock (through Geyser and Floodgate), and controls that
need only single clicks or taps.

## Operating Context

Admins drop the jar into `plugins`, restart, and players run `/porticlegun` or craft a gun. Resource packs are sent and
installed automatically. Releases live on GitHub (`nurkert/porticle-gun`), with the jar, both resource packs and the
Geyser mapping as assets; a Spigot resource page also exists.

## Capabilities and Constraints

- PorticleGun: two linked portals per gun, configurable colours and shapes, settings menu, portal status rings.
- Gravity gun: carry blocks and mobs, chests keep contents, spawners their mob.
- Interdimensional gun: rift pairs to any world, spawns, homes, back, last death; fuelled by portal fluid from rift
  shards; the rift compass attunes it for free rifts and the rift atlas (15 structure types).
- WorldGuard flags, English and German texts, `/porticlegun` commands and permissions.
- The plugin's in-game settings menu (the PorticleGun dropper menu) is frozen and must not be redesigned.
- Licence: GPL-3.0. Not affiliated with Mojang or Microsoft.

## Brand Commitments

- Name: PorticleGun. Tagline: "Two portals. One gun. Every wall is a door."
- Logo and banner: `images/porticlegun_logo.png`, `images/porticlegun_banner.png` (black stencil-like wordmark, orange and
  blue portal bars).
- Portal colours orange and blue; the interdimensional gun's identity is rift green. The rift must never contain black.
- Items look vanilla-style so they fit other texture packs.
- Website copy in English.

## Evidence on Hand

- Turntable GIFs with transparent background: `images/animations/` (portal gun, interdimensional gun, rift shard,
  portal fluid, rift compass, rift, tank level, inventory icons).
- Gameplay GIFs: `images/basic_preview.gif`, `gravity_preview.gif`, `gravity_preview_mobs.gif`,
  `in_action_preview.gif`, `freebuild_showcase.gif`, `change_settings.gif`.
- Stills: `portal_gun_colors.png`, `portal_hud.png`, crafting recipes `crafting_*.png`, `portal_gun_3d.png`.
- No testimonials, download counts or server lists exist; do not invent them.

## Product Principles

1. Show, don't tell: every claim sits next to a GIF or image of the real thing.
2. One click to the download, always the latest release.
3. Java and Bedrock are equal citizens.
4. Honest and concrete: real commands, real recipes, no invented numbers.
