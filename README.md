# FloatCue

A small floating teleprompter for Android. It draws a semi-transparent strip over other apps, near the front camera, with text that scrolls automatically.

## Why

Record with your normal camera app (and your external USB mic) while reading a script. FloatCue only shows the text; it never touches the camera or the microphone.

## Features

- Floating overlay over any app, portrait only; touches outside the strip reach the app below.
- Smooth time-based auto-scroll; speed is set in lines per minute (10 to 80). Tap the text to play or pause, drag to scrub.
- A reading line with a caret marks where to look: amber while playing, grey while paused.
- Controls: slower / faster (10 % steps), play/pause, restart (also pauses), close, and a grip to move the strip vertically.
- The strip hides while FloatCue's own settings screen is open. When you leave it, the strip comes back with your new settings, at the same reading position, paused.
- Adjustable speed, text size, background opacity and window height. Everything is saved automatically.
- `[pause]` markers are highlighted in the overlay and in the editor (see below).
- TalkBack support: the strip announces playing/paused, and the grip offers Move up / Move down actions.
- No third-party libraries: only AndroidX, Compose and Material 3.

## Install

Download the signed release APK from the Releases page and check its SHA-256 against the value in the release notes before installing:

```
shasum -a 256 floatcue-release.apk
```

Requires Android 8.0 (API 26) or newer. Built and aimed at a Pixel 8 Pro on Android 16.

## Build

```
./gradlew lint testDebugUnitTest assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Signed release

Create a keystore yourself and put its details in `keystore.properties` at the repository root (it is gitignored):

```
storeFile=/path/to/floatcue.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Alternatively set the environment variables `FLOATCUE_KEYSTORE`, `FLOATCUE_KEYSTORE_PASSWORD`, `FLOATCUE_KEY_ALIAS` and `FLOATCUE_KEY_PASSWORD`. Then run `./gradlew assembleRelease`; the APK is `app/build/outputs/apk/release/app-release.apk`. Without a key, the release build is produced unsigned.

## Permission

FloatCue needs "Display over other apps". The app guides you to the right settings screen. If the toggle is greyed out (Android 15+, sideloaded apps), open App info, tap the three-dot menu and choose "Allow restricted settings", then try again.

## Pause highlighting

The whole words `pause` and `pausa` (any case, optionally wrapped in square brackets, e.g. `[pause]`) are shown in bold amber so you remember to breathe. Words that merely contain them (`pausado`, `pauses`, `repausa`) are not highlighted.

## License

GPL-3.0. See [LICENSE](LICENSE).
