# Security

Lumbina asks for a lot. Here is what it does with each thing.

## Accessibility service

Reads the remote's key presses to take over Home and the mic button and to notice idle time for
the screensaver and auto-sleep. Knows which app is on screen: for recent apps, and to tell its
own screens from another app's. Performs system actions: Back, arrows from the phone remote,
standby. Shows the quick menu, recent apps and the voice overlay.

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
and what it does with it is the app's own matter. Recognized phrases are written to Lumbina's log on the device. The log leaves the
device only in a diagnostics report you save yourself.

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
- installs Lumbina TV Provider, the companion app it carries, if the device has no TV provider;
- turns a system app of the firmware back on when you choose "Turn on" on the diagnostics page;
- asks the shell about the device when you save a diagnostics report;
- starts a helper.

The helper runs as the adb shell from Lumbina's own APK. Android's microphone input is silent on
the projector, so apps that record the microphone themselves hear nothing. The helper registers
an audio policy that feeds the remote's audio into the recordings of the apps you allowed. When
an app on screen starts to record for the first time, Lumbina asks whether to let it hear the
remote. The app you chose to recognize speech is not asked about: choosing it is the answer, and
it gets only the phrases of voice input. The answers can be changed in the settings, on the Access page. The helper lives only as
long as its connection to Lumbina, and only the shell user can connect.

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
- `INTERNET` and `ACCESS_NETWORK_STATE`: the requests listed under Network, and the connection
  icon on the home screen.

## Phone remote

Off by default. While it's on, an HTTP server on port 8765 of the local network serves the remote
page. Commands are accepted only with the access key from the QR code. A new key locks out phones
that had the old one. It's plain HTTP, so use it on a network you trust.

## Network

Lumbina itself contacts only:

- speech models: `alphacephei.com`, mirror `huggingface.co` (checked by SHA-256);
- cloud speech recognition, only if you chose it: `api.openai.com` or `stt.api.cloud.yandex.net`;
- weather: `api.open-meteo.com`, `api.met.no`, city search `geocoding-api.open-meteo.com`,
  city by IP (unless you chose a city) `ipwho.is` and `get.geojs.io`;
- photos for wallpaper and screensaver: `picsum.photos`, `www.bing.com`;
- artwork URLs that apps publish in their channels.

Wallpaper plugins you install make their own requests. No analytics, no telemetry, no ads.

## Found a vulnerability?

Open a private security advisory on GitHub (the Security tab, "Report a vulnerability"), not a
public issue. I'll try to answer within a week.
