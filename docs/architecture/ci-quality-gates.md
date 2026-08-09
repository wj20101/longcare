# CI Quality Gates

## Purpose

This document is the source of truth for local and CI quality gate layering.
It defines the execution tiers, the `preflight_local.sh` modes, and the default
ownership of each quality script.

## Tier Definitions

- `local-fast`: fast local developer checks that should run before push.
- `ci-required`: checks that must pass in normal CI validation.
- `release-required`: checks required for release confidence/sign-off.
- `observability-only`: reporting, metrics, and monitoring; does not block merge by itself.

## Gate Registry

The canonical descriptive metadata for high-value gates lives in:

- `scripts/quality/quality_gate_registry.json`

The registry is the canonical governance metadata contract for high-value gates. Blocking CI and
release workflows explicitly execute their matching checks; generated lint checks run after Lint has
created its report.

Within the registry:

- `id` is the unique stable machine/integration key
- `name` is display text and may be presented to humans

Current schema contract:

- `version`: schema revision for compatibility-aware consumers.
- `gates`: list of high-value gate metadata entries.
- `id`: unique stable machine/integration key for a gate.
- `name`: human-facing gate label.
- `layer`: governance tier target (`local-fast`, `ci-required`, `release-required`, `observability-only`).
- `blocking`: whether the gate is intended to block at its target layer.
- `owner`: accountable team for policy and remediation direction.
- `source_of_truth`: canonical file/script/policy location for the rule.
- `likely_fix`: concise default remediation guidance for diagnostics.

High-value inclusion rule:

- Include gates that are merge/release meaningful, show recurring governance drift risk, or materially benefit from explicit ownership and remediation metadata.

The registry defines:

- gate ownership
- gate layer
- source of truth
- likely remediation path

Runner scripts and workflows should emit diagnostics that align with this registry.

## Local Preflight Modes

`scripts/quality/preflight_local.sh` is the local entrypoint.

| Mode | What runs |
|---|---|
| `local-fast` (default) | `check_new_files_guard.sh`, `verify_architecture_boundaries.sh`, `verify_module_dependency_whitelist.sh`, `verify_module_api_visibility.sh` |
| `--changed-only` | same baseline intent, but skips non-relevant checks when changed paths do not require them |
| `--full` | `local-fast` + `./gradlew :app:compileDebugKotlin` + `./gradlew :app:testDebugUnitTest` |
| `--release` | `--full` + `scripts/quality/run_quality_gate.sh` |

## Local Preflight Assumptions

### Required tooling

- Shell: `bash`
- SCM: `git` (used by changed-scope detection)
- Pattern scan: `rg` is preferred; `preflight_local.sh` falls back to `grep` for changed-path matching when `rg` is unavailable
- Build toolchain: `./gradlew` and a working local Gradle/Android setup for `--full` and `--release`
- Snapshot dependencies for release mode: `jq` (required by `collect_quality_snapshot.sh`)

### `--changed-only` base-ref behavior

- Changed-only mode tries to use a strong branch base in this order:
  1. `BASE_REF` (if valid)
  2. `origin/$GITHUB_BASE_REF` (if available)
  3. `origin/master`
  4. `origin/main`
- If a strong base cannot be resolved, changed-only mode does not trust partial diffs.
  It falls back to a broader safe behavior:
  - `check_new_files_guard.sh` switches to a full frozen-directory scan
  - `preflight_local.sh` runs full `local-fast` baseline checks instead of skip-based filtering
- This fallback avoids false-green outcomes on multi-commit branches or shallow/limited git history.

### Lint stale-waiver enforcement split

- `scripts/lint/verify_lint_warning_allowlist.sh` defaults `LINT_ENFORCE_UNUSED_WAIVERS=auto`.
- In local runs (non-`GITHUB_ACTIONS`), `auto` enforces stale-waiver failures as blocking.
- In CI (`GITHUB_ACTIONS=true`), `auto` keeps stale-waiver findings advisory (non-blocking) to reduce post-merge noise-driven flakes.
- CI can still run strict stale-waiver enforcement by setting `LINT_ENFORCE_UNUSED_WAIVERS=true`.

## Script Catalog

The table below reflects current runner defaults and execution behavior today.
Until follow-up alignment tasks land, treat the registry as target-state metadata contract,
not as an automatic execution override.

## Workflow Responsibilities

### Blocking policy split

- Blocking CI/CD is build-only.
- Blocking workflows must not fail solely because of unit tests, UI assertions, business regression checks, or business journey checks.
- Those broader correctness checks may still run in dedicated workflows or release validation, but they are not part of the blocking compile/build gate set.
- Quality snapshot collection remains available for local or observability-oriented use, but it is no longer part of the blocking Android CI or Android Release workflow path.

### Android CI

- `Android CI` is the primary blocking merge workflow for build validation.
- It owns compile/build confidence, lint, module policy, architecture policy, and CI governance checks needed for normal pull-request validation.
- It should not be redefined as the owner of business journey coverage or product-level regression sign-off.

### Android Release

- `Android Release` is the release-sign-off workflow.
- It owns release packaging confidence, release-only policy checks, and any higher-cost validation required before shipping.
- It may depend on `Android CI` being green first, but it is responsible for release readiness rather than day-to-day merge blocking.

### Face SDK Migration Check

- `Face SDK Migration Check` is a specialized blocking build-only workflow for Tencent face SDK source switching.
- Its responsibility is limited to verifying that the app still builds from the Maven-published face SDK path using the required build task set: `:app:compileDebugKotlin`, `:app:lintDebug`, `:app:processReleaseMainManifest`, and `:app:assembleDebug`.
- It no longer blocks on unit tests, UI assertions, business regression checks, or business journey checks.
- It may still enforce `scripts/quality/verify_release_exported_components.sh` because exported-component manifest safety remains a release/build safety invariant for the produced app package and release manifest path.

| Script | Purpose | Default Execution Layer | Common Failure Modes | Likely Remediation |
|---|---|---|---|---|
| `scripts/quality/preflight_local.sh` | Unified local preflight orchestration | `local-fast` | One or more child gates fail | Fix failing child gate first, then rerun the same mode |
| `scripts/quality/check_new_files_guard.sh` | Block new Kotlin files in frozen legacy directories using allowlists | `local-fast` | New file added under `app/.../features/**` not in allowlist | Move code to `feature/*`, or inline into an allowlisted file path |
| `scripts/quality/verify_architecture_boundaries.sh` | Enforce layered architecture, freeze rules, size thresholds | `local-fast` | Cross-layer imports, frozen legacy violations, oversized files | Remove forbidden imports, migrate code to proper module, split large files |
| `scripts/quality/verify_module_dependency_whitelist.sh` | Enforce Gradle module dependency allowlist | `local-fast` | New module edge not in allowlist, stale allowlist module entry | Update module dependencies to allowed edges or clean allowlist drift |
| `scripts/quality/verify_module_api_visibility.sh` | Enforce API visibility boundaries and contract ownership | `local-fast` | Internal package imports, repository contracts outside domain | Move contracts to `core/domain`, stop importing internal symbols directly |
| `scripts/quality/verify_no_tracked_keystore_files.sh` | Block tracked keystore artifacts | `ci-required` | Keystore file tracked by git | Remove file from index/history and use secret distribution |
| `scripts/quality/verify_release_exported_components.sh` | Validate exported component allowlist for release | `ci-required` | AndroidManifest exported mismatch | Fix manifest export settings or allowlist policy |
| `scripts/quality/verify_vendor_sdk_release_readiness.sh` | Block production on known vendor TLS/16 KB/R8 findings | `release-required` | QLZ weak TLS or Tencent face binary warnings remain | Replace the vendor artifacts and rerun Lint/SDK regression |
| `scripts/lint/verify_lint_warning_allowlist.sh` | Enforce lint waiver policy from lint report | `ci-required` | New lint warning not allowlisted | Fix lint issue or formally manage waiver policy |
| `scripts/lint/verify_lint_ignore_policy.sh` | Enforce lint ignore policy integrity | `ci-required` | Disallowed lint ignore entry | Remove forbidden ignore and fix root warning |
| `scripts/quality/verify_jetpack_compat_apis.sh` | Guard Jetpack API compatibility baselines | `ci-required` | Regression in guarded API usage | Revert incompatible API usage or update compatible implementation |
| `scripts/quality/verify_baselineprofile_journeys.sh` | Validate baseline profile journey coverage | `ci-required` | Missing expected profile journey definition | Update baseline profile journeys/tests |
| `scripts/quality/verify_cancellation_guards.sh` | Guard coroutine cancellation patterns | `ci-required` | Missing cancellation checks in sensitive flows | Add structured cancellation handling and rerun |
| `scripts/quality/verify_no_empty_catch_blocks.sh` | Block empty catch blocks | `ci-required` | Empty catch found in Kotlin sources | Handle exception with explicit logic/logging or rethrow |
| `scripts/quality/verify_target_sdk_upgrade.sh` | Guard target SDK upgrade workflow alignment | `ci-required` | SDK version/workflow mismatch | Align constants and workflow upgrade procedure |
| `scripts/quality/verify_exact_alarm_permission_config.sh` | Guard exact alarm permission manifest policy | `ci-required` | Missing/wrong permission declaration | Correct manifest permissions and related runtime flow |
| `scripts/quality/verify_ci_workflow_quality.sh` | Enforce CI workflow governance rules | `ci-required` | Unpinned actions, timeout/retention policy drift | Pin action versions and restore workflow governance settings |
| `scripts/quality/affected-modules.sh` | Compute affected scope for CI task targeting | `ci-required` | Wrong base/head or diff scope assumptions | Provide explicit base ref or fix module path mapping |
| `scripts/quality/run_target_sdk_local_smoke.sh` | Local target-SDK emulator smoke verification | `ci-required` | Missing emulator/SDK target, smoke test failure | Provision matching AVD, fix smoke test failures |
| `scripts/quality/run_quality_gate.sh` | Unified quality snapshot entrypoint | `release-required` | Any required check fails in snapshot chain | Fix failed child check, rerun after lint/bootstrap fixes |
| `scripts/quality/verify_gradle_stability.sh` | Guard Gradle stability constraints | `release-required` | Wrapper/dependency stability violations | Restore approved Gradle config or dependency constraints |
| `scripts/quality/collect_quality_snapshot.sh` | Execute checks and emit machine/human-readable snapshot | `observability-only` | Missing tooling, child check failures in report | Install required tooling and resolve failing child checks |
| `scripts/quality/collect_ci_run_metrics.sh` | Collect CI duration/health metrics | `observability-only` | Missing run metadata or parse failures | Fix metric source path/format and retry collection |
| `scripts/quality/collect_build_baseline.sh` | Produce build baseline timing artifacts | `observability-only` | Build command failure or missing output | Fix build stability first, then recollect baseline |
| `scripts/quality/monitor_ci_health.sh` | Evaluate CI health trends against thresholds | `observability-only` | Threshold breach or missing metrics inputs | Reduce failure rate/duration regressions or tune data feed |
| `scripts/quality/free_runner_disk_space.sh` | CI runner disk cleanup helper | `observability-only` | Cleanup not enough, path assumptions outdated | Update cleanup targets or increase runner capacity |

## Governance Files Used By Freeze Rules

- `scripts/quality/legacy_feature_files_allowlist.txt`
- `scripts/quality/architecture_legacy_imports_allowlist.txt`
- `scripts/quality/architecture_legacy_import_budget.txt`
