# Budgeter graphics list

Every graphic the app uses or plans to use. Native sizes are the pixel-art canvas; the app upscales with nearest-neighbour, so draw at the small size.
Drop finished PNGs into `app/src/main/res/drawable-nodpi/` with the file name shown and they replace the placeholder with no code change.

Palette to stay inside: orange `#F4512A`, bright orange `#FF4B16`, cream `#E7D6AD`, light cream `#F1E4C6`, navy `#10172F`, brown `#2B1D12`, blue `#2459A6`, green `#4A9A78`, red `#C93721`, highlight `#FFF4DC`.

Status key: **CC0** = royalty-free placeholder from `CREDITS.md`. **Code** = drawn in code as a placeholder. **Needed** = nothing yet.

## 1. Identity

| File | Where | Native size | Now |
|---|---|---|---|
| `ic_launcher_fg` | Home-screen app icon (adaptive foreground, keep art inside the centre 66 of 108) | 108×108 | Code: pixel coin |
| `ic_launcher_mono` | Themed (monochrome) app icon on Android 13+ | 108×108 | Code: same coin |
| `hero_welcome.png` | First-run setup screen, large character greeting | 64×64 | CC0: princess portrait |
| `char_profile.png` | Profile tab character card | 38×38 to 64×64 | CC0: boy portrait |

## 2. Mascot (reacts to your budget)

| File | Where | Native size | Now |
|---|---|---|---|
| `mascot_idle.png` | Home toolbar and Daily Budget window | 16×16 to 32×32 | CC0: princess idle |
| `mascot_happy.png` | Shown when pacing is On track | same | Needed (uses idle) |
| `mascot_worried.png` | Slightly over | same | Needed (uses idle) |
| `mascot_over.png` | Over budget | same | Needed (uses idle) |
| `mascot_scan_1.png` … `_4.png` | Animation while a receipt is being read | same, 4 frames | Needed (pixel loading bar instead) |
| `mascot_travel.png` | Active-trip window on Home | same | Needed (uses idle) |

## 3. Bottom navigation (5)

| File | Tab | Native size | Now |
|---|---|---|---|
| `nav_home.png` | HOME | 9×9 to 16×16 | Code glyph |
| `nav_txns.png` | TXNS | same | Code glyph |
| `nav_budget.png` | BUDGET | same | Code glyph (coin) |
| `nav_shared.png` | SHARED (groups and trips) | same | Code glyph |
| `nav_profile.png` | PROFILE | same | Code glyph |

## 4. Quick actions (Home)

| File | Action | Native size | Now |
|---|---|---|---|
| `act_add.png` | + Expense | 16×16 | Code glyph (plus) |
| `icon_camera.png` | Scan receipt | 16×16 | CC0, recoloured |
| `act_screenshot.png` | Import screenshot | 16×16 | Code glyph |
| `icon_star.png` | Planned item | 16×16 | CC0, recoloured |

## 5. Categories (8 defaults, user can add more)

| File | Category | Native size | Now |
|---|---|---|---|
| `cat_food.png` | Food | 16×16 | Needed (text chip) |
| `cat_transport.png` | Transport | 16×16 | Needed |
| `cat_groceries.png` | Groceries | 16×16 | Needed |
| `cat_shopping.png` | Shopping | 16×16 | Needed |
| `cat_bills.png` | Bills | 16×16 | Needed |
| `cat_fun.png` | Fun | 16×16 | Needed |
| `cat_health.png` | Health | 16×16 | Needed |
| `cat_other.png` | Other | 16×16 | Needed |

## 6. Feature icons

| File | Where | Native size | Now |
|---|---|---|---|
| `icon_receipt.png` | Itemised bills, receipt editor | 16×16 | CC0, recoloured |
| `icon_airplane.png` | Trips list, active-trip window | 16×16 | CC0, recoloured |
| `icon_suitcase.png` | Trip fund | 16×16 | CC0, recoloured |
| `icon_money_bag.png` | Savings section (the only place totals show) | 16×16 | CC0 |
| `icon_coin.png` | Income and extra money | 10×10 to 16×16 | CC0 |
| `icon_heart.png` | Planned item priority | 14×12 | CC0 |
| `icon_lock.png` | "Savings hidden" note on Home | 16×16 | Needed |
| `icon_group.png` | Group cards | 16×16 | Code glyph |
| `icon_settle.png` | Settle up | 16×16 | Needed |
| `icon_ai.png` | AI provider settings (generic chip, no brand logos) | 16×16 | Needed |
| `icon_sheets_ok.png` / `icon_sheets_paused.png` | Sync status | 16×16 | Code glyph + text |

Provider logos (Claude, Gemini, OpenAI and others) are trademarks, so the app shows provider names as text instead.

## 7. Window chrome and decoration

| File | Where | Native size | Now |
|---|---|---|---|
| Window frame, title bar, close button | Every window | — | Code |
| `deco_rule.png` | Dotted dividers between sections | 8×2 tile | Code |
| `deco_dither.png` | Patterned header strip on hero windows | 4×4 tile | Needed |
| `deco_star_small.png`, `deco_sparkle.png` | Scattered near the mascot and empty space | 7×7 | Needed |
| `scene_footer.png` | City-skyline footer at the bottom of Home | 160×40 | Needed |

## 8. Empty states (one small illustration each)

| File | Shown when | Native size | Now |
|---|---|---|---|
| `empty_txns.png` | No transactions yet | 48×48 | Needed (text only) |
| `empty_planned.png` | No planned items | 48×48 | Needed |
| `empty_groups.png` | No groups | 48×48 | Needed |
| `empty_trips.png` | No trips | 48×48 | Needed |
| `empty_scan_failed.png` | OCR found no text | 48×48 | Needed |

## 9. Widgets (home screen)

| File | Widget | Native size | Now |
|---|---|---|---|
| `widget_preview_spend.png` | Spending Progress picker preview | screenshot | Needed (generic) |
| `widget_preview_daily.png` | Daily Budget picker preview | screenshot | Needed |
| `widget_preview_quick.png` | Quick Add picker preview | screenshot | Needed |
| `widget_preview_planned.png` | Planned Items picker preview | screenshot | Needed |

Previews are best made as screenshots once the widgets exist.

## 10. Store listing (only for a public Play Store release)

| Asset | Size |
|---|---|
| Hi-res icon | 512×512 |
| Feature graphic | 1024×500 |
| Phone screenshots | 2 to 8, 1080×1920 or similar |
