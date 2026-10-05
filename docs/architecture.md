# Architecture

Lumbina is a multi-module Android project organised around a pure-Kotlin domain.

```
            :app  (Hilt graph, manifest, navigation, flavors)
              │
   ┌──────────┼───────────────────────────────┐
   ▼          ▼                               ▼
:feature:*   adapters                        :core:data
(Compose UI, :platform:*  :speech:*          (Room, DataStore)
 ViewModels) :vendor:*
   │          │                               │
   └──────────┴──────────────┬────────────────┘
                             ▼
                 :core:domain  <- :core:common
          (entities, ports, use cases, no Android)
```

## Modules

| Module | Role |
|---|---|
| `:core:domain` | Entities, value objects, ports (interfaces), use cases. Plain Kotlin/JVM. |
| `:core:common` | `LumbinaActions` (in-app broadcast actions and extras) and the `LumbinaLog` port. Plain Kotlin/JVM. |
| `:core:network` | `HttpGet` port and its `HttpURLConnection` adapter (timeouts, User-Agent, status check), shared by the platform adapters. Plain Kotlin/JVM. |
| `:core:data` | Persistence: Room (sections, folders, hidden apps, labels) and JSON DataStore files for settings, multi-process where both processes use them. |
| `:core:designsystem` | TV theme: colours, accent, typography. |
| `:core:ui` | Shared TV components: app cards, program tiles, dialogs, on-screen keyboard, app artwork, wallpaper layer, QR code. |
| `:core:testing` | Fakes shared by tests. Plain Kotlin/JVM. |
| `:feature:*` | Screens and view models: `home` (incl. all apps), `settings`, `onboarding`, `screensaver`. |
| `:platform:*` | Android adapters: `packages`, `accessibility`, `system`, `weather`, `wallpaper`, `tvprovider`, `adb`. |
| `:speech:vosk` | Offline speech recognition: engine, model store, and the `:voice` process that runs it. |
| `:speech:cloud` | Cloud speech recognition (OpenAI, Yandex SpeechKit) with the user's API keys; the voice settings choose between it and the device (`SpeechEngineChoice`). |
| `:vendor:*` | Device-specific behaviour (`hisense`, `generic`), selected at runtime. |
| `:companion:tvprovider` | Lumbina TV Provider: a standalone APK with an `android.media.tv` provider for firmware that lacks one. |
| `:baselineprofile` | Generates the baseline profile and runs startup/scroll benchmarks against `:app`. |

## Dependency rules

Enforced by `./gradlew checkModuleGraph` (see `build.gradle.kts`):

- `core:domain` depends only on `core:common`; `core:common` on nothing. Neither uses Android.
- `feature:*` depends on `core:*` only. Adapters reach features through ports bound in Hilt.
- `core:network` depends only on `core:common`.
- `platform:*` depends on `core:domain` / `core:common` / `core:network` only; `speech:*` on `core:domain` / `core:common` only.
- `vendor:*` may additionally use `platform:accessibility` (key mapping types).
- `companion:*` has no project dependencies; `baselineprofile` only targets `:app`.
- `:app` wires everything.
- Test code may always depend on `:core:testing`.

## Devices

Each vendor module contributes a `DeviceProfile` (`@IntoSet`, `core:domain` `device/`) that says
whether it matches the running device (`DeviceFingerprint`: manufacturer, model, installed
packages). `DeviceProfileResolver` picks the matching profile with the highest priority; the
generic profile matches everything. The profile supplies the remote's key map, the stock
launcher (reachable from the quick menu) and, if the vendor exposes it, the remote-mic audio
source. Adding a device family means adding one module and one line in `:app`.

## Processes

The launcher UI runs in `:ui`, the app's default process. The accessibility service
(remote buttons, voice input, overlays, phone remote, sleep) and the self-heal job run in
`:remote`, so keys never wait for the UI's main thread and a UI crash can't take the
Home button down. They share settings through multi-process DataStore files and in-app
broadcasts (`LumbinaActions`).

Speech recognition runs in `:voice` (`VoiceService`), bound through
`RemoteSpeechEngine` when an utterance starts and let go five minutes after the last one; the
process then ends, taking the models' memory with it. The first phrase after that waits for a
model to load; the mic is read meanwhile.

Speech for other apps runs in `:remote` too: the `RECOGNIZE_SPEECH` screen and the
`RecognitionService` share the service's connection to the remote's mic. Where Lumbina reaches its
own adb, it also starts a helper (`MicInjector`) as the adb shell from its own APK; it is no
process of Lumbina's and lives exactly as long as its socket connection to `:remote`.

The names of the processes come from the build (`processNames` in `build-logic`): a release may
be built with a prefix before them, set outside this repository. A build from source has the
plain names.

## Build

- Convention plugins in `build-logic/` (`lumbina.jvm.library`, `lumbina.android.library`,
  `lumbina.android.application`, `lumbina.android.compose`, `lumbina.android.feature`,
  `lumbina.hilt`, `lumbina.quality`); versions in `gradle/libs.versions.toml`.
- AGP 9 compiles Kotlin itself (built-in Kotlin); no separate Kotlin Android plugin.
- Tests: JUnit 5 in JVM modules; JUnit 4 + Robolectric in Android modules.
