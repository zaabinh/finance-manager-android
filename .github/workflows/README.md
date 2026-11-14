# Basic CI — Phase 2

| Workflow | Trigger | Steps | Required check name |
| --- | --- | --- | --- |
| `backend-ci.yml` | PR targeting `develop` / `main`, manual dispatch | JDK 21 → Maven tests → package jar | `Backend Build and Test` |
| `android-ci.yml` | PR targeting `develop` / `main`, Android/workflow pushes, manual dispatch | JDK 25 → debug APK → JVM unit tests | `Android Build and Test` |
| `deploy-staging-checks.yml` | Deployment-code pushes / PRs targeting `develop` / `main`, manual dispatch | Bash syntax → mock-Docker regression tests | `Validate staging deployment` (path-filtered; do not require globally) |
| `deploy-staging.yml` | Backend/deployment-code pushes to `develop`, manual dispatch on `develop` | Maven tests → GHCR full-SHA image → SSH deployment → public readiness | Deployment workflow; not a PR check |
| Admin | Deferred by request | No `package.json` or application source yet | Not enabled |

Staging CD code is configured; [admin setup](../../docs/deployment/CD_STAGING.md) is still required before use.
Production deployment, auto-merge, release signing and app publication are not configured. Backend/Android CI
use read-only repository permissions and do not depend on a running backend/database/emulator. They cancel
superseded runs and retain reports/build artifacts for seven days.

The staging deployment publishes images using package write permission, uses the `staging` environment's SSH
secrets, and serializes deployments without cancelling a deployment in progress. All its jobs enforce the
`develop` ref, so manually selecting a feature branch cannot publish or deploy. Android-only changes do not deploy.
The separate staging checks use no staging secrets, do not contact the server and run on feature-branch pushes
that change deployment code. The script attempts image rollback on internal deployment failure; a later public
readiness failure fails the workflow without automatic rollback. Image rollback does not undo database migrations.

## Backend

The PR workflow runs on every PR into `develop` or `main`, without path filters:

```sh
cd backend
bash ./mvnw -B -ntp test
bash ./mvnw -B -ntp -DskipTests package
```

`package` only runs after tests pass. It skips repeating the already-passed tests. A nonzero exit from
either command fails the job; no `continue-on-error` or artificial success is used.
Smoke/security/error-handler tests use the test-only profile and do not need developer credentials.
The basic workflow does not verify real PostgreSQL/Flyway migrations. For that additional check on a
Docker-enabled machine, run `./mvnw -Ppostgres-it verify` before staging deployment.

## Android

JDK 25 matches the existing daemon toolchain file; Android Java source/target remains 11.
The existing Wrapper uses Gradle 9.5.0. CI installs Android SDK 37 and Build Tools 36.0.0, then runs:

```sh
cd android
bash ./gradlew --no-daemon --stacktrace --console=plain :app:assembleDebug :app:testDebugUnitTest
```

Compilation or test failure fails the job. Lint, emulator/instrumentation tests and Android-to-backend
health calls are outside this basic workflow. APK and JVM test reports are uploaded as artifacts.
No Android source, manifest, dependency or Gradle file is edited for CI.
`feature/frontend-auth` can receive these `.github` changes independently of backend features.

## PR enforcement

After committing/pushing the workflows, open a PR to `develop` or `main` and wait for real successful runs.
In GitHub Settings → Rules / Branch protection, require pull requests and require `Backend Build and Test`
and `Android Build and Test` on both branches. Without branch protection, a red CI check does not itself prevent merging.
No GitHub ruleset is modified by this local setup.

Backend and Android PR workflows deliberately have no path filters so required checks do not remain pending on unrelated PRs.
Staging checks are path-filtered and should not be a globally required check.
Android push runs still use path filters to avoid unnecessary feature-branch builds.
Manual-dispatch UI/API availability requires the workflow on the default branch (currently `main`); pushing a
feature branch alone does not make a new workflow discoverable for manual dispatch.

Download APK, jar and test reports from Actions run artifacts. A local build or actionlint pass does not
prove Ubuntu-hosted SDK setup, GitHub caching or artifact uploads; verify those in the first pushed run.

Admin CI and browser integration are added when the admin application and its lint/test/build scripts exist.

References: [required status checks](https://docs.github.com/en/pull-requests/reference/status-checks)
and [skipped required workflows](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks).

## Local validation

The original Backend CI and Android CI workflows passed actionlint. The backend test/package commands succeeded with 13 smoke/unit tests;
two additional PostgreSQL integration tests passed outside the basic CI pipeline. The Android commands
succeeded against an isolated `feature/frontend-auth@ac71557` snapshot, reusing valid Gradle task outputs.
No hosted Actions run or branch-protection change is claimed.
