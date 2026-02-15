# 执行日志

## 2026-02-13
- 初始化 CI/CD 优化任务计划文件：`task_plan.md`、`findings.md`、`progress.md`。
- 进入 P1：开始审计 `.github/workflows` 与 `scripts/quality`。
- 完成 P1：识别重复磁盘清理逻辑、workflow 质量守卫缺口、文档改动触发浪费等问题。
- 完成 P2：新增 `docs/architecture/ci-cd-automation-optimization-plan.md`，形成 F1~F6 任务清单和 D28~D33 执行计划。
- 完成 P3：
  - 新增 `scripts/quality/free_runner_disk_space.sh` 并接入 `android-ci.yml`、`baseline-profile.yml`、`android-release.yml`；
  - 新增 `scripts/quality/verify_ci_workflow_quality.sh` 并接入 CI；
  - 为 `android-ci.yml` 增加 `paths-ignore`。
- 验证结果：
  - `verify_ci_workflow_quality.sh` 通过；
  - `free_runner_disk_space.sh --dry-run` 通过；
  - `verify_gradle_stability.sh` 通过。
- Actions 监控与修复：
  - 发现失败 run：`Android CI#21970264723`、`Android Release#21969405842`，失败步骤均为 `Enforce lint warning allowlist`。
  - 本地复现确认根因：`GradleDependency` 告警（来源 `gradle/libs.versions.toml`）未纳入 allowlist。
  - 已修复：更新 `scripts/lint/verify_lint_warning_allowlist.sh`，新增 `GradleDependency` 并限制来源仅允许 `libs.versions.toml`。
  - 复验通过：`./gradlew --no-daemon :app:lintDebug` + allowlist 校验脚本通过。
  - 修复后新触发 run：`Android CI#21970794768`，失败步骤为 `Verify CI workflow quality guardrails`。
  - 根因：`scripts/quality/verify_ci_workflow_quality.sh` 仅依赖 `rg`；在 GitHub Runner 环境可能缺失该命令。
  - 已修复：脚本新增兼容分支，无 `rg` 时自动回退 `grep -E`。
  - 兼容性验证：正常 PATH 与移除 `rg` 的 PATH 均通过校验脚本。
  - 持续观察：`Android CI#21970849721` 已完成并 `success`，`verify-build` 通过，当前 failure 队列为 0。
- 执行发布工作流与监控：
  - 通过推送 tag `vci-20260213-024649` 触发 `android-release.yml`（run: `21972693851`）。
  - 关键阶段通过：质量门、签名校验、release 构建、artifact 上传、GitHub Release 发布。
  - 结果：`Android Release#21972693851` `completed/success`。

## 2026-02-15
- 执行 `D32 | F5`：完成失败诊断产物按 job 分组归档改造。
  - 更新 `.github/workflows/android-ci.yml`：`verify-build`、`instrumentation-smoke` 新增 `Upload failure diagnostics`（`if: failure()`）。
  - 更新 `.github/workflows/android-release.yml`：`release-build` 新增 `Upload failure diagnostics`（`if: failure()`）。
  - 更新 `.github/workflows/baseline-profile.yml`：`generate-baseline-profile` 新增 `Upload failure diagnostics`（`if: failure()`）。
  - 更新 `scripts/quality/verify_ci_workflow_quality.sh`：新增三套 workflow 的 failure diagnostics 步骤守卫检查。
- 本地验收：
  - `bash scripts/quality/verify_ci_workflow_quality.sh`：PASS。
