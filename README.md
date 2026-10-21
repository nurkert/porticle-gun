<p align="center">
  <img src="images/porticlegun_banner.png" alt="PorticleGun" width="820">
</p>

<p align="center">
  <b>Two portals. One gun. Every wall is a door.</b><br>
  A portal gun and a gravity gun for Minecraft servers, with its own 3D model for Java and Bedrock players.
</p>

<p align="center">
  <a href="https://github.com/nurkert/porticle-gun/releases/latest"><img src="https://img.shields.io/github/v/release/nurkert/porticle-gun?label=release&color=FF9A1F" alt="Latest release"></a>
  <a href="https://github.com/nurkert/porticle-gun/actions/workflows/ci.yml"><img src="https://github.com/nurkert/porticle-gun/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <img src="https://img.shields.io/badge/minecraft-1.21.4%2B-36C2FF" alt="Minecraft 1.21.4+">
  <img src="https://img.shields.io/badge/java%20%26%20bedrock-supported-36C2FF" alt="Java and Bedrock">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-555555" alt="License GPL-3.0"></a>
  <a href="https://www.spigotmc.org/resources/porticlegun-1-9.107796/"><img src="https://img.shields.io/badge/spigot-resource-FF9A1F" alt="Spigot"></a>
</p>

<p align="center">
  <a href="#quick-start">Quick start</a> ·
  <a href="#controls">Controls</a> ·
  <a href="#commands-and-permissions">Commands</a> ·
  <a href="#documentation">Documentation</a> ·
  <a href="CHANGELOG.md">Changelog</a>
</p>

<p align="center">
  <img src="images/portal_gun_3d.png" alt="The PorticleGun 3D model" width="460">
</p>

<p align="center"><img src="images/divider.png" alt="" width="100%"></p>

## Highlights

<table>
  <tr>
    <td width="33%" valign="top">
      <img src="images/basic_preview.gif" alt="Placing portals" width="100%"><br>
      <b>Real portal physics</b><br>
      Walk, fall or fly through linked portals on walls, floors and ceilings. Speed and view direction carry over, so
      you can fling yourself across the map. Mobs, items and arrows travel too.
    </td>
    <td width="33%" valign="top">
      <img src="images/gravity_preview.gif" alt="Gravity gun" width="100%"><br>
      <b>Gravity gun included</b><br>
      Pick up blocks and mobs and carry them around. Chests keep their contents, spawners their mob, even across
      restarts.
    </td>
    <td width="33%" valign="top">
      <img src="images/in_action_preview.gif" alt="Portals in action" width="100%"><br>
      <b>Java and Bedrock</b><br>
      Bedrock players join through Geyser and Floodgate and get the same gun, the same 3D model and controls that fit
      their device.
    </td>
  </tr>
</table>

<p align="center"><img src="images/divider.png" alt="" width="100%"></p>

## A gun in your colours

<p align="center">
  <img src="images/portal_gun_colors.png" alt="The gun in four portal colour combinations" width="100%">
</p>

PorticleGun ships its own 3D model, built block by block. Its lights follow the gun's portals: the claw tips glow in
the primary colour, windows and emitter in the secondary colour, and they change the moment you pick new colours.
The resource pack is sent automatically, adds files only in its own namespace and works next to any other pack.
Players without it simply see a brewing stand. More in [docs/resourcepack.md](docs/resourcepack.md).

<p align="center"><img src="images/divider.png" alt="" width="100%"></p>

## Features

| | |
|---|---|
| **Portals** | Two linked portals per gun, oval or rectangular, in any of the configured colours |
| **Physics** | Momentum and view direction are rotated into the exit portal, for players and other entities |
| **Gravity gun** | Carry blocks and mobs; contents and spawner types survive restarts and crashes |
| **3D model** | Own Java and Bedrock model, tinted in each gun's colours, installed into Geyser automatically |
| **Bedrock** | Floodgate integration, portal status mirrored for controllers, Bedrock pack validated on every change |
| **Protection** | WorldGuard flags for creating portals, using portals and the gravity gun |
| **Languages** | English and German, chosen per player from their game language, fully editable |
| **Server friendly** | Asynchronous saving, worlds loaded late (Multiverse) supported, `/porticlegun reload` for config changes |

<p align="center"><img src="images/divider.png" alt="" width="100%"></p>

## Quick start

1. Download `PorticleGun-<version>.jar` from the [latest release](https://github.com/nurkert/porticle-gun/releases/latest).
2. Put it into your server's `plugins` folder and restart (Spigot or Paper **1.21.4+**, Java 21).
3. Run `/porticlegun` and take your gun, or craft one.

Java players get the 3D model on join. With Geyser and Floodgate on the same server, the Bedrock pack is installed into
Geyser on startup; for Geyser on a proxy see [docs/bedrock.md](docs/bedrock.md). Optional integrations: WorldGuard 7,
Multiverse-Core.

### Crafting

<p align="center">
  <img src="images/crafting_recipe.png" alt="Crafting recipe" width="760">
</p>

<p align="center">
  Obsidian · Beacon · Obsidian<br>
  Quartz block · Ender pearl · Quartz block<br>
  Quartz block · Crossbow · Quartz block
</p>

Every gun has its own pair of portals. Placing a new primary or secondary portal moves the gun's previous one.

<p align="center"><img src="images/divider.png" alt="" width="100%"></p>

## Controls

| Action | Java (mouse) | Bedrock controller |
|---|---|---|
| Primary portal | Left click | Right trigger (RT / R2) |
| Secondary portal | Right click | Left trigger (LT / L2) |
| Settings: colours, shape, close portals | Sneak + right click | Sneak + left trigger |
| Gravity gun: pick up / let go | Drop key (Q) | Drop |
| Drop the gun itself | Drop key twice quickly | Drop twice quickly |
| Break free when carried by another player | Sneak | Sneak |

The portal status shown while holding the gun marks which portals are open, in their colours. For Bedrock controller
players it is mirrored, so the left marker belongs to the left trigger.

<p align="center">
  <img src="images/change_settings.gif" alt="Changing the gun's settings" width="640">
</p>

<p align="center"><img src="images/divider.png" alt="" width="100%"></p>

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

<p align="center"><img src="images/divider.png" alt="" width="100%"></p>

## Documentation

| Guide | Covers |
|---|---|
| [Configuration](docs/configuration.md) | Every option in `config.yml`, custom portal colours |
| [Resource pack and 3D model](docs/resourcepack.md) | How the model is delivered, self-hosting, changing the model |
| [Bedrock / Geyser](docs/bedrock.md) | Setup, proxies, what Bedrock players get |
| [WorldGuard](docs/worldguard.md) | Region flags |
| [Localization](docs/localization.md) | Editing texts, adding languages |
| [Development](docs/development.md) | Building, tests, smoke tests, releasing |

<p align="center">
  <img src="images/freebuild_showcase.gif" alt="Freebuild showcase" width="640">
</p>

<p align="center"><img src="images/divider.png" alt="" width="100%"></p>

## Contributing

Bug reports and pull requests are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md). Every change is built, tested and
smoke-tested on a real Paper server with and without Geyser.

<p align="center">
  <img src="images/porticlegun_logo.png" alt="" width="72"><br>
  PorticleGun is licensed under the <a href="LICENSE">GNU General Public License v3.0</a>.
</p>
