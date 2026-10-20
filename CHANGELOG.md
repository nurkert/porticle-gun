# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [semantic versioning](https://semver.org).

## [Unreleased] - 1.3.0

### Added
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
