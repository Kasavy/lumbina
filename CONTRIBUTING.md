# Contributing

Bug reports, "works / doesn't work on my device" reports and ideas are welcome. Open an issue.

## Code

Pull requests are not merged for now. The reason is licensing: Lumbina is GPL-3.0 and I want to
keep the right to decide later how it is licensed and funded, so every line has to be mine. If
you have a fix, describe it in an issue.

You can fork under the [license](LICENSE). A fork needs its own name and icon, see
[TRADEMARKS.md](TRADEMARKS.md). It updates itself from the releases of the repository named by
`lumbina.updatesRepo` in `gradle.properties`: set your own.

The release workflow publishes a tag as a pre-release, and GitHub's "latest release" skips those:
users are offered it only once it is made a release by hand on GitHub. The tag must be `v` plus
`versionName`, and `versionCode` must be above the previous tag's; the workflow checks both.

To try updating without publishing a release, build with `-Plumbina.updatesUrl=http://<host>/latest.json`
pointing at a server that answers as GitHub's "latest release" does (`tag_name`, and `assets` with
the APK and `SHA256SUMS.txt`). Plain http is accepted only from that host. The test APK must be
signed with the same key as the installed build and have a `versionCode` no lower, or it is refused.
A build made this way checks that server for good: don't hand it out.

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

Keys sent with `adb shell input` go past an accessibility service, so a debug build takes
broadcasts instead: a remote button as the service sees it, the quick menu, recent apps, a spoken
phrase, a short sleep timer. They are listed in `DebugCommands.kt`; only the adb shell can send
them, and a release build has none.
