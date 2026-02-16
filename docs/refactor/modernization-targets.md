# LongCare Modernization Targets (D1-D14)

本文档定义本轮现代化改造的可量化目标、统计口径与验收阈值。

## Scope

- 项目路径：`/Users/yuyingui/StudioProjects/longcare`
- 周期：D1-D14（两周）
- 基线时间：2026-02-16

## Current Snapshot (Baseline)

来源：`/Users/yuyingui/StudioProjects/longcare/docs/refactor/baseline-metrics.md` 最近一次记录（2026-02-16 16:05:23 +0800）

| Metric | Current |
|---|---:|
| `:app:compileDebugKotlin` | 13s |
| `:app:testDebugUnitTest` | 113s |
| `:app:assembleDebug` | 71s |
| APK Size | 46M (48208268 bytes) |
| Dex File Count | 35 |
| Module Count (`settings.gradle.kts`) | 10 |

## Two-Week Targets

| Category | Metric | Target | Rule |
|---|---|---:|---|
| Build | `:app:compileDebugKotlin` | <= 12s | 在同机同负载条件下对比 |
| Build | `:app:testDebugUnitTest` | <= 95s | 允许阶段性波动，收官前达标 |
| Build | `:app:assembleDebug` | <= 60s | 以 clean baseline 为准 |
| Artifact | APK Size | <= 48MB | 新增功能前提下体积可控 |
| Artifact | Dex File Count | <= 36 | 不允许无业务收益增长 |
| Architecture | Legacy 新增违规数 | 0 | 允许历史债务，禁止新增债务 |
| Architecture | `AppNavigation.kt` 文件长度 | <= 300 行 | 逐步拆分后收敛 |
| Modularity | `app/features` 代码量 | -30% | 以迁移开始时统计值为基准 |
| Quality | CI 关键门禁通过率 | 100% | 架构边界 + 模块 API + Gradle 稳定性 |

## Measurement Commands

```bash
cd /Users/yuyingui/StudioProjects/longcare

# 构建基线
BASELINE_CLEAN_BEFORE_RUN=true ./scripts/quality/collect_build_baseline.sh \
  /Users/yuyingui/StudioProjects/longcare/docs/refactor/baseline-metrics.md \
  /tmp/longcare_baseline_logs

# 架构边界
bash /Users/yuyingui/StudioProjects/longcare/scripts/quality/verify_architecture_boundaries.sh .
bash /Users/yuyingui/StudioProjects/longcare/scripts/quality/verify_module_api_visibility.sh \
  /Users/yuyingui/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare .

# Gradle 稳定性
bash /Users/yuyingui/StudioProjects/longcare/scripts/quality/verify_gradle_stability.sh
```

## D1-D3 Acceptance Criteria

- D1:
  - 已追加一条新的 baseline run（含 compile/test/assemble、APK、Dex 指标）。
  - 已固化当前值与目标值（本文档）。
- D2:
  - convention plugin 承接 Android library/Kotlin 公共配置。
  - core/feature 模块删除重复编译配置（不影响构建）。
- D3:
  - 架构边界脚本覆盖 legacy `app/features`。
  - 对 legacy 违规采用“基线锁定 + 禁止新增”策略。

## Notes

- 本文档只定义“目标与口径”，不替代详细实施任务清单。
- 若任一核心指标恶化超过 15%，必须在当日进展中记录原因与回滚/修复计划。
