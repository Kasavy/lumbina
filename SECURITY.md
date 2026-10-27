# Security

Lumbina asks for a lot. Here is what it does with each thing.

## Accessibility service

Reads the remote's key presses to take over Home, the mic button, Menu on Lumbina's own screens
and any button you gave something to do, and to notice idle time for the screensaver and
auto-sleep. A button nothing is set on goes on to the device untouched. Knows which app is on
screen: for recent apps, to tell its own screens from another app's, and to bring you back to
Lumbina with Back from an input you opened from it. Performs system actions:
Back, arrows from the phone remote, standby; and what you set on a button: opening an app or a
screen of Lumbina's, the player's keys, muting, the screensaver, turning the screen off. Shows the quick menu, recent apps
and the voice overlay. Opens Lumbina when the device turns on, if you asked for that, and right
after Lumbina is updated, if the update left the home screen in front.

It reads the screen only right after you use voice input, to find the text field or on-screen
keyboard to type into. Screen content is never stored or sent anywhere.

## WRITE_SECURE_SETTINGS

Optional, you grant it over adb. Lumbina uses it to turn its own accessibility service back on
when the firmware drops it (it only adds its own entry and removes duplicates) and to make itself
the system screensaver when you ask. Without the grant there is no self-healing.

## Microphone

Audio comes from the remote's mic, and only in three cases: you use voice input, another app asks
Lumbina for speech, or an app Lumbina passes the mic to is recording.

It is recognized on the device (Vosk) and not saved. If you choose a cloud service in the voice
settings (OpenAI or Yandex SpeechKit), each phrase goes to that service with your API key and
nothing else. If you choose another app on the device to recognize, each phrase goes to that app,
and what it does with it is the app's own matter. Recognized phrases are written to Lumbina's log on
the device. The log leaves the device only in a diagnostics report you save yourself.

API keys are pasted on the phone remote page, stored on the TV encrypted with an Android Keystore
key, never shown and never logged.

On Hisense the remote's audio needs a handshake. So Lumbina stays connected for about 30 seconds
after you release the mic button and keeps the last 1.5 seconds in memory, otherwise the next
phrase loses its first syllable. That audio is dropped unless you start voice input again.

## Speech for other apps (Hisense)

Lumbina registers as the system speech recognizer (`RECOGNIZE_SPEECH` and `SpeechRecognizer`) and
answers apps that ask for speech with text. It declares `RECORD_AUDIO` only because the system
checks that a recognizer holds it. Lumbina never records from Android's microphone.

If Lumbina has its own debugging connection (below), it grants itself that permission and makes
itself the system recognizer once. If you choose another recognizer later, yours stays.

## Lumbina's own debugging connection (Hisense)

Optional. You turn on USB debugging and allow Lumbina's key on the TV. Lumbina then connects to
the device's adb on the loopback address and:

- keeps its service on (the WRITE_SECURE_SETTINGS grant above);
- grants itself usage access, notification access and `RECORD_AUDIO`;
- sets the system speech recognizer;
- stops an app you close from "Recent apps";
- installs Lumbina TV Provider, the companion app it carries, if the device has no TV provider, and
  updates an older copy of it; on Android 11 and newer it is installed visible to all apps
  (`--force-queryable`), as the system's TV provider is, so that apps can publish channels to it;
- turns a system app of the firmware back on when you choose "Turn on" on the diagnostics page;
- installs a new version of Lumbina you asked it to download, without the system's question;
- asks the shell about the device when you save a diagnostics report;
- holds OK for the phone remote, which the accessibility service can't do;
- starts a helper.

The helper runs as the adb shell from Lumbina's own APK. Android's microphone input is silent on
the projector, so apps that record the microphone themselves hear nothing. The helper registers
an audio policy that feeds the remote's audio into the recordings of the apps you allowed. When
an app on screen starts to record for the first time, Lumbina asks whether to let it hear the
remote. The app you chose to recognize speech is not asked about: choosing it is the answer, and
it gets only the phrases of voice input. The answers can be changed in the settings, on the Access
page. The helper lives only as long as its connection to Lumbina, and only the shell user can
connect.

Revoke debugging authorizations in the developer options and all of this is gone.

## Other access

- Notification access (optional): the notification indicator and "now playing".
- Usage access (optional, `PACKAGE_USAGE_STATS`): counts how often apps come to the front over
  two weeks, for the "Frequently used" row and for ordering apps and recommendations by use. Stays
  on the device.
- TV provider (`READ_TV_LISTINGS` and the provider's own read permissions): reads the channels
  and "continue watching" that apps publish.
- Installed apps: as a launcher Lumbina lists the apps that can be started. It doesn't ask for
  `QUERY_ALL_PACKAGES`.
- `RECEIVE_BOOT_COMPLETED`: after a boot or an update Lumbina checks that its accessibility
  service is on.
- `REQUEST_DELETE_PACKAGES`: opens the system's uninstall dialog when you remove an app from the
  home screen. The system asks you, Lumbina removes nothing by itself.
- `REQUEST_INSTALL_PACKAGES`: Lumbina updates itself when you ask on the "About Lumbina" page. It
  downloads the new version from its releases on GitHub over https and checks it against the
  checksum published beside it; a release without one is not installed. It also checks that the
  package is Lumbina's own and no older than the installed one. Where Lumbina has its own
  debugging connection, it installs the package over it without the system's question (see the
  list above): the package is copied to `/data/local/tmp` for the install and removed after it.
  Otherwise, or when that fails, it hands the package to the system's installer, which asks you.
  Where Lumbina may not install apps yet, choosing the update also opens the system's screen where
  you allow it. The system installs it only if it is signed with the same key as the installed
  Lumbina. A downloaded package stays in Lumbina's cache until it is installed, so a failed or
  interrupted try does not download it again; a package of an older release is deleted. Nothing
  is checked or downloaded by itself. A build for a store would leave this permission out.
- `INTERNET` and `ACCESS_NETWORK_STATE`: the requests listed under Network, and the connection
  icon on the home screen.

## Phone remote

Off by default. While it's on, an HTTP server on port 8765 of the local network serves the remote
page. The page itself is given to anyone; commands are accepted only with the access key from the
QR code. With the same key in its address, the server also gives the last diagnostics report you
saved (the settings show a code for it), once: it is deleted as it goes. That file holds the
log's tail, recognized phrases included. A new key locks out phones that had the old one. The code
is shown in the settings and the quick menu; showing it on the screensaver is a separate switch, off
by default, since then everyone in the room can read the key. It's plain HTTP, so use it on a
network you trust.

## Network

Lumbina itself contacts only:

- speech models: `alphacephei.com`, mirror `huggingface.co` (checked by SHA-256);
- cloud speech recognition, only if you chose it: `api.openai.com` or `stt.api.cloud.yandex.net`;
- weather: `api.open-meteo.com`, `api.met.no`, city search `geocoding-api.open-meteo.com`,
  city by IP (unless you chose a city) `ipwho.is` and `get.geojs.io`;
- photos for wallpaper and screensaver: `picsum.photos`, `www.bing.com`;
- artwork URLs that apps publish in their channels;
- a new version of Lumbina, only when you ask for the check: `api.github.com` and the release's
  files on `github.com`.

Wallpaper plugins you install make their own requests. No analytics, no telemetry, no ads.

## Found a vulnerability?

Open a private security advisory on GitHub (the Security tab, "Report a vulnerability"), not a
public issue. I'll try to answer within a week.
