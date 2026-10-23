# Graph Report - Budgeter  (2026-10-23)

## Corpus Check
- 59 files · ~104,122 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 632 nodes · 1566 edges · 25 communities (22 shown, 3 thin omitted)
- Extraction: 70% EXTRACTED · 30% INFERRED · 0% AMBIGUOUS · INFERRED: 469 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `d94f354c`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Small
- AI Provider Presets (OpenAI-compatible chat/completions)
- AppState
- CoreTest
- MealDialog
- Ai
- Ledger.kt
- Screen
- Sheets
- App
- DateClient.kt
- .rows
- DateMoney.kt
- Kind
- gradlew
- Rates
- Art
- UpdaterTest
- Widgets.kt
- Budget.kt
- Food
- DateBudgetFold

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
- `Budgeter Project Plan` --references--> `Ninja Adventure Asset Pack (Pixel-boy & AAA)`  [AMBIGUOUS]
  PLAN.md → CREDITS.md
- `Cheapest AI Setup (Free Gemini Key)` --references--> `AI Provider Presets (OpenAI-compatible chat/completions)`  [INFERRED]
  README.md → PLAN.md
- `MealDialog()` --calls--> `newId()`  [INFERRED]
  app/src/main/java/com/nyxulrix/budgeter/ui/MacrosScreen.kt → app/src/main/java/com/nyxulrix/budgeter/data/State.kt
- `Ten-Colour Pixel Palette` --implements--> `Nostelika Pixel-Art Brief`  [INFERRED]
  docs/GRAPHICS.md → PLAN.md
- `Cheapest AI Setup (Free Gemini Key)` --references--> `Rule-Based Receipt Parser (ReceiptText.kt)`  [INFERRED]
  README.md → PLAN.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Receipt scan to saved transaction flow** — plan_ml_kit_text_recognition, plan_ai_provider_presets, plan_rule_based_receipt_parser, plan_parsed_receipt, plan_receipt_allocation [EXTRACTED 1.00]
- **Budget math core rules** — plan_spendable_formula, plan_daily_budget, plan_pacing, plan_my_share_counts_as_spent, plan_money_minor_units [EXTRACTED 1.00]
- **Google Sheets sync auth setup** — plan_drive_file_scope, plan_authorization_client, docs_google_setup_oauth_consent_screen, docs_google_setup_android_oauth_client [INFERRED 0.85]

## Communities (25 total, 3 thin omitted)

### Community 0 - "Small"
Cohesion: 0.09
Nodes (77): android, androidx, money(), parseMoney(), Country, countryName(), currencyOf(), detectCountry() (+69 more)

### Community 1 - "AI Provider Presets (OpenAI-compatible chat/completions)"
Cohesion: 0.05
Nodes (49): 1-bit Pixel Icons (Nikoichu), Art Credits, CC0 1.0 Licence, Ninja Adventure Asset Pack (Pixel-boy & AAA), Android OAuth Client (package + SHA-1), OAuth Consent Screen (External, Published), Turning on Google Sheets Sync, Sheets Sync Troubleshooting (+41 more)

### Community 2 - "AppState"
Cohesion: 0.06
Nodes (56): addLine(), addTrip(), boughtNow(), buyNow(), deleteMeal(), deletePlanned(), deletePreset(), deleteTrip() (+48 more)

### Community 3 - "CoreTest"
Cohesion: 0.06
Nodes (25): allocate(), convert(), digits(), plain(), ParsedReceipt, receiptDate(), ReceiptItem, ReceiptText (+17 more)

### Community 4 - "MealDialog"
Cohesion: 0.15
Nodes (19): fmtMacro(), Macro, macroReport(), MacroStat, onTarget(), sumMacros(), Meal, AddFoodButton() (+11 more)

### Community 5 - "Ai"
Cohesion: 0.21
Nodes (8): Ai, Dto, ItemDto, Context, Preset, Provider, Secrets, SecretKey

### Community 6 - "Ledger.kt"
Cohesion: 0.10
Nodes (37): activeTrip(), available(), byCategory(), inBudget(), macrosOn(), mealsOn(), monthSaved(), monthsToSave() (+29 more)

### Community 7 - "Screen"
Cohesion: 0.07
Nodes (27): Ai, BottomBar(), Camera, Credits, EditSetup, Expense, com, moodOf() (+19 more)

### Community 8 - "Sheets"
Cohesion: 0.11
Nodes (18): Categories, mergeRemote(), recordObjects(), Remote, stampedSince(), toUpload(), withRecord(), enableSync() (+10 more)

### Community 10 - "DateClient.kt"
Cohesion: 0.12
Nodes (29): api(), Budget, DateState, dec(), decFields(), detached(), enc(), fields() (+21 more)

### Community 11 - ".rows"
Cohesion: 0.33
Nodes (4): Context, Ocr, Scanner, Uri

### Community 12 - "DateMoney.kt"
Cohesion: 0.15
Nodes (21): advance(), budgetRows(), Contributions, creditDate(), currentStep(), Every, FORTNIGHT, MONTH (+13 more)

### Community 13 - "Kind"
Cohesion: 0.50
Nodes (4): Kind, DANGER, PRIMARY, SECONDARY

### Community 14 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 19 - "Art"
Cohesion: 0.35
Nodes (6): Art, Context, WidgetArt, Bitmap, RectF, Typeface

### Community 21 - "Widgets.kt"
Cohesion: 0.07
Nodes (26): Context, start(), MainActivity, BudgeterTheme(), Context, Release, Updater, BarsReceiver (+18 more)

### Community 22 - "Budget.kt"
Cohesion: 0.19
Nodes (10): Day, DaySpend, ofKey(), Pace, ON_TRACK, OVER, SLIGHTLY_OVER, Period (+2 more)

### Community 23 - "Food"
Cohesion: 0.30
Nodes (5): Food, FoodHit, Context, JsonObject, Portion

### Community 25 - "DateBudgetFold"
Cohesion: 0.33
Nodes (9): DateBudgetFold(), dateLine(), DateRefresh(), com, monthLeft(), monthly(), Section(), setMyShare() (+1 more)

## Ambiguous Edges - Review These
- `Ninja Adventure Asset Pack (Pixel-boy & AAA)` → `Budgeter Project Plan`  [AMBIGUOUS]
  PLAN.md · relation: references

## Knowledge Gaps
- **60 isolated node(s):** `Preset`, `Dto`, `ItemDto`, `ON_TRACK`, `SLIGHTLY_OVER` (+55 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `Ninja Adventure Asset Pack (Pixel-boy & AAA)` and `Budgeter Project Plan`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **Why does `AppState` connect `AppState` to `Small`, `CoreTest`, `MealDialog`, `Ledger.kt`, `Screen`, `Sheets`, `Art`, `DateBudgetFold`?**
  _High betweenness centrality (0.267) - this node is a cross-community bridge._
- **Why does `ExpenseScreen()` connect `Small` to `AppState`, `CoreTest`, `MealDialog`, `Screen`, `DateMoney.kt`?**
  _High betweenness centrality (0.154) - this node is a cross-community bridge._
- **Why does `money()` connect `Small` to `CoreTest`, `Ledger.kt`, `Art`, `Widgets.kt`, `DateBudgetFold`?**
  _High betweenness centrality (0.060) - this node is a cross-community bridge._
- **Are the 14 inferred relationships involving `AppState` (e.g. with `.foodExpensesWaitUntilLoggedAndMealsSync()` and `.boughtItemEditedBelowItsReservationFreesTheRest()`) actually correct?**
  _`AppState` has 14 INFERRED edges - model-reasoned connections that need verification._
- **Are the 34 inferred relationships involving `Small()` (e.g. with `BudgetScreen()` and `CameraScreen()`) actually correct?**
  _`Small()` has 34 INFERRED edges - model-reasoned connections that need verification._
- **Are the 30 inferred relationships involving `PixelButton()` (e.g. with `BudgetScreen()` and `CameraScreen()`) actually correct?**
  _`PixelButton()` has 30 INFERRED edges - model-reasoned connections that need verification._