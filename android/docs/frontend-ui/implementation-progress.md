# Frontend UI Implementation Progress

## Audit Summary & Execution (Phase 1 & 4)

| Feature | Screen / Component | Existing Files | Status | Remaining Work | Priority |
|---|---|---|---|---|---|
| Transactions | Transaction List v2 | `activity_transaction_list.xml`, `item_transaction.xml` | DONE | None | P1 |
| Transactions | Transaction Detail v2 | `activity_transaction_detail.xml` | DONE | None | P1 |
| Transactions | Expense / Income / Transfer Form v2 | `activity_transaction_form.xml`, `view_transaction_type_segmented.xml`, `view_amount_input.xml` | DONE | None | P1 |
| Transactions | Quick Import & Review (OCR) | `activity_receipt_import.xml`, `activity_receipt_review.xml` | DONE | None | P2 |
| Budget | Budget List v2 (`676:8462`) | `activity_budget_list.xml`, `item_budget.xml` | DONE | None | P1 |
| Budget | Budget Add / Edit (`676:8695` / `676:8919`) | `activity_budget_form.xml` | DONE | None | P1 |
| Budget | Budget Detail v2 (`676:8924`) | `activity_budget_detail.xml` | DONE | None | P2 |

## Historical XML-only review (Phase 5)
- All Transaction and Budget XML layouts implemented with Material 3 tokens.
- Review 2026-10-30: mock debug build, unit tests, instrumentation APK compilation, and lint passed (lint warnings remain).
- At this review stage only XML resources were implemented; the runtime follow-up below supersedes the integration TODOs.
- Preview coverage includes Transaction list/detail/form/transfer, receipt import/review, Budget list/form/detail and list items at 320/360/412dp and font scales 1.3/1.5.
- Before running instrumentation, sync sources from `android/` using `./scripts/sync-preview-layouts.ps1` (Windows) or `sh scripts/sync-preview-layouts.sh`.
- No device or AVD was available during this review. Render tests were compiled but not executed; screenshot comparison with the approved Figma frames is still pending. DONE above means XML implemented, not visually verified.
- `tools:*` content is preview-only.

## Runtime follow-up (2026-10-30)

- Transaction list/search/type/wallet/category/month filters, create/edit/detail/delete and transfers are wired through Activities → ViewModel → Repository. Budget list/month/create/edit/detail/delete derives spending from expense transactions. Home totals refresh from the same ledger.
- Income/expense affect one wallet; transfers affect both wallets and do not affect income/expense/budget totals. Integer VND, Vietnamese calendar month boundaries, matching categories, distinct transfer wallets and duplicate monthly budgets are validated.
- `mock` uses per-user persistent sample data; real flavors use Retrofit and never fall back to mock on failure. Backend endpoints remain a proposed client contract, documented in `docs/api/TRANSACTION_BUDGET_ANDROID.md`.
- Receipt capture/document batches use bundled on-device OCR with explicit per-image review/confirmation. Ambiguous amounts stay blank. No image upload.
- Final local checks passed: Dev/Mock APK builds, 42 unit/contract tests, mock lint (existing warnings remain), and instrumentation APK compilation.
- Ten instrumentation cases are compiled (flows, persistence, OCR). The attempted device run failed with window focus loss followed by an emulator disconnect. A retry encountered a `system_server` pre-watchdog during boot. These tests are **not verified as passing**; rerun on a stable device/emulator before acceptance. The temporary emulator was stopped.
- Screenshot comparison with approved Figma frames and end-to-end verification against the future backend remain pending.
