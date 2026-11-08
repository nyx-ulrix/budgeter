# Graph Report - Budgeter  (2026-11-08)

## Corpus Check
- 61 files · ~57,453 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 25 file(s) not represented in the graph (top: .xml 10, (none) 4, .ttf 4)

## Summary
- 1013 nodes · 3107 edges · 44 communities (26 shown, 18 thin omitted)
- Extraction: 87% EXTRACTED · 13% INFERRED · 0% AMBIGUOUS · INFERRED: 406 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `47e45689`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Small
- AppState
- Sheets
- BudgetScreen.kt
- Widgets.kt
- MacrosScreen.kt
- Ai
- DateClient.kt
- MainActivity.kt
- Budgeter README
- Receipt Pipeline
- TxnsScreen.kt
- plain
- env
- Budgeter graphics list
- Daily budget replay with leftovers
- Turning on Google Sheets sync (setup guide)
- DateScreen.kt
- Date budget shared with a partner (Firestore)
- ProfileScreen.kt
- Release GitHub Actions workflow
- Updater
- Root.kt
- Theme.kt
- Scanner.kt
- OpenRouterLogin.kt
- Budgeter Web (React Native for Web)
- Ai.kt
- Receipt scanning flow
- Kind
- Widget Picker Previews
- Sheets.kt
- currency
- Tab
- Phase
- UpdaterTest.kt

## God Nodes (most connected - your core abstractions)
1. `AppState` - 96 edges
2. `Small()` - 39 edges
3. `PixelButton()` - 38 edges
4. `money()` - 37 edges
5. `Txn` - 35 edges
6. `ExpenseScreen()` - 33 edges
7. `Body()` - 28 edges
8. `Root()` - 26 edges
9. `Snapshot` - 25 edges
10. `HomeScreen()` - 25 edges

## Surprising Connections (you probably didn't know these)
- `Signing key step (refuses throwaway key)` --semantically_similar_to--> `Release signing key must be backed up`  [INFERRED] [semantically similar]
  .github/workflows/release.yml → README.md
- `Release key SHA-1 fingerprint` --conceptually_related_to--> `Release GitHub Actions workflow`  [INFERRED]
  docs/google-setup.md → .github/workflows/release.yml
- `Publish GitHub release step` --shares_data_with--> `GitHub Releases (signed APKs and update check)`  [INFERRED]
  .github/workflows/release.yml → README.md
- `Landing page feature list` --semantically_similar_to--> `Bill Splits and Group Balances`  [INFERRED] [semantically similar]
  docs/index.html → PLAN.md
- `Data stays on the device (no server, no analytics)` --semantically_similar_to--> `Single JSON file storage (not Room)`  [INFERRED] [semantically similar]
  docs/privacy.html → PLAN.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Phone and web share one Google Sheet via _sync records** — readme_budgeter_android, readme_budgeter_web, readme_user_google_sheet, readme_two_way_sync, plan_web_twin_two_way_sync [EXTRACTED 0.95]
- **Release pipeline (tag, signed build, GitHub release, in-app update)** — github_workflows_release_workflow, github_workflows_release_signing_key_step, github_workflows_release_test_and_build, github_workflows_release_publish_step, readme_github_releases [EXTRACTED 0.95]
- **Date budget stack (apps, Firebase Auth login, Firestore)** — readme_date_budget, readme_one_google_login, readme_firebase_project, readme_datebudget_site, plan_date_overspend [INFERRED 0.85]

## Communities (44 total, 18 thin omitted)

### Community 0 - "Small"
Cohesion: 0.09
Nodes (66): convert(), digits(), money(), parseMoney(), plain(), countryName(), personLabel(), CameraScreen() (+58 more)

### Community 1 - "AppState"
Cohesion: 0.07
Nodes (62): addLine(), addTrip(), boughtNow(), buyNow(), chargeOverspend(), deleteMeal(), deletePlanned(), deletePreset() (+54 more)

### Community 3 - "BudgetScreen.kt"
Cohesion: 0.08
Nodes (44): Day, DaySpend, Pace, ON_TRACK, OVER, SLIGHTLY_OVER, Period, periodOf() (+36 more)

### Community 4 - "Widgets.kt"
Cohesion: 0.07
Nodes (13): macroTarget(), Glyphs, Art, WidgetArt, BarsReceiver, BarsWidget, MacrosReceiver, MacrosWidget (+5 more)

### Community 5 - "MacrosScreen.kt"
Cohesion: 0.07
Nodes (40): Food, FoodHit, Portion, fmtMacro(), Macro, macroReport(), MacroStat, onTarget() (+32 more)

### Community 6 - "Ai"
Cohesion: 0.19
Nodes (5): Ai, Dto, ItemDto, Provider, Secrets

### Community 8 - "DateClient.kt"
Cohesion: 0.14
Nodes (20): api(), Budget, DateState, dec(), decFields(), detached(), enc(), fields() (+12 more)

### Community 10 - "Budgeter README"
Cohesion: 0.22
Nodes (8): Data stays on the device (no server, no analytics), Architecture (core, data, ui, ocr, ai, sync, widget), Milestones M0-M9, Budgeter Project Plan, v1.1 changes (groups removed, separate budget, automatic savings), Budgeter README, Code map (graphify-out), Install via USB Debugging (installDebug)

### Community 11 - "Receipt Pipeline"
Cohesion: 0.27
Nodes (8): AI Provider Presets (OpenAI-compatible chat/completions), Expense Entry Modes, Keystore-Encrypted AI Keys, ML Kit On-Device Text Recognition, OpenRouter OAuth PKCE Sign-In, ParsedReceipt Model, Receipt Pipeline, Rule-Based Receipt Parser (ReceiptText.kt)

### Community 12 - "TxnsScreen.kt"
Cohesion: 0.15
Nodes (3): App, activeTrip(), tripSpent()

### Community 14 - "plain"
Cohesion: 0.05
Nodes (29): allocate(), ParsedReceipt, receiptDate(), ReceiptItem, ReceiptText, Seg, itemShares(), split() (+21 more)

### Community 15 - "env"
Cohesion: 0.08
Nodes (24): BUDGETER_KEY_ALIAS, BUDGETER_KEY_PASSWORD, BUDGETER_KEYSTORE, BUDGETER_KEYSTORE_PASSWORD, FIREBASE_API_KEY, GH_TOKEN, KEYSTORE_B64, jobs (+16 more)

### Community 16 - "Budgeter graphics list"
Cohesion: 0.23
Nodes (10): Art credits, CC0 1.0 licence, Ninja Adventure asset pack (Pixel-boy & AAA), 1-bit Pixel Icons pack (Nikoichu), Category icons (8 defaults), Budgeter graphics list, Nostelika colour palette, BudgeterTheme (Nostelika in Compose) (+2 more)

### Community 17 - "Daily budget replay with leftovers"
Cohesion: 0.14
Nodes (15): Budget-Reactive Mascot, Landing page feature list, Currency from Country, Daily Budget, Daily budget replay with leftovers, Date budget overspend covering (v1.9.2), Flip-Phone Cover Screen Concise View, Glance widgets (+7 more)

### Community 18 - "Turning on Google Sheets sync (setup guide)"
Cohesion: 0.22
Nodes (9): Android OAuth Client (package + SHA-1), Turning on Google Sheets sync (setup guide), OAuth Consent Screen (External, Published), Release key SHA-1 fingerprint, Sheets Sync Troubleshooting, AuthorizationClient (Play Services), Google Sheets Sync (Export Only), WorkManager Sync Job (+1 more)

### Community 20 - "DateScreen.kt"
Cohesion: 0.07
Nodes (31): advance(), budgetRows(), Contributions, creditDate(), currentStep(), Every, FORTNIGHT, MONTH (+23 more)

### Community 21 - "Date budget shared with a partner (Firestore)"
Cohesion: 0.23
Nodes (13): Redirect to budgeter.liewjiaen.com, Budgeter landing page, Budgeter Privacy Policy, Publish GitHub release step, Budgeter Android app (Kotlin, Compose), Cloudflare DNS for liewjiaen.com, Date budget shared with a partner (Firestore), DateBudget site (React + Vite) (+5 more)

### Community 23 - "Release GitHub Actions workflow"
Cohesion: 0.47
Nodes (4): Repository secrets (keystore, keystore password, FIREBASE_API_KEY), Test and build step (gradle testDebugUnitTest + assembleRelease), Release GitHub Actions workflow, Release flow (Publish release.cmd, tag, Actions, Release)

### Community 24 - "Updater"
Cohesion: 0.36
Nodes (3): UpdateBanner(), Release, Updater

### Community 26 - "Theme.kt"
Cohesion: 0.13
Nodes (4): BudgeterTheme(), Glyph(), Px, Type

### Community 30 - "Budgeter Web (React Native for Web)"
Cohesion: 0.24
Nodes (9): Google user data and Limited Use policy, Budget Planner App PRD, Google Sheets sync, Two-way sync with web app via _sync tab (Records.kt), Budgeter Web (React Native for Web), Layered architecture (UI, Store, core, data, sync, date, ai, widget), Today's budget day-by-day replay, Two-way sync phone and web (newest record wins) (+1 more)

### Community 33 - "Receipt scanning flow"
Cohesion: 0.32
Nodes (7): AI receipt and macros data sent to provider, AI provider access (one OpenAI-compatible client, presets), OpenRouter sign-in (OAuth PKCE), AI providers (user's own key), Cheapest AI Setup (Free Gemini Key), Macros (meals, presets, AI look up), Receipt scanning flow

### Community 37 - "Kind"
Cohesion: 0.50
Nodes (4): Kind, DANGER, PRIMARY, SECONDARY

### Community 40 - "currency"
Cohesion: 0.29
Nodes (3): Country, currencyOf(), detectCountry()

### Community 41 - "Tab"
Cohesion: 0.29
Nodes (6): Tab, BUDGET, HOME, MACROS, PROFILE, TXNS

### Community 42 - "Phase"
Cohesion: 0.40
Nodes (5): Phase, Loading, NoBudget, Ready, SignedOut

## Knowledge Gaps
- **45 isolated node(s):** `ON_TRACK`, `SLIGHTLY_OVER`, `OVER`, `WEEK`, `FORTNIGHT` (+40 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 238 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **18 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppState` connect `AppState` to `Small`, `Sheets`, `BudgetScreen.kt`, `Widgets.kt`, `MacrosScreen.kt`, `Sheets.kt`, `TxnsScreen.kt`, `plain`, `MonthBar.kt`, `DateScreen.kt`, `ProfileScreen.kt`, `Root.kt`, `Scanner.kt`?**
  _High betweenness centrality (0.118) - this node is a cross-community bridge._
- **Are the 16 inferred relationships involving `AppState` (e.g. with `.aDateExpenseHasAtMostOneOverspendChargeReplacedOnEverySave()` and `.bankBalanceCountsTheDateExpensesIPaid()`) actually correct?**
  _`AppState` has 16 INFERRED edges - model-reasoned connections that need verification._
- **What connects `ON_TRACK`, `SLIGHTLY_OVER`, `OVER` to the rest of the system?**
  _45 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Small` be split into smaller, more focused modules?**
  _Cohesion score 0.08835546475995915 - nodes in this community are weakly interconnected._
- **Why does `App` connect `TxnsScreen.kt` to `AppState`, `BudgetScreen.kt`, `Widgets.kt`, `MacrosScreen.kt`, `Sheets.kt`, `DateClient.kt`, `App`, `DateScreen.kt`, `ProfileScreen.kt`, `Root.kt`?**
  _High betweenness centrality (0.029) - this node is a cross-community bridge._
- **Are the 35 inferred relationships involving `Small()` (e.g. with `CameraScreen()` and `CompactScreen()`) actually correct?**
  _`Small()` has 35 INFERRED edges - model-reasoned connections that need verification._
- **Should `AppState` be split into smaller, more focused modules?**
  _Cohesion score 0.066167290886392 - nodes in this community are weakly interconnected._