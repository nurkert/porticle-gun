# WorldGuard

When [WorldGuard](https://enginehub.org/worldguard) 7 is installed, PorticleGun registers three region flags:

| Flag | Controls |
|---|---|
| `porticlegun-portal-create` | Opening portals inside the region. When denied, the gun does not fire. |
| `porticlegun-portal-use` | Travelling through portals that touch the region, in both directions. |
| `porticlegun-gravity-gun` | Picking up or moving blocks and mobs with the gravity gun. |

All flags default to `allow`, so existing regions keep working. To restrict a feature, deny the flag:

```
/rg flag spawn porticlegun-portal-create deny
```
