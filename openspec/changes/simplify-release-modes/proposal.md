## Why

当前正式构建额外区分 `production` 与 `acceptance`，导致工作流选项、Gradle 参数和产物命名重复且容易选错。用户明确要求正式版只有一种正式 Release，不再维护验收发布分支。

## What Changes

- **BREAKING** 删除发布模式选择及 `release.production` / `release.acceptance` 参数消费，Release 始终按正式签名和现有质量要求构建，不保留旧模式别名或兼容层。
- 删除工作流中的验收双包发布分支、模式解析和命名分支；新 APK/AAB 不含 `production` / `acceptance` 后缀，GitHub Release 默认是正式版及 Latest。
- 本地双应用构建仅使用标准 `debug` / `release` 变体；助手仍为独立内部工具，不混入正式对外发布集合。
- 清理模式相关脚本、测试和当前文档引用；保留有效的厂商风险告警、签名、R8、CI、Lint 和隔离检查。

## Capabilities

### New Capabilities

- `single-release-flow`: 单一正式发布入口、产物命名和安全检查契约。

### Modified Capabilities

- `dual-apk-packaging`: 将内部双包构建的独立验收模式替换为标准 Release 变体，保留助手身份和分发隔离。

## Impact

- 涉及 Android Release workflow、App Gradle 发布检查、双包脚本、质量守卫/测试、AGENT.md、OpenSpec 当前上下文及发布文档，不修改业务逻辑、依赖或厂商 SDK。
- 现有厂商风险接受继续有效，名称简化不意味着风险消除；正式签名和质量要求不降低。
- 历史 OpenSpec 验收记录保留原事实；已发布 `v1.0.6-60` 文件及下载链接不改动。本次不自动触发新版本发布，也不新增 CI 自动编排框架。
