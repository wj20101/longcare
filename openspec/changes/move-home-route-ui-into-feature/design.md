## Context

参见 [proposal.md](proposal.md) 的动机与范围。当前 `:feature:home` 只持有 `HomeActions`、登录日志抽象和 `HomeSharedViewModel`，而 `:app` 仍持有 Home 根组合、护理端仪表盘/护理工作/个人中心 UI 与状态持有者。`AppNavGraphsEntry.HomeDestination` 同时负责 `HomeGraphRoute` 的 back stack owner、`TodayOrderViewModel` 图级实例、Camera 返回结果、Startup fully-drawn 和 `NavController` 动作适配；销售体验及其 `SalesNavigationState` 也仍属于 `:app`。

这次迁移必须遵守现有模块方向：Feature 只能依赖 Core 抽象，不得引用 `:app`、`:core:data`、导航控制器或厂商类型。`MainDashboardViewModel` 当前直接读取 `SystemConfigManager`，这是迁移前必须切断的 Data 实现依赖。Home 与订单列表又共享 HomeGraph 级今日订单状态，因此不能简单把现有图级 ViewModel 改成 feature 私有实例，否则会破坏同一导航图内的刷新和返回一致性。

设计同时遵循 Android 官方当前架构建议：状态提升到最低共同所有者，屏幕级 ViewModel 暴露不可变 UI state，可复用子组件只接收状态和事件，并使用生命周期感知方式收集 Flow；导航目的地则由调用方通过窄接口封装。依据为 [State hoisting in Compose](https://developer.android.com/develop/ui/compose/state-hoisting)、[Recommendations for Android architecture](https://developer.android.com/topic/architecture/recommendations)、[Common modularization patterns](https://developer.android.com/topic/modularization/patterns) 与 [Encapsulate your navigation code](https://developer.android.com/guide/navigation/design/encapsulate)。

现有 production Release 仍因 QLZ 固定测试配置/弱 TLS、腾讯人脸 16 KB 对齐和 consumer rules 问题 fail-closed；本迁移只能证明 Home 边界和行为等价，不改变这些发布判定，也不代替真实厂商设备验收。

## Goals / Non-Goals

**Goals:**

- 让 `:feature:home` 成为 Home 根体验和护理端三个一级页面的唯一源码、资源、状态与 focused test owner。
- 保留 `:app` 对根导航、HomeGraph 生命周期、销售 renderer、Camera/WebView、Startup reporter 和厂商/平台实现的所有权。
- 把 Home 组合改为不可变 state + event 的单向数据流，删除向护理、销售或兼容页面传递/查找 `HomeSharedViewModel` 的路径。
- 保持同一 HomeGraph 内今日订单共享、角色分流、可保存 UI 状态、反馈消费和认证根替换语义。
- 用可执行的依赖守卫、负向 fixture、模块测试和 API 36/37 回归证明边界不会再次退化。

**Non-Goals:**

- 不把 Sales 源码或 `SalesViewModel` 迁入 `:feature:home`，也不借本次迁移拆分销售领域。
- 不迁移到 Navigation 3，不改变 route payload、SavedStateHandle key、WebView 开放 host 策略或认证/用户存储。
- 不把 NFC 测试辅助、Camera、Startup reporter 或厂商 SDK UI 下沉到 feature。
- 不升级 targetSdk、AGP、Kotlin、Compose 或其他依赖，不修改 Room schema、网络协议、Manifest、权限、R8 或厂商 AAR。
- 不为了消除 app import 而把业务专属组件批量提升到 `:core:ui`。

## Decisions

### 1. 使用单一公开 `HomeFeatureScreen`，平台能力全部通过值对象和动作注入

`:feature:home` 对 app 暴露一个稳定的 `HomeFeatureScreen` 入口，以及最小的不可变输入：应用版本展示值、Home actions、图级订单状态端口、销售内容 renderer 和 Startup 完成通知。公开边界不得包含 `NavController`、route 类型、`SavedStateHandle`、`Activity`、`Context`、app `R`、Data 实现或厂商 SDK 类型。Home 内部页面与状态持有者默认保持 `internal`。

app 的 `HomeDestination` 继续创建现有导航动作、处理 Camera 结果、打开不限制 host 的 WebView、提供 app 版本并渲染销售内容。Startup hook 只接收已经解析的 Home experience 或一次性“首帧可报告”信号，app 负责去重并调用现有 reporter；feature 不感知启动性能实现。

选择这一方案是因为它让 feature 拥有完整的屏幕组合，同时保留应用壳层的导航和平台职责。未采用“把 `NavController` 传入 feature”，因为它会让 route contract 和平台实现穿透模块；也未采用“每个护理页面各自暴露 app route”，因为它会继续让 app 拥有 Home 的组合细节。

### 2. Home ViewModel 只在 feature 屏幕入口创建，子页面只消费 immutable state 与 events

现有 `HomeSharedViewModel` 重命名/收敛为 feature 内部屏幕级状态持有者，并输出单一 `StateFlow<HomeUiState>`。状态至少表达用户解析阶段、已登录用户、角色体验和护理仪表盘选中的订单 Tab；登录日志、Tab 切换及需要保留到消费的反馈使用显式事件入口和现有可消费队列语义。Composable 通过 `collectAsStateWithLifecycle()` 收集，纯内容函数只接收数据和 lambda。

护理仪表盘、护理工作、个人中心、销售 renderer 以及兼容人脸调用点不得接收或通过 Hilt/back stack 查找 Home ViewModel。它们只获得需要的 `User`/展示模型、局部 UI state 和动作。护理三页当前页是可保存的界面状态，继续由 pager/`rememberSaveable` 管理；用户、角色、订单等业务状态不复制进可保存 Bundle。

选择聚合的屏幕 state 而不是多个任意 Flow，是为了保证用户切换、加载/角色分流和重组时获得一致快照。未采用全局 singleton Home state，因为它会跨 HomeGraph/账号泄漏；也未让每个子页自行读取用户仓库，因为这会产生多个事实来源并增加测试复杂度。

### 3. HomeGraph 今日订单继续由 app 图级 owner 持有，通过窄端口接入 feature

`TodayOrderViewModel` 的生命周期与 HomeGraph back stack entry 保持不变，订单列表/服务流程仍可使用同一图级实例。`:feature:home` 定义不暴露 ViewModel 类型的 `HomeOrderStateSource`（最终命名可按项目约定调整），仅包含所需的不可变订单状态 Flow、刷新命令、Tab/反馈消费动作。app 用适配器将现有图级 ViewModel 映射为该端口，并在创建 `HomeFeatureScreen` 时注入。

这使模块迁移不改变图级状态所有权，也满足子页面不泄漏 ViewModel。未采用在 feature 内创建第二个订单 ViewModel，因为返回订单列表后会出现不同缓存和刷新时序；未把 `TodayOrderViewModel` 移入 feature，因为服务单目的地并不属于本 change，且会形成 Feature 间实现耦合。后续若订单状态完成 Domain 化，可单独替换端口实现。

### 4. 护理端 UI/状态整体迁移，平台校验辅助留在 app

以下内容迁入 `:feature:home`：Home 根与 loading/role split、adaptive 三页导航、`maindashboard` 的业务 UI/状态、`nursing` 的日期与订单 UI/状态、`profile` 的 UI/状态及其专属 preview、资源和测试。迁移后这些包可在 feature 内按 `home/dashboard`、`home/nursing`、`home/profile` 分层，但只有 Home 入口和必要的无状态 UI 接口公开。

`NfcTestHelper*`、NFC 测试 Dialog/监听代理不是 Home 业务状态，而是 app/platform 验证工具；它们保留在 `:app` 并移出 `features/maindashboard` 命名空间，Home 如确有入口只通过 app action 请求。`DefaultHomeLoginLogInfoProvider` 作为 app 环境信息实现和 Hilt binding 也留在 app，feature 只保留 reporting 契约。

选择按完整业务切片迁移，而不是只移动 Composable，是为了避免 app 继续拥有 ViewModel、资源或测试形成“双 owner”。平台工具不随页面搬迁，避免 feature 反向依赖 NFC/Activity 生命周期。

### 5. 公司名称通过 Domain 契约读取，应用版本通过配置值注入

在 `:core:domain` 增加只表达业务需求的公司名称读取契约（例如 `CompanyNameProvider`），`:core:data` 用现有 `SystemConfigManager` 提供实现并通过当前 DI 方式绑定。`MainDashboardViewModel` 只依赖该 Domain 契约；契约不暴露 preferences key、DataStore 或 Android 类型。若当前配置在会话切换时可变化，实现必须延续现有用户隔离和刷新行为。

Profile 所需版本名称由 app 读取 `BuildConfig` 后作为不可变 `HomeAppVersion`/字符串配置注入，不在 feature 中读取 app BuildConfig。协议/隐私 URL 也通过现有 app action 或配置传入，不复制常量。

未把 `SystemConfigManager` 搬到 Domain，因为 Domain 必须 Android-free 且不能持有存储实现；未在 feature 自行读取 DataStore，因为会破坏依赖方向。

### 6. 共享组件按真实复用决定归属，不以“消除 import”为目标

迁移前用全仓引用确认 `UserAvatar`、`EmptyView`、`ServiceOrderItemCard` 和日期展示工具的消费者：

- 只有 Home/护理端使用的组件和时间格式逻辑迁为 feature-local。
- 已被多个独立业务切片使用、且只接收纯展示模型/事件的组件才提升到 `:core:ui`。
- 服务单业务模型或 route-bound 行为不得为了复用进入 `:core:ui`；必要时 Home 自有无状态展示组件，或通过窄 renderer 复用 app-owned 内容。
- app 与 Sales 目前复用的 `TopHeader`、Profile 内容或 adaptive nav，不公开 feature 内部状态持有者。若迁移期间确需继续复用，只公开最小的无状态 renderer/component API，输入为展示数据和动作；该临时公共面由 API guard 固定，Sales 后续迁移时再删除。

这一取舍避免把 legacy app 依赖替换成臃肿 Core。未采用批量搬到 `:core:ui`，因为业务语义组件会使 Core 成为新的耦合中心。

### 7. Sales 通过 app-owned renderer 嵌入，内部导航状态完全不进入 feature

`HomeFeatureScreen` 根据用户角色决定是否调用销售 renderer，但 renderer 的实现、`SalesViewModel`、`SalesNavigationState`、Camera/WebView 结果和 SavedState 恢复都留在 app。renderer 只接收 Home 判定后的当前用户和必要的通用布局约束；feature 不读取销售 Tab、详情目标或提醒选择。

如果 Sales 当前直接接收 `HomeSharedViewModel`，app 在 renderer 边界将所需用户值和显式 Home action 适配给 Sales。若 Sales 复用 Profile UI，则通过上一决策中的无状态公共组件/renderer 接入，不让 Sales import feature internal 包。

选择 renderer slot 是为了完成 Home 根 UI 所有权迁移，又不把超大 Sales 切片混入同一风险窗口。未采用本次同时迁移 Sales，因为会显著扩大验证面并掩盖 Home 边界回归。

### 8. 测试所有权随源码迁移，app 保留集成契约测试

`:feature:home` 启用 Compose、Hilt 所需插件/依赖和独立 instrumentation 能力，迁入 Home role/loading、护理 dashboard、Nursing、Profile 的 JVM/Compose/instrumentation tests。测试覆盖生命周期收集、用户切换、角色 `2` 分流、一次性登录/Startup 信号、三页切换、刷新/错误、日期订单动作、Profile 统计/协议/登出和 app version。

`:app` 保留 `HomeDestination`/HomeGraph owner、Camera result、开放 WebView、Startup reporter、销售 renderer 与 `SalesNavigationState` 恢复的 focused integration tests。增加源码所有权/API/依赖守卫和负向 fixture，验证 app 重新创建 Home/护理 UI、导入 feature internal、feature 导入 app/data/platform 类型都会失败。同步 `affected` detector、instrumentation owner、API 36/37 selector、legacy feature allowlist、模块依赖清单和长期架构文档。

模拟器验证使用 Android CLI 管理的 API 36 目标执行安装、启动、角色分流和护理三页流程，并在 API 37/大屏 selector 跑现有 adaptive tests；这些证据不改变真实厂商设备和 production Release 的独立结论。

## Risks / Trade-offs

- [Risk] Sales 继续复用 Home/Profile 无状态组件，可能扩大 `:feature:home` 的公开 API → 只公开输入稳定的展示组件或 renderer，禁止暴露 ViewModel/内部 state，并用 API guard 固定白名单；Sales 模块化后回收临时边界。
- [Risk] 图级订单端口与现有 `TodayOrderViewModel` 同时存在，短期增加适配层 → 端口只映射 Home 实际使用的 state/actions，不复制缓存或业务逻辑，并用同一实例集成测试证明状态一致。
- [Risk] 移动资源时同名 app/feature 资源、preview 或 test fixture 造成重复或视觉漂移 → 先建立资源/截图基线，按所有权逐项移动并删除旧副本，使用 Compose test 与 API 36/37 截图复核关键布局。
- [Risk] Home 状态收敛后副作用时序变化，导致登录日志、刷新或 Startup reporter 重复/遗漏 → 以明确的生命周期/事件 key 去重，在迁移前补齐重组、配置变化和前后台 characterization tests。
- [Risk] 公司名称抽象可能意外改变用户隔离或读取时机 → Data 实现委托现有 `SystemConfigManager`，增加用户切换与空值测试，不修改存储 key/格式。
- [Risk] 一次移动较多文件会与并行未提交 change 冲突 → 实施时记录起始 diff，只修改本 change 列出的 Home 范围；既有 acceptance/R8/startup change 的文件和判定保持原样，冲突时停止合并而非覆盖。
- [Trade-off] `:app` 暂时仍保留 Sales、NFC 校验辅助和订单图级 ViewModel，因此不是最终纯壳层 → 这是有意的风险隔离；后续按独立 OpenSpec change 迁移 Sales/订单 owner，不在本次隐藏扩展范围。

## Migration Plan

1. 固化迁移前角色分流、护理三页、HomeGraph 订单共享、Sales 恢复、Startup/登录上报和平台动作的 characterization tests，并记录源码/资源/allowlist 基线。
2. 在 Core 建立公司名称 Domain 契约和 Data binding；为 `:feature:home` 补齐 Compose/Hilt/test 依赖，但暂不切换 app 入口。
3. 在 feature 内实现 `HomeUiState`、内部状态持有者、订单状态端口及公开 `HomeFeatureScreen`/actions/config/renderer 边界，通过 fake 依赖先完成 feature tests。
4. 按 Home 根、护理仪表盘、护理工作、个人中心顺序迁移 UI、状态、资源和测试；每个子切片迁移后运行 focused tests，NFC 平台辅助留在 app 验证命名空间。
5. 将 app `HomeDestination` 切换到唯一公开入口，接入现有 HomeGraph 订单实例、Sales renderer、Startup reporter、Camera/WebView 和导航 actions；验证同图返回与 SavedState 恢复。
6. 删除 app 中已迁移的旧 UI/VM/资源和过渡 adapter，收缩 legacy allowlist，启用边界 guard/负向 fixture，并同步模块清单、CI selector、质量矩阵与长期文档。
7. 执行 feature/app focused tests、完整 preflight、lint/assemble、API 36 Home 流程和 API 37 adaptive 回归，再做 acceptance Release 静态校验；保持 production 厂商门禁原判定。

迁移不包含数据库、DataStore 或网络格式变化，无运行时数据迁移。回滚以整个 Home 入口切换为原子边界：在删除旧实现前保留单次提交可逆性；若集成回归失败，恢复 app 入口与旧源码/资源，同时撤销未被使用的公开 feature API 和 Domain binding。不得通过保留双实现、放宽 allowlist/guard 或降低 production 门禁完成回滚。
