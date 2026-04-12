# LongCare 现代化可优化项清单（按优先级）

更新时间：2026-02-16

## 当前事实（来自仓库现状）

- `app/src/main/kotlin/com/ytone/longcare/features`：`120` 个 Kotlin 文件，约 `23046` 行。
- `feature/*` 模块：`18` 个 Kotlin 文件，约 `296` 行。
- 架构遗留白名单：`scripts/quality/architecture_legacy_imports_allowlist.txt` 共有 `0` 条。
- 已增加白名单预算门禁：`scripts/quality/architecture_legacy_import_budget.txt`（当前阈值 `0`），CI 侧限制 allowlist 只减不增。
- `AppNavigation.kt` 已降至 `272` 行，并已加入脚本门禁（<= 300 行）。
- `AppNavGraphs.kt` 已收敛为入口编排文件（`10` 行），并拆分为 `AppNavGraphsEntry.kt`（`53` 行）、`AppNavGraphsServiceFlow.kt`（`255` 行）、`AppNavGraphsSupport.kt`（`181` 行）。
- 质量聚合脚本 `scripts/quality/collect_quality_snapshot.sh` 已落地，并接入 `android-ci.yml` 与 `android-release.yml`，可统一输出 Markdown/JSON 质量快照。
- `app/build.gradle.kts` 中 `androidx.legacy.*` 兼容依赖已移除，且 `compile + lint + unit test` 回归通过。
- `app/src/main/res/xml/data_extraction_rules.xml` 与 `backup_rules.xml` 已从示例 TODO 改为显式规则（default deny，云备份与设备迁移均排除应用私有域数据）。
- `LogExt.kt` 已补齐文件日志基础能力（初始化、写入、按日期与大小滚动、历史清理），并新增默认敏感信息脱敏（token/password/手机号/邮箱/证件号），`compile + lint + unit test` 回归通过。
- `MainApplication` 已统一 `KLogger` 初始化策略：日志目录固定为应用私有目录 `filesDir/logs`，并补齐 `LogExtTest` 脱敏回归用例（开关开启/关闭两条路径）。
- `CryptoUtils` 旧密钥生成签名（`generateAESKey(Int)`、`generateRSAKeyPair(Int)`）已移除，仅保留 `KeySize` 统一入口，避免旧 API 回流。

## P0（本周优先，直接影响演进效率）

| 优化项 | 现状问题 | 建议动作 | 验收标准 |
|---|---|---|---|
| 1. 继续去 Legacy 化（`app/features` -> `feature/*`） | 业务代码主要仍在 legacy 路径，模块边界收益未充分兑现 | 先迁移高变更频次链路（登录、首页、识别、服务流程），新需求禁止落在 `app/features` | `app/features` 代码量两周内下降 >= 30%，新增功能 0 处落入 legacy 目录 |
| 2. 消减架构白名单债务 | 已从“冻结”升级到“预算门禁”（超预算直接失败） | 建立按周燃尽目标（例如每周减少 10~15 条），每次迁移同步删白名单并下调预算值 | allowlist 条目持续下降且无新增违规 |
| 3. 导航图拆分维持轻量化 | 首轮拆分已完成，需防止新增路由回流到单点文件 | 保持分域注册（Entry/ServiceFlow/Support）并持续门禁 | 任一导航文件 <= 300 行，`AppNavGraphs.kt` 持续保持轻量入口 |
| 4. 质量门禁前移到 CI 必经路径 | 本地脚本完备，但需确保 PR 必经 | 将架构边界、模块 API、Gradle 稳定性检查纳入主 PR 工作流强制门禁 | PR 未通过门禁无法合并 |

## P1（两周内完成，直接提升稳定性与性能）

| 优化项 | 现状问题 | 建议动作 | 验收标准 |
|---|---|---|---|
| 5. 构建性能持续压降 | 现有 baseline 记录中 `:app:testDebugUnitTest` 仍偏慢 | 以 `collect_build_baseline.sh` 建立周报；定位慢测并做分层/并行优化 | `:app:testDebugUnitTest` 降至目标阈值（<= 95s） |
| 6. Baseline Profile 流程闭环 | 已有 `:baselineprofile` 模块，但建议常态化执行 | 在 CI 定期生成/校验 profile，回归启动关键路径 | 启动与首屏指标稳定，profile 变更可追踪 |
| 7. 依赖兼容层清理（Legacy Support） | 已完成首轮清理：`androidx.legacy.*` 已移除 | 持续跟踪腾讯人脸 SDK 上游变更，避免重新引入 legacy 依赖 | 保持移除状态，回归持续通过 |
| 8. 规则型质量脚本统一报告 | 已完成：质量快照脚本与 CI 接入 | 将质量快照用于 PR 审阅与周报沉淀 | 一次运行可得到完整质量快照 |

## P2（持续治理项）

| 优化项 | 现状问题 | 建议动作 | 验收标准 |
|---|---|---|---|
| 9. 安全与加密 API 债务清理 | 已完成第二阶段：旧密钥生成签名已删除，统一到 `KeySize` 入口 | 补充迁移注释与加密回归用例（含异常路径） | 旧 API 调用清零且无法新增，回归可追踪 |
| 10. 日志落盘与可观测性补全 | 已完成第二阶段：应用已统一私有目录日志策略（`filesDir/logs`），并新增脱敏回归用例 | 持续扩充敏感字段样本（如证件变体、Header 组合）并纳入周度回归 | 敏感信息默认不落盘，目录策略可追踪，故障定位信息可追踪 |
| 11. 资源备份规则完善 | 已完成：`data_extraction_rules.xml` / `backup_rules.xml` 显式 default-deny | 与数据安全评审确认是否需要白名单式放开非敏感配置 | 备份行为可预期且通过回归 |

## 推荐执行顺序

1. P0-1 + P0-2（先削减结构性债务，避免继续累积）。
2. P0-3 + P0-4（稳定导航演进面与 CI 防线）。
3. P1-5 + P1-7（性能与依赖并行治理）。
4. P2 项作为每周固定治理窗口持续推进。
