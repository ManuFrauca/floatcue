# Contributing to FloatCue

Thanks for helping. FloatCue does one job: show a script over the camera app while you record. Keeping it small is a feature.

## Bugs

Open an issue with the bug form. Include your phone model, Android version, the camera app you used, and the steps that reproduce the problem. A screen recording helps a lot.

## Feature ideas

Open an issue before writing code. Good ideas make reading while recording easier and don't add settings most people would never touch.

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Keep the change focused. One fix or one feature per pull request.
3. Add a unit test for new logic (see `PrompterMathTest` and `PauseHighlighterTest`).
4. Make sure this passes with no lint errors:
   ```sh
   ./gradlew lint testDebugUnitTest assembleDebug
   ```
5. Try the change on a real device or an emulator, over a camera app, and say in the pull request what you tested.

Ground rules:

- No new third-party dependencies. AndroidX, Compose and Material 3 only.
- No internet permission, analytics or tracking.
- English for code, strings and docs.

By contributing, you agree that your contribution is licensed under GPL-3.0.
