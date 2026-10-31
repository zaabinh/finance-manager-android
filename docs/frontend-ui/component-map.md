# Approved Figma → XML component mapping

Status: PROPOSED → IMPLEMENTED (XML compiles) → VISUALLY_VERIFIED (rendered screenshot compared). Only VISUALLY_VERIFIED rows are safe to treat as final.

## Tokens (unified into develop's resources, 2026-10-30)

Develop already ships the v2 palette (same hex as Figma P1). There is one token set — no `dong_*` duplicates.

| Figma | Android | Notes |
|---|---|---|
| Primitive colours | `values/colors.xml` `green_*`, `neutral_*`, `semantic_*` | + `semantic_error_800` |
| `color/bg/*` | `bg_app_canvas`, `bg_card_surface`, `bg_surface_secondary`, `bg_brand_subtle`, `bg_inverse`, `bg_scrim` | |
| `color/text/*` | `text_primary`, `text_secondary` (also placeholder), `text_disabled`, `text_on_primary` (inverse), `color_primary` (brand), `status_error` (danger) | |
| `color/border/*` | `divider_light` (default), `border_input_outline` (strong), `color_primary` (focus), `status_error` | |
| `color/action/*` | `color_primary`, `color_primary_pressed`, `text_on_primary`, `bg_surface_secondary` (disabled bg / secondary pressed), `bg_brand_subtle` (tertiary pressed), `status_error` + `semantic_error_800` (danger) | |
| `color/status/*` | `status_success/_bg`, `status_warning/_bg`, `status_error/_bg`, `status_info` | |
| `color/amount/*` | `amount_income`, `amount_expense` (neutral), `amount_transfer` | |
| `color/icon/*` | `text_primary`, `text_secondary`, `color_primary`, `text_on_primary` | no separate icon tokens |
| `space/*` | `values/dimens.xml` `spacing_4…48` | |
| `radius/*` | `radius_xs` 8, `radius_input_button` 12, `radius_card` 16, `radius_hero` 20, `radius_bottom_sheet` 24, `radius_full` | |
| Component minimums | `button_height` 48, `input_min_height` 52 (Figma; develop's `input_height` 48 is legacy), `touch_target_min` 48, `border_width_focus` 2, `divider_height` 1 | |
| `type/*` | `values/styles.xml` `TextAppearance.App.*` (develop's; SemiBold rendered as bold, no line heights) | |
| State colours | `res/color/button_*.xml`, `input_stroke.xml` | |

## Components

| Figma component | Node | Android target | Owner | Status |
|---|---|---|---|---|
| Brand/Logo/Micro | 625:1951 | `drawable/ic_micro_logo.xml` (exact Figma geometry, replaced develop's approximation) | Foundation | IMPLEMENTED |
| Button (Primary/Secondary/Tertiary/Outline/Destructive) | 185:802 | `Widget.App.Button.*` (MaterialButton) | Foundation | IMPLEMENTED |
| Input (label + field + helper/error) | 47:150 | `Widget.App.FieldLabel` TextView + `Widget.App.TextInputLayout` | Foundation | IMPLEMENTED |
| Feedback/Inline | 628:2683 | `layout/view_feedback_inline.xml` + `Widget.App.FeedbackInline.*`, `ic_{info,circle_check,triangle_alert,circle_alert}` | Foundation | IMPLEMENTED |
| Input/OTP | 47:81 | TBD (S0-F3) | Foundation | PROPOSED |
| Field/Selector | 628:2477 | TBD (S0-F3) | Foundation | PROPOSED |
| Control/Segmented | 364:1866 | MaterialButtonToggleGroup + style (S0-F3) | Foundation | PROPOSED |
| Chip/Filter | 628:2626 | Chip style (S0-F3) | Foundation | PROPOSED |
| Navigation/AppBar | 364:1867 | `layout/view_app_bar.xml` (S0-F4) | Foundation | PROPOSED |
| Navigation/BottomBar v2 | 643:2498 | BottomNavigationView style + menu (S0-F4) | Foundation | PROPOSED |
| Navigation/FAB | 172:716 | FAB style (S0-F4) | Foundation | PROPOSED |
| Section/Header | 639:2181 | `layout/view_section_header.xml` (S0-F4) | Foundation | PROPOSED |
| State/Empty, State/Loading | 629:2939, 629:2970 | `layout/view_state_empty.xml`, `view_state_loading_*.xml` (S0-F5) | Foundation | PROPOSED |
| Feedback/Toast, Dialog/Confirm | 628:2706, 628:2734 | Snackbar style, `layout/dialog_confirm.xml` (S0-F5) | Foundation | PROPOSED |
| Sheet/Container, List/Option | 629:2895, 628:2749 | `layout/sheet_container.xml`, `item_option.xml` (S0-F5) | Foundation | PROPOSED |
| Input/Amount | 628:2576 | `layout/view_amount_input.xml` | B | PROPOSED |
| Card/Transaction | 429:2230 | `layout/item_transaction.xml` | B | PROPOSED |
| Card/Wallet | 643:2369 | `layout/item_wallet.xml` | B | PROPOSED |
| Card/HeroBalance | 640:2384 | `layout/view_hero_balance.xml` | B | PROPOSED |
| Action/QuickActions | 639:2213 | `layout/view_quick_actions.xml` | B | PROPOSED |
| Card/AttentionItem | 639:2285 | `layout/item_attention.xml` | B | PROPOSED |
| Card/Category | 364:1865 | `layout/item_category.xml` | C | PROPOSED |
| Card/BudgetProgress | 641:2358 | `layout/item_budget.xml` | C | PROPOSED |
| Card/Khoan | 642:2371 | — excluded (`[Concept]`) | — | BLOCKED |

Do not silently create separate copies of a shared component in feature packages.

## Notes
- Typography: develop renders Figma SemiBold as bold; adjust centrally in `TextAppearance.App.*` if review flags it.
- Amount text: use `style="@style/Widget.App.TextView.Amount"` (tabular numerals) + an amount `TextAppearance`.
- Feedback/Inline action link gets `minHeight=48dp` (accessibility) — taller than Figma's 16 dp text when the action is shown.
