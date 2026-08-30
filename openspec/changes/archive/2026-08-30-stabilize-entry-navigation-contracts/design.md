## Context

参见 [proposal.md](proposal.md) 的 Why。当前 `MainApp` 在组合层先读取 `PrivacyConsentManager`，同意后才收集 `MainViewModel.sessionState`；`Unknown` 显示 Splash，`LoggedOut` 与 `LoggedIn` 则分别把 `LoginRoute`、`HomeGraphRoute` 作为 `AppNavigation` 的动态起点。`AppNavigation` 内部自行创建 `NavHostController`，因此测试无法注入 `TestNavHostController`，项目也尚未声明 `navigation-testing`。

认证转换目前有两个信号源：`LoginScreen` 在 `LoginUiState.Success` 时调用 `onLoginSuccess`，而 `UserSessionRepository.login()` 会先把全局会话变为 `LoggedIn`。登出和 3002 会话失效则只通过全局会话变化触发根 UI 重组。现有代码没有验证这些信号竞态是否会产生重复 HomeGraph、保留旧账号 back stack，或在重建时改变起点。

HomeGraph 已显式用父 `NavBackStackEntry` 共享 `TodayOrderViewModel`，但 `HomeSharedViewModel` 保持 Home 目的地作用域。Home 内部再按 `userIdentity == 2` 分流销售体验；销售体验不用应用 NavController，而由 `rememberSaveable` 的 `SalesNavigationState` 管理内部页面。现有测试覆盖部分纯返回函数，却没有覆盖 saver 还原、系统返回入口或真实 typed route back stack。

2026-08-30 已通过 Android CLI 核对 Google 官方资料：

- [Recommendations for Android architecture](https://developer.android.com/topic/architecture/recommendations) 强烈建议 UDF、生命周期感知状态收集、screen-level ViewModel，并把 UI 导航回归测试作为最低测试集合之一。
- [Test Compose navigation](https://developer.android.com/guide/navigation/testing/compose) 建议目的地接收回调而不是 `NavController`，根 `NavHost` 接受可注入控制器，并用 `TestNavHostController` 验证起点和导航动作。
- [ViewModel scoping APIs](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-apis) 明确父导航图 back stack entry 可作为共享 ViewModel owner，其生命周期随该图结束。
- [Modularization patterns](https://developer.android.com/topic/modularization/patterns) 要求 app 模块承担根导航协调，feature 保持最小公开 API；因此本变更不把 route 注册移入 feature。

## Goals / Non-Goals

**Goals:**

- 把隐私、会话与根导航转换表达为可独立测试的有限状态与幂等命令。
- 使用实际 typed route 和 back stack 语义验证 Login/Home 入口，而不让测试依赖真实网络、厂商 SDK 或完整 Hilt 页面树。
- 明确 HomeGraph 与 HomeRoute 各自的 ViewModel owner，防止后续 UI 下沉时无意改变生命周期。
- 对 Sales 内部导航的保存、恢复和返回规则建立与应用导航相互独立的回归基线。

**Non-Goals:**

- 不迁移 `:feature:login`、`:feature:home` 或 Sales UI，也不创建新的业务 feature module。
- 不替换 Navigation Compose 2，不设计 Nav3 back stack、entry decorator 或 adaptive multi-pane。
- 不改变 route 参数、`SavedStateHandle` 结果键、登录/登出 API、用户持久化或隐私文案。
- 不处理 Baseline Profile、R8、API 37、厂商 AAR 或生产发布门禁。

## Decisions

### 1. 以入口状态模型隔离隐私门禁与会话解析

提取一个不依赖 Compose 的内部入口状态映射，将 UI 明确分为 `ConsentRequired`、`ResolvingSession`、`LoggedOut`、`LoggedIn`。只有 consent 已成立后才读取会话并生成后三种状态，继续保证隐私同意前不会实例化 Login/Home 或执行依赖同意的初始化。

同意动作继续由 UI 调用现有 manager 持久化，并通过进程内幂等保护只执行一次 `performPostConsentInit()`。入口模型不持有 `Context`、Activity 或 manager 实现。

替代方案是继续在 Composable 中散布布尔分支；它改动更小，但不能穷举验证状态转换，也无法明确“未同意时不得创建导航图”的边界，因此不采用。

### 2. 会话状态是认证边界的事实来源，所有触发统一经过幂等协调器

Login 成功回调和 session emission 都可以唤醒协调，但不得各自直接构造不同导航动作。一个内部纯协调器根据“目标认证状态 + 当前根目的地”返回 `ShowLogin`、`ShowHome` 或 `NoOp`，UI 层再执行导航命令。相同目标重复到达必须为 `NoOp`；跨认证边界时必须清除相反根及其子历史。

首次从 `Unknown` 解析时直接以目标根创建导航图，避免先显示错误页面。导航图已存在后的登录、登出或会话失效使用显式根替换，不依赖仅修改 `startDestination` 后由框架隐式重置 back stack。

替代方案一是给 `AppNavigation` 加 session key、每次变化都重建整个控制器；这虽然天然清栈，但会无条件丢弃合法的 Home 状态并掩盖重复转换。替代方案二是只保留 Login 成功回调；它无法覆盖 3002 和后台登出，因此均不采用。

### 3. 根 NavHost 接受控制器，并为入口内容提供内部测试接缝

将当前控制器创建 wrapper 与实际 `NavHost` 分开：生产 wrapper 使用 `rememberNavController()`，内部 host 接受 `NavHostController` 和明确起点。route 类型、图层级与 `registerAppNavGraphs` 仍由 `:app` 唯一拥有。

入口目的地注册提供最小的内部 content callback/factory，生产默认值仍调用真实 `LoginScreen` 和 `HomeScreen`；导航 instrumentation 使用轻量标记内容，从而对实际 LoginRoute/HomeGraphRoute 及导航动作做断言，而不启动网络、厂商 SDK 或无关 Hilt 依赖。该接缝保持 `internal`，不得成为 feature 公共 API。

只用最小测试专用 NavHost 的替代方案不能证明生产注册和 typed route 相同；直接启动完整真实页面则会让导航测试受数据、Hilt 和厂商环境影响。两者都不足以作为稳定契约。

### 4. 保持当前两层 ViewModel owner，不把所有 Home 状态扩大到图级

`TodayOrderViewModel` 继续以 `HomeGraphRoute` 的 back stack entry 为 owner，供 Home 和订单列表子目的地共享；owner 查找失败不得静默创建另一个图级实例。测试以可观察的 owner identity/生命周期探针验证同图共享、换图释放。

`HomeSharedViewModel` 继续由 `HomeRoute` 持有并传给护理 pager 或销售根内容，因为当前没有 HomeGraph 兄弟目的地需要共享它。把它提升到图级会延长用户/Tab 状态生命周期且扩大跨页面耦合，没有现实收益。

替代方案是统一把两个 ViewModel 都设为 HomeGraph scope；该方案表面一致，但改变已有生命周期，故不采用。

### 5. 角色选择与 Sales 返回规则使用纯状态转换验证

把 Home 的角色分流判断提取为不依赖 Compose 的内部映射，输入当前用户角色，输出 Loading、Care 或 Sales；页面仍只渲染结果，不更改 `userIdentity == 2` 的现有业务约定。

Sales 保留 app 内部的 plain state holder 和 `rememberSaveable`。为其定义可序列化 snapshot，并让 saver 与返回 reducer 都通过同一状态模型工作；JVM 测试覆盖每条父页面规则和异常枚举恢复，Compose 状态恢复测试覆盖 saver 的真实 save/restore。系统返回与页面返回必须调用同一个 reducer/handler。

不把 Sales 内部页面立即转换为应用级 destination，因为这会把 998 行 ViewModel 拆分、Camera/WebView 结果和模块迁移混入本 change，失去独立回滚能力。

### 6. 测试分层并保持依赖为 test-only

- JVM/Robolectric：入口状态映射、认证协调命令幂等性、角色选择、Sales snapshot/返回 reducer。
- Instrumentation：使用与 `androidxNavigation = 2.10.0` 同版本的 `androidx.navigation:navigation-testing`、`TestNavHostController` 和 Compose navigator，验证实际 typed routes、Login 清栈、登出清保护栈、重复转换、系统返回和 HomeGraph owner。
- Compose 状态恢复：使用可保存状态重建验证 Sales 当前页、Tab 与返回目标；配置变化 smoke 验证 consent 不重复弹出或重复初始化。

`navigation-testing` 通过 version catalog 仅加入 `androidTestImplementation`，不得进入 release runtime classpath。focused 任务接入现有 affected-scope 质量编排；普通无关改动不新增模拟器成本。

## Risks / Trade-offs

- [登录成功回调与 session emission 同时到达造成双重导航] → 所有触发调用同一幂等协调器，并以重复信号测试锁定 `NoOp`。
- [清栈实现误删 Login/Home 之外的合法状态] → 只在跨认证边界时替换根；同一已登录会话内不因重复 emission 重建 HomeGraph。
- [测试 content 接缝泄漏为生产 API] → 限定在 `:app` 的 `internal` 导航组装层，并增加 API visibility/源码边界断言。
- [HomeGraph owner fallback 产生两个共享 ViewModel] → 移除或限制静默 fallback，以真实图 entry 为唯一 owner；focused test 覆盖 entry 缺失的失败语义。
- [Instrumentation 因完整应用依赖而不稳定] → 入口 route 使用轻量内部 renderer，真实业务页面行为继续由已有 focused UI/ViewModel 测试承担。
- [Sales saver 格式调整导致恢复不兼容] → 保持现有字段含义，给 snapshot 提供默认/容错恢复，并测试未知页面值回退；本变更不承诺跨应用版本持久化内部临时页面。

## Migration Plan

1. 先补入口状态、认证协调和 Sales 状态转换的 characterization tests，记录当前期望而不改变页面。
2. 分离控制器 wrapper 与可注入根 host，引入 test-only navigation 依赖和轻量入口 renderer。
3. 让 Login 成功、全局登录、登出和会话失效统一经过幂等协调器；逐项通过 back stack 测试。
4. 固定 HomeGraph/HomeRoute owner，补角色分流、owner 生命周期和 Sales save/restore 测试。
5. 接入 focused 质量门禁并同步架构路线图、导航地图和 CI 说明。
6. 运行完整预检、Lint、Debug 构建及 API 36 focused instrumentation；生产代码无数据迁移，可按本 change 整体回滚。
