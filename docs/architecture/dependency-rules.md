# 依赖与架构规则

最后核对：2026-08-27

本文同时记录“当前允许的依赖”和“继续迁移的目标”。两者不能混写：当前 `:app` 仍有业务实现，但新代码不得因此扩大 legacy 边界。

## 项目模块依赖

精确机器真相是 `scripts/quality/module_dependency_allowlist.txt`。当前允许的项目模块边如下：

| 源模块 | 允许依赖的项目模块 |
|---|---|
| `:app` | `:baselineprofile`、全部 `:core:*`、全部现有 `:feature:*` |
| `:baselineprofile` | 无 |
| `:core:model` | 无 |
| `:core:domain` | `:core:model` |
| `:core:common` | `:core:model` |
| `:core:data` | `:core:common`、`:core:domain`、`:core:model` |
| `:core:ui` | `:core:common`、`:core:domain`、`:core:model` |
| `:feature:home` | `:core:domain`、`:core:model` |
| 其他现有 `:feature:*` | `:core:common`、`:core:domain`、`:core:model`；identification 额外允许 `:core:ui` |

新增或修改 Gradle 项目依赖时，必须同步检查实际 build 文件和 allowlist；不能只更新本文。

## 分层边界

### Model

- `:core:model` 是 Kotlin/JVM 模块，只保存跨层模型和值对象。
- 禁止引入 Android framework。
- 网络字段注解只在确有共享序列化契约时保留；不要把 Retrofit 接口或数据源实现放入 Model。

### Domain

- `:core:domain` 是 Kotlin/JVM 模块，保存 Repository/网关契约和跨 feature 的领域规则。
- 禁止 `android.*`、Activity/Context、Retrofit、Room、具体 SDK 类型和 `*Impl`。
- Feature 依赖抽象，不依赖 `:core:data`。

### Data

- `:core:data` 实现 Domain 契约，拥有 Retrofit、Room、COS、DataStore 相关数据访问和绑定。
- 网络专用 DTO、接口路径、参数注解和数据源应隐藏在 Data 边界内。
- Data 不得依赖 Feature/UI，也不得反向调用页面导航。

### Common 与 UI

- `:core:common` 是 Android library，可持有真正跨业务复用的基础能力；它不是无边界的杂物目录。
- `:core:ui` 只保存通用 UI、主题/组件和 UI 支撑，禁止网络、数据库或 Repository 实现。
- 只被一个 feature 使用的 helper 优先留在该 feature，不要为了“复用可能性”提前放入 Core。

### Feature

- Feature 负责一组紧密相关的用户能力、状态和 UI/编排。
- Feature 只能使用允许的 Core 抽象，禁止直接依赖 Data 实现或另一个 Feature 的 internal 实现。
- 公共入口保持最小；非契约声明使用 `internal` / `private`。
- 新业务 UI 应优先进入 `:feature:*`，不要继续扩大 `:app/features/**`。

### App

- 目标职责是启动、根导航、DI 组装、Manifest 和 Android/厂商平台适配。
- 当前仍有大量 route-bound UI 和流程代码，因此 `:app` 对 Core/Data/Feature 的依赖是现实允许边，而不是鼓励新业务继续堆入壳层。
- `app/src/main/.../features/**` 受冻结目录和文件 allowlist 保护；优先在现有允许文件内做小修复，新增能力迁往 Feature。

## UI、状态与生命周期

1. ViewModel 负责状态编排，不直接写 Retrofit/Room 调用细节。
2. 可持续 UI 状态使用 `StateFlow`。
3. 导航、确认结果和用户可见错误等不可丢失动作必须保持到 UI 明确消费；`SharedFlow(replay = 0)` 只用于可丢失的实时信号。
4. ViewModel 不持有 Activity；Context 只通过明确的应用级抽象或平台网关使用。
5. 协程调度器通过 DI 注入。捕获异常时必须重新抛出 `CancellationException`。
6. Compose 收集 Flow 使用 lifecycle-aware API；Activity/Service/传感器/相机/NFC 资源必须在对应生命周期释放。
7. 面向用户的文案来自资源或可测试的文本抽象，避免在业务状态类散落硬编码字符串。

## 平台能力

- Service、通知、闹钟、安装器、NFC 前台调度、Activity Result 和第三方 SDK UI 由 app-owned gateway/controller 或明确的平台模块封装。
- 前台 Service 必须声明匹配用途的 `foregroundServiceType` 和权限，并从满足运行时前置条件的用户可见流程启动。
- Release 组件默认 `exported=false`；新增导出组件需要最小 intent surface、安全审查和 allowlist 更新。
- Debug-only mock、launcher 或诊断能力必须放在 debug source set；共享验证入口进入 main source set 时仍需在 Release 保持不可导出。
- 权限请求要有用途说明、拒绝恢复和从设置页返回后的重新检查，不能在 Application 无上下文地批量申请。

## 数据、网络和文件

- Room schema 变更必须提交 schema JSON、显式 Migration 和迁移测试；禁止破坏性 fallback 或异常后删库。
- 需要跨进程/重建恢复的任务使用 WorkManager 或持久状态，不能只依赖进程内事件。
- Retrofit 接口只使用稳定的网络契约；方法、路径、注解和关键 JSON key 变更必须同步契约测试。
- 会话写入必须等待持久化完成，不能先报告登录/退出成功。
- 应用自有持久 JPEG 统一经过 `UnifiedImagePipeline` 和 `ImageProcessingPolicies`。
- 受管文件的删除责任要与数据库/业务所有权绑定，不能依赖每个 UI 调用方各自补偿。
- 厂商 secret 不得进入源码、资源、BuildConfig、日志或 APK；客户端只接收受限 token/临时凭据。

## 导航

- 路由使用 Kotlin Serialization 类型安全定义，并在 `:app/navigation` 组装。
- 跨页面优先传稳定 ID/轻量 key；当前订单路由使用 `OrderNavParams(orderId, planId)`，不要传完整订单对象。
- 相机、人脸等结果通过 `SavedStateHandle` 返回，并由接收页消费后清除。
- 路由行为变更必须同步[页面与路由地图](ui-and-screen-map.md)并增加对应导航/状态测试。
- Navigation 3 迁移必须作为独立的路由等价项目进行，不能与业务 API 或大规模模块搬迁混在同一改动中。

## 自动守卫

| 守卫 | 保护内容 |
|---|---|
| `verify_architecture_boundaries.sh` | Android-free Domain、禁止 Feature/Data 反向依赖、ViewModel/调度器/文件规模等规则 |
| `verify_module_dependency_whitelist.sh` | Gradle 项目模块边 |
| `verify_module_api_visibility.sh` | 跨模块 API 和 internal 实现边界 |
| `check_new_files_guard.sh` | 冻结 legacy feature 目录不新增文件 |
| `verify_cancellation_guards.sh` | 敏感协程取消处理 |
| `verify_no_empty_catch_blocks.sh` | 禁止吞异常 |

本地快速检查：

```bash
bash scripts/quality/preflight_local.sh --local-fast
```

## 例外处理

架构例外必须同时满足：

1. PR 中写明现实原因、影响范围和回收条件。
2. 只修改最小必要 allowlist/预算，不使用宽泛通配规则。
3. 增加能防止例外继续扩大的测试或守卫。
4. 将长期有效的取舍写入 ADR；短期计划留在 Issue/PR，不再新增执行日志文档。

已有分层决策见 [ADR-001](adr/ADR-001-layer-boundary.md)。
