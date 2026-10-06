# Budgeter

Pixel-art Android budget planner: daily budget, monthly pacing, receipt scanning with per-item tax and service charge, bill splitting, trips, planned purchases, Google Sheets export and home-screen widgets.

Plan and decisions: [PLAN.md](PLAN.md). Art list: [docs/GRAPHICS.md](docs/GRAPHICS.md). Sheets setup: [docs/google-setup.md](docs/google-setup.md).

## Install on your phone

1. On the phone: Settings → About phone → tap **Build number** 7 times. Then Settings → System → Developer options → turn on **USB debugging**. (On Oppo/ColorOS: Settings → About device → Version → tap Build number.)
2. Plug the phone into this PC and accept the "Allow USB debugging" prompt.
3. From this folder:

```bash
./gradlew installDebug
```

Windows needs Android Studio's Java first, for example in Git Bash: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`.

## Tests

```bash
./gradlew testDebugUnitTest
```

## Cheapest AI setup

Get a free Gemini key at https://aistudio.google.com/apikey and add it under Profile → AI receipt reading. Without any key the built-in offline reader is used.
