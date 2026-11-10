# Basic CI — Phase 2

| Workflow | Trigger | Steps | Required check name |
| --- | --- | --- | --- |
| `backend-ci.yml` | PR targeting `develop` / `main`, manual dispatch | JDK 21 → Maven tests → package jar | `Backend Build and Test` |
| `android-ci.yml` | PR targeting `develop` / `main`, Android/workflow pushes, manual dispatch | JDK 25 → debug APK → JVM unit tests | `Android Build and Test` |
| Admin | Deferred by request | No `package.json` or application source yet | Not enabled |

No CD or production deployment is configured. There is no auto-merge, release signing, app publication
or dependency on a running backend/database/emulator. Both workflows use read-only repository permissions,
cancel superseded runs, and retain reports/build artifacts for seven days.

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
In GitHub Settings → Rules / Branch protection, require pull requests and require the two check names
listed above on both branches. Without branch protection, a red CI check does not itself prevent merging.
No GitHub ruleset is modified by this local setup.

PR workflows deliberately have no path filters so required checks do not remain pending on unrelated PRs.
Android push runs still use path filters to avoid unnecessary feature-branch builds.
Manual-dispatch UI availability requires the workflow on the default branch.

Download APK, jar and test reports from Actions run artifacts. A local build or actionlint pass does not
prove Ubuntu-hosted SDK setup, GitHub caching or artifact uploads; verify those in the first pushed run.

Admin CI and browser integration are added when the admin application and its lint/test/build scripts exist.

References: [required status checks](https://docs.github.com/en/pull-requests/reference/status-checks)
and [skipped required workflows](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks).

## Local validation

Both workflows passed actionlint. The backend test/package commands succeeded with 13 smoke/unit tests;
two additional PostgreSQL integration tests passed outside the basic CI pipeline. The Android commands
succeeded against an isolated `feature/frontend-auth@ac71557` snapshot, reusing valid Gradle task outputs.
No hosted Actions run or branch-protection change is claimed.
