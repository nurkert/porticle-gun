# Contributing

Thanks for helping! Bug reports, ideas and pull requests are welcome.

## Reporting bugs

Open an [issue](https://github.com/nurkert/porticle-gun/issues/new/choose) with the server software and version, the
PorticleGun version, whether the player was on Java or Bedrock, steps to reproduce and the relevant part of the server
log.

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Build and test: `mvn verify` (see [docs/development.md](docs/development.md)). Run `mvn spotless:apply` to fix
   formatting.
3. For gameplay changes, test on a server: `scripts/dev-server.sh` starts one with Geyser for Bedrock tests.
4. Add a line to the `Unreleased` section of [CHANGELOG.md](CHANGELOG.md).
5. Open the pull request and describe what changed and how you tested it.

Changes to the settings menu (`SettingsHandler`) need to be discussed in an issue first.

By contributing you agree that your contribution is licensed under the [GPL-3.0](LICENSE).
