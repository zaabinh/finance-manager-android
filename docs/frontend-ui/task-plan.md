# UI-only task plan — S0 → S1 (3 developers)

Rules: one task = one PR to `develop` from `feature/ui-<task-id>`. A screen task starts only when its frame is **APPROVED** in [screen-manifest.md](screen-manifest.md). Shared files (`values/colors|dimens|styles|themes.xml`, `res/color/*`, shared `view_*` layouts) are edited **only by A / a Foundation task**, one PR at a time; B and C request changes through A. Feature tasks own only their own `activity_*/item_*/fragment_*` layouts, feature drawables and a feature `strings_<feature>.xml`.

Agents (configured in `.claude/agents/`): `figma-ui-inspector` (Haiku) reads the node → `android-xml-builder` (Sonnet) writes XML → `ui-visual-reviewer` (Sonnet) reviews renders. Opus only for cross-feature design-system conflicts. The main session model is whatever `/model` is set to; routing happens only through these subagents.

Common acceptance (every task): resources compile (`:app:assembleDevDebug` on develop); Java-referenced IDs preserved; strings in `strings*.xml`, Vietnamese; `tools:*` only for sample data; no fake status/nav bars; 48 dp targets; rendered screenshot at 360 dp compared with Figma, plus 320 dp, 412 dp, font scale 1.3×/1.5×, `1.250.000.000 đ` and long Vietnamese labels; manifest/component-map status updated. No Java/manifest/Gradle/backend changes.

## S0 — Foundation (A, sequential, merge before S1)

| ID | Figma | Target files | Depends | Acceptance | Status |
|---|---|---|---|---|---|
| S0-F1 | P1 vars `623:1873/1874`, type styles, P2 Button `185:802`, Input `47:150`, Feedback/Inline `628:2683`, Logo/Micro `625:1951` | `values/{colors,dimens,styles}.xml` additions, `color/button_*`, `drawable/ic_{info,circle_*,triangle_alert,micro_logo}`, `bg_feedback_inline`, `layout/view_feedback_inline.xml` | — | compiles; tokens match P1 hex/values | **Done (compile only, not rendered)** |
| S0-F2 | Login v2 `676:9894` | `layout/activity_auth_login.xml` (pilot) | F1, frame APPROVED | keeps IDs `etUsernameOrEmail, etPassword, ivTogglePassword, btnLogin, tvForgotPassword, tvSignUp`; rendered compare passes | Blocked: frame REVIEW |
| S0-F3 | Input/OTP `47:81`, Selector `628:2477`, Segmented `364:1866`, Chip/Filter `628:2626` | `styles.xml`, shared `view_*` | F2 verified | one gallery-free render each in a feature screen | Todo |
| S0-F4 | AppBar `364:1867`, BottomBar v2 `643:2498`, FAB `172:716`, Section/Header `639:2181` | `layout/view_app_bar.xml`, `view_section_header.xml`, `menu/bottom_nav.xml`, styles | F1 | 52 dp bar, 50 dp bottom bar, insets not faked | Todo |
| S0-F5 | State/Empty `629:2939`, State/Loading `629:2970`, Toast `628:2706`, Dialog/Confirm `628:2734`, Sheet `629:2895`, List/Option `628:2749` | shared `view_state_*`, `dialog_confirm.xml`, `sheet_container.xml`, `item_option.xml` | F1 | each state renders at 320 dp | Todo |

## S1 — Features (parallel after S0 merges)

### A — Auth + First Setup
| ID | Figma | Target | Depends |
|---|---|---|---|
| A1 | Register v2 `679:10075` | `activity_auth_register.xml` (keep `etUsername, etEmail, etPassword, etConfirmPassword, ivTogglePassword, ivToggleConfirmPassword, cbTerms, btnRegister, tvSignIn`) | F1 |
| A2 | Verify Email v2 `679:10089` | `activity_auth_verify_email.xml` (keep `etOtp1..4, btnVerify, tvResendOtp`) | F3 (OTP) |
| A3 | Forgot Password v2 `679:10103` | `activity_auth_forgot_password.xml` (keep `etEmail, btnSend, tvBackToLogin`) | F1 |
| A4 | Setup Welcome / BasicInfo / Notification / Wallet / Complete `679:10557/10515/10529/10543/10572` — one PR each | `activity_setup_*.xml` (welcome exists) | F1, F3 |

### B — Home, Transactions, Transfer, Wallets
| ID | Figma | Target | Depends |
|---|---|---|---|
| B1 | Card/Transaction `429:2230`, Input/Amount `628:2576`, Card/Wallet `643:2369`, HeroBalance `640:2384`, QuickActions `639:2213`, AttentionItem `639:2285` — one PR per component | `item_transaction.xml`, `view_amount_input.xml`, `item_wallet.xml`, `view_hero_balance.xml`, `view_quick_actions.xml`, `item_attention.xml` | F1 |
| B2 | Home v2 `656:2755` | `activity_home_dashboard.xml` | B1, F4, C1 (BudgetProgress) |
| B3 | Transaction List `658:4618`, Detail `658:5588` | `activity_transaction_list.xml`, `activity_transaction_detail.xml` | B1, F3 (chips), F5 |
| B4 | Expense Form `658:5712`, Transfer `674:7013`, Review `658:6660` | `activity_transaction_form.xml`, `activity_transfer.xml`, `activity_transaction_review.xml` | B1, F3, F5 |
| B5 | Wallet List/Detail/Create/Edit `663:7267/7467/6228/6383` | `activity_wallet_*.xml` | B1, F4 |

### C — Categories, Budgets, Analytics, Notifications, Settings
| ID | Figma | Target | Depends |
|---|---|---|---|
| C1 | Card/Category `364:1865`, Card/BudgetProgress `641:2358` | `item_category.xml`, `item_budget.xml` | F1 |
| C2 | Category Expense `663:7751` | `activity_category.xml` | C1, F3 |
| C3 | Budget List/Add/Detail `676:8462/8695/8924` | `activity_budget_*.xml` | C1, F3, F5 |
| C4 | Analytics `679:11088` | `activity_analytics.xml` (chart palette not defined in P1 — needs design decision) | F4 |
| C5 | Notification List `679:11076` | `activity_notifications.xml`, `item_notification.xml` | F4, F5 |
| C6 | Settings Profile/Notification/Security `679:10895/11064/11070` | `activity_settings_*.xml` (`activity_auth_change_password.xml` IDs preserved) | F4 |

`[Concept] Khoản` (`Card/Khoan` `642:2371`, P7 screens): not scheduled.
