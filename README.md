# ![Alt text](https://raw.githubusercontent.com/nurkert/PorticleGun/main/images/porticlegun_banner.png)
 A server plugin for Minecraft that adds a device to place portals.
 
![Alt text](https://raw.githubusercontent.com/nurkert/PorticleGun/main/images/basic_preview.gif)

![Alt text](https://raw.githubusercontent.com/nurkert/PorticleGun/main/images/gravity_preview.gif)

![Alt text](https://raw.githubusercontent.com/nurkert/PorticleGun/main/images/freebuild_showcase.gif)

![Alt text](https://raw.githubusercontent.com/nurkert/PorticleGun/main/images/change_settings.gif)

![Alt text](https://raw.githubusercontent.com/nurkert/PorticleGun/main/images/crafting_recipe.png)

For even more information please visit the [plugin page](https://www.spigotmc.org/resources/porticlegun-1-9.107796/).

## Configuration

The plugin ships with a `config.yml` that allows you to fine-tune portal tracing behaviour:

| Option | Default | Description |
| --- | --- | --- |
| `portal.max-target-distance` | `128` | Maximum distance (in blocks) that a block can be targeted for portal placement. |
| `portal.max-player-distance` | `100` | Maximum distance (in blocks) between the player and the projected portal location. |
| `portal.max-block-trace` | `100` | Maximum distance (in blocks) used when tracing the face of the targeted block. |

After adjusting the configuration you can reload it in-game or from the console with:

```
/porticlegun reload
```

The reload subcommand requires the `porticlegun.admin` permission (granted to server operators by default).
