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

## 3. 逐日执行计划（D28~D33）

| 日程 | 对应任务 | 具体文件改动清单 | 当日验收门禁 | 状态 |
|---|---|---|---|---|
| D28 | F1 | `docs/architecture/ci-cd-automation-optimization-plan.md` | 文档包含审计结论+任务清单+验收标准 | DONE |
| D29 | F2 | `scripts/quality/free_runner_disk_space.sh`、`.github/workflows/android-ci.yml`、`.github/workflows/baseline-profile.yml`、`.github/workflows/android-release.yml` | 磁盘清理脚本统一接入并可本地 dry-run 验证 | DONE |
| D30 | F3 | `scripts/quality/verify_ci_workflow_quality.sh`、`.github/workflows/android-ci.yml`、`.github/workflows/baseline-profile.yml`、`.github/workflows/android-release.yml` | workflow 质量守卫脚本通过并接入 CI | DONE |
| D31 | F4 | `.github/workflows/android-ci.yml` | 纯文档改动不触发 android-ci（基于 paths-ignore） | DONE |
| D32 | F5 | `.github/workflows/android-ci.yml`、`.github/workflows/android-release.yml`、`.github/workflows/baseline-profile.yml` | 失败诊断产物按 job 结构化上传 | TODO |
| D33 | F6 | `.github/workflows/*.yml`（可复用 workflow 抽象） | 重复步骤收敛且功能一致 | TODO |

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
