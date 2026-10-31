# UI-only implementation plan — 3 parallel developers

## Fixed scope

Pure Android Views XML/layout/theme/drawable/string/style assets and reusable UI components. `tools:*` is preview-only; no functional stub buttons, Java Activity wiring, navigation, backend, business logic, ViewModel, repository or API.

## S0 (single-owner foundation, merge before splitting)

Owner A: audit `develop` + latest Figma, freeze target screens individually; build tokens (colors, dimensions, typography), main icons/logo, common shell/navigation visual components, button/text input styles, empty/loading/error states; define Android XML conventions and component mapping. B+C review. Preserve existing Java resource IDs and existing AppCompat activities.

## S1 (three parallel streams after S0 merges)

- A: Auth + First Setup layouts (including states, validation appearances, OTP, notification prompt, wallet default, completion). Focus on existing auth layout replacement safely.
- B: Home + Transactions (Income/Expense/Transfer + search/filter/OCR review) + Wallets. Complex work; split into short PRs by component, then screen.
- C: Categories + Budget + Analytics + Settings + Notification Center. Implement approved MVP only; leave Khoản concept unimplemented until approved.

Each owner writes only its feature layouts, feature-specific drawables/string resources. Changes to shared XML/tokens go through A. Cross-feature shared components require dedicated PR approval; no parallel edits to these shared files.

## S2 Visual QA and freeze

Cross-review rotations A → B, B → C, C → A. Check 320, 360, 412 dp; text scaling 1.3x and 1.5x, long Vietnamese, large VND values, light/dark mode if supported, edge-to-edge/insets, dialogs/sheets, loading/empty/error. Baselines rendered on identical environment (system bars differ from Figma artboard). Use Android Studio previews first; optionally evaluate Paparazzi tests after checking AGP 9.3.3 compatibility.

## S3 Hand-off to logic developers

Deliver screen inventory, resource path IDs, component & token map, screenshot reference, unresolved UX mismatches, views IDs needed for binding, and explicit TODOs for navigation/integration. Only then open separate tasks for Activities/ViewModels/Repositories and API contract.

## PR gate

- One independently reviewable change scope, branch feature/ui-<feature> from develop; PR -> develop.
- Affected screen must be APPROVED; no code generated from draft redesign.
- Android resources build passes (`:app:assembleDevDebug`) when environment has SDK.
- Screenshot evidence of real preview/device; do not claim a match from XML syntax alone.
- No unapproved Java, manifest, Gradle, backend or API modifications.
- All strings externalized; UI language Vietnamese; `tools:*` used only for preview.
- No partial invisible layout, content behind system/keyboard bars, or direct copied Figma status bar.

## Model strategy (suggestion, not automatic model switching)

Main orchestrator Sonnet medium; Haiku subagent for bounded node/asset inspection; Sonnet agent for XML implementation & visual review. Use Opus for an early one-time design-system architectural decision or complicated mismatch only. Verify actual subagent names via Claude Code `/agents`. For cost, one file+one screen per task, use Figma IDs, and use FigWright `design_diff` if supported to handle redesign incrementally.
