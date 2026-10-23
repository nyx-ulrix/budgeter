# Graph Report - .  (2026-10-23)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 681 nodes · 1651 edges · 36 communities (32 shown, 4 thin omitted)
- Extraction: 71% EXTRACTED · 29% INFERRED · 0% AMBIGUOUS · INFERRED: 481 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `98cbd993`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Small
- AppState
- Sheets
- Ledger.kt
- Widgets.kt
- MealDialog
- Ai
- DateMoney.kt
- DateClient.kt
- Screen
- Budgeter README
- Receipt Pipeline
- Art
- Food
- ReceiptText
- allocate
- Budgeter graphics list
- Daily budget replay with leftovers
- Turning on Google Sheets sync (setup guide)
- Budget.kt
- DateBudgetFold
- Date budget shared with a partner (Firestore)
- Updater
- Release GitHub Actions workflow
- draftFrom
- CoreTest
- Countries.kt
- .rows
- App
- Pace
- gradlew
- Rates
- UpdaterTest

## God Nodes (most connected - your core abstractions)
1. `AppState` - 62 edges
2. `Small()` - 37 edges
3. `PixelButton()` - 36 edges
4. `ExpenseScreen()` - 31 edges
5. `Body()` - 27 edges
6. `money()` - 25 edges
7. `Root()` - 25 edges
8. `Window()` - 23 edges
9. `HomeScreen()` - 23 edges
10. `Txn` - 22 edges

## Surprising Connections (you probably didn't know these)
- `Daily budget replay with leftovers` --semantically_similar_to--> `Today's budget day-by-day replay`  [INFERRED] [semantically similar]
  PLAN.md → README.md
- `Glance widgets` --semantically_similar_to--> `Home-screen widgets (spend, bars, macros)`  [INFERRED] [semantically similar]
  PLAN.md → README.md
- `Landing page feature list` --semantically_similar_to--> `Bill Splits and Group Balances`  [INFERRED] [semantically similar]
  docs/index.html → PLAN.md
- `Receipt Pipeline` --semantically_similar_to--> `Receipt scanning flow`  [INFERRED] [semantically similar]
  PLAN.md → README.md
- `Release key SHA-1 fingerprint` --conceptually_related_to--> `Release GitHub Actions workflow`  [INFERRED]
  docs/google-setup.md → .github/workflows/release.yml

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Release pipeline (tag, signed build, GitHub release, in-app update)** — github_workflows_release_workflow, github_workflows_release_signing_key_step, github_workflows_release_test_and_build, github_workflows_release_publish_step, readme_github_releases [EXTRACTED 0.95]
- **Phone and web share one Google Sheet via _sync records** — readme_budgeter_android, readme_budgeter_web, readme_user_google_sheet, readme_two_way_sync, plan_web_twin_two_way_sync [EXTRACTED 0.95]
- **Date budget stack (apps, Firebase Auth login, Firestore)** — readme_date_budget, readme_one_google_login, readme_firebase_project, readme_datebudget_site, plan_date_overspend [INFERRED 0.85]

## Communities (36 total, 4 thin omitted)

### Community 0 - "Small"
Cohesion: 0.09
Nodes (72): android, convert(), digits(), money(), parseMoney(), plain(), personLabel(), BudgetScreen() (+64 more)

### Community 1 - "AppState"
Cohesion: 0.06
Nodes (56): addLine(), addTrip(), boughtNow(), buyNow(), deleteMeal(), deletePlanned(), deletePreset(), deleteTrip() (+48 more)

### Community 2 - "Sheets"
Cohesion: 0.11
Nodes (18): Categories, mergeRemote(), recordObjects(), Remote, stampedSince(), toUpload(), withRecord(), enableSync() (+10 more)

### Community 3 - "Ledger.kt"
Cohesion: 0.10
Nodes (37): activeTrip(), available(), byCategory(), inBudget(), macrosOn(), mealsOn(), monthSaved(), monthsToSave() (+29 more)

### Community 4 - "Widgets.kt"
Cohesion: 0.10
Nodes (20): MainActivity, BudgeterTheme(), BarsReceiver, BarsWidget, Context, CoroutineWorker, Result, MacrosReceiver (+12 more)

### Community 5 - "MealDialog"
Cohesion: 0.13
Nodes (26): androidx, fmtMacro(), Macro, macroReport(), MacroStat, onTarget(), sumMacros(), Meal (+18 more)

### Community 6 - "Ai"
Cohesion: 0.16
Nodes (11): Ai, Dto, ItemDto, Context, Preset, Provider, Secrets, Context (+3 more)

### Community 7 - "DateMoney.kt"
Cohesion: 0.15
Nodes (21): advance(), budgetRows(), Contributions, creditDate(), currentStep(), Every, FORTNIGHT, MONTH (+13 more)

### Community 8 - "DateClient.kt"
Cohesion: 0.12
Nodes (29): api(), Budget, DateState, dec(), decFields(), detached(), enc(), fields() (+21 more)

### Community 9 - "Screen"
Cohesion: 0.07
Nodes (27): Ai, BottomBar(), Camera, Credits, EditSetup, Expense, com, moodOf() (+19 more)

### Community 10 - "Budgeter README"
Cohesion: 0.14
Nodes (19): Google user data and Limited Use policy, Data stays on the device (no server, no analytics), Budgeter Privacy Policy, Architecture (core, data, ui, ocr, ai, sync, widget), Milestones M0-M9, Budgeter Project Plan, Single JSON file storage (not Room), v1.1 changes (groups removed, separate budget, automatic savings) (+11 more)

### Community 11 - "Receipt Pipeline"
Cohesion: 0.18
Nodes (17): AI receipt and macros data sent to provider, AI provider access (one OpenAI-compatible client, presets), AI Provider Presets (OpenAI-compatible chat/completions), Gemini reasoning_effort low (v1.9.1), Expense Entry Modes, Keystore-Encrypted AI Keys, ML Kit On-Device Text Recognition, OpenRouter sign-in (OAuth PKCE) (+9 more)

### Community 12 - "Art"
Cohesion: 0.35
Nodes (6): Art, Context, WidgetArt, Bitmap, RectF, Typeface

### Community 13 - "Food"
Cohesion: 0.30
Nodes (5): Food, FoodHit, Context, JsonObject, Portion

### Community 14 - "ReceiptText"
Cohesion: 0.20
Nodes (6): ParsedReceipt, receiptDate(), ReceiptItem, ReceiptText, Seg, java

### Community 15 - "allocate"
Cohesion: 0.18
Nodes (10): allocate(), itemShares(), split(), SplitMethod, EQUAL, EXACT, ITEMS, PERCENT (+2 more)

### Community 16 - "Budgeter graphics list"
Cohesion: 0.19
Nodes (14): Art credits, CC0 1.0 licence, Ninja Adventure asset pack (Pixel-boy & AAA), 1-bit Pixel Icons pack (Nikoichu), Category icons (8 defaults), Drop-In Drawable Replacement, Drop-in drawable-nodpi asset replacement, Budgeter graphics list (+6 more)

### Community 17 - "Daily budget replay with leftovers"
Cohesion: 0.14
Nodes (19): Budget-Reactive Mascot, Widget Picker Previews, Landing page feature list, Currency from Country, Daily Budget, Daily budget replay with leftovers, Date budget overspend covering (v1.9.2), Glance widgets (+11 more)

### Community 18 - "Turning on Google Sheets sync (setup guide)"
Cohesion: 0.21
Nodes (12): Android OAuth Client (package + SHA-1), Turning on Google Sheets sync (setup guide), OAuth Consent Screen (External, Published), Release key SHA-1 fingerprint, Sheets Sync Troubleshooting, AuthorizationClient (Play Services), Budget Planner App PRD, drive.file OAuth Scope (+4 more)

### Community 19 - "Budget.kt"
Cohesion: 0.29
Nodes (6): Day, DaySpend, ofKey(), Period, periodOf(), today()

### Community 20 - "DateBudgetFold"
Cohesion: 0.33
Nodes (9): DateBudgetFold(), dateLine(), DateRefresh(), com, monthLeft(), monthly(), Section(), setMyShare() (+1 more)

### Community 21 - "Date budget shared with a partner (Firestore)"
Cohesion: 0.31
Nodes (9): Redirect to budgeter.liewjiaen.com, Budgeter landing page, Cloudflare DNS for liewjiaen.com, Date budget shared with a partner (Firestore), DateBudget site (React + Vite), Firebase project datebudget (backup), Firebase project budgeter-510810, Google Cloud project (OAuth clients, API keys) (+1 more)

### Community 22 - "Updater"
Cohesion: 0.42
Nodes (3): Context, Release, Updater

### Community 23 - "Release GitHub Actions workflow"
Cohesion: 0.36
Nodes (8): Publish GitHub release step, Repository secrets (keystore, keystore password, FIREBASE_API_KEY), Signing key step (refuses throwaway key), Test and build step (gradle testDebugUnitTest + assembleRelease), Release GitHub Actions workflow, GitHub Releases (signed APKs and update check), Release flow (Publish release.cmd, tag, Actions, Release), Release signing key must be backed up

### Community 24 - "draftFrom"
Cohesion: 0.48
Nodes (5): Draft, draftFrom(), ItemDraft, SteppedBar(), DateTx

### Community 26 - "Countries.kt"
Cohesion: 0.33
Nodes (3): Country, detectCountry(), Context

### Community 27 - ".rows"
Cohesion: 0.33
Nodes (4): Context, Ocr, Scanner, Uri

### Community 29 - "Pace"
Cohesion: 0.50
Nodes (4): Pace, ON_TRACK, OVER, SLIGHTLY_OVER

### Community 31 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **62 isolated node(s):** `Country`, `Budget Planner App PRD`, `ML Kit On-Device Text Recognition`, `OpenRouter OAuth PKCE Sign-In`, `Keystore-Encrypted AI Keys` (+57 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AppState` connect `AppState` to `Small`, `Sheets`, `Ledger.kt`, `MealDialog`, `Screen`, `Art`, `DateBudgetFold`, `draftFrom`?**
  _High betweenness centrality (0.226) - this node is a cross-community bridge._
- **Why does `ExpenseScreen()` connect `Small` to `AppState`, `MealDialog`, `DateMoney.kt`, `Screen`, `ReceiptText`, `allocate`, `draftFrom`?**
  _High betweenness centrality (0.131) - this node is a cross-community bridge._
- **Why does `money()` connect `Small` to `Ledger.kt`, `Widgets.kt`, `MealDialog`, `Art`, `DateBudgetFold`?**
  _High betweenness centrality (0.051) - this node is a cross-community bridge._
- **Are the 14 inferred relationships involving `AppState` (e.g. with `.foodExpensesWaitUntilLoggedAndMealsSync()` and `.boughtItemEditedBelowItsReservationFreesTheRest()`) actually correct?**
  _`AppState` has 14 INFERRED edges - model-reasoned connections that need verification._
- **Are the 34 inferred relationships involving `Small()` (e.g. with `BudgetScreen()` and `CameraScreen()`) actually correct?**
  _`Small()` has 34 INFERRED edges - model-reasoned connections that need verification._
- **Are the 30 inferred relationships involving `PixelButton()` (e.g. with `BudgetScreen()` and `CameraScreen()`) actually correct?**
  _`PixelButton()` has 30 INFERRED edges - model-reasoned connections that need verification._
- **Are the 23 inferred relationships involving `ExpenseScreen()` (e.g. with `overdrawn()` and `allocate()`) actually correct?**
  _`ExpenseScreen()` has 23 INFERRED edges - model-reasoned connections that need verification._