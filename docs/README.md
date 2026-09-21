# LongCare 文档索引与维护规则

最后核对：2026-09-20。当前实现基线：`4036a76a`。先读根 [README](../README.md) 的构建入口，协作约束见 [AGENT](../AGENT.md)。

## 当前文档与职责

每个主题指定一份详细主文档，其他入口只作摘要引用。维护角色是职责建议，具体负责人在相关 PR/Issue 确定。

| 领域 | 唯一详细主入口 | 内容 / 维护角色 |
|---|---|---|
| 整体分析与优化 | [项目整体分析](analysis/project-review.md) | 20 章需求、技术分析、风险、实施顺序和验收矩阵；技术负责人＋产品＋QA |
| 产品 | [产品概览](product/overview.md) | 角色、流程、规则与能力状态；产品＋业务移动端 |
| 架构与分层 | [系统概览](architecture/system-overview.md) | 模块、依赖规则、ADR-001、定位生命周期与平台边界；移动端架构 |
| 工具链 | [技术栈](architecture/tech-stack.md) | SDK、依赖、版本、变体及配置；移动端平台 |
| 导航 | [页面与路由](architecture/ui-and-screen-map.md) | 页面归属、owner、返回与恢复；业务移动端 |
| 质量与交付 | [CI 与门禁](architecture/ci-quality-gates.md) | 本地/CI/设备执行范围及发布；平台＋QA |
| 厂商评估 | [QLZ 接入](integrations/qlz-sdk.md) | BLE、SDK、H5、接口、风险和专项验收；移动端＋服务端＋厂商 |
| 历史合规 | [2026-05 隐私整改](compliance/2026-05-app-store-privacy-remediation.md) | 历史整改与外部待核项；资料维护人，不证明政策已上线 |

定位运行契约见[定位会话与生命周期](architecture/system-overview.md#定位会话与生命周期)，后续工作统一维护在[报告第 16～18 章](analysis/project-review.md#16-风险与优化事项登记)。不再单独维护路线图副本或手工逐文件清单。

## 事实与规范的关系

- 代码、Gradle、Manifest、workflow 和可执行测试证明当前实现；专项文档应准确描述这些事实。
- OpenSpec 主规格和已接受的变更定义行为契约。实现与契约不一致时先记录偏差，不自动把当前代码变成新的产品要求。
- 活动 change 可能只剩验收未完成；后续已落地规格可以替代其旧设计，须明确同步，不靠目录时间猜测。
- 整体分析是有日期的优化基线，历史 ADR/archive/整改是特定时期证据，均不得覆盖后续当前契约。
- 仓库文档或 CLI 显示存在 APK，不证明该包来自当前 commit 或已通过本轮业务验收。

版本以 `constants.gradle.kts`、依赖以 `gradle/libs.versions.toml`、模块以 `settings.gradle.kts`、执行门禁以实际脚本/workflow 为准。`quality_gate_registry.json` 是门禁元数据，不代表其中所有门禁在每次命令都被执行。

## 文档维护规则

| 变更 | 必须同步 |
|---|---|
| 产品角色、流程、用户可见行为 | 产品概览＋相关 OpenSpec；跨域判断变化更新报告 |
| 模块、运行组件、平台边界 | 系统概览＋相关路由归属 |
| 路由、返回、状态 owner | 页面地图＋对应行为规格/测试 |
| 版本、SDK、工具链、库 | 技术栈；版本 snapshot 检查；报告保留基线日期 |
| Room schema 或升级策略 | 系统概览＋AGENT/OpenSpec 配置约束＋升级测试 |
| CI、脚本或发布 | CI 与门禁＋README 摘要＋相关主规格 |
| SDK、权限或信任边界 | 集成说明＋相关产品/架构说明；合规材料标明是否已外部复核 |
| 长期技术决策 | 系统概览中的 ADR；不得把待确认优化建议写成 Accepted |
| 新增、删除、移动 Markdown | 本索引（全部当前文档）＋链接检查 |
| 活动 change 状态 | 任务真实勾选＋本页 OpenSpec 索引；归档按技能流程执行 |

同一事实只指定一个详细主文档，其他文档保留必要摘要和链接。不要新建并行 task plan、progress、findings 或会话执行日志；OpenSpec 是变更规划入口。

人工维护的整体分析可放在 `docs/analysis/`，必须注明基线、来源、现状/推断/建议和验证边界。Lint 输出、性能快照、截图、构建日志等机器生成报告仍放在 `build/` 或 CI artifact，不复制到长期 Markdown。

新增长期决策在系统概览 ADR 部分保留编号、状态、日期、背景、决策和代价；尚未接受的方案继续使用 OpenSpec change。历史决策不能随文档合并被改成新结论。

处理冲突时，先区分实现、契约、建议和历史，再查配置、代码、测试与已接受规格；修正主文档和引用，记录仍未闭合的偏差。活动变更的后续契约可以更新，未完成验收不得改成通过。历史归档保留原始时间背景，通过下面的替代关系说明适用边界。

## OpenSpec 规格与变更

OpenSpec 采用 delta-first，主规格不覆盖所有存量能力。`config.yaml` 提供规划上下文；版本、运行行为和执行范围仍须核对代码及测试。

### 如何阅读

- `specs/`：已同步的行为契约。重叠部分按最新已接受的主规格理解，不恢复旧实现。
- `changes/<name>/`：真实增量方案和任务；“planning complete”只代表产物齐全，不代表实现/验收全部完成。
- `changes/archive/`：历史决策与验收范围，保留当时信息。旧发布模式、旧网页方案或旧依赖版本不能直接用于当前开发。
- `config.yaml`：规划约束和工程上下文；版本和实现仍由代码核对。

### 当前未归档目录

| Change | 实际状态 | 当前适用说明 |
|---|---|---|
| [integrate-card-diagnostics-in-app](../openspec/changes/integrate-card-diagnostics-in-app/tasks.md) | 长按入口已由用户确认真机验收，读卡硬件验收待安排 | 登录页中央大 Logo 长按确认进入 NFC/R65C，无震动，退役助手与双包发布 |
| [publish-assistant-release-artifact](../openspec/changes/publish-assistant-release-artifact/tasks.md) | 任务全部勾选，相关主规格已同步，目录尚未归档 | 历史双应用同一 Release 方案；已由主应用内读卡及单应用发布变更替代，后续归档不得覆盖新契约 |
| [remove-confirmed-redundancy](../openspec/changes/remove-confirmed-redundancy/tasks.md) | 任务全部勾选，目录尚未归档 | 无新行为规格；保留清理范围，不再次执行已完成删除 |
| [use-qlz-custom-evaluation-ui](../openspec/changes/use-qlz-custom-evaluation-ui/tasks.md) | 设备结果/报告顺序已实现并通过自动化与构建；真机验收暂缓 | 上传后原生结果页查询 GetCheckResult，手动查看报告；纯表单不变 |

本轮整理未移动归档目录、未新增验收证据、未把未完成任务改为完成。已完成目录后续可按 archive 技能执行正式归档；不能仅凭 status 的 isComplete 判断硬件验收通过。

### 主规格分组

| 能力 | 当前主规格 |
|---|---|
| 单应用正式构建与分发 | [single-release-flow](../openspec/specs/single-release-flow/spec.md) |
| 主应用本地读卡检测 | [validation-assistant-app](../openspec/specs/validation-assistant-app/spec.md) |
| 厂商风险接受 | [approved-vendor-release](../openspec/specs/approved-vendor-release/spec.md) |
| 连续设备/H5 评估 | [device-h5-evaluation-flow](../openspec/specs/device-h5-evaluation-flow/spec.md) |
| H5 关闭与容器 | [legacy-h5-close](../openspec/specs/legacy-h5-close/spec.md)、[evaluation-h5-close](../openspec/specs/evaluation-h5-close/spec.md) |
| 评估展示 | [evaluation-design-presentation](../openspec/specs/evaluation-design-presentation/spec.md) |
| 服务完成导航 | [service-completion-navigation](../openspec/specs/service-completion-navigation/spec.md) |
| CI 升级验证 | [ci-upgrade-validation](../openspec/specs/ci-upgrade-validation/spec.md) |

### 历史方案的替代关系

- 原 acceptance/production 发布选项已由单一标准 Release 取代，历史命令不再是当前操作步骤。
- 助手曾由 CI artifact 改为 Release 附件；现已退役独立助手，未来只发布主应用 APK/AAB。历史附件不删除。
- Navigation 2 迁移已完成，历史迁移任务不是当前待办。
- QLZ 设备流程按 2026-09-21 确认改为“上传 → 原生结果/GetCheckResult → 点击查看报告”，取代自动 H5 顺序；纯表单仍在 JS 主动关闭后查询结果。
- 旧 H5 来源限制/现代消息桥方案已由当前最小 NativeBridge 契约替代；历史讨论不授权新增敏感桥方法。
- QLZ/腾讯已接受风险的告警策略不代表二进制缺陷已修复，也不延伸到其他版本/失败。

## 检查与完整文件清单

```bash
# Git 跟踪及未忽略的新 Markdown，自动按目录分类；无需维护第二份文件表
python3 scripts/quality/verify_documentation.py --list

# 当前文档索引覆盖、全量本地链接/标题锚点、指定技术栈版本
python3 scripts/quality/verify_documentation.py
bash scripts/quality/preflight_local.sh --local-fast
openspec validate --all --strict --no-interactive
git diff --check
```

文档脚本只读，排除已删除文件和 ignored 构建输出。根入口及所有非 OpenSpec/技能 Markdown 必须在本页索引；OpenSpec 主规格及未归档变更的 tasks 也须在本页链接；其余 OpenSpec 与 `.agents/skills` 由固定目录分类并参与全量链接检查，技能文档不作为产品要求。历史文件不会因旧版本词句被自动改写。

脚本不访问外链，不检查 reference-style 链接、全部 Markdown 方言或全部依赖版本，也不能证明自然语言契约和代码一致。目前它是手动检查入口，未接入 CI/preflight。业务变更按[质量文档](architecture/ci-quality-gates.md)补充对应测试；合规材料须另行核对线上实际状态。
