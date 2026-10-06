# Budgeter: Project Plan

Android budget planner from `Budget_Planner_App_PRD.md`, skinned with the Nostelika pixel-art brief.
This file records every decision that differs from or adds to those two briefs. Where it is silent, the briefs apply.

Status: building, started 2026-10-06.

---

## 1. Decisions

| Topic | Decision | Source |
|---|---|---|
| Stack | Kotlin + Jetpack Compose, single `:app` module, no backend | User |
| Scope | Everything in the PRD, plus trips and AI receipt parsing | User |
| Test device | Physical phone over USB | User |
| Repo | Private GitHub repo `budgeter` | User |
| OCR | On-device ML Kit Text Recognition (bundled model, works offline) | User |
| AI | Never sees images. Only receives raw OCR text and returns structured JSON | User |
| AI providers | Claude, Gemini, OpenAI, Perplexity, DeepSeek, OpenRouter, custom. Several stored at once; user picks the active one | User |
| AI login | No desktop relay. Direct from the phone: OpenRouter sign-in (OAuth) plus per-provider API keys. See section 6 | User asked for cheapest without a desktop |
| AI usage | Receipt-text parsing only. Nothing else calls an AI | User |
| Charges | Never invent tax or service charge. Only use what is printed on the receipt | User |
| Per-item cost | Receipt parsing splits the bill into items and computes each item's cost including its share of printed tax and service charge | User |
| Manual entry | User can also key in total, tax and service charge directly as one expense, no items needed | User |
| Currency | From the user's country. Prefilled from the phone, user can type a different country | User |
| Trips | Pre-funded. Trip spending draws from its own fund and is excluded from the monthly and daily budget | User |
| Art | Royalty-free pixel art as placeholders. User supplies final art later | User |
| Google OAuth | Later. User creates the Cloud project at the Sheets milestone; app is built with sync ready but switched off | User |
| Spent = my share | Only my share of a split bill counts against my budget. **Assumed, please confirm** | Default |

---

## 2. Architecture

```
app/src/main/java/com/nyxulrix/budgeter/
  core/      Pure Kotlin, no Android imports, unit-tested
             Money.kt       minor-unit Long math, largest-remainder allocation
             Budget.kt      period dates, spendable, daily budget, pacing
             Split.kt       equal / exact / percent / shares, balances, who-owes-whom
             Receipt.kt     ParsedReceipt model, validation, per-item allocation
             ReceiptText.kt rule-based fallback parser for raw OCR text
  data/      @Serializable state + Store (one JSON file)
  ui/        theme/, components/, screens/
  ocr/       CameraX capture + ML Kit, screenshot import
  ai/        provider presets, encrypted credentials, one chat-completions call
  sync/      Google sign-in, Sheets REST client, WorkManager job
  widget/    Glance widgets
```

- **Storage is one JSON file, not Room.** A personal budget is a few thousand records a year. The whole state lives in memory as a `StateFlow` and is rewritten atomically (`AtomicFile`) on each change. No DAOs, no migrations (new fields get defaults), no annotation processor. Ceiling: move to Room if one file passes roughly 10k transactions.
- **Money is always `Long` minor units.** Decimal places come from `java.util.Currency.defaultFractionDigits`, so JPY has none.
- **No DI framework.** One `App` object holds the store.
- **No HTTP library.** `HttpURLConnection` plus kotlinx.serialization cover Sheets and AI REST calls.
- **Secrets** (AI keys) are encrypted with an Android Keystore AES key and stored in SharedPreferences.
- **Toolchain:** Gradle 8.14.3, Android Gradle Plugin 8.13, Kotlin 2.2.20, compile/target SDK 36, min SDK 26. These are already cached on this PC.

---

## 3. Core rules (the math)

### 3.1 Budget period
- Period starts on the user's chosen day (1 to 28). A "month" is that period, named by its start month, e.g. `2026-10`.
- `Spendable = Income + Σ Extra − Σ Fixed − Σ Savings − Σ Trip funds set aside this period − Σ Reserved planned items`
- Bought planned items become ordinary transactions and stop being reserved.

### 3.2 What counts as "spent"
- **My share** of each non-trip transaction counts against the period. If I pay $100 for four people, $25 is spent and $75 is owed to me.
- This tracks consumption, not cash leaving the account. Flagged for confirmation.

### 3.3 Daily budget
- `Today's budget = (Spendable − spent before today) / days left in period including today`
- Fixed for the whole day. Overspend today lowers tomorrow's automatically.
- `Remaining today = Today's budget − spent today`

### 3.4 Pacing
- `Expected by now = Spendable × days elapsed including today / days in period`
- On track if spent ≤ expected. Slightly over if ≤ 110 % of expected. Over beyond that.
- Status always shows as text plus colour, never colour alone.

### 3.5 Receipt allocation
The AI or the fallback parser only extracts what is printed. All arithmetic happens in `Receipt.kt`.

Parsed fields: merchant, date, currency, items (name, qty, line price), discount, subtotal, service charge, tax, `taxIncludedInPrices`, total.

1. Charges to spread = printed service charge + printed tax (tax only if `taxIncludedInPrices` is false) − printed bill-level discount.
2. Each item's inclusive cost = line price + its proportional share of those charges.
3. Rounding uses largest remainder, so item costs always sum exactly to the computed total.
4. If items + charges ≠ printed total, the receipt is flagged and the user corrects it before saving. Nothing is guessed.
5. No percentage is ever applied that the receipt did not print.

### 3.6 Splits
- Methods: equal, exact amounts, percentage, shares/weights, and itemised (receipt items assigned to people).
- All methods produce exact minor-unit shares via largest remainder.
- Settle-ups are separate records. Group balances are net totals, simplified into "A pays B" transfers.

---

## 4. Features beyond the PRD

### 4.1 Currency from country
- First run prefills the country from the SIM or network (`TelephonyManager`), falling back to the phone locale. No location permission.
- User can pick a different country from the full ISO list.
- Currency = `Currency.getInstance(Locale("", country))`, editable.

### 4.2 Trips
- A trip has name, destination country, dates, fund amount, and an optional group for shared costs.
- Trip currency comes from the destination country the same way.
- Creating a trip sets its fund aside in the current period, like a savings line. After that, trip spending only touches the trip fund.
- Each trip expense stores the foreign amount and an exchange rate. The rate is fetched from a free no-key API (open.er-api.com) and is always editable. Only currency codes are sent.
- While a trip is active, quick-add defaults to that trip and its currency, with a switch back to normal spending.
- Trip view: fund, spent, remaining, per-day-left. Overspending shows as "over"; a top-up is another set-aside.

### 4.3 Expense entry modes
- **Quick:** amount, category, done.
- **Full manual:** total, optional tax and service charge, merchant, date, notes, payer, split, trip.
- **Itemised manual:** type items yourself; same allocation as receipts.
- **Scan / screenshot:** OCR, then parse, then the same itemised editor for review.

---

## 5. Receipt pipeline

```
Camera (CameraX) or screenshot picker
  → ML Kit Text Recognition, on device
  → raw text
  → active AI provider  OR  rule-based parser (no provider / call failed)
  → ParsedReceipt JSON, validated in Kotlin
  → itemised editor: fix anything, assign items to people or categories
  → Receipt.kt allocates tax and service charge per item
  → save transaction
```

Images never leave the phone.

---

## 6. AI provider access

The ReadyTalent app's account login runs Claude Code and Gemini CLI on a laptop. A phone can't, and a laptop relay needs a desktop running. So the app calls providers directly.

Every supported provider exposes an OpenAI-compatible `chat/completions` endpoint, so there is one client and a table of presets:

| Preset | How the user connects | Cost for receipt parsing |
|---|---|---|
| Gemini | Free API key from aistudio.google.com/apikey | Free tier |
| OpenRouter | **Sign in** button (OAuth PKCE in a browser tab). One login reaches Claude, Gemini, GPT, DeepSeek, Perplexity and free models | Pay per use, or free models |
| Claude | Anthropic API key | Pay per use |
| OpenAI | API key | Pay per use |
| DeepSeek | API key | Pay per use, very cheap |
| Perplexity | API key | Pay per use |
| Custom | Base URL + key (Ollama, LM Studio, any proxy) | Your own |

- Several can be saved at once. One is marked active; the receipt screen also lets you pick per scan.
- The rule-based parser always works with no provider at all.

---

## 7. Google Sheets sync

- **Scope: `drive.file` instead of the PRD's `spreadsheets`.** The app creates its own spreadsheet and edits only that. `drive.file` is non-sensitive, so no Google verification and no weekly re-consent. Catch: the app cannot write into a sheet you created by hand.
- Sign-in with Credential Manager; Sheets access via `AuthorizationClient`. Play Services holds the refresh token, so the app never stores one (replaces PRD 10.2).
- One spreadsheet, one tab per period (`2026-10`), header row on first write.
- Columns: `ID, Date, Merchant, Category, Amount, My Share, Tax, Service Charge, Payer, Split Method, Trip, Currency, Items JSON, Notes`.
- Duplicates prevented by looking up the `ID` column before appending. Edits rewrite that row; deletes clear it.
- WorkManager job with network constraint and exponential backoff on 429 and 5xx. A 401 or revoked grant shows "Sync paused" with a re-auth button.
- Export only. Edits in Sheets do not flow back.

---

## 8. UI: Nostelika in Compose

- One `BudgeterTheme`: the ten palette tokens, square corners, bundled pixel fonts (SIL Open Font License).
- Shared composables: `Window`, `PixelButton`, `PixelField`, `PixelProgressBar`, `StatusChip`, bottom bar.
- Motion uses stepped easing, off when the system animation scale is 0.
- Touch targets 48 dp minimum. Every icon button has a content description.
- Light theme only; the brief defines no dark palette.

### Navigation (5 tabs)
`HOME · TXNS · BUDGET · SHARED · PROFILE`
- SHARED holds Groups and Trips.
- BUDGET holds income, extras, fixed costs, savings (the only place total savings appears), category caps, planned purchases.

### Home
Above the fold: toolbar, Daily Budget window, Monthly Pacing window, Quick Actions row. Collapsed below: active trip, planned items, recent transactions, sync status. Hidden: category breakdown, group balances.

### Art placeholders
CC0 pixel art (Kenney.nl) or art drawn in code, plus a mascot slot. Sources and licences go in `CREDITS.md`.

---

## 9. Widgets (Glance)

Spending Progress, Daily Budget, Quick Add (deep link to add-expense), Planned Items.
Refreshed on every save plus once just after midnight. No periodic polling, so no refresh-rate setting.

---

## 10. Milestones

Each ends with a working build. After M9, one Fable audit of the whole app, then fix what it finds.

| # | Milestone |
|---|---|
| M0 | Skeleton: builds, pixel theme, five tabs |
| M1 | Budget engine: first-run, income, extras, fixed, savings, daily budget, pacing, manual entry |
| M2 | Planned purchases |
| M3 | Splitting and groups |
| M4 | Trips |
| M5 | OCR + rule-based parser + itemised editor |
| M6 | AI providers |
| M7 | Sheets sync (needs your Google Cloud project) |
| M8 | Widgets |
| M9 | Polish, accessibility, your art |

---

## 11. What I need from you

| When | What |
|---|---|
| Any time | Confirm "my share counts as spent" (3.2) |
| Install | Phone plugged in by USB with Developer options → USB debugging on |
| M7 | Create a Google Cloud project and Android OAuth client; steps in `docs/google-setup.md` |
| M9 | Your final art |
