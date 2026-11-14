# Android refactor — execution record

Plan: `android_refactor.md`. Base commit `bb0bf71` on `develop`. User authorized a local commit covering the integrated Android refactor; unrelated starting changes remain excluded.

## Starting state (preserved, not part of the refactor)
- Modified: `.gitignore`, `android/app/build.gradle.kts` (staging/prod `BASE_URL`), `backend/.../AuthController.java`.
- Untracked: `AGENTS.md`, `ORCHESTRATOR.md`, `UI_FIXING.md`, `Fable52.md`, `android_refactor.md`, `final_report.md`, `docs/deployment/*`.

## Baseline (before any edit)
| Check | Result |
|---|---|
| `:app:assembleMockDebug :app:testMockDebugUnitTest` | pass, 106 tests |
| `:app:assembleDevDebug :app:testDevDebugUnitTest` | pass, 106 tests |
| `:app:assembleStagingDebug :app:assembleProdRelease` | pass |
| `:app:lintMockDebug` | 0 errors, 396 warnings |
| `:app:connectedMockDebugAndroidTest` (base commit, AVD `Pixel_8` API 37) | 11 of 18 fail before any edit: `RootViewWithoutFocusException` (emulator window focus), stale view IDs, `Unknown layout activity_receipt_import`. No API 24/36 image installed. |

## Phase status
| Phase | Status | Owner | Evidence |
|---|---|---|---|
| 1 Dependency construction | done, reviewed | Integration Agent | 114 unit tests pass on mock and dev; mock/dev/prodRelease assemble; androidTest compiles. Instrumentation: 12 of 18 fail vs 11 at baseline, same failure classes (the differing tests fail on emulator window focus, which is flaky). Snapshot tree `67110a5`. |
| 2 Session-safe async | done, reviewed | Integration Agent | Clean rebuild: 164 unit tests pass on mock and dev; mock/dev/staging debug + prodRelease (R8) assemble; lint 0 errors, 396 warnings (same as baseline). Mutation check: removing the generation comparison fails 2 authenticator tests, making login reuse the generation fails 5. Instrumentation: 3 new `EncryptedSessionStorageTest` pass; the 5 login UI tests fail identically (4 of 5) on base commit and on this tree. Snapshot tree `03b2a7f`. |
| 3 Typed errors/results | done, reviewed | Main orchestrator; contract/review subagent (drafts only, no filesystem tools) | 170 unit tests pass on mock and dev; androidTest APK builds; lint 0 errors, 396 warnings. Typed parser/cancellation tests added. Log: `android/build/phase3-check2.log`. |
| 4 Canonical models | done, reviewed | Main integration; persistence/test draft agents; independent reviewer `gpt-6.1-sol/high` | 184 unit tests pass on mock and dev; mock/dev/staging debug, prodRelease/R8 and androidTest assemble pass; lint pass. Immutable entities/snapshots, explicit v1 file codec, domain helpers and compatibility tests. Log: `android/build/phase34-final.log`. Targeted device storage checks NOT RUN; runtime limitations below. |
| 5 Pagination/mutation lifecycle | done, reviewed | Integration/review `gpt-6.1-sol/high`; UI and tests `gpt-6.1-sol/medium`; main validation/commit | Final tree: 201 unit tests pass per mock/dev flavor, all flavor builds/R8, lint and androidTest assembly pass. Scoped snapshots, independent pagination, mutation receipts and one-time acknowledgment. P1 queued-session issue fixed and independently re-reviewed. Log: `android/build/phase35-verified.log`. |
| 6 UI cleanup / mock isolation | pending | | |
| 7 Cleanup / CI | pending | | |

## Stable shared contracts
- `app.FintrackApplication` → `container()`, `static container(Context)`.
- `core.di.AppContainer` is the only constructor of session, Retrofit clients, API services, repositories, executors, ViewModel factory.
- `core.di.AppExecutors`: `io()`, `serial()` (all mock-store access), `computation()` (OCR), `main()`.
- `core.di.AppViewModelFactory`; ViewModels are plain `ViewModel` with constructor-injected dependencies.

- Phase 2: `core.session.SessionStore` / `SessionSnapshot` (generation) / `SessionStorage`; requests tagged with their originating snapshot in the Retrofit call factory; conditional writes only (`updateTokens`, `updateProfile`, `completeSetup`, `clear`); session writes live in ViewModels, never in Activities.

- Phase 3: immutable `AppError`, `Resource.getError()`, pure `HttpErrorParser`, cancellable terminal repository callbacks. Error localization lives in `ErrorMapper.text()`. Obsolete reads and stale-session logout/deactivation events are suppressed.
- Phase 4: canonical immutable finance entities/enums, `FinanceSnapshot` and read-only accessors; `LedgerCalculations`, `BudgetPolicy`, `TransactionQueryEngine`; explicit `FinanceFileDto`/`FinanceFileCodec` retain the v1 disk schema and legacy upgrades.
- Phase 5: constructor-scoped `FinanceViewModel` owns snapshots and command Events; `TransactionListViewModel` owns query generations, immutable rows and pagination. `MutationReceipt` carries the exact created ID and an optional detached mock snapshot. Snapshot reads, pagination and saving have separate loading states. A confirmed save is acknowledged before any remote refresh.

## Phase 3–5 execution notes
- 2026-11-14: fresh subagent forks have working filesystem tools. Main delegates production/UI/tests; earlier phase3/4 draft-only agents did not edit the workspace. Independent phase3/4 reviewer found no confirmed regression.
- Graphify code-only extraction: 158 Java files, 1,716 nodes, 5,335 edges; no semantic/LLM extraction tokens. Queried finance/history/form dependencies before locking phase5 ownership. Generated graph and logs live in ignored `android/build/`, not in application sources or the commit.
- User authorized a local refactor commit on completion. Preserve/exclude the original `.gitignore`, build.gradle environment URL edits, backend AuthController changes, and unrelated documents. Include required existing Android phase1/2 dependencies so the committed refactor builds independently.
- Phase5 ownership: Integration Agent (`gpt-6.1-sol/high`) owns repositories/actions/receipts/ViewModels/factory; Feature Implementer (`gpt-6.1-sol/medium`) owns the four affected Activities; Test Agent (`gpt-6.1-sol/medium`) owns regression tests; Reviewer Agent (`gpt-6.1-sol/high`) reviews independently. Main orchestrates shared contracts, final build and commit.
- Independent phase5 review found a queued mock mutation could start after logout/re-login. The fix checks the originating session immediately before worker execution. It deliberately does not use the ViewModel-cleared flag, so closing the screen does not cancel a valid queued write; started writes remain uninterrupted. Regression tests exercise both cases through the real async repository path. The independent reviewer rechecked and approved this fix; no other phase5 finding remains.
- Quick-create selects `receipt.createdEntityId`; no list-difference inference remains. Transaction `clientRequestId` survives recreation/uncertain failure and is regenerated only for the definitive `TRANSACTION_REPLAY_VOIDED` response. These Activity paths were source-reviewed; device rotation/quick-create interaction was not executed.
- First phase5 combined gate: 195 unit tests pass in each mock/dev flavor; all four flavor builds, release R8, lint and androidTest APK assembly pass (`android/build/phase35-final.log`). This preceded the queued-write fix; the final post-fix gate is recorded below.
- Final cleanup removes the obsolete local-action Consumer adapter and unused command-message field; callers/tests use the final contracts directly.
- Targeted device tests for mock persistence and encrypted storage were NOT RUN in this continuation. On the available API37 AVD, attempts either reached an authorized ADB connection before the emulator threads became suspended, or exited before boot completed. No successful instrumentation runner result was produced. Compiling the androidTest APK is not counted as device-test success. Emulator logs: `android/build/phase34-emulator-authorized.log`, `phase34-emulator-foreground.log`, `phase34-emulator-final.log`; no targeted instrumentation result log was created.

## Final phase 3–5 verification (2026-11-14)
Executed from `android/` using `$env:GRADLE_USER_HOME='C:/Users/Zaabinh/.gradle'` and `./gradlew.bat --offline --no-daemon --console=plain` with all tasks below in one invocation. Final source includes the reviewed session fix and removal of obsolete adapters. Process exit code **0**, `BUILD SUCCESSFUL in 1m 40s`; log: `android/build/phase35-verified.log`.

| Tasks | Actual result |
|---|---|
| `:app:testMockDebugUnitTest :app:testDevDebugUnitTest` | 201 tests in each flavor, 0 failures/errors/skips; XML results checked. |
| `:app:assembleMockDebug :app:assembleDevDebug :app:assembleStagingDebug :app:assembleProdRelease` | pass, including release R8 shrinking. |
| `:app:lintMockDebug` | pass, 0 errors and 396 warnings, equal to baseline. |
| `:app:assembleMockDebugAndroidTest` | pass; APK assembly only, device tests NOT RUN. |
| `git diff --check` | pass; only Windows LF/CRLF informational warnings. |

Phase5 tests cover stale query responses, pagination retry without losing rows or advancing the failed page, save acknowledgment followed by refresh failure, one-time Events, clearing ViewModels without canceling writes, queued session changes, actual Retrofit transport cancellation, cancellation after delivery has been queued, and exact local/remote creation IDs. No dependency or testing framework was added. Phase6/7 remain pending.

## Phase 2 behaviour changes to be aware of
- Logging back into the same account is a new session: old ViewModels, requests and 401s are inert.
- The local setup-completed flag is set when the server confirms first setup, not only on the final button.
- Logout / deactivation results are one-time events (no repeated toast or redirect on recreation).
- A request issued under a session that has since ended fails locally before it is sent.
- Not verified on a device: manual login → logout → same-account login.

## Blockers
- Instrumentation cannot be used as a clean gate: the suite already fails on the base commit on the only available emulator (API 37).
