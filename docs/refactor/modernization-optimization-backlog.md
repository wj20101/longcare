# LongCare 现代化可优化项清单（按优先级）

更新时间：2026-02-16

## 当前事实（来自仓库现状）

- `app/src/main/kotlin/com/ytone/longcare/features`：`120` 个 Kotlin 文件，约 `23046` 行。
- `feature/*` 模块：`18` 个 Kotlin 文件，约 `296` 行。
- 架构遗留白名单：`scripts/quality/architecture_legacy_imports_allowlist.txt` 共有 `71` 条。
- `AppNavigation.kt` 已降至 `272` 行，并已加入脚本门禁（<= 300 行）。
- `AppNavGraphs.kt` 当前 `449` 行，已完成首轮抽离但仍偏大。

## P0（本周优先，直接影响演进效率）

| 优化项 | 现状问题 | 建议动作 | 验收标准 |
|---|---|---|---|
| 1. 继续去 Legacy 化（`app/features` -> `feature/*`） | 业务代码主要仍在 legacy 路径，模块边界收益未充分兑现 | 先迁移高变更频次链路（登录、首页、识别、服务流程），新需求禁止落在 `app/features` | `app/features` 代码量两周内下降 >= 30%，新增功能 0 处落入 legacy 目录 |
| 2. 消减架构白名单债务 | allowlist 仍有 71 条，当前策略是“冻结而非消减” | 建立按周燃尽目标（例如每周减少 10~15 条），每次迁移同步删白名单 | allowlist 条目持续下降且无新增违规 |
| 3. 继续拆分导航图 | `AppNavGraphs.kt` 仍 449 行，服务流程与工具页耦合 | 拆成 `ServiceFlowNavGraph`、`ToolingNavGraph`、`UserFlowNavGraph` 等注册函数/文件 | 单文件控制在 <= 300 行，导航变更影响范围收敛 |
| 4. 质量门禁前移到 CI 必经路径 | 本地脚本完备，但需确保 PR 必经 | 将架构边界、模块 API、Gradle 稳定性检查纳入主 PR 工作流强制门禁 | PR 未通过门禁无法合并 |

## P1（两周内完成，直接提升稳定性与性能）

| 优化项 | 现状问题 | 建议动作 | 验收标准 |
|---|---|---|---|
| 5. 构建性能持续压降 | 现有 baseline 记录中 `:app:testDebugUnitTest` 仍偏慢 | 以 `collect_build_baseline.sh` 建立周报；定位慢测并做分层/并行优化 | `:app:testDebugUnitTest` 降至目标阈值（<= 95s） |
| 6. Baseline Profile 流程闭环 | 已有 `:baselineprofile` 模块，但建议常态化执行 | 在 CI 定期生成/校验 profile，回归启动关键路径 | 启动与首屏指标稳定，profile 变更可追踪 |
| 7. 依赖兼容层清理（Legacy Support） | `app/build.gradle.kts` 仍有 `androidx.legacy.*` 依赖 | 梳理腾讯人脸 SDK 真实依赖，验证可移除后分阶段删除 | 移除后构建与核心流程回归通过 |
| 8. 规则型质量脚本统一报告 | 质量脚本多，但报告聚合度有限 | 增加统一输出（汇总 markdown/json）用于 PR 与周报 | 一次运行可得到完整质量快照 |

## P2（持续治理项）

| 优化项 | 现状问题 | 建议动作 | 验收标准 |
|---|---|---|---|
| 9. 安全与加密 API 债务清理 | `CryptoUtils` 存在 `@Deprecated` 迁移痕迹 | 统一到单一加密实现，补齐回归测试与迁移注释 | 废弃 API 调用清零或明确下线计划 |
| 10. 日志落盘与可观测性补全 | `LogExt.kt` 仍有 TODO（文件日志） | 引入结构化日志与可控落盘策略（分级、脱敏、轮转） | TODO 清零，故障定位信息可追踪 |
| 11. 资源备份规则完善 | `data_extraction_rules.xml` 存在 TODO | 明确 include/exclude 策略并联动数据安全审查 | 备份行为可预期且通过回归 |

## 推荐执行顺序

1. P0-1 + P0-2（先削减结构性债务，避免继续累积）。
2. P0-3 + P0-4（稳定导航演进面与 CI 防线）。
3. P1-5 + P1-7（性能与依赖并行治理）。
4. P2 项作为每周固定治理窗口持续推进。
