# LongCare 文档索引

最后核对：2026-09-20。

本目录保存当前产品/工程说明、长期分析和必要专项资料。首次阅读先看根 [README](../README.md)，代码协作先看 [AGENT](../AGENT.md)。

## 整体分析与优化

- [项目整体分析与优化基线](analysis/project-review.md)：20 章，覆盖需求、实现、模块、数据、平台生命周期、测试发布、风险、优化顺序和验收矩阵。基线是 2026-09-20 的 `ea514003`；首页已注明后续 `cd483252` 的助手退役与单应用发布变化，主体旧数量/双包描述属于分析快照。
- [路线图与开放问题](architecture/roadmap-and-open-gaps.md)：维护当前可行动事项，与报告风险编号关联。
- [文档治理与完整清单](maintenance.md)：逐文件分类、事实归属、冲突处理、复核范围和检查命令。

## 当前专项文档

| 领域 | 主文档 | 内容 |
|---|---|---|
| 产品 | [产品概览](product/overview.md) | 角色、流程、规则与能力状态 |
| 架构 | [系统概览](architecture/system-overview.md) | 实际运行形态、模块与资源边界 |
| 构建 | [技术栈](architecture/tech-stack.md) | SDK、依赖、版本、变体和配置入口 |
| 导航 | [页面与路由](architecture/ui-and-screen-map.md) | 路由、owner、结果、页面归属与读卡检测 |
| 分层 | [依赖规则](architecture/dependency-rules.md) | 允许依赖、现状与后续约束 |
| 质量 | [CI 与门禁](architecture/ci-quality-gates.md) | 本地/CI/设备测试范围、发布与产物 |
| 决策 | [ADR-001](architecture/adr/ADR-001-layer-boundary.md) | 已接受的分层方向；不是全部当前实现清单 |
| 厂商 | [QLZ 接入](integrations/qlz-sdk.md) | BLE、SDK、H5、接口、风险与专项验收 |
| 定位 | [定位模块说明](../feature/location/src/main/kotlin/com/ytone/longcare/features/location/README.md) | 会话、上报、停止和恢复边界 |
| 历史合规 | [2026-05 隐私整改](compliance/2026-05-app-store-privacy-remediation.md) | 历史整改与当前外部待核项，不证明政策已上线 |
| 行为规格 | [OpenSpec 入口](../openspec/README.md) | 主规格、活动变更和历史归档的适用范围 |

## 事实与规范的关系

- 代码、Gradle、Manifest、workflow 和可执行测试证明当前实现；专项文档应准确描述这些事实。
- OpenSpec 主规格和已接受的变更定义行为契约。实现与契约不一致时先记录偏差，不自动把当前代码变成新的产品要求。
- 活动 change 可能只剩验收未完成；后续已落地规格可以替代其旧设计，须明确同步，不靠目录时间猜测。
- 本报告是有日期的优化基线，历史 ADR/archive/整改是特定时期证据，均不得覆盖后续当前契约。
- 仓库文档或 CLI 显示存在 APK，不证明该包来自当前 commit 或已通过本轮业务验收。

版本以 `constants.gradle.kts`、依赖以 `gradle/libs.versions.toml`、模块以 `settings.gradle.kts`、执行门禁以实际脚本/workflow 为准。`quality_gate_registry.json` 是门禁元数据，不代表其中所有门禁在每次命令都被执行。

## 文档维护规则

| 变更 | 必须同步 |
|---|---|
| 产品角色、流程、用户可见行为 | 产品概览＋相关 OpenSpec；跨域判断变化更新报告 |
| 模块、运行组件、平台边界 | 系统概览＋依赖规则＋相关路由归属 |
| 路由、返回、状态 owner | 页面地图＋对应行为规格/测试 |
| 版本、SDK、工具链、库 | 技术栈；版本 snapshot 检查；报告保留基线日期 |
| Room schema 或升级策略 | 系统概览＋依赖规则＋AGENT/OpenSpec 配置约束＋升级测试 |
| CI、脚本或发布 | CI 与门禁＋README 摘要＋相关主规格 |
| SDK、权限或信任边界 | 集成说明＋相关产品/架构说明；合规材料标明是否已外部复核 |
| 长期技术决策 | ADR；不得把待确认优化建议写成 Accepted |
| 新增、删除、移动 Markdown | 本索引（如为主入口）＋完整清单＋链接检查 |
| 活动 change 状态 | 任务真实勾选＋OpenSpec 入口；归档按技能流程执行 |

同一事实只指定一个详细主文档，其他文档保留必要摘要和链接。不要新建并行 task plan、progress、findings 或会话执行日志；OpenSpec 是变更规划入口。

人工维护的整体分析可放在 `docs/analysis/`，必须注明基线、来源、现状/推断/建议和验证边界。Lint 输出、性能快照、截图、构建日志等机器生成报告仍放在 `build/` 或 CI artifact，不复制到长期 Markdown。

## 最小检查

```bash
python3 scripts/quality/verify_documentation.py
bash scripts/quality/preflight_local.sh --local-fast
openspec validate --all --strict --no-interactive
git diff --check
```

文档脚本检查全量 Markdown 清单、仓库内普通 Markdown 链接/标题锚点及当前技术栈指定版本；它不会执行外链访问或证明自然语言需求一致，也尚未接入 CI/preflight。修改业务代码仍须按风险执行对应测试。
