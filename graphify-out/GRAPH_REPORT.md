# Graph Report - Budgeter  (2026-11-01)

## Corpus Check
- 61 files · ~54,877 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 25 file(s) not represented in the graph (top: .xml 10, (none) 4, .ttf 4)

## Summary
- 997 nodes · 3039 edges · 43 communities (26 shown, 17 thin omitted)
- Extraction: 87% EXTRACTED · 13% INFERRED · 0% AMBIGUOUS · INFERRED: 394 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `5e570049`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Small
- AppState
- Sheets
- Ledger.kt
- Widgets.kt
- MacrosScreen.kt
- Ai
- DateMoney.kt
- DateClient.kt
- Theme.kt
- Budgeter README
- Receipt Pipeline
- BudgetScreen.kt
- plain
- env
- Budgeter graphics list
- Daily budget replay with leftovers
- Turning on Google Sheets sync (setup guide)
- HomeScreen.kt
- Date budget shared with a partner (Firestore)
- Ai.kt
- Release GitHub Actions workflow
- ExpenseScreen.kt
- Root.kt
- Theme.kt
- ProfileScreen.kt
- Store.kt
- OpenRouterLogin.kt
- Budgeter Web (React Native for Web)
- Updater.kt
- Receipt scanning flow
- Kind
- Widget Picker Previews
- Context
- Records.kt
- SyncWorker

## God Nodes (most connected - your core abstractions)
1. `AppState` - 92 edges
2. `Small()` - 39 edges
3. `PixelButton()` - 38 edges
4. `money()` - 37 edges
5. `Txn` - 32 edges
6. `ExpenseScreen()` - 32 edges
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

## Communities (43 total, 17 thin omitted)

### Community 0 - "Small"
Cohesion: 0.08
Nodes (65): money(), Country, countryName(), currencyOf(), detectCountry(), personLabel(), CameraScreen(), ReceiptGuides() (+57 more)

### Community 1 - "AppState"
Cohesion: 0.06
Nodes (61): addLine(), addTrip(), boughtNow(), buyNow(), deleteMeal(), deletePlanned(), deletePreset(), deleteTrip() (+53 more)

### Community 3 - "Ledger.kt"
Cohesion: 0.08
Nodes (42): Day, DaySpend, Pace, ON_TRACK, OVER, SLIGHTLY_OVER, Period, periodOf() (+34 more)

### Community 4 - "Widgets.kt"
Cohesion: 0.12
Nodes (7): BarsReceiver, BarsWidget, MacrosReceiver, MacrosWidget, MidnightWorker, SpendReceiver, SpendWidget

### Community 5 - "MacrosScreen.kt"
Cohesion: 0.10
Nodes (30): Food, FoodHit, Portion, fmtMacro(), Macro, macroReport(), MacroStat, onTarget() (+22 more)

### Community 6 - "Ai"
Cohesion: 0.20
Nodes (5): Ai, Dto, ItemDto, Provider, Secrets

### Community 7 - "DateMoney.kt"
Cohesion: 0.11
Nodes (29): advance(), budgetRows(), Contributions, creditDate(), currentStep(), Every, FORTNIGHT, MONTH (+21 more)

### Community 8 - "DateClient.kt"
Cohesion: 0.15
Nodes (19): api(), Budget, DateState, dec(), decFields(), detached(), enc(), fields() (+11 more)

### Community 9 - "Theme.kt"
Cohesion: 0.09
Nodes (10): MainActivity, Tab, BUDGET, HOME, MACROS, PROFILE, TXNS, Release (+2 more)

### Community 10 - "Budgeter README"
Cohesion: 0.22
Nodes (8): Data stays on the device (no server, no analytics), Architecture (core, data, ui, ocr, ai, sync, widget), Milestones M0-M9, Budgeter Project Plan, v1.1 changes (groups removed, separate budget, automatic savings), Budgeter README, Code map (graphify-out), Install via USB Debugging (installDebug)

### Community 11 - "Receipt Pipeline"
Cohesion: 0.27
Nodes (8): AI Provider Presets (OpenAI-compatible chat/completions), Expense Entry Modes, Keystore-Encrypted AI Keys, ML Kit On-Device Text Recognition, OpenRouter OAuth PKCE Sign-In, ParsedReceipt Model, Receipt Pipeline, Rule-Based Receipt Parser (ReceiptText.kt)

### Community 12 - "BudgetScreen.kt"
Cohesion: 0.14
Nodes (5): convert(), digits(), parseMoney(), plain(), tripSpent()

### Community 14 - "plain"
Cohesion: 0.06
Nodes (26): ParsedReceipt, receiptDate(), ReceiptItem, ReceiptText, Seg, itemShares(), split(), SplitMethod (+18 more)

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

### Community 21 - "Date budget shared with a partner (Firestore)"
Cohesion: 0.23
Nodes (13): Redirect to budgeter.liewjiaen.com, Budgeter landing page, Budgeter Privacy Policy, Publish GitHub release step, Budgeter Android app (Kotlin, Compose), Cloudflare DNS for liewjiaen.com, Date budget shared with a partner (Firestore), DateBudget site (React + Vite) (+5 more)

### Community 23 - "Release GitHub Actions workflow"
Cohesion: 0.47
Nodes (4): Repository secrets (keystore, keystore password, FIREBASE_API_KEY), Test and build step (gradle testDebugUnitTest + assembleRelease), Release GitHub Actions workflow, Release flow (Publish release.cmd, tag, Actions, Release)

### Community 24 - "ExpenseScreen.kt"
Cohesion: 0.13
Nodes (7): activeTrip(), Phase, Loading, NoBudget, Ready, SignedOut, TxDraft

### Community 25 - "Root.kt"
Cohesion: 0.11
Nodes (3): App, artId(), moodOf()

### Community 26 - "Theme.kt"
Cohesion: 0.09
Nodes (8): macroTarget(), BudgeterTheme(), Glyph(), Glyphs, Px, Type, Art, WidgetArt

### Community 30 - "Budgeter Web (React Native for Web)"
Cohesion: 0.24
Nodes (9): Google user data and Limited Use policy, Budget Planner App PRD, Google Sheets sync, Two-way sync with web app via _sync tab (Records.kt), Budgeter Web (React Native for Web), Layered architecture (UI, Store, core, data, sync, date, ai, widget), Today's budget day-by-day replay, Two-way sync phone and web (newest record wins) (+1 more)

### Community 33 - "Receipt scanning flow"
Cohesion: 0.32
Nodes (7): AI receipt and macros data sent to provider, AI provider access (one OpenAI-compatible client, presets), OpenRouter sign-in (OAuth PKCE), AI providers (user's own key), Cheapest AI Setup (Free Gemini Key), Macros (meals, presets, AI look up), Receipt scanning flow

### Community 37 - "Kind"
Cohesion: 0.50
Nodes (4): Kind, DANGER, PRIMARY, SECONDARY

### Community 41 - "Records.kt"
Cohesion: 0.36
Nodes (7): Categories, mergeRemote(), recordObjects(), Remote, stampedSince(), toUpload(), withRecord()

## Knowledge Gaps
- **45 isolated node(s):** `ON_TRACK`, `SLIGHTLY_OVER`, `OVER`, `WEEK`, `FORTNIGHT` (+40 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 236 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **17 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppState` connect `AppState` to `Small`, `Updater.kt`, `Sheets`, `Ledger.kt`, `MacrosScreen.kt`, `Sheets.kt`, `DateMoney.kt`, `Records.kt`, `BudgetScreen.kt`, `plain`, `HomeScreen.kt`, `DateScreen.kt`, `ExpenseScreen.kt`, `Root.kt`, `Theme.kt`, `ProfileScreen.kt`, `Store.kt`?**
  _High betweenness centrality (0.121) - this node is a cross-community bridge._
- **Are the 14 inferred relationships involving `AppState` (e.g. with `.bankBalanceIsTheEnteredBalancePlusWhatMovedAfterItsDay()` and `.boughtItemEditedBelowItsReservationFreesTheRest()`) actually correct?**
  _`AppState` has 14 INFERRED edges - model-reasoned connections that need verification._
- **What connects `ON_TRACK`, `SLIGHTLY_OVER`, `OVER` to the rest of the system?**
  _45 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Small` be split into smaller, more focused modules?**
  _Cohesion score 0.082896379525593 - nodes in this community are weakly interconnected._
- **Why does `money()` connect `Small` to `Ledger.kt`, `Widgets.kt`, `MacrosScreen.kt`, `DateMoney.kt`, `Context`, `BudgetScreen.kt`, `plain`, `HomeScreen.kt`, `DateScreen.kt`, `ExpenseScreen.kt`, `Root.kt`, `Theme.kt`?**
  _High betweenness centrality (0.025) - this node is a cross-community bridge._
- **Are the 35 inferred relationships involving `Small()` (e.g. with `CameraScreen()` and `CompactScreen()`) actually correct?**
  _`Small()` has 35 INFERRED edges - model-reasoned connections that need verification._
- **Should `AppState` be split into smaller, more focused modules?**
  _Cohesion score 0.06227106227106227 - nodes in this community are weakly interconnected._