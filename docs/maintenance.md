# 文档治理与完整清单

核对日期：2026-09-20。目标是让需求、实现、测试和发布说明可以相互追溯，避免将历史方案、未完成验收或建议误认为当前事实。

## 文档归属与维护责任

Owner 以下均为建议的角色责任，不代表已指定具体成员。实际负责人应在相关 PR/Issue 中确定。

| 主事实 | 详细说明的唯一主入口 | 机器/契约来源 | 维护角色 |
|---|---|---|---|
| 产品角色与用户规则 | product/overview.md | 当前行为规格＋代码 | 产品＋业务移动端 |
| 模块与运行时 | architecture/system-overview.md | Gradle、Manifest、组装代码 | 移动端架构 |
| 版本/工具链 | architecture/tech-stack.md | constants、catalog、Wrapper | 移动端平台 |
| 页面/路由/结果 | architecture/ui-and-screen-map.md | navigation、页面入口与测试 | 业务移动端 |
| 依赖边界 | architecture/dependency-rules.md | allowlist、架构守卫 | 移动端架构 |
| CI/发布/执行范围 | architecture/ci-quality-gates.md | workflow、preflight、affected 脚本 | 平台＋QA |
| QLZ/评估/H5 集成细节 | integrations/qlz-sdk.md | adapter、SalesViewModel、主规格 | 移动端＋服务端＋厂商 |
| 定位会话与实时上传 | 定位模块 README | location 源码/测试 | 定位模块 owner |
| 后续可行动优先级 | architecture/roadmap-and-open-gaps.md | 未完成事项及风险证据 | 技术负责人 |
| 跨域分析/优化依据 | analysis/project-review.md | 带日期的代码基线与来源 | 技术负责人＋产品＋QA |
| 已接受架构决策 | architecture/adr/ | ADR 决策状态 | 相关评审人 |
| 增量规划/验收状态 | openspec/changes/ | proposal/spec/design/tasks | 变更负责人 |
| 已同步行为契约 | openspec/specs/ | 已接受并同步的变化 | 产品＋变更负责人 |
| 历史整改/归档 | compliance/、openspec/changes/archive/ | 当时范围与证据 | 资料维护人 |

摘要可以跨文档重复，复杂流程、接口表和操作步骤应引用主入口。版本类摘要必须跟随配置变化；分析报告的历史快照保留日期，不能被误当成无日期的最新配置。

## 本轮处理范围与结论

- 当前入口和专项文档：核对版本、发布、模块、导航、数据库、质量脚本和集成流程，修正已确认冲突。
- 活动 QLZ change：对齐已落地的 H5/权限/Release 行为，保留 5.3 未完成；不新增通过证据。
- 已完成但未归档 change：在 OpenSpec 入口标明实际状态，未自动移动目录或重新执行工作。
- 历史 archive/ADR：保留决策原文，统一从 OpenSpec 入口说明替代关系；不是当前构建步骤。
- 合规历史材料：标明线上页面与提审事实仍须外部复核；不声称本轮已做市场合规审查。
- 定位文档保留专项维护用途，旧日期不代表本轮真机重验；助手图标文档随助手退役删除。
- `.agents/skills`：纳入工具文档清单和链接检查，但不是产品需求，本次没有修改生成的技能文件。

主要纠偏证据见[整体分析第 19 章](analysis/project-review.md#19-文档一致性与维护制度)。本轮没有修改业务代码、数据库行为、版本、SDK 或发布权限。

## 冲突处理与更新流程

1. 明确文本表达的是当前实现、期望契约、待议建议还是历史证据。
2. 查配置/代码/测试和已接受规格；若二者不一致，记录偏差，不直接“以实现覆盖需求”。
3. 修正主文档，再修改根入口及确有重复的摘要；不要复制整份内容建第二套说明。
4. 活动变更被后续行为替代时，更新其规划产物中的当前约束，不改写未完成勾选或伪造历史通过。
5. 历史归档保留原始时间背景，在索引指向替代规格；不要为了全文没有旧词而抹去历史。
6. 执行文档脚本、OpenSpec strict、diff 检查及与改动相称的项目验证。

本轮 Room 的处理示例：代码和测试证明当前采用重建；因此文档写清现状与风险，并要求下一次 schema 变更先确认保留需求。没有因为旧文档要求显式迁移就直接改数据库，也没有把所有本地状态都声明为可丢弃缓存。

## 一致性检查工具

```bash
python3 scripts/quality/verify_documentation.py
```

工具只读，使用 Python 标准库和 Git 文件清单，不访问网络或用户业务数据。包括跟踪文件及未忽略的新 Markdown，排除已删除文件和 ignored 构建目录。它检查：

- 完整清单是否漏项、重复或含已删除项。
- 普通内联 Markdown 本地链接和标题锚点是否存在，忽略 fenced code block 示例。
- 技术栈中的应用版本、SDK/JDK、Gradle/AGP/Kotlin/KSP、Compose、CameraX、Room 是否与配置相符。

它不检查外链可用性、reference-style 链接、全部 Markdown 方言、每个依赖版本，也不理解自然语言需求是否矛盾。复杂语义仍由代码/规格/测试核对。该脚本目前是手动入口，未加入 CI/preflight；接入自动门禁须在后续工程变更中明确执行成本和触发范围。

新增文档时在下表补一行，选择状态和用途；删除文档时移除对应行并修复所有引用。保持路径在第一列且为仓库相对路径，脚本据此核对覆盖。

## 完整 Markdown 清单

状态约定：“当前”用于维护现行事实；“分析快照”必须带基线；“活动变更”需看真实 tasks；“已完成待归档”不是待实现任务；“历史”不能直接指导当前发布；“工具”描述协作能力。

<!-- inventory:start -->

| 文件 | 状态 | 用途/标题 |
|---|---|---|
| [.agents/skills/openspec-apply-change/SKILL.md](../.agents/skills/openspec-apply-change/SKILL.md) | 工具 | Implementing: <change-name> (schema: <schema-name>) |
| [.agents/skills/openspec-archive-change/SKILL.md](../.agents/skills/openspec-archive-change/SKILL.md) | 工具 | Archive Complete |
| [.agents/skills/openspec-explore/SKILL.md](../.agents/skills/openspec-explore/SKILL.md) | 工具 | The Stance |
| [.agents/skills/openspec-propose/SKILL.md](../.agents/skills/openspec-propose/SKILL.md) | 工具 | SKILL |
| [.agents/skills/openspec-sync-specs/SKILL.md](../.agents/skills/openspec-sync-specs/SKILL.md) | 工具 | Purpose |
| [.agents/skills/openspec-update-change/SKILL.md](../.agents/skills/openspec-update-change/SKILL.md) | 工具 | SKILL |
| [AGENT.md](../AGENT.md) | 当前 | LongCare 协作入口 |
| [README.md](../README.md) | 当前 | LongCare |
| [docs/README.md](README.md) | 当前 | LongCare 文档索引 |
| [docs/analysis/project-review.md](analysis/project-review.md) | 分析快照 | LongCare 项目整体分析与优化基线 |
| [docs/architecture/adr/ADR-001-layer-boundary.md](architecture/adr/ADR-001-layer-boundary.md) | 已接受决策 | ADR-001: Layer Boundary And Dependency Direction |
| [docs/architecture/ci-quality-gates.md](architecture/ci-quality-gates.md) | 当前 | CI、质量门禁与发布 |
| [docs/architecture/dependency-rules.md](architecture/dependency-rules.md) | 当前 | 依赖与架构规则 |
| [docs/architecture/roadmap-and-open-gaps.md](architecture/roadmap-and-open-gaps.md) | 当前 | 路线图与开放问题 |
| [docs/architecture/system-overview.md](architecture/system-overview.md) | 当前 | 系统架构概览 |
| [docs/architecture/tech-stack.md](architecture/tech-stack.md) | 当前 | 技术栈与构建基线 |
| [docs/architecture/ui-and-screen-map.md](architecture/ui-and-screen-map.md) | 当前 | 页面与路由地图 |
| [docs/compliance/2026-05-app-store-privacy-remediation.md](compliance/2026-05-app-store-privacy-remediation.md) | 历史＋外部待核 | 应用市场隐私合规整改说明 |
| [docs/integrations/qlz-sdk.md](integrations/qlz-sdk.md) | 当前 | QLZ SDK 1.3.0.5 接入说明 |
| [docs/maintenance.md](maintenance.md) | 当前 | 文档治理与完整清单 |
| [docs/product/overview.md](product/overview.md) | 当前 | LongCare 产品概览 |
| [feature/location/src/main/kotlin/com/ytone/longcare/features/location/README.md](../feature/location/src/main/kotlin/com/ytone/longcare/features/location/README.md) | 当前 | 定位模块说明 |
| [openspec/README.md](../openspec/README.md) | 当前 | OpenSpec 规格与变更入口 |
| [openspec/changes/archive/2026-09-12-separate-validation-assistant-app/design.md](../openspec/changes/archive/2026-09-12-separate-validation-assistant-app/design.md) | 历史 | Context |
| [openspec/changes/archive/2026-09-12-separate-validation-assistant-app/proposal.md](../openspec/changes/archive/2026-09-12-separate-validation-assistant-app/proposal.md) | 历史 | Why |
| [openspec/changes/archive/2026-09-12-separate-validation-assistant-app/specs/dual-apk-packaging/spec.md](../openspec/changes/archive/2026-09-12-separate-validation-assistant-app/specs/dual-apk-packaging/spec.md) | 历史 | Purpose |
| [openspec/changes/archive/2026-09-12-separate-validation-assistant-app/specs/validation-assistant-app/spec.md](../openspec/changes/archive/2026-09-12-separate-validation-assistant-app/specs/validation-assistant-app/spec.md) | 历史 | Purpose |
| [openspec/changes/archive/2026-09-12-separate-validation-assistant-app/tasks.md](../openspec/changes/archive/2026-09-12-separate-validation-assistant-app/tasks.md) | 历史 | 1. 建立双应用与厂商集成构建边界 |
| [openspec/changes/archive/2026-09-19-align-evaluation-ui-and-h5-close/design.md](../openspec/changes/archive/2026-09-19-align-evaluation-ui-and-h5-close/design.md) | 历史 | Context |
| [openspec/changes/archive/2026-09-19-align-evaluation-ui-and-h5-close/proposal.md](../openspec/changes/archive/2026-09-19-align-evaluation-ui-and-h5-close/proposal.md) | 历史 | Why |
| [openspec/changes/archive/2026-09-19-align-evaluation-ui-and-h5-close/specs/evaluation-design-presentation/spec.md](../openspec/changes/archive/2026-09-19-align-evaluation-ui-and-h5-close/specs/evaluation-design-presentation/spec.md) | 历史 | Purpose |
| [openspec/changes/archive/2026-09-19-align-evaluation-ui-and-h5-close/specs/evaluation-h5-close/spec.md](../openspec/changes/archive/2026-09-19-align-evaluation-ui-and-h5-close/specs/evaluation-h5-close/spec.md) | 历史 | Purpose |
| [openspec/changes/archive/2026-09-19-align-evaluation-ui-and-h5-close/tasks.md](../openspec/changes/archive/2026-09-19-align-evaluation-ui-and-h5-close/tasks.md) | 历史 | 1. 核对设计与平台事实 |
| [openspec/changes/archive/2026-09-19-allow-approved-vendor-release/design.md](../openspec/changes/archive/2026-09-19-allow-approved-vendor-release/design.md) | 历史 | Context |
| [openspec/changes/archive/2026-09-19-allow-approved-vendor-release/proposal.md](../openspec/changes/archive/2026-09-19-allow-approved-vendor-release/proposal.md) | 历史 | Why |
| [openspec/changes/archive/2026-09-19-allow-approved-vendor-release/specs/approved-vendor-release/spec.md](../openspec/changes/archive/2026-09-19-allow-approved-vendor-release/specs/approved-vendor-release/spec.md) | 历史 | Purpose |
| [openspec/changes/archive/2026-09-19-allow-approved-vendor-release/specs/dual-apk-packaging/spec.md](../openspec/changes/archive/2026-09-19-allow-approved-vendor-release/specs/dual-apk-packaging/spec.md) | 历史 | MODIFIED Requirements |
| [openspec/changes/archive/2026-09-19-allow-approved-vendor-release/tasks.md](../openspec/changes/archive/2026-09-19-allow-approved-vendor-release/tasks.md) | 历史 | 1. 最小发布策略调整 |
| [openspec/changes/archive/2026-09-19-complete-device-h5-evaluation-flow/design.md](../openspec/changes/archive/2026-09-19-complete-device-h5-evaluation-flow/design.md) | 历史 | Context |
| [openspec/changes/archive/2026-09-19-complete-device-h5-evaluation-flow/proposal.md](../openspec/changes/archive/2026-09-19-complete-device-h5-evaluation-flow/proposal.md) | 历史 | Why |
| [openspec/changes/archive/2026-09-19-complete-device-h5-evaluation-flow/specs/device-h5-evaluation-flow/spec.md](../openspec/changes/archive/2026-09-19-complete-device-h5-evaluation-flow/specs/device-h5-evaluation-flow/spec.md) | 历史 | Purpose |
| [openspec/changes/archive/2026-09-19-complete-device-h5-evaluation-flow/specs/legacy-h5-close/spec.md](../openspec/changes/archive/2026-09-19-complete-device-h5-evaluation-flow/specs/legacy-h5-close/spec.md) | 历史 | Purpose |
| [openspec/changes/archive/2026-09-19-complete-device-h5-evaluation-flow/tasks.md](../openspec/changes/archive/2026-09-19-complete-device-h5-evaluation-flow/tasks.md) | 历史 | 1. 契约与安全前置核查 |
| [openspec/changes/archive/2026-09-19-migrate-to-navigation-3/design.md](../openspec/changes/archive/2026-09-19-migrate-to-navigation-3/design.md) | 历史 | Context |
| [openspec/changes/archive/2026-09-19-migrate-to-navigation-3/proposal.md](../openspec/changes/archive/2026-09-19-migrate-to-navigation-3/proposal.md) | 历史 | Why |
| [openspec/changes/archive/2026-09-19-migrate-to-navigation-3/specs/service-completion-navigation/spec.md](../openspec/changes/archive/2026-09-19-migrate-to-navigation-3/specs/service-completion-navigation/spec.md) | 历史 | Purpose |
| [openspec/changes/archive/2026-09-19-migrate-to-navigation-3/tasks.md](../openspec/changes/archive/2026-09-19-migrate-to-navigation-3/tasks.md) | 历史 | 1. 固化基线与依赖 |
| [openspec/changes/archive/2026-09-19-simplify-release-modes/design.md](../openspec/changes/archive/2026-09-19-simplify-release-modes/design.md) | 历史 | Context |
| [openspec/changes/archive/2026-09-19-simplify-release-modes/proposal.md](../openspec/changes/archive/2026-09-19-simplify-release-modes/proposal.md) | 历史 | Why |
| [openspec/changes/archive/2026-09-19-simplify-release-modes/specs/dual-apk-packaging/spec.md](../openspec/changes/archive/2026-09-19-simplify-release-modes/specs/dual-apk-packaging/spec.md) | 历史 | MODIFIED Requirements |
| [openspec/changes/archive/2026-09-19-simplify-release-modes/specs/single-release-flow/spec.md](../openspec/changes/archive/2026-09-19-simplify-release-modes/specs/single-release-flow/spec.md) | 历史 | Purpose |
| [openspec/changes/archive/2026-09-19-simplify-release-modes/tasks.md](../openspec/changes/archive/2026-09-19-simplify-release-modes/tasks.md) | 历史 | 1. 删除额外发布模式 |
| [openspec/changes/archive/2026-09-19-upgrade-dependencies-and-review-prs/design.md](../openspec/changes/archive/2026-09-19-upgrade-dependencies-and-review-prs/design.md) | 历史 | Context |
| [openspec/changes/archive/2026-09-19-upgrade-dependencies-and-review-prs/proposal.md](../openspec/changes/archive/2026-09-19-upgrade-dependencies-and-review-prs/proposal.md) | 历史 | Why |
| [openspec/changes/archive/2026-09-19-upgrade-dependencies-and-review-prs/specs/ci-upgrade-validation/spec.md](../openspec/changes/archive/2026-09-19-upgrade-dependencies-and-review-prs/specs/ci-upgrade-validation/spec.md) | 历史 | Purpose |
| [openspec/changes/archive/2026-09-19-upgrade-dependencies-and-review-prs/tasks.md](../openspec/changes/archive/2026-09-19-upgrade-dependencies-and-review-prs/tasks.md) | 历史 | 1. 实施基线与范围 |
| [openspec/changes/archive/2026-09-19-upgrade-protobuf-javalite/design.md](../openspec/changes/archive/2026-09-19-upgrade-protobuf-javalite/design.md) | 历史 | Context |
| [openspec/changes/archive/2026-09-19-upgrade-protobuf-javalite/proposal.md](../openspec/changes/archive/2026-09-19-upgrade-protobuf-javalite/proposal.md) | 历史 | Why |
| [openspec/changes/archive/2026-09-19-upgrade-protobuf-javalite/tasks.md](../openspec/changes/archive/2026-09-19-upgrade-protobuf-javalite/tasks.md) | 历史 | 1. 实施基线与授权 |
| [openspec/changes/publish-assistant-release-artifact/design.md](../openspec/changes/publish-assistant-release-artifact/design.md) | 已完成待归档 | Context |
| [openspec/changes/publish-assistant-release-artifact/proposal.md](../openspec/changes/publish-assistant-release-artifact/proposal.md) | 已完成待归档 | Why |
| [openspec/changes/publish-assistant-release-artifact/specs/dual-apk-packaging/spec.md](../openspec/changes/publish-assistant-release-artifact/specs/dual-apk-packaging/spec.md) | 已完成待归档 | REMOVED Requirements |
| [openspec/changes/publish-assistant-release-artifact/specs/single-release-flow/spec.md](../openspec/changes/publish-assistant-release-artifact/specs/single-release-flow/spec.md) | 已完成待归档 | ADDED Requirements |
| [openspec/changes/publish-assistant-release-artifact/tasks.md](../openspec/changes/publish-assistant-release-artifact/tasks.md) | 已完成待归档 | 1. 补齐发布产物 |
| [openspec/changes/remove-confirmed-redundancy/design.md](../openspec/changes/remove-confirmed-redundancy/design.md) | 已完成待归档 | Context |
| [openspec/changes/remove-confirmed-redundancy/proposal.md](../openspec/changes/remove-confirmed-redundancy/proposal.md) | 已完成待归档 | Why |
| [openspec/changes/remove-confirmed-redundancy/tasks.md](../openspec/changes/remove-confirmed-redundancy/tasks.md) | 已完成待归档 | 1. 无用代码与占位文件 |
| [openspec/changes/use-qlz-custom-evaluation-ui/design.md](../openspec/changes/use-qlz-custom-evaluation-ui/design.md) | 活动变更（真机项未完成） | Context |
| [openspec/changes/use-qlz-custom-evaluation-ui/proposal.md](../openspec/changes/use-qlz-custom-evaluation-ui/proposal.md) | 活动变更（真机项未完成） | Why |
| [openspec/changes/use-qlz-custom-evaluation-ui/specs/qlz-custom-evaluation-ui/spec.md](../openspec/changes/use-qlz-custom-evaluation-ui/specs/qlz-custom-evaluation-ui/spec.md) | 活动变更（真机项未完成） | Purpose |
| [openspec/changes/use-qlz-custom-evaluation-ui/tasks.md](../openspec/changes/use-qlz-custom-evaluation-ui/tasks.md) | 活动变更（真机项未完成） | 1. 建立可测试的 QLZ 自定义会话边界 |
| [openspec/specs/approved-vendor-release/spec.md](../openspec/specs/approved-vendor-release/spec.md) | 当前行为契约 | approved-vendor-release Specification |
| [openspec/specs/ci-upgrade-validation/spec.md](../openspec/specs/ci-upgrade-validation/spec.md) | 当前行为契约 | ci-upgrade-validation Specification |
| [openspec/specs/device-h5-evaluation-flow/spec.md](../openspec/specs/device-h5-evaluation-flow/spec.md) | 当前行为契约 | device-h5-evaluation-flow Specification |
| [openspec/specs/evaluation-design-presentation/spec.md](../openspec/specs/evaluation-design-presentation/spec.md) | 当前行为契约 | evaluation-design-presentation Specification |
| [openspec/specs/evaluation-h5-close/spec.md](../openspec/specs/evaluation-h5-close/spec.md) | 当前行为契约 | evaluation-h5-close Specification |
| [openspec/specs/legacy-h5-close/spec.md](../openspec/specs/legacy-h5-close/spec.md) | 当前行为契约 | legacy-h5-close Specification |
| [openspec/specs/service-completion-navigation/spec.md](../openspec/specs/service-completion-navigation/spec.md) | 当前行为契约 | service-completion-navigation Specification |
| [openspec/specs/single-release-flow/spec.md](../openspec/specs/single-release-flow/spec.md) | 当前行为契约 | single-release-flow Specification |
| [openspec/specs/validation-assistant-app/spec.md](../openspec/specs/validation-assistant-app/spec.md) | 当前行为契约 | validation-assistant-app Specification |

| [openspec/changes/integrate-card-diagnostics-in-app/proposal.md](../openspec/changes/integrate-card-diagnostics-in-app/proposal.md) | 活动变更 | 主应用本地读卡与大 Logo 长按确认 |
| [openspec/changes/integrate-card-diagnostics-in-app/design.md](../openspec/changes/integrate-card-diagnostics-in-app/design.md) | 活动变更 | 主应用本地读卡与大 Logo 长按确认 |
| [openspec/changes/integrate-card-diagnostics-in-app/tasks.md](../openspec/changes/integrate-card-diagnostics-in-app/tasks.md) | 活动变更 | 主应用本地读卡与大 Logo 长按确认 |
| [openspec/changes/integrate-card-diagnostics-in-app/specs/validation-assistant-app/spec.md](../openspec/changes/integrate-card-diagnostics-in-app/specs/validation-assistant-app/spec.md) | 活动变更 | 主应用本地读卡与大 Logo 长按确认 |
| [openspec/changes/integrate-card-diagnostics-in-app/specs/dual-apk-packaging/spec.md](../openspec/changes/integrate-card-diagnostics-in-app/specs/dual-apk-packaging/spec.md) | 活动变更 | 主应用本地读卡与大 Logo 长按确认 |
| [openspec/changes/integrate-card-diagnostics-in-app/specs/single-release-flow/spec.md](../openspec/changes/integrate-card-diagnostics-in-app/specs/single-release-flow/spec.md) | 活动变更 | 主应用本地读卡与大 Logo 长按确认 |

<!-- inventory:end -->

## 复核与证据规则

- 版本号变更只更新当前版本说明；历史报告/归档保留当时版本。
- 将“有测试文件”“以前通过”“本轮执行通过”“CI 当前必跑”分开写。
- 每次报告引用真机结果时明确设备/版本/场景范围；不复制客户资料、凭据和敏感日志。
- 需求或行为变化使用 OpenSpec；不为已有全仓库批量补规格，也不把本报告的建议直接标为已接受要求。
- 机器生成的测试、性能和质量输出留在 build/CI artifact。人工分析保留结论、来源、验收标准和未确认项。
