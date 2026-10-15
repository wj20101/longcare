# 系统架构概览

最后核对：2026-09-20（代码与文档静态核对；非本轮全量运行验收）

本文描述当前代码实际运行形态，不把目标架构写成已经完成的事实。版本和依赖见[技术栈与构建基线](tech-stack.md)，产品行为见[产品概览](../product/overview.md)。

## 总体形态

LongCare 是单应用、多模块的 Compose Android 应用。当前采用“壳层 + Core + 部分 Feature 下沉”的过渡架构：

- `:app` 负责 Application/Activity、隐私和会话入口、类型安全导航、Manifest 组件、平台/厂商 SDK 适配，以及仍未迁出的多数 route-bound UI。
- `:feature:carddiagnostics` 提供本地 NFC/R65C UI，登录页中央大 Logo 长按确认后进入，平台读卡监听在 `:app`。
- `:integration:txface` 统一拥有腾讯人脸 SDK；本地 AAR 通过两个纯 artifact wrapper 模块供 Android library 消费。
- `:core:*` 提供模型、领域契约、数据实现、通用 UI 和基础设施。
- `:feature:*` 已承接部分业务状态、用例、平台能力或 UI，但模块迁移尚未完成。
- `:baselineprofile` 提供启动测量及 Profile 生成；当前生成器仅有启动、盲滑和返回操作，尚未建立登录后业务旅程断言。

```mermaid
flowchart LR
    APP[":app<br/>启动·导航·平台组装·遗留 UI"] --> FEATURES[":feature:*"]
    APP --> DATA[":core:data"]
    APP --> UI[":core:ui"]
    APP --> COMMON[":core:common"]
    APP --> DOMAIN[":core:domain"]
    APP --> MODEL[":core:model"]
    APP --> TX[":integration:txface"]

    FEATURES --> UI
    FEATURES --> COMMON
    FEATURES --> DOMAIN
    FEATURES --> MODEL
    DATA --> COMMON
    DATA --> DOMAIN
    DATA --> MODEL
    UI --> COMMON
    UI --> DOMAIN
    UI --> MODEL
    COMMON --> MODEL
    DOMAIN --> MODEL
```

箭头表示允许出现的项目模块依赖；每个模块的精确白名单以 `scripts/quality/module_dependency_allowlist.txt` 为准。

## 模块职责与现实归属

| 模块 | 当前职责 | 当前现实/迁移状态 |
|---|---|---|
| `:app` | 运行时壳、导航、Manifest、平台网关、厂商 UI 控制器、更新任务 | 仍包含护理、销售、NFC、倒计时等大量业务 UI；legacy feature 目录已冻结新增 |
| `:feature:carddiagnostics` | NFC/R65C 本地检测 UI | 不依赖 Data、会话或业务事件总线 |
| `:integration:txface` | 腾讯人脸 SDK adapter、依赖与规则 | `FaceVerifier` 契约不泄漏厂商类型 |
| `:baselineprofile` | Macrobenchmark 旅程与 Baseline Profile 生成 | 使用 Pixel 6 API 33 managed device，目标为 `:app` |
| `:core:model` | 跨层模型、值对象、`ApiResult`、序列化模型 | Kotlin/JVM 模块，不依赖 Android framework |
| `:core:domain` | Repository/网关契约和领域规则 | Kotlin/JVM 模块，不依赖 Android framework 或数据实现 |
| `:core:data` | Retrofit、Room、DataStore/COS 相关实现、Repository 实现和 Hilt 绑定 | 数据实现集中地；不得依赖 feature/UI |
| `:core:common` | 日志、诊断、运行配置、调度器、图片输出/受管文件、通用 Android 能力 | Android library；不是纯 Kotlin 模块 |
| `:core:ui` | 共用 Compose/UI 支撑、共享 ViewModel、统一图片预览及通用按钮文案 | 可依赖 Core 契约，不得访问数据实现 |
| `:feature:login` | 登录 ViewModel、动作接口、DI 和 feature entry | `LoginScreen` 仍在 `:app` |
| `:feature:home` | 首页共享状态、上报能力、动作接口和 feature entry | 护理/销售首页 route UI 仍在 `:app` |
| `:feature:identification` | 身份用例/网关、状态编排、CameraX + ML Kit 人脸采集和默认比对页 | 已拥有默认、手动采集、备用腾讯人脸 UI；`IdentificationScreen` 主页面仍在 `:app` |
| `:feature:location` | 定位 Service、管理器、会话、上报、诊断 | 作为服务流程内嵌能力，没有独立路由 |
| `:feature:photoupload` | 上传门面、任务队列、标准相机/水印与照片处理状态 | `PhotoUploadScreen` 仍在 `:app`；`CameraScreen` 已共享 |
| `:feature:servicecountdown` | 倒计时状态、轮询和平台网关契约 | route UI、Service、闹钟实现仍在 `:app` |

## 启动与会话

1. `MainActivity` 使用 Hilt，启用 edge-to-edge，并把 UI 交给 `MainApp`。
2. 未同意隐私政策时只显示同意弹窗；同意后执行需要用户授权的后置初始化。
3. `MainViewModel` 暴露持久会话：
   - `Unknown` → 启动进度页
   - `LoggedOut` → `LoginRoute`
   - `LoggedIn` → `HomeRoute`；恢复前校验账号身份，退出或换号清理旧栈
4. 全局 `SessionInvalidationHandler` 负责失效提示与退出；业务页面不各自实现一套登出导航。
5. WorkManager 启动任务检查新版本；UI 只观察最新一次启动请求，避免历史成功任务重新弹出旧更新。

## 导航组装

导航使用 Navigation 3 的可序列化 NavKey、唯一 entry ID 和可保存单栈，由 NavDisplay 管理页面生命周期，并在 `:app/navigation` 统一注册：

- Entry：登录、Home 和订单列表；首页、计划和记录列表显式共享 Home entry 的 TodayOrderViewModel owner。
- Sales：客户/提醒、登记、评估选择、设备和结果使用 `SalesRoute`，每个 entry 独立持有 SalesViewModel；扫描与进度在同一设备 entry 中展示。
- Service flow：服务详情、护理执行、NFC、选择服务、照片上传、倒计时、结束选择、完成摘要。
- Support：用户列表/记录、身份与默认人脸核验、相机、手动人脸采集和 WebView；旧设备选择页、人脸引导页及正式应用腾讯测试路由已移除。

所有应用内 H5 共用 `WebViewScreen` 与 `NativeBridge`；隐私网页以 Dialog 包裹同一容器，
启用 JavaScript 但关闭仅映射到网页 dismiss，不触发隐私同意/拒绝。统一关闭接口为
`window.NativeBridge.closeWebView()`；评估容器额外启用 `enterUserDetails(pingguuserid)`，替换 H5 为客户详情，同客户来源仅弹出并刷新。两个方法共用主线程、前台生命周期、单次导航及路由 entry 校验。
内部 H5 不设置额外 URL 白名单或导航拦截；新增方法直接放入 NativeBridge 并显式添加注解，不使用通用分发框架。

订单相关路由传递轻量 `OrderNavParams(orderId, planId)`，页面再通过 Repository/共享状态加载业务数据。跨页面结果使用 entry ID 定向的可保存邮箱，保留原 key 与 StateFlow 契约；消费会发出空值，图片 map 只编码文件引用和元数据。来源已离开的回调不可修改新栈。服务完成保留 Home、移除执行中间页，普通返回到首页。

当前只有 login、home、identification 三个 feature entry 常量进入运行时 registry；registry 的数量校验不是完整路由清单。完整页面映射见[页面与路由地图](ui-and-screen-map.md)。

返回必须使用当前 entry 出栈，不得用固定目标 navigate 或重建页面模拟返回。前进才压栈；登录态切换、业务已完成页面替换等特殊场景须明确移除范围并测试保留的 entry 序列。销售登记填写→确认、确认成功→提交结果、设备上传成功→评估结果使用替换；失败不导航。结果页数据归属自身 entry，打开另一客户详情不清空或覆盖它。

## 状态与异步约定

- 可持续渲染状态使用 `StateFlow`，Compose 使用 `collectAsStateWithLifecycle()`。
- 会触发导航或用户可见结果的重要动作必须可确认消费，避免用 `SharedFlow(replay = 0)` 承载不能丢失的事件。
- replay-zero 流只用于允许观察者缺席时丢失的实时输入或诊断信号，例如 NFC/RFID 瞬时事件。
- 协程取消必须继续抛出 `CancellationException`；敏感流程由质量脚本扫描。
- ViewModel 不持有 Activity；需要 Activity、Context、Service、闹钟、安装器或厂商 SDK UI 时通过 app-owned gateway/controller。
- Repository 会话写入为 suspend 操作，调用者不能在 DataStore 持久化完成前报告登录/退出成功。

## 数据与持久化

- Retrofit + Moshi 承载 LongCare API；API 方法、路径、参数注解和关键 JSON 字段由契约测试保护。
- 正式 Moshi 由 `:core:data` 的 DI 配置提供；`DefaultMoshi` 仅在 App 测试源集中使用。
- Room 当前 schema 版本为 3，schema JSON 保存在 `app/schemas`。`DatabaseModule` 仅允许从已确认的 v1/v2 破坏性重建；v3 及后续版本缺少迁移路径时拒绝打开，不再无条件清表。测试覆盖历史版本重建、正常重开保留本地进度/照片，以及未知版本打开失败后记录仍保留。后续 schema 变更必须提交保留本地状态与未上传照片的 Migration 和升级测试。
- 订单详情统一通过 `OrderDetailRepository` 读取；`OrderRepository.getOrderInfo` 委托其强制刷新路径。请求保留 `orderId/planId`；刷新成功同步 Room 后才写内存缓存。订单内存缓存与腾讯凭据共用 `UserSessionTracker` 的会话代次，退出、换号、令牌变化和重新登录时清空，旧请求不能回填新会话缓存。
- DataStore 保存会话、偏好和少量兼容记录。
- WorkManager 用于启动更新检查、APK 下载等需要跨重建继续或恢复结果的任务。
- 腾讯 COS 负责业务图片/文件上传，Feature 通过 `PhotoCloudUploader` 等受校验门面使用。
- Debug 可选的本地 mock 只存在于 debug source set，不进入 Release。

## 图片与人脸链路

- 标准业务照片统一进入 `CameraRoute`，由 `UnifiedImagePipeline` 完成 EXIF 方向修正、水印、JPEG 压缩、原子写入、大小校验和受管文件生命周期。
- `ImageProcessingPolicies` 集中维护图片参数；业务页面不得各自硬编码压缩策略。
- `PhotoPreviewDialog` 是通用全屏预览实现。
- 订单图片行删除与受管文件删除在数据层耦合，单张删除、整单清理和完成流程使用同一生命周期。
- 照片上传后的云端 key 必须先完成 Room 写入，再更新内存中的上传成功状态和发布页面结果；数据库写入失败保留可重试状态。
- 默认服务人员核验由 `:feature:identification` 的 CameraX/ML Kit 流程完成：单人/姿态检查 → 建立睁眼基线 → 闭眼 → 稳定睁开 → 拍摄 → 编码 → `/V1/User/CheckFace`。
- 服务端登记照只作为“是否需要补录”的权威状态，不重新下载为客户端本地缓存；旧版遗留文件在需要补录时清理。
- 腾讯人脸 SDK 和手动采集仍保留兼容/验证路径，但不是默认订单核验入口。
- 腾讯 AppSecret 不进入普通系统配置缓存或 UI 状态：保存、恢复和刷新配置时只保留公开第三方字段，旧嵌套 JSON 在读取时重写；配置请求禁止 HTTP 缓存。Access Token 缓存未命中时，由数据层重新请求现有配置接口，短暂提取 AppSecret 换取 token。
- Access Token/SIGN ticket 按 AppId 与登录会话代次在内存中缓存，保留过期安全窗口和并发合并；NONCE ticket 每次认证独立请求。退出、换号、令牌变化及重新登录会清空缓存，旧异步响应、界面请求和 SDK 延迟回调不再生效；SDK 成功后的底图上传也在挂起边界检查原会话，失效后不继续登记或更新认证成功状态。核验对象 userId 可以是老人，不与操作员 userId 混同。
- 腾讯 SDK 启动自身 Activity 会使宿主进入 `onStop`，该事件不释放认证；资源在认证结束、所属 UI 销毁或登录会话失效时释放。客户端仍会收到 AppSecret，剩余安全边界见[工程风险](../analysis/project-review.md#腾讯人脸凭据)。

## Android 组件边界

最终组件来自 app、feature Manifest 和 AAR 合并：

- Activity：
  - `MainActivity`
  - `CountdownAlarmActivity`
  - QLZ SDK 的内部 `MainLoadingActivity`
- Service：
  - 订单定位 `LocationTrackingService` 和 AMap `APSService`（`location` 类型）
  - 倒计时前台 Service（`specialUse`）
  - 响铃 Service（`mediaPlayback`）
- Receiver：倒计时、关闭响铃、服务结束提醒和设备启动恢复。
- Provider：受限 `FileProvider`；WorkManager 默认 initializer 被移除，改为应用自定义配置。

主应用不新增检测 Activity 或检测 Launcher；登录页中央大 Logo 长按确认后在既有 NavDisplay 内打开读卡页，不使用传感器或震动。NFC Reader Mode 只回调当前页面，退出即释放，不向业务事件总线派发。SDK 内部 Activity 不导出。

## 权限与平台约束

- 相机、人脸、NFC、蓝牙、定位、通知、精确闹钟、全屏提醒和应用安装均按业务入口请求，不应在 Application 无条件触发。
- Android 14+ 的前台服务类型及对应权限在 Manifest 中显式声明；定位 Service 只能在满足位置服务和运行时权限的用户可见流程中启动。
- 正式主入口 `MainActivity` 当前锁定竖屏；`CountdownAlarmActivity` 的源 Manifest 没有方向锁定声明。targetSdk 36 在 sw600dp+ 默认忽略方向/可调整大小限制，项目用 Activity 级 `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` 暂时退出该行为。
- Android API 37 会取消上述大屏退出能力；在升级 targetSdk 37 前必须完成旋转、多窗口、相机预览和状态恢复验证。
- 顶层护理/销售导航已经使用 Material 3 Adaptive Navigation Suite，根据窗口尺寸选择底栏或导航轨。

## 外部集成

| 集成 | 用途 | 代码边界 |
|---|---|---|
| AMap Location | 服务中定位和单次业务定位 | `:feature:location`；平台 Service 在模块 Manifest 中声明 |
| Tencent COS | 图片/文件对象存储 | `:core:data` 实现，Feature 使用领域契约/上传门面 |
| CameraX + ML Kit | 标准相机、人脸检测和眨眼活体 | `:feature:photoupload` 与 `:feature:identification` |
| Tencent Face | 旧版/兼容人脸验证 | `:integration:txface` adapter + `:core:ui` UI controller，默认订单核验不进入该 SDK |
| QLZ | 销售蓝牙设备自动评估 | app-owned SDK controller；Sale API 分层在 Core |
| Bugly | 同意后的崩溃与分类诊断上报 | `DiagnosticEventTracker` → `CrashReportGateway` → `BuglyRuntime`，SDK 调用仅在 `:core:common`，App 保留显式依赖以延续原生库 Lint 检查 |
| WorkManager | 更新检查、下载与可恢复后台任务 | 自定义初始化，Worker 位于 `:app` |

### 诊断上报

- 业务分类统一使用 `DiagnosticCategory`，事件码沿用各业务的稳定 code。相机、定位、人脸、倒计时 Tracker 只保留事件目录和业务字段，格式、脱敏、长度限制及投递统一由 `:core:common/diagnostics` 处理。
- Bugly 原生 UserID 与事件字段 `userId` 使用原始 `User.userId.toString()`，不拼接公司、设备或会话 ID。未登录使用与请求公共信息一致的 `0`，覆盖上一账号；不传 SDK 会忽略的空字符串。
- `DefaultUserSessionRepository` 在发布会话状态前同步诊断身份；登录、更新、退出的持久化操作等待对应状态发布。恢复会话不依赖 UI 订阅，SDK 初始化时读取已同步的身份。照片任务在执行前捕获原始 userId，过期账号的回调不向当前账号提交。
- 会话读取遇到 I/O 异常时暂按未登录处理，每秒重新订阅；持久化后的状态发布最多等待 5 秒，超时以 `IOException` 告知调用方并释放写入锁，外部协程取消正常传播。
- 普通 INFO 事件只进入本地日志和 Bugly 上下文日志；ERROR 走捕获异常接口。NFC 定位获取成功、提交前复核、接口成功以及正式开始服务位置提交是明确的诊断例外：以 INFO 级别独立投递，供后台对照签到与签退位置。协程取消不作为错误上报。隐私同意后才初始化远端 runtime，Debug 和未初始化路径不调用 SDK。
- 单条诊断采用不可变 JSON 字段，控制在 SDK 的 1000 字符消息截断阈值以下。异常回调从该条消息解析 `category/eventCode/level/userId` 等字段，不使用全局“最后事件”变量或临时覆盖 `putUserData`。
- 人工诊断异常以明确标记为 `<diagnostic-event>` 的稳定分类/事件帧参与分组，后接实际出错帧。动态 UserID 和错误描述不写入堆栈，不附原始 cause；相关异常的类型、消息及首帧以有限、脱敏字段保存。真实崩溃保留 SDK 默认捕获行为。
- 同用户、分类、事件、错误类型/码、订单及前部堆栈的重复错误限制为 30 秒一次，内存最多保留 128 个键；切换用户清空限流记录。不新增磁盘投递队列，SDK 管理自身上传。
- 全局 UserID 是 Bugly SDK 的当前身份，SDK 异步封装期间的换号仍需实际控制台验收；事件字段保留创建时的 userId。字段可展示不等于控制台均支持筛选，线上检索和聚合以当前控制台能力为准。
- `/V1/Login/Log` 保留独立业务协议和调用链，不转为 Bugly 异常。

## 构建与发布现实

- Debug、Release、nonMinifiedRelease 和 benchmarkRelease 变体由 Android CLI/Gradle 识别。
- Android CI 与本地 `--full` 通过 `run_jvm_tests.sh` 运行全部 Android 模块 Debug 单测及两个 Kotlin/JVM 模块测试；CI 另执行构建、Lint、架构检查和按需设备冒烟。具体范围见[CI 与门禁](ci-quality-gates.md)。
- 正式构建统一使用标准 Release，保留正式签名、R8 和资源压缩；仅提供主应用 APK/AAB，历史助手附件保持不变。
- QLZ 使用默认正式环境；QLZ 1.3.0.5 弱 TLS 和腾讯人脸 6.6.2 已知厂商问题经用户明确接受，Release 输出警告。正式签名、其他质量和产物检查仍必须通过，不将风险接受视为问题修复。

## 已接受的技术债

- 大多数 route-bound UI 仍位于 `:app/features/**`。
- `:app` 同时承担壳层、平台适配和较多业务组装；新增 legacy feature 文件受 allowlist/freeze guard 约束。
- 销售体验仍由 `:app` 持有，`SalesViewModel` 体量较大。
- Navigation 3 路由和三个 feature entry 常量还不是统一的 feature-owned 导航模型。
- Manifest 组件面较广，源于定位、计时、闹钟、NFC、更新和厂商 SDK 的现实需求。
- Jetifier 移除及厂商风险修复依赖兼容的新 AAR；当前发布风险接受不等于完成这些修复，也不允许忽略其他 Lint 或签名问题。

已知限制及处理时机见[工程风险](../analysis/project-review.md)，不另设分阶段改造计划。

## 读卡检测隐私与运行时

检测仅在全局隐私同意后的登录页通过中央大 Logo 长按及确认进入，不创建单独登录、照片缓存或上传流程。NFC/R65C 只在对应模式前台监听，切换、后台和退出释放；返回原登录表单。未完成长按与确认状态不跨后台恢复。

## 依赖与架构规则

以下是开发约束。当前 App 中仍有业务实现，不构成新增 legacy 代码的许可。

### 项目模块依赖

精确机器真相是 `scripts/quality/module_dependency_allowlist.txt`。当前允许的项目模块边如下：

| 源模块 | 允许依赖的项目模块 |
|---|---|
| `:app` | `:baselineprofile`、全部 `:core:*`、全部现有 `:feature:*`、`:integration:txface` |
| `:feature:carddiagnostics` | `:core:common` |
| `:integration:txface` | `:core:common`、`:core:domain`、`:core:model`，本地来源时依赖 txface-live/txface-normal artifact wrapper |
| `:integration:txface-live` / `:integration:txface-normal` | 无项目依赖，仅暴露现有 AAR artifact |
| `:baselineprofile` | 无 |
| `:core:model` | 无 |
| `:core:domain` | `:core:model` |
| `:core:common` | `:core:model` |
| `:core:data` | `:core:common`、`:core:domain`、`:core:model` |
| `:core:ui` | `:core:common`、`:core:domain`、`:core:model` |
| `:feature:home` | `:core:domain`、`:core:model` |
| 其他现有 `:feature:*` | `:core:common`、`:core:domain`、`:core:model`；identification 和 photoupload 额外允许 `:core:ui` |

读卡检测 UI/HID 状态由 `:feature:carddiagnostics` 持有，只依赖 `:core:common`；平台监听由 `:app` 管理，不依赖业务 Repository 或事件总线。

新增或修改 Gradle 项目依赖时，必须同步检查实际 build 文件和 allowlist；不能只更新本文。

### 分层边界

#### Model

- `:core:model` 是 Kotlin/JVM 模块，只保存跨层模型和值对象。
- 禁止引入 Android framework。
- 网络字段注解只在确有共享序列化契约时保留；不要把 Retrofit 接口或数据源实现放入 Model。

#### Domain

- `:core:domain` 是 Kotlin/JVM 模块，保存 Repository/网关契约和跨 feature 的领域规则。
- 禁止 `android.*`、Activity/Context、Retrofit、Room、具体 SDK 类型和 `*Impl`。
- Feature 依赖抽象，不依赖 `:core:data`。

#### Data

- `:core:data` 实现 Domain 契约，拥有 Retrofit、Room、COS、DataStore 相关数据访问和绑定。
- 网络专用 DTO、接口路径、参数注解和数据源应隐藏在 Data 边界内。
- Data 不得依赖 Feature/UI，也不得反向调用页面导航。

#### Common 与 UI

- `:core:common` 是 Android library，可持有真正跨业务复用的基础能力；它不是无边界的杂物目录。
- `:core:ui` 只保存通用 UI、主题/组件和 UI 支撑，禁止网络、数据库或 Repository 实现。
- 只被一个 feature 使用的 helper 优先留在该 feature，不要为了“复用可能性”提前放入 Core。

#### Feature

- Feature 负责一组紧密相关的用户能力、状态和 UI/编排。
- Feature 只能使用允许的 Core 抽象，禁止直接依赖 Data 实现或另一个 Feature 的 internal 实现。
- 公共入口保持最小；非契约声明使用 `internal` / `private`。
- 新业务 UI 应优先进入 `:feature:*`，不要继续扩大 `:app/features/**`。

#### App

- 目标职责是启动、根导航、DI 组装、Manifest 和 Android/厂商平台适配。
- 当前仍有大量 route-bound UI 和流程代码，因此 `:app` 对 Core/Data/Feature 的依赖是现实允许边，而不是鼓励新业务继续堆入壳层。
- `app/src/main/.../features/**` 受冻结目录和文件 allowlist 保护；优先在现有允许文件内做小修复，新增能力迁往 Feature。


### 实施约束与例外

运行时状态、平台、数据和导航规则分别见本文对应章节；页面归属与结果 key 见[页面地图](ui-and-screen-map.md)。补充约束：

- ViewModel 通过 UseCase/Repository 契约访问数据，调度器经 DI 注入；用户文案使用资源或可测试文本抽象。
- 平台资源在对应生命周期释放；权限拒绝、设置返回与后台恢复必须可验证。
- Release 组件默认 `exported=false`；新增导出组件须安全审查、最小 intent surface 和 allowlist 更新。
- Retrofit 方法、路径、注解与 JSON key 变动同步契约测试；持久任务不能只依赖进程内事件。
- 生产 secret 不得写入源码、资源、BuildConfig、日志或 APK；客户端使用受限 token/临时凭据。QLZ 保留用户确认可用于正式环境的 appKey，appSecret 仅在服务端；不能把客户端 appKey 配置扩展到其他秘密凭据。
- 路由优化保持现有 Navigation 3 entry 邮箱、轻量参数和消费清理契约；不与业务 API 变化或大规模模块搬迁混在一次修改中。
- 架构例外在 PR 写明原因、影响与回收条件，只改最小 allowlist/预算，并增加防扩散守卫。长期取舍记录 ADR；短期执行过程留在 PR/Issue。

自动检查及执行范围统一见[CI 与质量门禁](ci-quality-gates.md)。

## ADR-001：分层边界

- Status: Accepted
- Date: 2026-02-13
- Owners: LongCare Android Team

**背景**：项目由单 App 演进，虽有 domain/data/features 目录，曾缺少可执行依赖约束，造成 UI 访问 Data 实现、Domain 混入 Android 类型和评审标准不统一。

**决策**：UI/Feature 通过 Domain 接口与模型调用业务能力；Data 实现 Domain，承担网络/数据库/存储；Domain 保持纯 Kotlin。Repository 接口在 Domain、实现在 Data、装配在 DI，禁止 Feature import Data Impl 和 Domain import Android；ViewModel 不实现数据访问细节。当前允许的 Core 基础依赖以本页依赖表及机器白名单为准。

**影响**：统一评审标准、提高业务可测试性并支持渐进模块化；代价是接口与 DI 绑定维护成本。CI 架构守卫和 PR review 持续执行这些约束。该决策表达分层方向，不证明当前所有 UI 已迁出 App。

## 定位会话与生命周期

### 业务边界

- 页面可见且精确权限、定位开关就绪时先准备 `location` 前台 Service；确认订单执行中之后才允许服务采集与上报。签到、签退取点按当前动作的窗口单独校验。
- App 切到后台后，由 `location` 类型前台 Service 继续采集和上报。
- 单个定位点上传失败后直接丢弃；不落库、不排队、不补传。
- 订单结束成功、退出登录、Token 失效、账号切换或划掉任务时停止，并清除恢复身份。普通 `onDestroy` 只释放资源，保留仍在执行的业务恢复意图。
- `START_STICKY` 接受系统重建（包括空 Intent）：先建立通知，再校验隐私、精确权限、定位开关、持久账号及服务端执行中状态；只上传重新采集的位置。
- App 普通冷启动、设备重启及系统用户主动停止后不自动恢复；存在未结束身份时显示手动恢复入口。仅保存账号、订单、会话标识，不保存坐标。
- 同进程返回前台时重试尚未成功启动的服务；用户手动恢复或重新进入订单时重新确认状态，订单已结束则清除恢复身份。

### 核心组件

1. `LocationFacade`
   - 普通采集使用 `acquireCurrentLocation` / `isUsable`；服务订单使用 `acquireServiceOrderLocation` / `serviceOrderLocationFailure`，保留权限、开关、取消和账号隔离。返回成功样本或分类失败，精度不足独立为 `PRECISION`。
2. `LocationKeepAliveManager`
   - 只保存一个当前订单 owner，以 generation 隔离迟到的系统回调；不维护多 owner 引用计数或无人订阅的状态流。失败可由前台重试。系统恢复采用当前 Service，无需从后台自行启动另一个前台服务。
3. `LocationTrackingService`
   - 持有前台通知、高德持续定位 collector 和唯一 `AddPostion` 调用点，不查询订单状态。
   - 上传闭包固定绑定订单 ID 与业务会话；切换订单先取消并释放旧 collector，再创建新采集。每次持续采集使用独立高德客户端，旧客户端的迟到成功/失败回调不得进入新会话。
4. `LocationQuality` / `LocationSampleEvaluator`
   - 单次与持续共用元数据校验；`ServiceOrderLocationQuality` 对签到、绑定、正式开始、签退和服务中上报增加 200 米精度上限。100 米为优先目标，100～200 米立即可用，超过 500 米标为明显低精度；不计算业务距离、位移或速度。
   - 已删除业务缓存及无消费者的样本中心；Service 直接消费 SDK Flow，最多一个在途上传，繁忙时丢弃回调。上传最小间隔 30 秒，不保留待补传点。
5. `LocationReportingManager`
   - 实现业务层 `ServiceOrderLifecycle`，统一同步订单状态，驱动上报会话启停，不执行上传。
   - 状态同步不依赖页面存活；倒计时页面仅订阅同一份状态，不重复请求。恢复身份通过已有 DataStore 和应用作用域中的单一串行写入协程保存、清除；换号、登出在账号写入的同一事务清除，迟到保存必须匹配持久账号。
6. `LocationSessionLifecycleObserver`
   - 登出或账号切换时强制停止；首次读取未知时暂停授权而非伪造登出。登录不会自行启动定位。

### 启停上报

```kotlin
trackingManager.startTracking(orderKey)
trackingManager.stopTracking()
```

结束接口成功后，业务执行器立即调用 `ServiceOrderLifecycle.onOrderEnded(orderId)`，
先使对应会话失效并取消在途协程，再停止 Android Service，最后执行 UI/资源清理。
结束接口失败、仅打开结束确认流程、倒计时归零均不会结束真实业务会话。
定位质量校验后、发请求前再次检查会话与取消状态；请求返回时再次检查，忽略已结束会话的迟到结果。停止、注销、后台保活释放和客户端销毁各自执行，单步异常不跳过其余释放；订单切换保留前台通知并为新客户端重新绑定。

正式开始动作在页面可见时同步准备 Service，再等待定位及接口；正式开始接口成功立即开启上传门禁，不等待额外查询，也不从异步结果再次启动 Service。重新打开订单、系统重建时先重新确认状态。准备阶段不向尚未开始的订单上报，失败或取消释放未确认的准备；旧动作的迟到清理不能释放新动作。
系统恢复先接管 owner，再开始订单查询，查询立即返回非执行中时也能停止 Service。新可见请求取消尚未完成的恢复读取；旧读取即使晚返回空值或异常，也不能停止已接管的新会话。划掉任务同步使会话失效并停止 Service，身份清除交给既有串行写入协程，避免磁盘操作完成后误停新订单。
正常业务状态同步每 30 秒一次，与上传结果无关；失败按 30/60 秒退避。初始状态不可用时 Service 保留通知但不采集、不上传，网络恢复后继续。
已确认服务中时，查询异常保留最后已确认状态，不因异常次数永久停报；
查询确认非服务中则立即停止，不等待用户确认弹窗。

`AddPostion` 没有“非服务中”专用错误码。上传结果仅记录诊断，不触发状态反查、
不修改订单状态、不弹 Toast、不因业务失败停报，不重试同一个定位点。
既有全局登录失效安全处理保持不变。后台远程状态变化存在同步延迟，
已发出的请求无法撤回，服务端仍需校验订单状态。

SDK 连续 90 秒没有原始回调时，既有 Service 健康检查在权限、开关正常且非深度 Doze 时重建一次客户端；持续无回调则显示中断并等待前台重试，不无限重启。错误回调、精度不合格均不等于 SDK 无回调。通知仅随状态变化更新；不增加 Worker、闹钟、唤醒锁或多进程保活。

Android 6+ 通过动态精确定位权限；Android 12+ 同时申请精确/大致权限并检查精确授权。Android 10+ 持续采集由已在可见流程启动的定位前台服务承载，不自动申请后台定位；Android 14+ 明确声明前台服务类型及权限，捕获系统恢复时的 while-in-use 限制。通知权限被拒绝不等同于禁止启动前台服务。厂商省电或系统用户停止不能保证自动恢复，也不保证锁屏每 30 秒准时联网。

### 单次业务定位

```kotlin
val result = locationFacade.acquireCurrentLocation()
// Success 携带完整样本；Failure 携带具体失败原因及可用的 SDK 失败样本。
```

单次定位使用独立高德客户端，不会创建第二个持续定位 collector，也不进入实时上报链路。只有一个在途请求槽，冲突返回 Busy；质量不足最多再采集一次，超时与重试共用 15 秒总预算。取消或账号切换释放本次请求，不影响持续采集。

服务订单采用同一持续采集流：动作期间将间隔升至 3 秒，最多等待 30 秒；取得不超过 200 米的合格点即返回，不为达到 100 米延长等待。不合格精度、暂时网络失败继续等待本次窗口的新回调；权限、配置、模拟立即失败。动作结束后有执行中服务则恢复 30 秒间隔，否则关闭客户端。只有一个在途服务动作，冲突返回 Busy；保留失败元数据供诊断，不将不合格点转为成功。普通销售、拍照保留独立单次客户端和 15 秒总预算，移除旧服务订单单次重试分支。

单次和持续都显式关闭 SDK 缓存，保持高精度、Wi-Fi 扫描、禁止模拟、不请求地址及 GCJ02 输出；持续采集使用 `isOnceLocationLatest=true`、`isOnceLocation=false`，SDK HTTP 超时 20 秒。3 秒动作采集属于短时提升，30 秒采集属于低频高精度，并非 SDK 低功耗模式。保留 SDK 原始时间与实际精度，不截断或转换经纬度，不用收到回调的时间替换采样时间。

质量规则：SDK 成功、坐标有限且合法、时间存在且非未来；精度有限且为正。普通登记和相机保留原 15 秒元数据规则。服务订单提交取消固定 15 秒年龄拦截，改为实际写入阶段的新采集；SDK 获取时拒绝本次启动前三秒窗口之外的历史结果。持续流拒绝会话前和乱序采样，并以墙钟、单调时钟校验等待不超过 60 秒（两个默认 30 秒周期），防止慢上传后发送陈旧点。订单隔离由会话与独立客户端生命周期保证。接受实时 GPS、同请求、Wi-Fi、基站、在线补偿及系统网络来源；服务订单允许 HIGH/NORMAL 可信度，NORMAL 仍须通过来源、时间与精度校验，拒绝缓存、离线、粗略来源、LOW/BAD、模拟、最后位置和非 GCJ02；普通登记、拍照仍要求 HIGH。质量合格不代表真实误差一定小于 200 米。

NFC 入页不预定位。签到先读取客户资料，再采集；未绑定时先确认绑定，再取位置。签退先执行不含坐标的 `CheckEndOrder`，成功或用户确认后才取位置并 `EndOrder`。位置失败保留本次碰卡、订单、项目与照片供定位重试；业务请求已发出后不自动重放，确认取消、离页和订单切换取消旧任务。位置不合格不得正常提交，签退成功前保留真实服务会话。当前接口没有低精度异常提交协议，不能通过空坐标、历史点或放宽门槛代替异常流程。

登记位置仍可选，获取时通过原元数据质量检查，重新获取先清除旧结果，确认路由携带完整样本。登记提交按用户点击获取后页面显示的定位快照发送，不因填写、确认或照片上传耗时丢弃；该快照也传入后续评估。相机拍摄前仍复核精确权限和样本时效，失效或大致权限不阻止拍照，但不写入旧坐标。

权限永久拒绝时提供应用设置入口；设置返回后重新检查权限，业务请求再核对系统定位开关。普通诊断不包含精确坐标；NFC 签到/签退及正式开始服务距离排查专门保留高德原始经纬度，统一载荷仅对这些事件放行有限数值坐标，保留完整 Double 精度。

NFC 定位诊断使用 `category=location`、`nfc_location_*` 事件码，携带原始 `userId`、`orderId/planId`、`signInMode=START_ORDER/END_ORDER` 和 `stage`。获取、权限等待/恢复、失败、取消、绑定取点及提交前复核均有事件；成功获取、复核、接口成功独立投递，取消仅记上下文。高德样本保留坐标系、定位类型、SDK 估计精度、精度分档、采样时间、样本年龄、可信等级、模拟/最后位置标记及 SDK 错误码/说明，不上传地址或 SDK 整体 dump。`requestId` 关联同一次动作采集、业务提交和结果；普通单次的两次尝试共用 ID，持续流每个独立客户端使用自己的 ID。权限拒绝保留碰卡及绑定/签退上下文供手动重试，重复授权回调不会重复提交。

`start_check` 和 `end_submit` 复核事件中的坐标直接转为对应接口参数，不转换、不截断。接口结果日志复用请求发出前的定位快照，样本年龄/质量不计入网络耗时；载荷优先保留订单、场景、原始坐标、精度、时效和错误码，再装入可选说明，避免长错误文本挤掉定位证据。既有 `nfc_workflow` 接口失败事件补充同一份样本、服务端失败码和说明，便于按用户及订单比较签到点与签退点。正式开始服务会再次定位，`service_start_location_*` 使用同一字段格式独立上报 `StarOrder` 提交点与结果（`stage=service_start`），排查时必须区分碰卡签到与正式开始点。未获得 SDK 结果的权限拒绝、Busy、超时事件不伪造坐标。质量失败重试后保留最后一次结果。本地测试不替代真机定位及 Bugly 在线投递验收。

### Android 生命周期

- 应用发起的前台 Service 从用户可见流程同步准备，声明 `foregroundServiceType="location"`；异步订单查询不承担后台启动职责。
- Service 返回 `START_STICKY`，系统重建先通知后验证账号、权限及订单；应用不通过 Worker、闹钟或反复启动服务模拟系统恢复。
- Service 使用 `stopWithTask=false` 以接收 `onTaskRemoved()`，划掉任务取消采集、清除恢复身份并显式停止；普通 `onDestroy()` 只释放资源。
- 同进程返回前台重试仍希望执行的服务；普通冷启动只提供手动恢复入口。系统用户主动停止、强制停止和设备重启不自行启动。
- 硬终止不保证 `onDestroy()` 或服务自动重建。仅保存恢复身份，重建确认执行中后重新采集，不保存或补传历史坐标。
