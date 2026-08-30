## Why

当前入口流已经依赖隐私同意、异步会话恢复、Login/Home 动态起点、HomeGraph 共享状态和销售端内部返回栈，但项目没有一套可注入 `NavHostController` 的端到端导航契约测试；现有测试只覆盖局部 ViewModel、登录页组件和少量纯状态逻辑。此时直接迁移 `:feature:login` / `:feature:home` UI 或引入 Navigation 3，会让清栈、作用域、角色分流及恢复行为在缺少基线的情况下被改坏，因此应先把现有正确行为固化为可执行契约。

## What Changes

- 建立统一的入口状态契约，明确隐私未同意、会话解析中、已登出和已登录四种状态各自允许创建和显示的 UI/导航范围。
- 将根 Navigation Compose 2 `NavHost` 调整为可注入测试控制器的壳层，同时保持 `:app` 负责 route 注册和跨 feature 导航组装。
- 为 Login/Home 起点、登录成功清栈、登出或会话失效回到登录、重复导航、系统返回键及配置/状态恢复建立可执行回归测试。
- 固定 HomeGraph 的共享 ViewModel owner 和护理/销售角色分流契约，并补齐销售端内部页面、根 Tab、返回目标的保存与恢复验证。
- 为 Navigation Compose 测试增加与现有 `androidxNavigation` 版本一致的 test-only 依赖，并把 focused 导航验证接入受影响范围的质量门禁。
- 保持现有用户可见页面、Navigation Compose 2 route 类型、`SavedStateHandle` 结果 key、业务/网络/存储契约及公开 feature actions 不变。
- 非目标：不迁移 Login/Home UI，不引入 Navigation 3，不收敛大对象 route payload，不调整 targetSdk，不修改厂商 AAR、厂商规则或生产发布阻断策略。

## Capabilities

### New Capabilities

- `entry-navigation-contracts`: 定义应用入口隐私/会话门禁、Login/Home back stack、HomeGraph 共享作用域、角色分流与销售内部导航恢复的可执行等价契约。

### Modified Capabilities

无。

## Impact

- 主要影响 `:app` 的 `MainApp`、根 `NavHost`、入口图注册、导航动作以及对应 JVM/instrumentation 测试；可能为测试接缝提取小型纯函数或内部状态模型。
- `:feature:login` 与 `:feature:home` 的生产行为和公开动作接口保持不变；本变更只验证其与 app 壳层的组合契约。
- `SalesNavigationState` 的现有页面/返回语义保持不变，但 saver 和恢复路径需要可独立验证。
- `gradle/libs.versions.toml` 与 `app/build.gradle.kts` 增加 `androidx.navigation:navigation-testing` 的 test-only 声明，不增加运行时依赖。
- 质量脚本和架构文档将同步记录 focused 导航契约测试及后续 UI 模块迁移的前置条件。
