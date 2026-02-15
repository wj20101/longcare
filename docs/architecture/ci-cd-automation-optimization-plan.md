# CI/CD 与自动化优化计划（增量阶段）

## 1. 审计范围与结论

审计对象：
- `.github/workflows/android-ci.yml`
- `.github/workflows/baseline-profile.yml`
- `.github/workflows/android-release.yml`
- `.github/workflows/face-sdk-migration-check.yml`
- `scripts/quality/*`

结论（2026-02-13）：
- 现有 CI/CD 基础门禁较完整，但存在可持续优化空间：
  1. workflow 间存在重复脚本片段（runner 磁盘清理）；
  2. 缺少针对 workflow 规范本身的自动化守卫；
  3. `android-ci` 对纯文档改动仍会触发，存在资源浪费；
  4. CI/CD 优化事项尚未形成单独台账，不利于持续迭代。

## 2. 优化事项清单（按优先级）

| ID | 优化项 | 优先级 | 目标 |
|---|---|---|---|
| F1 | 输出 CI/CD 优化台账文档 | P0 | 明确问题、任务、文件清单、验收标准 |
| F2 | 抽取 runner 磁盘清理脚本并统一调用 | P0 | 去重并统一最小可用磁盘门禁 |
| F3 | 新增 workflow 质量守卫脚本并接入流水线 | P0 | 防止并发/超时/稳定性门禁被误删 |
| F4 | `android-ci` 增加 `paths-ignore` | P1 | 降低纯文档变更造成的 CI 资源消耗 |
| F5 | 失败诊断产物结构优化（按 job 分组） | P1 | 提升故障定位效率 |
| F6 | release/baseline 可复用 workflow 抽象 | P2 | 进一步减少重复配置与维护成本 |
| F7 | face-sdk migration workflow 规范统一 | P1 | 收敛环境初始化、并发控制与失败诊断标准 |
| F8 | workflow 最小权限守卫精确校验 | P1 | 防止 `permissions` 回退导致权限扩大 |
| F9 | action 版本稳定性守卫 | P1 | 防止可变引用（`@main/@master`）引入不确定性 |
| F10 | artifact action 版本固定守卫 | P1 | 防止上传产物 action 版本漂移导致兼容风险 |

## 3. 逐日执行计划（D28~D40）

| 日程 | 对应任务 | 具体文件改动清单 | 当日验收门禁 | 状态 |
|---|---|---|---|---|
| D28 | F1 | `docs/architecture/ci-cd-automation-optimization-plan.md` | 文档包含审计结论+任务清单+验收标准 | DONE |
| D29 | F2 | `scripts/quality/free_runner_disk_space.sh`、`.github/workflows/android-ci.yml`、`.github/workflows/baseline-profile.yml`、`.github/workflows/android-release.yml` | 磁盘清理脚本统一接入并可本地 dry-run 验证 | DONE |
| D30 | F3 | `scripts/quality/verify_ci_workflow_quality.sh`、`.github/workflows/android-ci.yml`、`.github/workflows/baseline-profile.yml`、`.github/workflows/android-release.yml` | workflow 质量守卫脚本通过并接入 CI | DONE |
| D31 | F4 | `.github/workflows/android-ci.yml` | 纯文档改动不触发 android-ci（基于 paths-ignore） | DONE |
| D32 | F5 | `.github/workflows/android-ci.yml`、`.github/workflows/android-release.yml`、`.github/workflows/baseline-profile.yml`、`scripts/quality/verify_ci_workflow_quality.sh` | 失败诊断产物按 job 结构化上传 | DONE |
| D33 | F6 | `.github/actions/android-build-env/action.yml`、`.github/workflows/android-ci.yml`、`.github/workflows/android-release.yml`、`.github/workflows/baseline-profile.yml`、`scripts/quality/verify_ci_workflow_quality.sh` | 重复步骤收敛且功能一致 | DONE |
| D34 | F7 | `.github/workflows/face-sdk-migration-check.yml`、`scripts/quality/verify_ci_workflow_quality.sh` | face-sdk workflow 与主流水线守卫标准一致 | DONE |
| D35 | F8 | `scripts/quality/verify_ci_workflow_quality.sh`、`docs/architecture/ci-cd-automation-optimization-plan.md`、`progress.md` | 权限守卫可阻断 read/write 配置回退 | DONE |
| D36 | F9 | `scripts/quality/verify_ci_workflow_quality.sh`、`docs/architecture/ci-cd-automation-optimization-plan.md`、`progress.md` | Action 引用稳定性守卫可阻断可变版本 | DONE |
| D40 | F10 | `scripts/quality/verify_ci_workflow_quality.sh`、`docs/architecture/ci-cd-automation-optimization-plan.md`、`progress.md` | 上传产物 action 版本固定守卫可阻断旧版本回归 | DONE |

## 4. 本轮已执行改动明细

1. 新增脚本：统一清理 runner 磁盘并校验最小剩余空间  
   - `scripts/quality/free_runner_disk_space.sh`

2. 新增脚本：CI workflow 质量守卫（并发、超时、稳定性、脚本接入）  
   - `scripts/quality/verify_ci_workflow_quality.sh`

3. workflow 改造：统一调用磁盘清理脚本、接入 workflow 守卫  
   - `.github/workflows/android-ci.yml`
   - `.github/workflows/baseline-profile.yml`
   - `.github/workflows/android-release.yml`

4. workflow 触发优化：`android-ci` 增加 `paths-ignore`  
   - `.github/workflows/android-ci.yml`

5. workflow 规范统一：`face-sdk-migration-check` 接入共享环境 action、并发控制、失败诊断归档  
   - `.github/workflows/face-sdk-migration-check.yml`
   - `scripts/quality/verify_ci_workflow_quality.sh`

6. workflow 安全守卫升级：最小权限（`permissions`）精确校验  
   - `scripts/quality/verify_ci_workflow_quality.sh`

7. workflow 供应链稳定性守卫：阻断可变 action 引用  
   - `scripts/quality/verify_ci_workflow_quality.sh`

8. workflow artifact action 稳定性守卫：固定 `upload-artifact@v6`  
   - `scripts/quality/verify_ci_workflow_quality.sh`

## 5. 验收记录（本轮）

- `bash scripts/quality/verify_ci_workflow_quality.sh`：PASS
- `bash scripts/quality/free_runner_disk_space.sh --dry-run --min-free-mb 1024`：PASS
- `bash scripts/quality/verify_gradle_stability.sh`：PASS

## 6. Actions 运行监控与修复记录（2026-02-13）

- 发现失败运行：
  - `Android CI`：`21970264723`
  - `Android Release`：`21969405842`
- 失败步骤一致：`Enforce lint warning allowlist`
- 根因：
  - lint 报告出现 `GradleDependency`（`gradle/libs.versions.toml` 中可升级依赖提示）；
  - allowlist 脚本未纳入该告警 ID，导致 CI 误阻断。
- 修复：
  - 更新 `scripts/lint/verify_lint_warning_allowlist.sh`：
    - 增加 `GradleDependency` 到 allowlist；
    - 严格限制来源仅允许 `gradle/libs.versions.toml`，避免放宽其它源告警。
- 修复后本地验证：
  - `./gradlew --no-daemon :app:lintDebug`：PASS
  - `bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt`：PASS

- 二次观察（修复后继续监控）：
  - 新触发 run：`Android CI` `21970794768`，失败于 `Verify CI workflow quality guardrails`。
  - 根因：守卫脚本只使用 `rg`，在 runner 环境下命令可用性不稳定。
  - 二次修复：`scripts/quality/verify_ci_workflow_quality.sh` 增加 `grep -E` fallback。
  - 验证：
    - `bash scripts/quality/verify_ci_workflow_quality.sh`：PASS
    - `PATH=/usr/bin:/bin bash scripts/quality/verify_ci_workflow_quality.sh`：PASS
- 持续监控结果：
  - 修复后 run `21970849721`：`success`
  - 失败队列扫描：`status=failure` 返回 0 条，当前无待修复 workflow run。

## 7. Android Release 执行记录（2026-02-13）

- 触发方式：推送 tag `vci-20260213-024649`（匹配 `android-release.yml` 的 `push.tags: v*`）
- workflow run：`21972693851`
- 运行结果：`completed/success`
- 关键步骤：
  - `Verify quality gates`：PASS
  - `Build release APK and AAB`：PASS
  - `Upload release artifacts`：PASS
  - `Publish artifacts to GitHub Releases`：PASS
- 产物发布：
  - Release 页面：`https://github.com/yyg20101/longcare/releases/tag/vci-20260213-024649`
  - 包含 APK、AAB、mapping 与 checksum 文件。

## 8. 失败诊断产物分层归档执行记录（2026-02-15）

- 任务：`D32 | F5`
- 改动文件：
  - `.github/workflows/android-ci.yml`
  - `.github/workflows/android-release.yml`
  - `.github/workflows/baseline-profile.yml`
  - `scripts/quality/verify_ci_workflow_quality.sh`
- 具体改动：
  - 在 `android-ci` 的 `verify-build`、`instrumentation-smoke` 增加 `if: failure()` 的诊断产物上传步骤；
  - 在 `android-release` 的 `release-build` 增加 `if: failure()` 的诊断产物上传步骤；
  - 在 `baseline-profile` 的 `generate-baseline-profile` 增加 `if: failure()` 的诊断产物上传步骤；
  - 诊断包命名统一为 `workflow-job-run` 结构（含 `job/run_id/run_attempt`）；
  - 守卫脚本新增校验：三套 workflow 必须包含 `Upload failure diagnostics`。
- 验证：
  - `bash scripts/quality/verify_ci_workflow_quality.sh`：PASS
  - `Android CI#22031772685`（commit `8ff77d2`）：`completed/success`
  - `Android CI#22031692880`（commit `931c3c2`）：`completed/success`

## 9. Reusable workflow 抽象执行记录（2026-02-15）

- 任务：`D33 | F6`
- 改动文件：
  - `.github/actions/android-build-env/action.yml`（新增）
  - `.github/workflows/android-ci.yml`
  - `.github/workflows/android-release.yml`
  - `.github/workflows/baseline-profile.yml`
  - `scripts/quality/verify_ci_workflow_quality.sh`
- 具体改动：
  - 新增复用 action：统一 JDK/Gradle/Android SDK 初始化与质量守卫执行；
  - `android-release`、`baseline-profile`、`android-ci` 改为调用共享 action，减少重复步骤；
  - 守卫脚本升级为支持“直接步骤 + 共享 action”双模式校验，并新增共享 action 接入校验。
- 验证：
  - `bash scripts/quality/verify_ci_workflow_quality.sh`：PASS

## 10. Face SDK workflow 规范统一执行记录（2026-02-15）

- 任务：`D34 | F7`
- 改动文件：
  - `.github/workflows/face-sdk-migration-check.yml`
  - `scripts/quality/verify_ci_workflow_quality.sh`
- 具体改动：
  - `face-sdk-migration-check` 新增 `concurrency`（`cancel-in-progress: true`）与最小权限 `permissions: contents: read`；
  - 移除重复的 JDK/Gradle 初始化，改为调用共享 action：`.github/actions/android-build-env`；
  - 启用 workflow 守卫执行（`run-workflow-quality-check: 'true'`）；
  - 新增 `Upload failure diagnostics` 步骤，失败时统一上传构建/测试/问题报告；
  - workflow 守卫脚本新增对 `face-sdk-migration-check` 的并发、权限、共享 action、守卫接入和失败诊断校验。
- 验证：
  - `bash scripts/quality/verify_ci_workflow_quality.sh`：PASS
  - `Android CI#22031556210`（commit `2794d97`）：`completed/success`
  - `Face SDK Migration Check#22031592571`（workflow_dispatch）：`completed/success`

## 11. Workflow 最小权限守卫升级执行记录（2026-02-15）

- 任务：`D35 | F8`
- 改动文件：
  - `scripts/quality/verify_ci_workflow_quality.sh`
  - `docs/architecture/ci-cd-automation-optimization-plan.md`
  - `progress.md`
- 具体改动：
  - 将权限守卫从“仅检测存在 `permissions` 块”升级为“检测最小权限值是否符合预期”；
  - 新增精确校验：
    - `android-ci` 与 `face-sdk-migration-check` 必须 `contents: read`；
    - `android-release` 必须 `contents: write`；
    - `baseline-profile` 必须同时具备 `contents: write` 与 `pull-requests: write`。
- 验证：
  - `bash scripts/quality/verify_ci_workflow_quality.sh`：PASS

## 12. Action 版本稳定性守卫执行记录（2026-02-15）

- 任务：`D36 | F9`
- 改动文件：
  - `scripts/quality/verify_ci_workflow_quality.sh`
  - `docs/architecture/ci-cd-automation-optimization-plan.md`
  - `progress.md`
- 具体改动：
  - 在 workflow 守卫中新增 action 版本稳定性校验：
    - 四条关键 workflow 必须使用 `actions/checkout@v6`；
    - 禁止使用可变 action 引用（`@main`、`@master`、`@HEAD`）。
  - 目标：降低上游 action 非预期变更导致的 CI 不确定性。
- 验证：
  - `bash scripts/quality/verify_ci_workflow_quality.sh`：PASS

## 13. Artifact Action 版本固定守卫执行记录（2026-02-15）

- 任务：`D40 | F10`
- 改动文件：
  - `scripts/quality/verify_ci_workflow_quality.sh`
  - `docs/architecture/ci-cd-automation-optimization-plan.md`
  - `progress.md`
- 具体改动：
  - 在 workflow 守卫中新增 `actions/upload-artifact@v6` 固定版本校验；
  - 增加反向守卫：阻断 `actions/upload-artifact` 旧版本（`v0-v5`）回归。
- 验证：
  - `bash scripts/quality/verify_ci_workflow_quality.sh`：PASS
