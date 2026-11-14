You are the main orchestrator, integration lead, code reviewer, and tester for this Android refactor.

Your task is to IMPLEMENT and VERIFY the approved refactor below. Delegate bounded implementation tasks to subagents while retaining responsibility for architectural decisions, integration, review, and final validation.

The user owns this repository. You are authorized to restructure code within the approved scope. Consolidate duplicated implementations where required by this plan, but do not expand the task into unrelated improvements.

## 1. Approved scope

Repository:
D:\ZB\Code\UIT\NT118\finance-manager\finance-manager-android

Android project:
android/

Keep:
- Java and XML.
- One Gradle application module.
- Feature-oriented packages.
- Existing backend contracts and navigation.
- Manual dependency injection.
- Existing financial behavior, integer VND amounts, user timezone handling,
  transaction idempotency, and compatibility with existing mock files.

Implement:
- Session-safe asynchronous operations.
- Constructor-injected dependencies.
- Typed errors and consistent repository results.
- One canonical set of finance domain models.
- Smaller domain and presentation responsibilities.
- Reliable pagination and mutation handling.
- ViewBinding and focused UI cleanup.
- Mock-only demo data.
- Meaningful tests and stronger CI coverage.

Do not:
- Migrate to Kotlin or Compose.
- Introduce Hilt, Room, or additional Gradle modules.
- Upgrade existing dependencies as part of this refactor.
- Redesign the UI or add backend functionality.
- Integrate real Analytics or Notification APIs in this task.

For Analytics and Notifications:
- The mock flavor keeps demo data.
- dev, staging, and prod show a localized “not connected yet” state.
- Do not represent an unavailable integration as a successful empty response.
- Keep access to notification settings.
- Do not show fabricated financial figures or alerts in real flavors.

## 2. Execution ownership and autonomy

Complete the entire approved task, including integration, tests, and fixes.

Do not stop after producing a plan, starting agents, completing one phase,
or listing remaining work. Continue through routine errors, timeouts, failed
tests, unavailable tools, and merge conflicts by diagnosing and resolving them
within your access and authority.

You may inspect, edit, refactor, remove verified obsolete code, run checks,
and coordinate subagents without asking for permission at every phase.

Respect actual access restrictions and protected files. Do not bypass a
deliberate restriction. Ask only when a missing product decision or restricted
action is necessary to continue. Continue independent work while waiting.

Do not push, publish PRs, deploy, contact other people, or modify live data.
Prepare seven reviewable local change batches. Commit only if the user or
applicable repository instructions authorize it.

Follow applicable AGENTS.md instructions. Treat older project plans and
ORCHESTRATOR.md as context: do not reimplement completed features merely
because an older document describes them as pending.

Before the first tool call, briefly explain your next action.
During long runs, provide a concise progress update every few tool calls,
roughly once per minute. Explain findings, decisions, blockers, and the next
verification step. Do not narrate every command.

## 3. Reconstruct the current state before editing

Read:
- Applicable AGENTS.md files and relevant project instructions.
- Android Gradle configuration, manifest, CI, and test setup.
- Relevant source and tests for session/auth, user, finance, transaction,
  wallet, category, budget, setup, home, OCR, analytics, and notifications.
- Existing mock persistence and migration logic.

Inspect:
- git status --short
- git diff --stat
- Targeted diffs for files you need to change.
- Recent relevant history when needed to explain current behavior.

Preserve all pre-existing user changes. Do not reset, clean, stash, or overwrite
them. Record the starting state so the final diff can distinguish existing work
from your edits. If a target file already contains user edits, integrate around
them; ask only if they create an unavoidable conflict.

Do not open secrets or credential files unless strictly required for an
authorized operation. Never print their contents.

Previous inspection found:
- Java/XML, minSdk 24, compileSdk/targetSdk 36.
- mock, dev, staging, and prod flavors.
- FinanceBook combines entities, validation, calculations, and queries.
- Duplicate finance entities exist in multiple packages.
- FinanceViewModel constructs repositories and handles several unrelated states.
- Session writes also occur in Activity observers.
- Analytics and Notifications use demo data directly.
- MockFinanceStore uses per-user files, AtomicFile, and legacy upgrades.
- CI primarily builds and tests mockDebug.
- Some tests check Resource or constructors instead of the named class.

These are starting hypotheses. Verify them against the current checkout.

Establish baseline build/test results before implementation.
An earlier test attempt stopped during Gradle Wrapper distribution retrieval;
that is not evidence of a failing test suite.

Keep a short execution record containing:
- Phase status.
- Stable shared contracts.
- Agent assignments and file ownership.
- Validation evidence.
- Remaining blockers.

If creating a tracking document, use one concise document rather than several
overlapping reports.

## 4. Subagent strategy

Use subagents deliberately. Do not create one agent per file.

Keep at most three subagents active alongside yourself, or fewer if the
environment has a lower limit. Do not allow recursive delegation.

Roles:
- Explorer: dependency mapping, migration risks, existing behavior, and contracts.
- Integration Agent: shared models, session/network interfaces, DI, and callers.
- Feature Implementer: isolated feature changes under stable contracts.
- Test Agent: focused regression tests and independent execution evidence.

You remain the primary reviewer and final tester.
Agent reports do not replace your own inspection and validation.

Start with one bounded Explorer pass. While it runs, inspect the baseline,
working tree, and build setup yourself. Use its findings to lock contracts
before broad implementation.

Parallelize only when:
- Shared contracts have been settled.
- Writable file ownership does not overlap.
- Each task has an independent acceptance criterion.

Serialize changes to shared files, including:
- Gradle configuration and the manifest.
- AppContainer and ViewModel factories.
- Session and network contracts.
- Resource/error types.
- Canonical domain models and FinanceBook.
- CI and shared test infrastructure.

Use isolated worktrees only when they preserve the user's current working
state and the environment supports them. Otherwise use explicit file ownership
in the shared checkout.

Give every implementation agent:
1. A concrete objective and relevant current code.
2. Exact writable files or directories.
3. Read-only dependencies and forbidden edits.
4. Shared APIs and behavior it must preserve.
5. Required tests and completion criteria.
6. Integration assumptions and known user edits.
7. A requirement to report changed files, tests, and unresolved issues.

Agents must escalate proposed shared-contract changes to you rather than
implementing incompatible alternatives independently.

If model selection is available:
- Use high effort for orchestration, session races, persistence compatibility,
  shared contracts, and integration review.
- Use medium effort for bounded feature and normal test implementation.
- Use low effort only for mechanical mappings, resources, or binding changes
  after interfaces are stable.
- Do not default to xhigh or max.
- If settings are unavailable, use the available model and report no invented
  model or effort choices.

If subagent tools are unavailable, say so once and execute the same work
sequentially. Delegation availability must not become an artificial blocker.

## 5. Seven implementation phases

Each phase is a reviewable batch. Integrate, inspect, test, and fix it before
starting a dependent phase. Approval by you is sufficient for routine phase
gates; do not ask the user to approve every batch.

### Phase 1 — Baseline and dependency construction

Main agent:
- Establish baseline results and inspect existing changes.
- Define and review constructor contracts.

Integration Agent:
- Add FintrackApplication and AppContainer.
- Centralize session, API services, repositories, and executors.
- Add ViewModelProvider.Factory support.
- Remove repository construction and singleton access from ViewModels.
- Inject Retrofit services into repositories.
- Provide background read execution, serialized mock writes, and main-thread
  result delivery.
- Move OCR away from FinanceRepository.IO.

Update callers together with constructors.
Do not leave parallel legacy initialization paths after migration.

Gate:
- Application startup works.
- Existing relevant tests pass or baseline failures are explicitly separated.
- Tests can supply fake dependencies.

### Phase 2 — Session-safe asynchronous operations

Assign the implementation to an Integration Agent. Review this phase yourself
with particular attention to races.

Introduce SessionStore and SessionSnapshot with a session generation:
- A new successful login or logout changes the generation.
- Token refresh does not.
- Logging back into the same account still creates a distinct session.
- Snapshot validation and conditional writes are atomic.

Provide conditional operations for:
- Updating tokens.
- Updating profile.
- Clearing the session.

Associate protected requests with their originating session.
Never retry an old-session request using a new account's token.

Preserve coordinated refresh behavior:
- Concurrent 401 responses for the same token share refresh work.
- Retry uses a refreshed token only within the same session.
- Network failures, 429, and 5xx preserve the session.
- Invalid refresh credentials clear only the matching session.

Move session-changing side effects out of Activity observers into auth/user
coordination. Cover login, profile, logout, password change, account
deactivation, and setup completion.

Ignore obsolete login attempts and old profile responses.
Rotation must not repeat session writes or navigation.
Onboarding state after login remains authoritative from the backend.

Test Agent:
- Add deterministic race tests using fake session/network dependencies.
- Test encrypted storage behavior with instrumentation where necessary.

Gate:
- Old callbacks cannot overwrite or clear a newer session.
- Same-account re-login is covered.
- Refresh behavior remains correct under concurrency.

### Phase 3 — Typed errors and repository results

Define one AppError representation with:
- Error category.
- Optional HTTP status and backend code.
- Field validation errors.
- Optional shortfallMinor.
- A technical cause where relevant.

Separate HTTP error parsing from localized presentation.
The parser must not depend on Android Context.

Replace string-driven control flow, including:
- AUTH_EMAIL_NOT_VERIFIED encoded as display text.
- Enum conversion through Resource messages and valueOf().

Repositories:
- Return results through callbacks with cancellation handles.
- Do not accept or mutate a ViewModel's MutableLiveData.
- Preserve endpoint-specific handling of successful empty/Void responses.

ViewModels own loading, success, error, and retained data.
UI maps typed errors to string resources and feature-specific presentation.

Preserve OTP, archived entity, voided transaction, field error, and budget
shortfall behavior. Do not expose raw server messages as the default UI.

Gate:
- Error bodies are parsed once.
- UI control flow does not depend on human-readable messages.
- Existing API contract tests remain valid.

### Phase 4 — Canonical models and smaller domain responsibilities

Lock the canonical model definitions before parallel caller migration.

Create one finance.domain model set containing all fields currently used by:
- Wallet.
- Category.
- Transaction.
- Budget.
- MonthlyBudget.

Consolidate enums and domain failures.
Keep remote DTOs separate.
Remove duplicate models only after migrating callers and relevant tests.

Make entities immutable and replace values through validated domain operations.
Expose read-only collections and detached snapshots.
Do not expose mutable State through FinanceBook.state().

Retain FinanceBook as the ledger/mutation boundary and extract:
- LedgerCalculations.
- BudgetPolicy.
- TransactionQueryEngine.

Keep financial behavior unchanged:
- Server aggregates remain authoritative.
- Partial server snapshots must not be treated as complete local ledgers.
- Preserve overflow checks, non-negative balances, archive/void rules,
  transfer semantics, month boundaries, and user timezone behavior.

Separate mock persistence DTOs from domain entities.
Preserve:
- Per-user filenames.
- JSON property names and enum values.
- Version 1 compatibility.
- Legacy includeInAssets and monthly-budget upgrades.
- AtomicFile writes and rollback on write failure.
- Storage errors for corrupt files rather than silent demo replacement.

Update R8 rules for relocated serialized types.

Use separate Feature Implementers for caller groups only after the shared
model contract is stable.

Gate:
- Old mock fixtures still load.
- Invalid writes do not corrupt existing files.
- External callers cannot bypass domain validation.
- Duplicate domain implementations have been removed.

### Phase 5 — Snapshot, pagination, and mutation lifecycle

Select mock/remote finance implementations in the container.

Extract TransactionListViewModel to own:
- Query and request generation.
- Rows, page, and totalPages.
- Initial loading and load-more errors.

Keep FinanceViewModel focused on scoped snapshots and commands.
Pass scope/detail ID during construction.

Use separate loading states for snapshot reads, pagination, and saves.
Discard obsolete query responses.
Advance pagination only after success.
Retain rows after load-more failure and support retry without duplication.

Cancel obsolete reads and reads owned by cleared ViewModels.
Do not assume canceling a write means the server did not save it.
Never automatically retry financial mutations after uncertain outcomes.

Return MutationReceipt with the created entity ID where available.
Quick-create wallet/category flows must select that ID rather than infer
creation from differences between lists.

Separate save acknowledgment from refresh:
- A confirmed save emits success once.
- A later refresh failure is a read failure.
- It must not turn a completed save into a failed save.

Use the existing Event<T> mechanism for one-time effects.
Preserve clientRequestId across uncertain retries and recreation.
Keep the existing definitive voided-replay rule for generating a fresh key.

Gate:
- Rapid filter changes cannot show stale results.
- Pagination retries preserve correct rows and page position.
- Save-success/refresh-failure behavior is explicitly tested.
- Rotation does not duplicate commands or success effects.

### Phase 6 — UI cleanup and mock isolation

Parallelize independent feature groups under the settled contracts:
1. Auth, setup, and settings.
2. Wallet, category, budget, and transaction.
3. Home, analytics, notifications, and related adapters.

Convert XML-backed screens, sheets, and adapters to ViewBinding.
Preserve view IDs, navigation behavior, accessibility, and UI states.

Keep Activities focused on binding, rendering, and dispatching user actions.
Move Home calculations and transaction/budget form validation into plain Java
classes that can be tested without Activities.

Narrow FinanceActivity to common lifecycle/render responsibilities.
Use a shared session-expiration navigation helper.

Preserve drafts, filters, and request IDs across recreation.
Do not persist passwords or OTPs to disk.

Inject Analytics and Notification providers:
- Demo providers are selected only for mock.
- Real flavors render the explicit not-connected state.
- Hide read actions when no real notification source is connected.
- Keep access to notification settings.
- Store mock notification read state per account and notification ID.
- Do not migrate the old global read flag into every account.

Gate:
- Binding conversion does not change screen behavior.
- Drafts and filters survive recreation.
- Quick-create and existing financial flows still work.
- Real flavors cannot show demo finance data.

### Phase 7 — Cleanup, CI, and final review

Remove:
- Temporary compatibility paths.
- Obsolete constructors and duplicate helpers.
- Unused imports and verified dead code.

Correct:
- The misleading phone parameter that actually represents timeZone.
- *Cents variable names for integer VND amounts.
- Duplicated Intent extra literals.
- User-visible hardcoded strings.
- Comments that merely repeat the following statement.

Move the existing Flexbox dependency into the version catalog.
Do not upgrade its version.

Replace placeholder tests with behavior tests.
Preserve useful domain and Retrofit contract tests.

Extend CI to cover:
- Mock build, unit tests, and lint.
- A real-flavor build and unit tests.
- Staging compilation.
- Prod release compilation with R8.

Do not hide new lint problems by broadly disabling checks.

## 6. Review and test gates

After each agent handoff:
1. Inspect the actual diff and all reported changed files.
2. Check file ownership and unexpected modifications.
3. Review shared contracts, architecture, duplicate logic, data integrity,
   session boundaries, lifecycle, navigation, and resource handling.
4. Run the relevant tests yourself against the integrated tree.
5. Fix findings or send a precise corrective task to the responsible agent.
6. Re-run checks affected by the fixes.

A subagent saying “tests pass” is not final evidence.
Do not repeatedly run unrelated tests after a stable result without a reason.

Mandatory regression scenarios:
- Old refresh/profile callbacks after logout or account change.
- Same-account re-login while old requests are running.
- Concurrent 401 responses.
- Refresh network failure, rate limit, and server failure.
- Income, expense, transfer, archive, and void behavior.
- Exact budget limits, overspending, shortfall, and period restrictions.
- Overflow and timezone boundaries.
- Existing mock JSON loading and failed-write recovery.
- Filter replacement during an in-flight request.
- Failed pagination followed by retry.
- Confirmed mutation followed by refresh failure.
- Stable transaction idempotency after recreation and uncertain outcomes.
- Rotation without duplicate navigation or success events.
- Correct quick-create entity selection.
- Demo isolation across flavors.

Use the project's existing JUnit, Retrofit transport tests, and Android
instrumentation facilities. Add a testing dependency only if a required
behavior cannot reasonably be tested with the existing setup.

From android/, run:

.\gradlew.bat :app:assembleMockDebug :app:testMockDebugUnitTest :app:lintMockDebug
.\gradlew.bat :app:assembleDevDebug :app:testDevDebugUnitTest
.\gradlew.bat :app:assembleStagingDebug :app:assembleProdRelease
.\gradlew.bat :app:connectedMockDebugAndroidTest

Use ./gradlew on non-Windows environments.

Run instrumentation on API 24 and API 36 when available.
Use staging, not production, for any authorized backend smoke testing.

If checks cannot run:
- Record the exact command and environmental failure.
- Attempt reasonable authorized recovery.
- Continue independent implementation and review.
- Mark that validation as blocked or not run.
- Never report the phase or project as fully verified.

## 7. Completion and reporting

Continue until all seven phases are implemented, integrated, reviewed, and
validated, or an actual blocker prevents further progress.

Do not finish with “I can continue” while authorized work remains.
If blocked, state the exact dependency on user input or environment access.
Do not invent a workaround that violates access or compatibility constraints.

The final report should be understandable without reading tool logs:
- What changed and why.
- Which phases are complete.
- Key architecture and public/internal contract changes.
- Tests actually executed and their results.
- Checks not run and the reason.
- Any behavior changes, remaining risks, or blockers.
- Confirmation that pre-existing user edits were preserved.

Use plain language. Keep progress and final reports in Vietnamese.
Use English for code identifiers, agent briefs, and technical artifacts
unless existing project conventions require otherwise.

Start now by reconstructing the working state, assigning the bounded Explorer
task, and establishing the baseline. Then execute the phases in order.