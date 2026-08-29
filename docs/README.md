# LongCare 文档索引

最后核对：2026-08-29

本目录只保存能够长期维护的当前说明、架构决策和仍有现实用途的专项资料。任务计划、执行日志、临时审计记录和生成报告不再作为仓库文档维护。

## 从哪里开始

- 第一次接触项目：先读根目录的 [README](../README.md)。
- 开始代码协作：先读根目录的 [AGENT](../AGENT.md)，再按任务选择下面的文档。
- 判断产品行为：[产品概览](product/overview.md)。
- 判断运行架构和模块归属：[系统概览](architecture/system-overview.md)。
- 查页面与路由：[页面与路由地图](architecture/ui-and-screen-map.md)。

## 当前真相集

### 产品

- [产品概览](product/overview.md)：用户角色、核心流程、能力状态和产品约束。

### 工程

- [系统概览](architecture/system-overview.md)：运行时、模块职责、平台边界和外部集成。
- [技术栈](architecture/tech-stack.md)：SDK、工具链、依赖版本、构建变体和配置入口。
- [页面与路由地图](architecture/ui-and-screen-map.md)：类型安全路由、页面归属和隐藏验证入口。
- [依赖规则](architecture/dependency-rules.md)：当前允许的模块依赖和代码边界。
- [CI 与质量门禁](architecture/ci-quality-gates.md)：本地、CI、验收与生产发布校验。
- [路线图与开放问题](architecture/roadmap-and-open-gaps.md)：仍需处理的产品、架构和发布风险。
- [ADR-001 分层边界](architecture/adr/ADR-001-layer-boundary.md)：分层方向的已接受决策。
- [ADR-002 用户存储冷切换](architecture/adr/ADR-002-user-storage-cold-cutover.md)：复合用户物理隔离、零 legacy 兼容、会话/GUID 边界和已确认的每用户破坏性数据库例外。

### 专项资料

- [QLZ SDK 接入](integrations/qlz-sdk.md)：销售评估 SDK、接口、权限和发布限制。
- [应用市场隐私整改记录](compliance/2026-05-app-store-privacy-remediation.md)：2026-05 的整改证据与外部复核项，不等同于当前线上政策已发布证明。
- [定位模块说明](../feature/location/src/main/kotlin/com/ytone/longcare/features/location/README.md)：定位会话、上报和生命周期边界。

## 真相优先级

出现冲突时按以下顺序判断：

1. 当前代码、Gradle/Manifest/workflow 配置和可执行测试。
2. 对应领域的专项当前文档。
3. 本索引与根目录入口说明。
4. Git 历史、PR、Issue 和旧提交中的计划或报告。

应用版本/JDK 以 `constants.gradle.kts` 为准，Android SDK 与模块清单以 `settings.gradle.kts` 为准，依赖版本以 `gradle/libs.versions.toml` 为准，质量门禁以 workflow、脚本和 `quality_gate_registry.json` 为准。

## 文档维护规则

| 变更 | 必须同步的文档 |
|---|---|
| 用户角色、业务流程、用户可见能力 | `docs/product/overview.md` |
| 模块、运行时组件、平台边界 | `system-overview.md` |
| 路由、页面、页面归属 | `ui-and-screen-map.md` |
| SDK、工具链、依赖或构建变体 | `tech-stack.md` |
| 模块依赖或架构守卫 | `dependency-rules.md` |
| CI、发布流程或质量脚本 | `ci-quality-gates.md` |
| 已确认且长期有效的架构决策 | 新增或更新 ADR |
| 第三方 SDK 行为、权限或安全状态 | 对应 `integrations/` / `compliance/` 文档 |

不要提交以下内容作为长期文档：

- 一次性任务计划、逐步执行日志、会话进度和临时 findings。
- 构建耗时、Lint 输出、质量快照和截图的本机路径。
- 从 Swagger 或外部页面复制、但没有契约测试约束的孤立接口片段。
- 已完成方案的整套 design/plan 副本；需要长期解释的结论应提炼到当前文档或 ADR。

生成报告统一写入 `build/reports/` 或其他被忽略的构建目录。历史执行材料需要追溯时使用 Git、PR 或 Issue。
