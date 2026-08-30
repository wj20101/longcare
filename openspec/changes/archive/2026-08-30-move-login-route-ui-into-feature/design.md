## Context

动机见 `proposal.md`，行为约束见 `specs/login-feature-boundary/spec.md`。

当前登录能力已部分模块化：`:feature:login` 持有 `LoginViewModel`、领域依赖、Hilt 配置、扩展函数和 `LoginFeatureActions`；`:app` 仍持有 `features/login/ui/` 下 4 个 Compose 文件、`presentation/validation/LoginValidationEntrySheet.kt`、3 个登录图片、登录/校验文案及登录 Compose instrumentation tests。`AppNavGraphsEntry.LoginDestination` 直接调用 app-owned `LoginScreen`，但根导航动作已经通过 feature-owned actions 注入。

现有隐藏校验面板的三个选项通过回调进入类型安全 route，正式人脸和 NFC 两项仍在 UI 内通过 `LocalContext`/`Intent` 直接启动 app Activity。登录页也直接引用 app-owned `AgreementUrls` 与 app `R`，因此不能原样迁入 Android library。`:feature:login` 当前尚未启用 Compose，也未依赖 `:core:ui`；`:feature:identification` 已提供同工程可复用的 Compose library、测试和公开屏幕入口范式。

2026-08-30 已通过 `android docs search/fetch` 核对官方资料：模块化指南要求 feature module 形成高内聚功能边界并避免反向依赖；Navigation 指南建议目的地通过回调暴露导航事件而不是把 `NavController` 传给页面；Compose 状态提升指南建议屏幕级业务状态由 ViewModel 持有，UI 元素状态留在 Composition，并由事件流向状态持有者。参考：

- [Common modularization patterns](https://developer.android.com/topic/modularization/patterns)
- [Encapsulate Navigation Destinations and Events](https://developer.android.com/guide/navigation/design/encapsulate)
- [State hoisting in Compose](https://developer.android.com/develop/ui/compose/state-hoisting)

## Goals / Non-Goals

**Goals:**

- 让登录页面、协议 UI、隐藏校验面板及专属资源统一归属 `:feature:login`，并让 `:app` 只消费公开 feature API。
- 保留 app-owned 根导航、开放 WebView route、协议全局来源以及 Activity/厂商能力适配，不让 feature 获取 `NavController` 或 `Intent` 能力。
- 保持现有 ViewModel 业务状态、登录/验证码/协议行为和认证根栈等价，并把现有登录 UI/VM 测试迁到其源码所有者模块。
- 缩减 legacy allowlist，增加可独立运行且有负向 fixture 的登录边界守卫，防止所有权回退。
- 使迁移能够按构建配置、公开 API、UI/资源、app 适配、测试与治理分批验证，并可整体回滚而不触碰用户数据。

**Non-Goals:**

- 不迁移 `LoginRoute`、`NavGraphBuilder` 目的地注册或认证根栈所有权，也不引入 Navigation 3。
- 不重写登录领域状态机、网络接口、用户会话/用户级存储、最近手机号持久化或启动配置拉取逻辑。
- 不改变 WebView host 策略、协议地址内容、隐私状态兼容策略、数据库或破坏性迁移要求。
- 不修改 Manifest 权限/导出组件、SDK/Gradle/AndroidX 版本、targetSdk、签名、R8 或厂商 AAR。
- 不迁移 Home 或其他 route-bound UI，不进行视觉改版、文案改写或通用资源体系重构。

## Decisions

### 1. `:app` 保留登录目的地，`:feature:login` 暴露唯一屏幕级 UI 入口

在 `com.ytone.longcare.feature.login.api` 增加公开 `LoginFeatureScreen` Compose 入口。`AppNavGraphsEntry.LoginDestination` 继续持有 `NavController` 和 `onLoginSuccess`，构造 actions/协议链接后调用该入口；类型安全 `LoginRoute`、认证 graph、`FeatureEntry.ROUTE` 及登录成功后的 Home owner 均不改变。登录渲染组件、协议弹窗、隐藏校验面板、preview 和测试辅助入口移动到 feature 的 UI 包并保持非 app API。

公开屏幕入口在当前 back stack entry 下通过 Hilt 获取现有 `LoginViewModel`，使用 `collectAsStateWithLifecycle()` 收集当前 `StateFlow`，继续按反馈 id 确认 Snackbar 消费。手机号、验证码、协议勾选、协议弹窗和校验面板等现有 UI 元素状态不提升进 ViewModel；迁移时保持当前 `remember`/`rememberSaveable` 恢复语义，不把纯 UI 状态写入用户存储。

**替代方案：** 把 `LoginRoute` 和目的地注册一起移入 feature。该方案会同时改变根导航类型所有权并与 Navigation 3 评估耦合，且跨 feature 的 Home/WebView route 仍需 app 组装；本次选择只收敛页面所有权。

### 2. 公开边界由动作和不可变协议配置组成，不传递导航或 Android 平台对象

保留 `LoginFeatureActions` 作为登录成功、打开网页和隐藏校验动作集合，并新增 feature-owned `LoginAgreementLinks(userAgreementUrl, privacyPolicyUrl)` 值对象。`:app` 从现有 `AgreementUrls` 构造该值，feature 使用服务端 `userXieYiUrl` 的非空值优先覆盖用户协议兜底地址；隐私政策继续使用 app 注入的既有地址。所有 URL 原样交给 app 的 `navigateToWebView`，不增加 host allowlist、URL 重写或 feature-owned WebView route。

公开 API 不接受 `NavController`、`Context`、`Activity`、`Intent`、app `R` 或厂商类型。现有 `FeatureEntry.ROUTE` 因 app 预热/架构契约仍保持兼容，但 app 不得导入 feature 的 UI 实现或 ViewModel。

**替代方案 A：** 把 `AgreementUrls` 移入 feature。该对象还服务隐私启动门和 Home，迁移会把全局策略错误归属登录或产生跨 feature 依赖，因此不采用。

**替代方案 B：** 在 feature 复制硬编码协议 URL。该方案会形成多事实来源并使后续地址变更错乱，因此不采用。

**替代方案 C：** 只传一个通用 map/config bundle。显式值对象更容易发现缺失字段、测试优先级和保持 API 演进，因此不采用弱类型配置。

### 3. 五个隐藏校验入口全部转换为 app-owned 平台动作

扩充 `LoginValidationEntryActions`，在现有相机、备用人脸、手动人脸回调之外加入正式人脸校验和 NFC 校验回调。feature-owned 面板只负责显示、测试 tag、关闭与调用恰好一个动作。`LoginValidationEntryNavigationActions.kt` 继续由 `:app` 持有：前三项沿用现有类型安全导航，后两项使用 app context 启动当前 `FaceVerificationValidationActivity` 与 `NfcValidationActivity`。

这样依赖方向保持为 `:app -> :feature:login -> :core:*`，厂商 Activity、Manifest 与 AAR 不进入 feature。入口继续位于共享 main 源集并通过主 logo 长按打开，不得引入 `BuildConfig.DEBUG` 分支；现有 Release 校验脚本改为检查 feature 路径和完整五动作边界。

**替代方案：** 让 feature 通过 `LocalContext` 构造显式 `Intent`。虽然不需要新增依赖注入，但会让 feature 编译期依赖 app Activity 且无法成为独立 library 边界，因此不采用。

### 4. UI 与独占资源原子迁移，复用 `:core:ui` 而不扩大资源重构

为 `:feature:login` 启用 Kotlin Compose plugin、`buildFeatures.compose` 和 instrumentation runner，增加 `:core:ui`、Compose BOM、生命周期 Compose、Hilt Compose 以及现有测试库的最小直接依赖；版本全部来自当前 version catalog，不在本 change 升级。现有主题实现已经位于 `:core:ui`，登录 UI 继续使用同一主题和组件。

`login_bg.webp`、`app_logo_name.webp`、`app_logo_small.webp` 及仅登录/隐藏校验使用的字符串移动到 feature。每个资源先做全仓引用盘点；仍被 app 其他页面引用的资源不得删除，而应保留 app 所有权或创建语义明确的 feature-local 等价值。4 个登录 UI 文件和校验面板在同一可构建提交中迁移并删除 app 副本，避免重复资源/类暂时成为长期状态。

**替代方案：** 把全部协议、登录与通用文案提升到 `:core:ui`。这些资源不是跨业务 UI 基元，提升会把业务语义污染 Core，因此不采用。

### 5. 测试随所有权迁移，并增加专用边界守卫

将 3 个 app 登录 Compose instrumentation tests 移入 `feature/login/src/androidTest`，将 `LoginViewModelPrivacyGateTest` 移入 feature JVM tests；必要的 dispatcher rule 采用 feature-local 测试工具或来自既有测试公共能力，不让 feature test 反向依赖 app。保持现有测试语义，并补齐协议动态/兜底 URL 选择、五个校验动作一一映射、面板关闭、反馈匹配 id 消费和成功动作不重复的 focused coverage。

新增 `verify_login_feature_boundary.sh` 及自验证 fixture，至少检查：app legacy 登录 UI 不再存在；feature 不 import app navigation/platform/presentation、app `R` 或 app Activity；app 只通过 `feature.login.api`/`FeatureEntry` 集成且不导入 feature UI/VM；Release 长按入口和五个回调仍在 main 源集。守卫接入 `verify_architecture_boundaries.sh`、`preflight_local.sh` 和质量门禁注册表；`affected-modules.sh`、目标平台测试矩阵与 CI selector 同步 feature 测试新路径。

迁移后从 `legacy_feature_files_allowlist.txt` 精确删除 4 个登录 UI 路径。现有 allowlist 守卫与新增 focused 守卫共同防止 app 回流；错误必须输出违规文件、规则和修复方向。

**替代方案：** 仅依赖 Gradle 编译。编译可以阻断非法 project dependency，却不能解释 app 页面回流、app `R` 引用或 Release 隐藏入口被调试条件移除；可执行守卫能提供更快、更明确的反馈。

### 6. 验证以现状刻画、模块构建、目标平台和关键真机烟测分层

实现前先让现有登录 ViewModel/Compose tests 在原位置通过，记录 Release guard 与 legacy allowlist 快照。迁移后先运行 feature JVM/Compose tests与登录边界 fixture，再运行 feature/app lint、compile/assemble 以及 `preflight_local.sh --full`。API 36 模拟器覆盖登录主路径、协议弹窗/链接、键盘焦点、旋转/重建、长按面板和不依赖厂商硬件的三条 route；正式人脸和 NFC Activity 至少验证回调映射与组件启动，厂商完整能力在支持环境的真机 smoke test。

普通 Debug 构建或模拟器绿色只证明迁移和可自动化行为，不代表 production Release 可发布。QLZ 固定测试配置/弱 TLS、腾讯人脸 16 KB 对齐与 consumer rules 等既有 fail-closed 条件保持原样，不得通过放宽守卫验收本 change。

## Risks / Trade-offs

- **[Compose library 配置遗漏导致 feature 编译或 instrumentation 不可运行]** → 复用已验证的 identification module 配置，只增加实际 import 所需的 version-catalog 依赖，并分别运行 feature lint/test/assemble。
- **[资源移动误删 app 共享资源或出现错误命名空间]** → 迁移前全仓引用盘点，独占资源才移动；编译、资源 lint、preview 与 UI tests 共同验证。
- **[协议地址优先级在注入边界改变]** → 以动态非空用户协议优先、app 兜底及隐私固定兜底的 characterization tests 锁定行为，不在迁移中清洗或限制 URL。
- **[登录成功或反馈因重组重复执行]** → 保留状态/反馈 id 语义，增加成功 destination 与反馈消费回归；不借迁移重写认证状态机。
- **[隐藏校验入口在 Release 丢失或平台动作映射错误]** → Release guard 同时检查共享源码、长按和五动作；Compose test 验证选择映射，app focused test 验证 Activity/route adapter。
- **[feature 公开 API 因测试或导航装配膨胀]** → 只公开屏幕入口、actions、协议链接和既有 `FeatureEntry`；内容组件在同模块测试，不为 app 暴露测试参数或 ViewModel。
- **[生产厂商限制被误判为已解决]** → 明确厂商 AAR 与 Release fail-closed 结果不变，验收报告区分架构迁移、模拟器证据和生产 readiness。

## Migration Plan

1. 记录迁移前登录 UI/资源/allowlist 与 Release guard 快照，运行现有 ViewModel 和 Compose characterization tests。
2. 为 `:feature:login` 启用 Compose/测试配置，增加协议链接值对象、完整五动作契约和公开 `LoginFeatureScreen`；先用 focused tests 固定动作与 URL 选择。
3. 将 4 个登录 UI 文件、隐藏校验面板和独占资源迁入 feature，在同一可构建步骤删除 app 副本；保持现有状态收集、焦点和 UI 恢复语义。
4. 将 app 登录目的地改为注入 `LoginFeatureActions`、`LoginAgreementLinks` 及五个 app-owned 平台动作；保持 route、认证根栈、WebView 和厂商 Activity 实现不变。
5. 迁移登录 JVM/instrumentation tests，更新 Release guard、目标平台矩阵、受影响模块、legacy allowlist，增加边界守卫与负向 fixture。
6. 同步架构、页面、依赖、质量和路线图文档；依次运行 focused tests、feature/app lint/build、`preflight_local.sh --full`、API 36 模拟器回归和可用真机烟测。

回滚时将 feature UI/资源、app 目的地适配、测试路径、allowlist/守卫和文档作为同一原子变更整体还原。route、用户会话、数据库、协议内容、网络接口、WebView 策略和厂商制品均未改变，因此回滚不需要数据迁移，也不得清理用户数据。
