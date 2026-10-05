# Contributing

Bug reports, "works / doesn't work on my device" reports and ideas are welcome. Open an issue.

## Code

Pull requests are not merged for now. The reason is licensing: Lumbina is GPL-3.0 and I want to
keep the right to decide later how it is licensed and funded, so every line has to be mine. If
you have a fix, describe it in an issue.

You can fork under the [license](LICENSE). A fork needs its own name and icon, see
[TRADEMARKS.md](TRADEMARKS.md).

## Working on the code

```sh
./gradlew spotlessApply      # format
./gradlew checkModuleGraph spotlessCheck detekt lint test testDebugUnitTest testFossDebugUnitTest
```

CI runs these checks and builds a release. Module rules are in [docs/architecture.md](docs/architecture.md): domain
logic goes to `:core:domain` behind ports, Android details to adapters, device quirks to a
`:vendor:*` module. A behaviour change comes with tests: JUnit 5 for the domain, Robolectric for
Android code.

## Testing on a device

`scripts/device/` has helpers to connect over adb, install, turn on the accessibility service and
grant the permissions Lumbina needs.
