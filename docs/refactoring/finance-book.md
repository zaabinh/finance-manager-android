# FinanceBook responsibility extraction

Branch: `refactor/finance-book`, based on `0dd5172` (completed Android phases 3–5).
Worktree: `.worktrees/finance-book-refactor`. Phase 6–7 continue in their existing
workspace; this branch does not check out, stash, reset, merge or edit that work.

## Scope and boundaries

- `FinanceBook` remains the sole owner of mutable ledger collections, lookups,
  mutation application, rollback and immutable snapshot creation.
- `LedgerRestorer` validates snapshot structure, timezone and reference entities.
  `FinanceBook` still replays entries through the normal validation path.
- `TransactionPolicy` validates and normalizes immutable transaction values,
  checks replacement rules and calculates affected-wallet contributions.
- `BudgetPolicy` receives immutable facts/read-only lookups rather than the book
  and its mutable builder. Balance checks evaluate expenses and spendable funds
  lazily, preserving the original validation and arithmetic order.
- Aggregate overflow validation belongs to the existing `LedgerCalculations`.

Public constructors, methods, constants, nested `Query`/`Page`, entities, finance
repository contracts and JSON v1 persistence remain unchanged. Wallet/category
rules remain in the book. UI, ViewModels, DI, API DTOs, build configuration and
the existing phase execution record are outside this change.

Ownership: Integration Agent (`gpt-6.1-sol/high`) implements the domain extraction;
Test Agent (`gpt-6.1-sol/medium`) adds public-contract tests; Reviewer Agent
(`gpt-6.1-sol/high`) independently checks the diff; main orchestrates contracts,
validation and the isolated branch. Graphify's existing dependency graph was
queried to check the domain/caller boundary; generated artifacts stay untracked.

## Compatibility checks

Characterization tests cover validation precedence, legacy transaction
normalization/audit metadata, archived references, failed-mutation snapshots,
transfer reversal at the balance boundary, budget rollback/lifecycle, timezone,
server aggregate null versus zero, and overflow. Baseline budget-edit rollback
may reinsert an entity at the end of its list; this existing behavior is preserved.

All ten contract tests passed against both original and extracted domain classes,
including a reduction that must skip an overflowing spendable-funds aggregate.
`javap -public` comparison confirms the `FinanceBook` public API is unchanged.
Independent source review found no regression in validation order, restore,
rollback, state ownership or lazy budget checks.

Final validation (2026-11-14), from this worktree's `android/`, with
`GRADLE_USER_HOME=C:/Users/Zaabinh/.gradle` and
`./gradlew.bat --offline --no-daemon --console=plain`:

| Tasks/check | Actual result |
|---|---|
| `:app:testMockDebugUnitTest :app:testDevDebugUnitTest` | 211 tests per flavor, 0 failures/errors/skips; both XML suites include all 10 new contract tests. |
| `:app:assembleMockDebug :app:assembleDevDebug :app:assembleStagingDebug :app:assembleProdRelease` | pass, including release R8. |
| `:app:lintMockDebug` | pass, 0 errors and 389 warnings. |
| `:app:assembleMockDebugAndroidTest` | pass, APK assembly only. |
| Standalone JUnit contract tests | 10 pass against original domain classes and 10 against refactored classes. |
| `javap -public` comparison | identical `FinanceBook` public API. |
| `git diff --check` | pass. |

The combined Gradle invocation exited **0**, `BUILD SUCCESSFUL in 3m 49s`.
Log: `android/build/finance-book-verified.log`. Logs and compiled baseline classes
are under ignored `android/build/` in this worktree. No new dependency or test
framework is introduced. No combined phase 6–7 integration test has been run.

## Integration with phase 6–7

This branch is intentionally not merged into the phase 6–7 branch. Integrate its
commit after the other work is ready, then rerun unit tests and affected builds
on the combined tree. Only domain implementation files, a new contract test and
this document are changed, so UI ViewBinding changes have no caller migration.

If phase 6 changes `FinanceBook.empty()` or moves demo/category seeds out of the
domain, retain those changes when merging. This refactor leaves that method and
the seed data untouched. A separate worktree prevents workspace interference;
it does not eliminate possible merge conflicts from later edits to the same file.

No new device instrumentation run is planned for this pure Java extraction.
The previous API37 emulator boot/runtime limitation remains; device-test success
must not be inferred from Android test APK assembly.
