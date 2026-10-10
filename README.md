# Lumbina

A launcher for Android TV. Works without Google services. Made and tested on Hisense VIDAA
projectors with Chinese firmware.

It works, but it's not 1.0 yet.

![The home screen: favorites, a folder, and what to continue watching](docs/screenshots/home.jpg)

## Why

On some firmware you can't just replace the stock launcher. Home is wired to the vendor's
launcher and the mic button opens the vendor's assistant. Lumbina takes both buttons back with an
accessibility service.

## What it does

- Home opens Lumbina. Double press shows recent apps (can be turned off), long press opens a
  quick menu: recent apps, sleep timer, the phone remote, another home screen (the device's own
  launcher, or one you choose), Lumbina's settings.
- Search from the home screen goes to the system's search or to YouTube, as you choose.
- Home screen with favorites, rows and folders. Rows can be reordered and hidden, apps renamed
  and hidden.
- "Continue watching" and the channels apps publish. If the firmware has no TV provider, Lumbina
  installs its own.
- Voice input from the remote's mic into any text field. Offline (Vosk, Russian and English),
  through OpenAI or Yandex SpeechKit with your own key, or by another app on the device that
  recognizes speech for apps (experimental: on Hisense, Android 13 or newer, with debugging
  Lumbina can reach).
- Voice search in other apps on Hisense, where the firmware gives them no microphone (Android 13
  or newer, with debugging Lumbina can reach).
- Voice commands: open an app, search, volume, playback, sleep timer.
- Wallpapers: your own pictures and videos, photo of the day, Projectivy wallpaper plugins, or
  none: the theme's background.
- Clock, weather, notifications, now playing.
- Screensaver, sleep timer, auto-sleep.
- Phone as a remote. Scan the QR code and a web page opens, nothing to install.
- No ads, no telemetry, no account.

## Screenshots

The interface is in Russian here; it also comes in English.

| | |
|---|---|
| ![Rows of the home screen](docs/screenshots/home-rows.jpg) | ![All apps](docs/screenshots/all-apps.jpg) |
| ![The quick menu](docs/screenshots/quick-menu.jpg) | ![Wallpaper settings](docs/screenshots/settings.jpg) |
| ![The screensaver](docs/screenshots/screensaver.jpg) | <img src="docs/screenshots/phone-remote.png" alt="The phone remote" width="260"> |

The films in "continue watching" are open movies by the Blender Foundation, and the apps on
the tiles belong to their makers: see [NOTICE](NOTICE).

## Build

You need JDK 17 or newer and Android SDK 37. Open the folder in Android Studio and press Run.
Or from the command line:

```sh
./gradlew :app:assembleFossDebug
```

All checks. CI runs them too and builds a release:

```sh
./gradlew checkModuleGraph spotlessCheck detekt lint test testDebugUnitTest testFossDebugUnitTest
```

Install on a device over the network:

```sh
bash scripts/device/connect.sh 192.168.1.50
bash scripts/device/install.sh
bash scripts/device/enable-a11y.sh     # if the firmware hides the accessibility settings
```

There are PowerShell versions of the scripts next to them.

One thing about release APKs. They are built with a process name prefix that is set outside this
repository. Everything else in them comes from this source. On Hisense your own build may work
worse than a release.

## Code

Kotlin, Compose for TV, Hilt. The domain is plain Kotlin with ports, Android and vendor code sits
behind them. Device quirks live in `:vendor:*` modules, so a new device is a new module.
More in [docs/architecture.md](docs/architecture.md).

## Privacy

No account, no analytics, no server of ours. Settings and launch history stay on the device.
Lumbina talks to these services directly, so each of them sees your IP:

| Service | When | What it gets | How to turn it off |
|---|---|---|---|
| [ipwho.is](https://ipwho.is), [geojs.io](https://www.geojs.io) | Weather with automatic city | The request itself (city by IP) | Choose a city or hide the weather |
| [api.met.no](https://api.met.no), [open-meteo.com](https://open-meteo.com) | Weather is on screen, every 30 min | Coordinates of the city | Hide the weather |
| [geocoding-api.open-meteo.com](https://open-meteo.com) | You search for a city | What you typed | Don't search |
| [picsum.photos](https://picsum.photos), [bing.com](https://www.bing.com) | Screensaver or "Photo of the day" wallpaper | Screen size | Turn off online photos, pick another wallpaper |
| [alphacephei.com](https://alphacephei.com), [huggingface.co](https://huggingface.co) | You download a speech model | Which model | Don't download |
| [api.openai.com](https://openai.com), [stt.api.cloud.yandex.net](https://yandex.cloud/services/speechkit) | You chose cloud recognition | Audio of each phrase and your API key | Recognize on the device, it's the default |
| Another app on the device, and whatever it sends on | You chose another app to recognize | Audio of each phrase | Recognize on the device, it's the default |
| Whatever hosts the apps name | An app publishes channels or "continue watching" | The request for the artwork the app points to | Hide the row or the channel |
| Wallpaper plugins | You chose a plugin as wallpaper | Whatever the plugin asks for | Pick another wallpaper |

Recognized phrases go to Lumbina's log on the device, with the names of the apps and windows you
open. The log leaves the device only if you save a diagnostics report yourself and send it to
someone: the report is a file with the log's tail, facts about the device and its firmware, and
lists of its system apps and of the apps that publish channels. The phone remote is plain HTTP on the local network, protected by
the key in its QR code.

What every permission is used for: [SECURITY.md](SECURITY.md).

## License

GPL-3.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE). Use it, change it, share it. If you
distribute a changed version, publish its source under the same license.

The name Lumbina and the icons are not part of the license. A fork needs its own name, icon and
application id, see [TRADEMARKS.md](TRADEMARKS.md).

Not affiliated with Hisense, Vidda, Google or anyone else.
