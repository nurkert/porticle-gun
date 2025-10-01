# ![Alt text](https://raw.githubusercontent.com/nurkert/PorticleGun/main/images/porticlegun_banner.png)
 A server plugin for Minecraft that adds a device to place portals.
 
![Alt text](https://raw.githubusercontent.com/nurkert/PorticleGun/main/images/basic_preview.gif)

![Alt text](https://raw.githubusercontent.com/nurkert/PorticleGun/main/images/gravity_preview.gif)

![Alt text](https://raw.githubusercontent.com/nurkert/PorticleGun/main/images/freebuild_showcase.gif)

![Alt text](https://raw.githubusercontent.com/nurkert/PorticleGun/main/images/change_settings.gif)

![Alt text](https://raw.githubusercontent.com/nurkert/PorticleGun/main/images/crafting_recipe.png)

For even more information please visit the [plugin page](https://www.spigotmc.org/resources/porticlegun-1-9.107796/).

## Recent Changes

* **Portal physics** – Entities now keep their full momentum and facing when travelling between linked portals. Movement vectors are rotated into the destination portal's frame so that Portal-style conservation of velocity and view direction is preserved after teleportation.

## Configuration

The plugin ships with a `config.yml` that can be used to tweak runtime behaviour without touching the code. After editing the file you can apply your changes with `/porticlegun reload`.

### Gravity Gun

* `gravity-gun.enabled` – Set to `false` to completely disable the gravity gun event listeners and background tasks.
* `gravity-gun.block-blacklist` – A list of Bukkit material names that players are prevented from picking up with the gravity gun.
