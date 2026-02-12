# Dependency Rules

## 1. 目录层级规则

1. `features/**` 只能依赖 `domain/**` 或 `core:domain` 接口。
2. `domain/**` 必须纯 Kotlin，禁止 `import android.*`。
3. `data/**` 可以依赖 `domain/**`，不能反向被 `domain/**` 依赖。
4. `ui/**` 和 `theme/**` 禁止包含网络与存储实现逻辑。

## 2. 代码级规则

1. ViewModel 只做状态编排，不直接写网络请求细节。
2. 一次性 UI 事件使用 `SharedFlow(replay = 0)`，持续状态使用 `StateFlow`。
3. 业务协程调度器通过 DI 注入，避免硬编码 `Dispatchers.*`。
4. Repository 命名约定：
   - 接口：`*Repository`（Domain）
   - 实现：`*RepositoryImpl`（Data）

## 3. PR 审查必查项

1. 是否出现跨层反向依赖。
2. 是否将 Android 类型引入领域层。
3. 是否存在 UI 直接依赖 Data 实现。
4. 是否新增超大类（建议单文件不超过 400 行）。

## 4. 自动检查建议（CI）

1. `scripts/quality/verify_architecture_boundaries.sh`
   - 拦截 `features` 对 `data` 实现依赖。
   - 拦截 `domain` 中 `android.*` 引用。
2. `scripts/quality/verify_module_api_visibility.sh`
   - 拦截模块边界外的内部实现调用。

## 5. 例外处理

如必须临时破例，需要：

1. 在 PR 描述声明原因与回收时间。
2. 在 `偏差说明` 中登记。
3. 下一阶段优先偿还该技术债。
