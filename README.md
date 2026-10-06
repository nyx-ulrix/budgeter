# Budgeter

Pixel-art Android budget planner: daily budget, monthly pacing, receipt scanning with per-item tax and service charge, bill splitting, trip budgeting, planned purchases, Google Sheets export and home-screen widgets.

Plan and decisions: [PLAN.md](PLAN.md). Art list: [docs/GRAPHICS.md](docs/GRAPHICS.md). Sheets setup: [docs/google-setup.md](docs/google-setup.md).

## Get the app

Download the latest `budgeter-vX.Y.Z.apk` from [Releases](https://github.com/nyx-ulrix/budgeter/releases/latest) and open it on the phone. After that the app updates itself: when a new release is out it shows a blue **Update** strip, or use Profile → Updates → Check for updates.

## Install from this PC (any plugged-in Android device)

Double-click **`Install on phone.cmd`**. It builds the app, waits for a device, and installs and opens it on every phone plugged in by USB.

The phone needs USB debugging once: Settings → About phone → tap **Build number** 7 times, then Developer options → **USB debugging**. On Oppo/ColorOS: Settings → About device → Version → tap Build number. Accept the "Allow USB debugging" prompt when you plug in.

## Publish an update

1. Commit your changes.
2. Double-click **`Publish release.cmd`** and type the new version, for example `0.3.0`.

It runs the tests, tags the commit and pushes. GitHub Actions ([release.yml](.github/workflows/release.yml)) builds the signed APK and publishes the release, and installed apps offer the update.

Every build, from this PC or from GitHub, is signed with the same release key (`keystore.properties`, not in git; the key file lives in `C:\Users\malco\.android\`). Android only installs an update signed with that key, so **keep a backup of the key file and `keystore.properties`**. Losing them means users must uninstall to get new versions.

## Test server

```bash
node tools/test-server.js 0.9.0
```

Or start **test-server** from the Claude app's preview menu ([.claude/launch.json](.claude/launch.json)). It runs on port 8787 and does two things:

- **Fake GitHub releases.** With a version number it builds that version and offers it as the latest release, so you can test updating without publishing. In a debug build: Profile → Updates → Test server, then Check for updates. Use `http://10.0.2.2:8787` on the emulator, or `http://localhost:8787` on a phone plugged in by USB (the server forwards the port automatically).
- **Fake AI provider.** Profile → AI → Custom, base URL `http://localhost:8787/v1` (or `http://10.0.2.2:8787/v1` on the emulator), any key, model `test`. Every receipt comes back as [tools/test-receipt.json](tools/test-receipt.json).

Test against the emulator rather than your own phone. A test version like 0.9.0 is newer than real releases, so a phone holding it won't take real updates until it's uninstalled.

## Tests

```bash
./gradlew testDebugUnitTest
```

Windows needs Android Studio's Java first, for example in Git Bash: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`.

## Cheapest AI setup

Get a free Gemini key at https://aistudio.google.com/apikey and add it under Profile → AI receipt reading. Without any key the built-in offline reader is used.
