# Dependency Rules

## 1. 目录层级规则

1. `features/**` 只能依赖 `domain/**` 或 `core:domain` 接口。
2. `:core:model`、`:core:domain` 必须使用 Kotlin/JVM 插件并保持纯 Kotlin，禁止 `import android.*`。
3. `data/**` 可以依赖 `domain/**`，不能反向被 `domain/**` 依赖。
4. `ui/**` 和 `theme/**` 禁止包含网络与存储实现逻辑。

## 2. 代码级规则

1. ViewModel 只做状态编排，不直接写网络请求细节。
2. 持续状态使用 `StateFlow`；会改变导航或向用户展示结果的动作必须保存在可确认消费的状态队列中，直到 UI 回执。
3. `SharedFlow(replay = 0)` 只用于允许在无观察者期间丢失的实时信号，例如 NFC/RFID 瞬时输入或诊断事件。
4. ViewModel 不持有 `Activity`/`Context`，Android Service、闹钟、安装器和第三方 SDK 入口通过 app 平台网关封装。
5. 业务协程调度器通过 DI 注入，避免硬编码 `Dispatchers.*`。
6. Room 版本升级必须提供显式 Migration 与迁移测试，禁止异常后删库或破坏性迁移兜底。
7. 需要跨进程重建继续执行或恢复结果的任务使用 WorkManager/持久存储，不使用进程内事件总线作为唯一结果通道。
8. Retrofit 接口只使用网络层 DTO；路径、HTTP 方法、参数注解及 JSON 字段必须由契约测试锁定，领域模型变化不得反向改变接口字段。
9. Repository 命名约定：
   - 接口：`*Repository`（Domain）
   - 实现：`*RepositoryImpl`（Data）

## 3. PR 审查必查项

1. 是否出现跨层反向依赖。
2. 是否将 Android 类型引入领域层。
3. 是否存在 UI 直接依赖 Data 实现。
4. 是否新增超大类（建议单文件不超过 400 行）。
5. 是否在没有接口文档与契约测试更新的情况下修改 Retrofit/DTO。

## 4. 自动检查建议（CI）

1. `scripts/quality/verify_architecture_boundaries.sh`
   - 拦截 `feature`/`presentation` 对 `data` 实现依赖。
   - 拦截 `domain` 中 `android.*` 引用。
   - 拦截 `core` 反向依赖 `feature`。
   - 拦截 ViewModel 持有 `Activity`/`Context`、直接 Toast 或硬编码调度器。
2. `scripts/quality/verify_module_api_visibility.sh`
   - 拦截模块边界外的内部实现调用。

## 5. 例外处理

如必须临时破例，需要：

1. 在 PR 描述声明原因与回收时间。
2. 在 `偏差说明` 中登记。
3. 下一阶段优先偿还该技术债。
