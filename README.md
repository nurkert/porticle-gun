![PorticleGun](images/porticlegun_banner.png)

[![CI](https://github.com/nurkert/porticle-gun/actions/workflows/ci.yml/badge.svg)](https://github.com/nurkert/porticle-gun/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/nurkert/porticle-gun?include_prereleases)](https://github.com/nurkert/porticle-gun/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
[![Spigot](https://img.shields.io/badge/spigot-PorticleGun-orange)](https://www.spigotmc.org/resources/porticlegun-1-9.107796/)

A Minecraft server plugin that adds a **portal gun**: shoot two linked portals onto any wall, floor or ceiling and walk,
fall or fly through them. Momentum and view direction carry over, so you can fling yourself across the map. A
**gravity gun** to pick up and throw blocks and mobs is included.

<p align="center">
  <img src="images/portal_gun_3d.png" alt="The PorticleGun 3D model" width="420">
</p>

<p align="center"><b>Its own 3D portal gun model</b>, built block by block, for Java <i>and</i> Bedrock players.<br>
The claw tips glow in the gun's primary portal colour, windows and emitter in the secondary colour.</p>

![Portals in action](images/basic_preview.gif)

## Features

- Two linked portals per gun, each in its own colour, oval or rectangular
- Players, mobs, items and projectiles travel through portals and keep their speed and direction
- Gravity gun: carry blocks (chests keep their contents, spawners their mob) and mobs
- Custom 3D gun model for Java **and** Bedrock players, tinted in each gun's portal colours; sent automatically and
  works next to other resource packs ([details](docs/resourcepack.md))
- Bedrock players via [Geyser](https://geysermc.org) and Floodgate
- WorldGuard flags to control where portals and the gravity gun may be used
- English and German, picked per player from their game language
- Custom portal colours in the config

![Gravity gun](images/gravity_preview.gif)

## Requirements

| | |
|---|---|
| Server | Spigot or Paper **1.21.4** or newer |
| Java | 21 |
| Clients | 1.21.4 or newer for the 3D model, older clients (ViaVersion) see a brewing stand |
| Optional | WorldGuard 7, Geyser + Floodgate, Multiverse-Core |

## Installation

1. Download `PorticleGun-<version>.jar` from the [releases](https://github.com/nurkert/porticle-gun/releases).
2. Put it into your server's `plugins` folder and restart.
3. Optional: adjust `plugins/PorticleGun/config.yml` and `messages.yml`, then run `/porticlegun reload`.

The 3D model is sent to Java players automatically. With Geyser on the same server, the Bedrock pack is installed into
Geyser on startup. See [docs/resourcepack.md](docs/resourcepack.md) and [docs/bedrock.md](docs/bedrock.md).

## Getting a gun

- `/porticlegun` opens a menu with a new gun, or
- craft it:

![Crafting recipe](images/crafting_recipe.png)

| | | |
|---|---|---|
| Obsidian | Beacon | Obsidian |
| Quartz block | Ender pearl | Quartz block |
| Quartz block | Crossbow | Quartz block |

Every gun has its own pair of portals. Placing a new primary or secondary portal closes the gun's previous one.

## Controls

| Action | Java (mouse) | Bedrock controller |
|---|---|---|
| Primary portal | Left click | Right trigger (RT / R2) |
| Secondary portal | Right click | Left trigger (LT / L2) |
| Settings (colours, shape, close portals) | Sneak + right click | Sneak + left trigger |
| Gravity gun: pick up / let go | Drop key (Q) | Drop |
| Drop the gun itself | Drop key twice quickly | Drop twice quickly |
| Break free when carried by another player | Sneak | Sneak |

The title shown while holding the gun marks which portals are open. On a Bedrock controller it is mirrored so the left
marker belongs to the left trigger. Touch controls on Bedrock still need testing, see [docs/bedrock.md](docs/bedrock.md).

![Changing settings](images/change_settings.gif)

## Commands and permissions

| Command | Description | Permission |
|---|---|---|
| `/porticlegun` | Opens the menu with a new gun | `porticlegun.command` |
| `/porticlegun removeportal [linked]` | Closes the portal you look at (and its partner) | `porticlegun.command` |
| `/porticlegun list` | Lists all stored guns and their portals | `porticlegun.admin` |
| `/porticlegun remove <id>` | Closes a gun's portals and deletes its data | `porticlegun.admin` |
| `/porticlegun clearplayer <player>` | Does the same for every gun an online player carries | `porticlegun.admin` |
| `/porticlegun reload` | Reloads config, messages and portals | `porticlegun.admin` |

`porticlegun.command` is granted to everyone by default, `porticlegun.admin` to operators. The console can run all
admin commands.

## Documentation

- [Configuration](docs/configuration.md): every option in `config.yml`
- [WorldGuard](docs/worldguard.md): region flags
- [Localization](docs/localization.md): editing texts and languages
- [Bedrock / Geyser](docs/bedrock.md)
- [Resource pack and 3D model](docs/resourcepack.md)
- [Development](docs/development.md): building, testing and releasing
- [Changelog](CHANGELOG.md)

![Freebuild showcase](images/freebuild_showcase.gif)

## Contributing

Bug reports and pull requests are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

PorticleGun is licensed under the [GNU General Public License v3.0](LICENSE).
