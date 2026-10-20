# Budgeter

Pixel-art Android budget planner for one person. Set a monthly budget and a savings target, see what you can spend today (unspent days carry forward), scan receipts on the phone and split them per item including service charge and GST, plan trips and save for them month by month, and keep everything in your own Google Sheet. Track meals and macros, and share a date budget with your partner. Three home-screen widgets.

There's also a web version, [Budgeter Web](https://github.com/nyx-ulrix/budgeter-web), at https://app.budgeter.liewjiaen.com. With Google Sheets sync on in both, the phone and the web app share everything: expenses, budget, planned purchases, trips, categories and settings.

Plan and decisions: [PLAN.md](PLAN.md). Art list: [docs/GRAPHICS.md](docs/GRAPHICS.md). Sheets setup: [docs/google-setup.md](docs/google-setup.md).

## Screenshots

| Home | Month in numbers | Budget | Transactions |
|---|---|---|---|
| ![Home](docs/screenshots/01-home.png) | ![Month details](docs/screenshots/02-month-details.png) | ![Budget](docs/screenshots/03-budget.png) | ![Transactions](docs/screenshots/04-transactions.png) |

| Receipt split by item | Trip budget | Receipt camera | Widgets |
|---|---|---|---|
| ![Receipt](docs/screenshots/05-receipt.png) | ![Trip](docs/screenshots/06-trip.png) | ![Camera](docs/screenshots/07-camera.png) | ![Widgets](docs/screenshots/08-widgets.png) |

## How it fits together

Three front ends share two kinds of data. Each person's own budget lives in their device and their own Google Sheet; the date budget shared by a couple lives in Budgeter's Firebase project. There is no Budgeter server.

```mermaid
flowchart LR
    subgraph People
        me([You])
        partner([Your partner])
    end

    subgraph Apps["Front ends"]
        android["Budgeter Android<br/>Kotlin · Jetpack Compose<br/>2 home-screen widgets + macros widget"]
        web["Budgeter Web<br/>React Native for Web (Expo)<br/>app.budgeter.liewjiaen.com"]
        dbsite["DateBudget site<br/>React + Vite<br/>datebudget.liewjiaen.com"]
    end

    subgraph Google["Your Google account"]
        sheet[("Budgeter spreadsheet<br/>month tabs + hidden _sync tab")]
    end

    subgraph Firebase["Firebase project budgeter-510810"]
        auth["Firebase Auth<br/>Google sign-in"]
        fs[("Firestore<br/>budgets/{code}<br/>budgets/{code}/tx/{id}")]
        hosting["Hosting<br/>budgeter.liewjiaen.com · app.budgeter.liewjiaen.com"]
    end

    ai["AI provider of your choice<br/>Gemini · OpenRouter · Claude · OpenAI · DeepSeek · Perplexity<br/>(your own key)"]
    fx["open.er-api.com<br/>exchange rates"]
    gh["GitHub Releases<br/>signed APKs + update check"]

    me --> android & web
    partner --> android & web & dbsite
    android <-->|"two-way sync (Drive/Sheets API)"| sheet
    web <-->|"two-way sync (Drive/Sheets API)"| sheet
    android & web & dbsite -->|"one Google login"| auth
    android & web & dbsite <-->|"date budget (Firestore REST / SDK)"| fs
    android & web -->|"receipt photo, food names"| ai
    android & web -->|"currency codes only"| fx
    android -->|"check + download updates"| gh
    hosting -.serves.-> web
```

Inside each app the layers are the same, and the Kotlin and TypeScript versions mirror each other file for file (`core/*.kt` ↔ `src/core/*.ts`, `data/*.kt` ↔ `src/data/*.ts`), with the same tests.

```mermaid
flowchart TB
    ui["UI<br/>Compose screens (ui/*.kt) · RN screens (src/screens/*.tsx)<br/>Home · Txns · Budget · Macros · Profile"]
    store["Store<br/>one AppState, saved as state.json / localStorage<br/>every change stamps the records it touched"]
    core["core: pure logic, unit-tested<br/>Budget (daily replay, pace) · Money · Split · Receipt · ReceiptText<br/>Macros · DateMoney"]
    data["data<br/>Actions (every state change) · Ledger (snapshots, totals) · Records (sync records) · Rates"]
    sync["sync<br/>Sheets.kt / sheets.ts: month tabs + _sync merge"]
    date["date<br/>DateClient.kt / client.ts: Firestore REST + Firebase Auth"]
    aiL["ai<br/>receipt reading, macros look up / fill"]
    widget["widget (Android only)<br/>spend · bars · macros"]

    ui --> data --> store
    data --> core
    ui --> core
    store --> sync
    ui --> date
    ui --> aiL
    store --> widget
```

### Two-way sync (phone ↔ web)

Every record (`setup`, `categories`, `macros`, `plan:<month>`, `txn:<id>`, `planned:<id>`, `trip:<id>`, `meal:<id>`, `preset:<id>`) carries the time it last changed. Each sync keeps whichever copy is newer; a delete is an empty record with a stamp, so it wins too.

```mermaid
sequenceDiagram
    participant A as Phone (or web)
    participant S as _sync tab in your Sheet
    participant B as Web (or phone)
    A->>A: change → Store.update stamps changed records (now)
    A->>S: read all records
    S-->>A: remote stamps + JSON
    A->>A: mergeRemote: take records newer than mine
    A->>S: toUpload: write my records newer than the sheet's
    A->>S: write expenses to the readable month tab
    B->>S: next sync (open, change, focus, every minute on web)
    S-->>B: newer records
    B->>B: mergeRemote
```

### Today's budget

What you can spend today is replayed day by day from the start of the budget month, so unspent money carries forward and spending on monthly categories (groceries, bills) lowers every remaining day instead of today alone.

```mermaid
flowchart TD
    start([Open the app]) --> plan["Spendable this month =<br/>income + extra money − fixed costs − savings − reservations − trip funds<br/>(capped by your planned budget, minus the savings target)"]
    plan --> loop{"For each earlier day<br/>of the month"}
    loop --> pool["pool = what that day could see as spendable − spent so far"]
    pool --> share["day's budget = (pool − carried bonus) ÷ days left"]
    share --> carry["bonus += day's budget − what was spent<br/>(never below 0, never above what's left)"]
    carry --> loop
    loop -->|done| today["Today's budget = (pool − bonus) ÷ days left<br/>bonus = saved-up money, minus today's monthly-category spending"]
    today --> show["Home: left today = budget + bonus − spent today<br/>pace = spent vs. an even burn of the month"]
```

### Scanning a receipt

```mermaid
flowchart TD
    cam([Scan / pick a photo]) --> ai{"AI provider set?"}
    ai -->|yes| send["Photo (≤1600 px) → provider<br/>JSON: items, discount, service charge, tax, total, category"]
    ai -->|no| ocr["On-device text recognition<br/>(ML Kit on Android, Tesseract on web)"]
    ocr --> rules["Built-in reader (ReceiptText): rows → items, charges, total"]
    send --> parsed["ParsedReceipt"]
    rules --> parsed
    parsed --> date["Date check: a misread year or a date far in the past/future → this year or today"]
    date --> editor["Expense editor: items, charges, split per item, currency + rate"]
    editor --> check{"Items + charges = printed total?"}
    check -->|no| fix["Shows the gap; fix an item or clear the total"] --> editor
    check -->|yes| toward{"Count toward"}
    toward -->|Monthly budget / trip| save["Save expense → Store → Sheets"]
    toward -->|Date budget| shared["Save to the shared date budget (Firestore)"]
    save --> food{"Category Food?"}
    food -->|yes| macros["Save & add to macros → Add food, items filled in"]
```

### Date budget (shared with a partner)

One Google login: the phone signs in quietly with the account it already used (and on Connect Google), the web reuses its Google Sheets token, and the DateBudget site uses Google's sign-in button. All three then read and write the same Firestore documents with the same rules.

```mermaid
sequenceDiagram
    participant App as Budgeter (phone / web)
    participant G as Google sign-in
    participant FA as Firebase Auth (budgeter-510810)
    participant FS as Firestore
    App->>G: ID token (phone) / Sheets access token (web)
    App->>FA: accounts:signInWithIdp
    FA-->>App: Firebase session (refresh token kept encrypted)
    App->>FS: runQuery budgets where members contains me
    FS-->>App: budget {members, names, plan}
    App->>FS: list budgets/{code}/tx
    Note over App: expense with Count toward → Date budget
    App->>FS: POST tx {kind, amount, note, date, by}
    FS-->>App: created document
    App->>App: show it at once, and only the newest reload may replace the list
    Note over App,FS: My monthly share is a fixed cost in my own budget,<br/>so date spending never comes out of it twice.
```

### Macros

```mermaid
flowchart TD
    exp["Food expense (meals, not groceries)"] --> wait["Waits in Macros → Meals: 'tap to add what you ate'"]
    free(["Add food: free food, home cooking"]) --> dlg
    homebtn(["Home: Food button"]) --> dlg
    wait --> dlg["Add food"]
    dlg --> preset["Preset (protein shake, morning coffee, own recipe)"]
    dlg --> look["Look up with AI: matches per 100 g + typical portion<br/>amount × unit (portion, g, ml, oz, cup, tbsp, tsp) → added on"]
    dlg --> fill["Fill all from the name (AI)"]
    dlg --> typed["Type in from a label (all nutrients)"]
    preset & look & fill & typed --> meal["Meal saved (synced to the other app)"]
    meal --> bars["Macros tab bars · Home card · macros widget"]
    meal --> report["Report: 7 / 30 days, average a day, days on target"]
```

### Releasing

```mermaid
flowchart LR
    dev["Commit"] --> pub["Publish release.cmd<br/>tests → tag vX.Y.Z → push"]
    pub --> act["GitHub Actions release.yml<br/>signs the APK with the release key<br/>(secrets: keystore + FIREBASE_API_KEY)"]
    act --> rel["GitHub Release<br/>budgeter-vX.Y.Z.apk"]
    rel --> upd["Installed apps show Update"]
    webdev["Budgeter Web commit"] --> build["npm run build:web"] --> deploy["firebase deploy --only hosting<br/>(site budgeter-app)"]
    docs["docs/ (this site)"] --> deploy2["firebase deploy --only hosting<br/>(default site → budgeter.liewjiaen.com)"]
```

## Platforms this project uses

| Platform | What it's for | Where |
|---|---|---|
| **GitHub** | Code, issues, release builds and APK downloads | [nyx-ulrix/budgeter](https://github.com/nyx-ulrix/budgeter) (Android app + website in `docs/`), [nyx-ulrix/budgeter-web](https://github.com/nyx-ulrix/budgeter-web) (web app), nyx-ulrix/DateBudget (private, DateBudget site). Actions: `.github/workflows/release.yml`. Repository secrets: `BUDGETER_KEYSTORE_B64`, `BUDGETER_KEYSTORE_PASSWORD`, `FIREBASE_API_KEY`. |
| **Firebase project `budgeter-510810`** | Hosting for the website and web app; Firestore and Auth for the shared date budget | [Firebase console](https://console.firebase.google.com/project/budgeter-510810). Hosting sites: default → budgeter.liewjiaen.com (`docs/`), `budgeter-app` → app.budgeter.liewjiaen.com. Firestore `(default)` in asia-southeast1, rules from the DateBudget repo's `firestore.rules`. Auth: Google provider; authorized domains include both sites and datebudget.liewjiaen.com. Apps: "Budgeter" web + Android (package `com.nyxulrix.budgeter`, release SHA-1 registered). |
| **Google Cloud (same project)** | Google sign-in and API keys | [Credentials](https://console.cloud.google.com/apis/credentials?project=budgeter-510810). OAuth clients: "Budgeter Web" (origins app.budgeter.liewjiaen.com, datebudget.liewjiaen.com, localhost:8081) and the Android client for Sheets. API keys: "Browser key (auto created by Firebase)" for the web and DateBudget site, "Android key (auto created by Firebase)" for the phone, each restricted to its apps and to Auth/Firestore. Enabled APIs: Drive, Sheets, Firestore, Identity Toolkit, Firebase Rules, API Keys. No billing account. |
| **Google Drive / Sheets (each user's own)** | Each person's budget, synced between their phone and browser | The user's Drive: one spreadsheet named "Budgeter" (scope `drive.file`, so the apps only see files they made). |
| **Firebase project `datebudget`** | Hosting for the DateBudget site; the old date budget database, kept as a backup | [Firebase console](https://console.firebase.google.com/project/datebudget). Hosting → datebudget.liewjiaen.com. Its data now lives in `budgeter-510810`. |
| **Cloudflare** | DNS for liewjiaen.com | CNAME records (DNS only, not proxied) for budgeter, app.budgeter and datebudget pointing at Firebase Hosting. |
| **AI providers** (optional, user's own key) | Reading receipt photos; macros look up and fill | Gemini, OpenRouter, Anthropic, OpenAI, DeepSeek or Perplexity, set in Profile → AI. Keys are encrypted on the device (Android Keystore / browser WebCrypto) and never synced. |
| **open.er-api.com** | Daily exchange rates for spending abroad | Free, no key; only currency codes are sent. |
| **Developer machine** | Building and publishing | Android Studio (its JDK in `jbr/`), Android SDK, Node.js (web app and DateBudget site), Firebase CLI, GitHub CLI. Not in git: `keystore.properties` + the release key in `%USERPROFILE%\.android\`, `BudgeterWeb/.env.local` and `DateBudget/.env.local` (the Firebase web key). |

## Code map

[`graphify-out/graph.html`](graphify-out/graph.html) is an interactive map of the code (open it in a browser after cloning); [`graphify-out/GRAPH_REPORT.md`](graphify-out/GRAPH_REPORT.md) summarises its main parts. Regenerate it with `graphify update .` after code changes.

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

## Tests

```bash
./gradlew testDebugUnitTest
```

Windows needs Android Studio's Java first, for example in Git Bash: `export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`.

## Cheapest AI setup

Get a free Gemini key at https://aistudio.google.com/apikey and add it under Profile → AI receipt reading. Without any key the built-in offline reader is used.
