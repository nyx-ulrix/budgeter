# Budgeter: Project Plan

Android budget planner from `Budget_Planner_App_PRD.md`, skinned with the Nostelika pixel-art brief.
This file records every decision that differs from or adds to those two briefs. Where it is silent, the briefs apply.

Status: v1.9.2 released 2026-10-23 (web app deployed the same day). Sections 12 and 14 override earlier sections where they differ.

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
| Flip phones | Concise view on small cover screens (Oppo Find N Flip and similar), responsive widgets with a tiny layout | User |
| Graphics | Full list of planned art in `docs/GRAPHICS.md`; drop-in file names replace placeholders with no code change | User |
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
The period is replayed day by day from its first day (`today()` in `core/Budget.kt`):
- `Day's budget = (money not yet spent − unused leftovers) / days left including that day`
- A day that spends less than its budget adds the difference to the leftovers ("Unused" in Saved up this month). Later budgets stay the same.
- Spending past the budget uses the leftovers first. Past those, and any Groceries/Bills (monthly categories) spending, lowers every remaining day.
- Leftovers stay within the period; whatever is left at the end becomes savings.
- Fixed for the whole day. `Left to spend today = today's budget − spent today`. The leftovers are shown separately.

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

Since 1.5.3 (user's call, 2026-10-10): with an AI provider selected, the receipt photo itself is sent to it (JPEG, max 1600 px). Text-only AI and the built-in reader are the fallbacks. Without a provider, images never leave the phone.

---

## 6. AI provider access

The ReadyTalent app's account login runs Claude Code and Gemini CLI on a laptop. A phone can't, and a laptop relay needs a desktop running. So the app calls providers directly.

Every supported provider exposes an OpenAI-compatible `chat/completions` endpoint, so there is one client and a table of presets:

| Preset | How the user connects | Cost for receipt parsing |
|---|---|---|
| Gemini | Free API key from aistudio.google.com/apikey | Free tier |
| OpenRouter | **Sign in** button (OAuth PKCE in the browser; the app catches the reply on a one-shot localhost listener, with a paste-the-code fallback). One login reaches Claude, Gemini, GPT, DeepSeek, Perplexity and free models | Pay per use, or free models |
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
- `AuthorizationClient` handles account choice and the Sheets grant in one step, so no separate sign-in library. Play Services holds the refresh token; the app never stores one (replaces PRD 10.2).
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

### Flip-phone cover screen
When the screen is under 320dp wide or 440dp tall, the app shows one concise window: today's number, month bar, Add and Scan, and the last expense. Quick add there is amount + category only. Scanned receipts still open the full editor. The activity handles screen changes itself, so folding or unfolding keeps your place.

### Navigation (5 tabs)
`HOME · TXNS · BUDGET · SHARED · PROFILE`
- SHARED holds Groups and Trips.
- BUDGET holds income, extras, fixed costs, savings (the only place total savings appears), category caps, planned purchases.

### Home
Above the fold: toolbar, Daily Budget window, Monthly Pacing window, Quick Actions row. Collapsed below: active trip, planned items, recent transactions, sync status. Hidden: category breakdown, group balances.

### Art placeholders
CC0 pixel art from Ninja Adventure (Pixel-boy & AAA) and 1-bit Pixel Icons (Nikoichu), plus icons drawn in code. Sources and licences are in `CREDITS.md`; the full art list is `docs/GRAPHICS.md`.

---

## 9. Widgets (Glance)

Spending Progress, Daily Budget, Quick Add (deep link to add-expense), Planned Items.
Refreshed on every save plus once just after midnight. No periodic polling, so no refresh-rate setting.
Each widget has a tiny layout (one big number, one line) used below about 150×60dp, which fits 1×1 cells and flip-phone cover screens. Widgets are also declared for the lock-screen category, which some cover screens use.

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

---

## 12. Changes in v1.1 (these override the sections above)

| Area | Now |
|---|---|
| Groups | Removed: no Groups tab, saved groups or settle-ups. An expense can be split between P1 (you), P2, P3… with no names. By-item splits list each person's items. Trips are no longer shared with a group. |
| Monthly budget | Separate from income: you set how much you plan to spend. Spendable = that budget, or everything available after fixed costs and set-asides if none is set. Carries over to later months. |
| Savings | Automatic: a finished month saves everything earned that wasn't spent (unspent budget and never-budgeted money). An overspent month subtracts. Manual savings lines are no longer edited in the app. |
| Month bar | One colour-coded bar: fixed costs, then each category's spending in its own colour. Tap for amounts and shares. All red, pulsing, when over budget; "Too fast" now only means ahead of an even pace. |
| Categories | Each is Daily or Monthly (Profile → Categories). Monthly ones (default Groceries, Bills) lower the month's money, and so every remaining day's budget, without counting as spent today. Category caps removed; Home's category window removed. |
| Widgets | Two widgets, both drawn in the app's pixel style. "Budgeter" (4×1): left to spend today, a camera button (opens the receipt scanner) and "+" (new expense). "Budgeter: bars" (4×1): today's bar above the month's colour bar. Quick add, Planned and Today widgets were removed (v1.2). |
| UI | Folds start closed and close again when you switch tabs. Transactions' search and filters sit in one fold. AI model is a dropdown loaded from the provider. In-app pixel camera. |
| Production | Test server removed. The install button installs the release build. |
| Savings target (v1.3) | A monthly amount kept aside first: spendable = (budget, or everything available) − target, and the daily budget follows. Spending past spendable dips into the target: the month bar pulses yellow with a "Dipping into savings" chip. Past the target too is over budget: red. Carries over like the budget. |

## 13. Two-way sync with the web app (v1.6)

The web app ([budgeter-web](https://github.com/nyx-ulrix/budgeter-web), React Native for Web) shares the same Budgeter spreadsheet. Besides the readable month tabs, a hidden `_sync` tab holds one row per record: `setup`, `categories`, `plan:<period>`, `txn:<id>`, `planned:<id>`, `trip:<id>`, each with the time it last changed and its JSON (empty = deleted). `Store.update` stamps every record a change touches (`Records.kt`). Each sync takes the sheet's records that are newer than ours, writes the month tabs, then uploads our newer records. Last writer wins per record. The phone syncs after each change and whenever the app is opened; the web app also syncs every minute while it's open. AI keys and sync settings stay on each device. `Records.kt` and the web's `records.ts` define the format and must stay in step.

## 14. AI speed, money audit and date overspend (v1.9.1–1.9.2, 2026-10-22/23)

| Area | Now |
|---|---|
| AI speed (1.9.1) | Gemini calls send `reasoning_effort: "low"` (`Ai.kt` / `ai.ts` `chat()`). Gemini 3.x Flash thinks at length by default, which made a receipt take many seconds; reading one needs little. Default model stays `gemini-3.8-flash` (free tier; paid price doubles 2027-01-01); `gemini-3.5-flash-lite` is the faster, cheaper fallback. A saved provider keeps its own model: change it in Profile → AI. |
| Date overspend (1.9.2) | A date expense that takes the shared fund below zero is covered by whoever saved it: a `topup` "Over budget: <name>" goes to the fund and the same amount becomes their own expense (note "Date budget overspend"). Only the part that expense overdraws counts (`overdrawn(left, amount, old)` in `DateMoney.kt` / `date/money.ts`), so a fund already short through someone else's spending isn't charged to me, and re-saving a covered expense charges nothing. To cover an older shortfall, open that expense and Save once. |
| Money audit (1.9.2) | Fable audit, fixes on Opus, both apps. Web AI amounts go through `toMinor` (exact, no thousands guess; 0.675 was read as 675). Old DateBudget `base` is one top-up on the budget's first day, not today (it counted again every month). Editing a "Buy now" expense below its reservation frees the extra (`withTxn` / `saveTxn`). Android currency decimals use the same fixed table as the web, not the phone's ICU. Date amounts are rounded, not truncated, on both. Android: rates shown as plain decimals, infinite rates refused. Web: a 29 Feb receipt date clamps like java.time; no split on date expenses. |

## 15. Past months savings (v1.10.0, 2026-10-27)

| Area | Now |
|---|---|
| Savings history | Budget → Savings has a "Past months" part: the last 12 finished months up to the month being viewed, as a bar each around a zero line (green saved, red went over), then a row each, newest first, with what the month saved and the running total. `savingsHistory()` in `Ledger.kt` / `ledger.ts` returns every finished month from the first plan with `saved` (= `monthSaved`) and `total`; `savedToDate` now reads its total from it. Nothing new is stored or synced. |
| What a month saves | Unchanged: income + extra money − fixed costs − reservations − trip funds − my spending (my share of splits, trips excluded). Savings lines and an unspent savings target are part of it; an overspent month is negative and lowers the total. The current month only counts once it ends. |
| Bank balance | Not tracked. Roughly: balance when you started + saved to date + this month so far (income + extra − fixed − spent) + set aside and not yet spent (trip funds − trip spending, unused reservations) − what others still owe you on bills you paid + what you owe others. The app counts a month's income and fixed costs from its first day, so mid-month the bank can differ until payday and rent go through. |

## 16. Bank balance check and date overspend by month (v1.11.0, 2026-11-01)

| Area | Now |
|---|---|
| Bank balance | Home → This month ends with "In the bank", hidden (••••••) every time Home opens until Show is tapped. Set it once with the balance the bank shows at the end of a day; `bankBalance()` (`Ledger.kt` / `ledger.ts`) then adds income (on each later period's first day), extra money and fixed costs (on the day added, else the period's first day), and takes off each later expense's `cashOut`: the whole bill if I paid, less shares already paid back; my share once I've paid back whoever paid; trips and planned purchases included. Set-asides (savings, reservations, trip funds) stay in the bank. Date expenses are paid from the "Date budget (my share)" fixed cost, plus any overspend charged to me. Stored as `setup.bank {amount, date}`, so it syncs with the setup record; both apps must know the field or they drop it when they save setup. |
| Date overspend by month (fix) | The charge for a date expense that overdraws the fund was worked out against the fund's all-time balance, which still counted earlier months' leftovers, while the fund (and the warning) is per month and months don't carry over. So it charged too little (a 70 dinner was charged 13 when that month's fund was shorter). Now `dateCover()` (`ui/DateScreen.kt` / `screens/DateScreen.tsx`) uses the expense's own month; an edited expense's old amount only counts if it was in that month. Expenses saved before the fix: open the expense and Save once, and the rest is charged. |

## 17. Bank balance from before Budgeter (v1.12.0, 2026-11-01)

| Area | Now |
|---|---|
| Starting balance | "In the bank" asks only for what was in the bank before you started Budgeter. It's stored as `setup.bank` dated `bankStart()`, the day before the first budget month, so every month's income, extra money, fixed costs and payments count and the figure always checks every deduction since the start. The Home row's button is "Starting balance". A balance dated another day (from 1.11.0) still works until it's saved again. |
| Jia En's data | Set from the sheet on 2026-11-01: `bank = {amount: 0, date: "2026-09-30"}` in the `_sync` setup record. With 0 before Budgeter the app's tally is SGD 1,085.64 (Oct net 259.23 + Nov so far 826.41, counting November's income, "hilite" and fixed costs from 1 Nov). Enter the real pre-October balance as the starting balance to compare against the bank. |

## 18. Date overspend charged once, history shows it once, fixed cost totals (v1.13.0, 2026-11-01)

| Area | Now |
|---|---|
| One charge per date expense | An overspend charge in my budget carries `dateId` (the date expense's id; synced). Every save of a date expense removes its earlier cover top-ups ("Over budget: <note>", same day: `coverTopups`) and its charges (`overspendsFor`), works the cover out afresh without them (`dateCover`), and adds back one top-up and one linked charge (`chargeOverspend`). So re-saving never charges twice, lowering refunds, and moving or renaming leaves nothing behind. Moving it to my own budget or deleting it removes its top-ups and charge too. Older unlinked charges are matched by day and name, but only when no other date expense has that day and name. |
| Save once | The Save button shows "Saving…" and ignores taps while a date expense saves over the network. |
| History | Txns shows each date expense once: its charge ("SGD x over, from your budget") is on its row; the charge and the cover top-up aren't listed separately while the date budget is loaded. |
| Fixed costs total | The Fixed costs and Extra money folds show the month's total in the title and under the list. |
| Audit (Fable 5.1) | Both apps give the same numbers on the real data. Fixed from it: lowering, moving, renaming or moving out a date expense left old top-ups and charges. Open design questions: the bank deducts the SGD 150 "my share" fixed cost plus charges, but if I pay every date and my partner's share doesn't reach my bank, the real outflow is the full date spending; "date init" SGD 92.10 counts as money in. Carried-over fixed costs land on the period's first day (`planFor` drops `added`), so the bank check matches once that month's bills have cleared. Two date expenses dated 2023-10-16 and 2024-10-16 (HISTRANGER, mistyped years) lower DateBudget's all-time balance only. |
| Data fix 2026-11-01 | Three duplicate SGD 13 "sweet cheeks" overspend charges (re-saved 09:41) were deleted through the `_sync` tab. |

## 19. Savings in the bank, saved this month, daily amounts (v1.14.0, 2026-11-08)

| Area | Now |
|---|---|
| Savings in the bank | Home → "In the bank" has a "Savings in it" line under it, hidden with the balance: `bankSavings()` = the starting balance + what every finished month saved (`savedToDate`). The rest of the balance is this month's money and what's set aside. |
| Saved this month | Budget → Spendable.exe shows "Saved this month" (a finished month) or "Saved so far" (this month: what it saves if nothing more is spent) next to the spendable figure: `monthSaved`. On a phone it wraps under it. |
| Daily amounts | Spendable.exe: a finished month shows what it spent a day on average (`dailyAverage`: budget spending ÷ the month's days); this month shows what can be spent a day for the days left, today included (`perDayLeft`: spendable − spent, ÷ days left). Past months rows show each month's average a day. Both round down to whole cents, the same on both apps. |
| Taste audit (UI) | design-taste-frontend app-screen checks, kept to the app's pixel style. Fixed: a fold's total now closes its list (above the add row); a finished month says "Spent", not "Spent so far"; the bank note on Home is one short line. |

## 20. Fixed costs ticked as deducted, date expenses in the bank (v1.15.0, 2026-11-08)

| Area | Now |
|---|---|
| Deducted tick | Each fixed cost in Budget → Fixed costs has a tick box: ticked once it has left the bank (`Line.paid`, the day ticked; synced with the plan; dropped when a plan is carried into a new month). In a month that hasn't ended, "In the bank" takes off a fixed cost only once it's ticked, on that day. Fixed costs of finished months count as before. |
| Date expenses in the bank | Every date budget expense I keyed in counts as paid from my bank, on its date: `bankBalance(s, date, dates)` with the date budget's expenses `by` me (Home passes them once the date budget has loaded). The "Date budget (my share)" fixed cost and the overspend charges then don't come off the bank (they're how the budget counts that money). Budget and savings are unchanged. Without the date budget loaded, the old rule stands in. |
| Jia En's data | The manual "fx date side" SGD 150 expense (31 Oct) duplicated this; with it removed, and the date budget's 3 Nov "sweet cheeks" (same dinner as 31 Oct) removed, the tally on 2026-11-08 is SGD 704.67 against the bank's 727.47 (starting balance 60.70). |

