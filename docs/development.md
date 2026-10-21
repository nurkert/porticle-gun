# Development

## Build

Requires JDK 21 and Maven.

```
mvn verify
```

compiles, runs the unit tests, checks formatting and builds into `target/`:

- `PorticleGun-<version>.jar` (the plugin)
- `PorticleGun-resourcepack-<version>.zip` (Java pack)
- `PorticleGun-bedrock-<version>.mcpack` (Bedrock pack)

Formatting is enforced with Spotless (palantir-java-format). Fix it with `mvn spotless:apply`.

## Tests

- Unit tests: `src/test/java`, run by `mvn verify`
- Smoke test: starts a real Paper server with the plugin, runs `/porticlegun list` and `reload` from the console and
  fails on errors:

  ```
  scripts/smoke-test.sh target/PorticleGun-<version>.jar 1.21.4          # plain Paper
  scripts/smoke-test.sh target/PorticleGun-<version>.jar 1.21.4 geyser   # with Geyser and Floodgate
  ```

- Resource packs: `python3 scripts/validate_bedrock_pack.py --schemas <checkout>` checks the Bedrock pack (see
  [bedrock.md](bedrock.md)); CI also checks that the generated JSON files are up to date.
- Recipe images: `python3 scripts/render_recipes.py <minecraft client jar>` (needs Pillow) re-renders the crafting
  recipe images in `images/` from the PorticleGun recipe screenshot, vanilla textures and the generated models.
- Manual testing: `scripts/dev-server.sh` builds the plugin and starts Paper with Geyser and Floodgate in `run/dev`.
  Java clients connect to `localhost:25565`, Bedrock clients to `localhost:19132`.

CI (`.github/workflows/ci.yml`) runs the build and both smoke tests for every push to `main` and every pull request.

## Project layout

| Package | Content |
|---|---|
| `eu.nurkert.porticlegun` | Plugin entry point and `LoadingHandler` |
| `commands` | `/porticlegun`, its sub commands and the gun menu |
| `portals` | Portal model, placement, teleportation, settings menu |
| `gravity` | Gravity gun |
| `item` | Gun items, recipe, item migration and 3D model appearance |
| `visualization` | Portal colours, particles, animations, titles |
| `persistence` | `portals.yml` |
| `resourcepack` | Sending the Java pack, installing the Bedrock pack |
| `config`, `messages`, `audio`, `builders`, `util` | Configuration, texts, sounds, item builders, WorldGuard and Floodgate |

## Releasing

1. Make sure `main` is green.
2. Tag and push: `git tag v1.3.0 && git push origin v1.3.0`.

The release workflow builds with the tag's version, runs the smoke test and publishes a GitHub release with the jar,
both resource packs and the Geyser mapping. Tags with a `-` (e.g. `v1.3.0-beta.1`) become pre-releases. The version in
`pom.xml` stays a `-SNAPSHOT` on `main`; bump it after a release.
