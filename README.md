# FloatCue

A small floating teleprompter for Android. It draws a semi-transparent strip over other apps, near the front camera, with text that scrolls automatically.

## Why

Record with your normal camera app (and your external USB mic) while reading a script. FloatCue only shows the text; it never touches the camera or the microphone.

![Demo](docs/demo.gif) <!-- TODO: add demo GIF -->

## Features

- Floating overlay over any app; touches outside the strip reach the app below.
- Smooth time-based auto-scroll; tap to pause/resume, drag to scrub.
- Controls: slower / faster (10 % steps), play/pause, restart, close, and a grip to move the strip vertically.
- Adjustable speed, text size, background opacity and window height. Everything is saved automatically.
- `[pause]` markers are highlighted (see below).
- No third-party libraries: only AndroidX, Compose and Material 3.

## Install

Download the APK from the Releases page, or build it yourself:

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires Android 8.0 (API 26) or newer. Built and aimed at a Pixel 8 Pro on Android 16.

## Permission

FloatCue needs "Display over other apps". The app guides you to the right settings screen. If the toggle is greyed out (Android 15+, sideloaded apps), open App info, tap the three-dot menu and choose "Allow restricted settings", then try again.

## Pause highlighting

The whole words `pause` and `pausa` (any case, optionally wrapped in square brackets, e.g. `[pause]`) are shown in bold amber so you remember to breathe. Words that merely contain them (`pausado`, `pauses`, `repausa`) are not highlighted.

## License

GPL-3.0. See [LICENSE](LICENSE).
