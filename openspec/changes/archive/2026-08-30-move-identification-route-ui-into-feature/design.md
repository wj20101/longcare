## Context

动机见 `proposal.md`，行为约束见 `specs/identification-feature-boundary/spec.md`。

当前身份识别能力已经部分模块化：`:feature:identification` 持有 ViewModel、用例、数据网关、默认人脸验证页面、CameraX/ML Kit 人脸采集支持及 Hilt 绑定；`:app` 仍持有 `features/identification/ui/` 下 9 个主页面文件、当前路由使用的手动采集兼容页面、两个头像资源和部分页面字符串。应用导航在 `registerIdentificationRoute` 中构造 `IdentificationActions`，通过三个 `SavedStateHandle` key 接收相机、手动人脸采集和默认验证结果。主页面同时直接创建 app-owned `FaceSdkUiController`，因此现有页面无法原样迁入 feature。

现有模块依赖已足够：`:feature:identification` 依赖 `:core:common`、`:core:domain`、`:core:model` 和 `:core:ui`，其中权限用途弹窗、返回键处理、共享订单 ViewModel 与主题都可从 Core 使用。盘点确认通用 `BottomSafeActionContainer` 仍误放在 `:app`；实现时保持包名/API 不变将其提升到 `:core:ui`，不增加 project dependency，也不让 feature 依赖 `:app`。

2026-08-30 已通过 `android docs search/fetch` 核对官方资料：Android 模块化指南要求 feature module 对应完整功能或相关页面、保持高内聚低耦合并只暴露最小 API；UI layer 指南建议以生命周期感知方式收集不可变 UI state，并让 screen wrapper 包装无状态渲染组件；Navigation 指南建议目的地通过回调暴露导航事件而不是接收 `NavController`。参考：

- [Modularization](https://developer.android.com/topic/modularization)
- [Common modularization patterns](https://developer.android.com/topic/modularization/patterns)
- [UI layer](https://developer.android.com/topic/architecture/ui-layer)
- [Encapsulate destinations](https://developer.android.com/guide/navigation/design/encapsulate)

## Goals / Non-Goals

**Goals:**

- 让身份识别主路由的页面、专属资源、屏幕状态装配和效果消费统一归属 `:feature:identification`。
- 将 app-owned 腾讯人脸 UI 适配器转换为 feature 可调用、但不反向依赖 `:app` 的显式启动边界。
- 以一个屏幕级不可变状态和明确用户事件驱动渲染，消除子组件直接依赖 ViewModel 的做法。
- 保持导航 route、结果 key、共享订单 ViewModel 作用域、权限与业务顺序等价，并为边界增加可执行防回退守卫。
- 让迁移可按测试、公开 API、UI/资源、导航适配、治理文档分批审查，并始终可以整体回滚而不触碰用户数据。

**Non-Goals:**

- 不把类型安全 route 或 `NavGraphBuilder` 目的地注册迁入 feature，也不在本 change 引入 Navigation 3。
- 不重写身份识别领域状态机、网络接口、照片处理算法、默认/手动人脸实现或厂商 SDK。
- 不迁移登录、首页、拍照上传、倒计时、销售等其他 route-bound UI，不新增 feature 或 Core 模块。
- 不进行视觉改版、文案重写或 Snackbar/Toast 体系重构。
- 不修改 Manifest 权限、Room、用户级存储、WebView、依赖/工具链版本、SDK level、签名或厂商 AAR。

## Decisions

### 1. 本次只迁移页面所有权，根目的地注册继续由 `:app` 持有

`:app` 继续定义并注册 `IdentificationRoute`，解析 `OrderNavParams`，持有 `NavController`，构造导航回调和三个结果流；然后调用 feature 的唯一公开 Compose 入口。这样 route 序列化、返回栈、`CAPTURED_IMAGE_URI_KEY`、`FACE_IMAGE_PATH_KEY`、`DEFAULT_FACE_VERIFICATION_RESULT_KEY` 以及相邻目的地均无需改变。

最终 app 层不得保留身份识别渲染 wrapper；route lambda 可以做 Compose 级平台装配，但不能包含验证状态、卡片 UI 或照片业务分支。`SharedOrderDetailViewModel` 与 `IdentificationViewModel` 仍在该 back stack entry 的 feature 入口中通过 Hilt 取得，从而维持当前作用域。

**替代方案：** 把 route type、目的地注册和 `NavGraphBuilder` 扩展一起移入 feature。该方案更接近官方“目的地靠近页面”的最终形态，但会同时引入 Navigation 依赖、跨 feature route 类型归属和结果 key 所有权变化，并与未来 Navigation 3 评估耦合。本项目当前缺少足够的路由等价测试，因此留给独立 change。

### 2. 暴露单一 feature 屏幕入口，并以显式回调跨越厂商 UI 边界

在 `features.identification.api` 提供一个公开的 `IdentificationFeatureScreen` Compose 入口。它接收 `OrderKey`、现有 `IdentificationActions` 和一个窄的 suspending 人脸 SDK 启动函数；页面实现、状态映射、卡片、Scaffold 与效果处理均为 module-internal。现有 `IdentificationActions` 继续承载导航回调、结果 `StateFlow` 与对应 clear acknowledgment，避免在纯迁移中大规模重塑 app-feature 合约。

厂商启动回调只接收 feature-owned、带 `id` 的启动请求和一个 `FaceSdkEvent` 回调。`:app` 的 route lambda 通过 `rememberFaceSdkUiController()` 与当前 UI Context 实现该函数。feature 观察到新的请求后调用它，把事件以同一 request id 交回 ViewModel，并在启动函数接受请求后确认消费。协调器继续忽略不匹配 id 的迟到事件。

这使依赖方向保持为 `:app -> :feature:identification -> :core:*`；`FaceSdkUiController`、`FaceVerifier` 的 UI 调用与 Hilt entry point 继续留在 `:app/platform/face`，厂商 AAR 不进入 feature API。

**替代方案 A：** 把 `FaceSdkUiController` 移到 feature。该控制器需要当前 UI Context 且封装厂商实现，会让 feature 直接承担 app/platform 适配责任，因此不采用。

**替代方案 B：** 让 app 直接观察身份识别 ViewModel 的请求并调用其消费方法。这样会把 ViewModel 变成 app 的公开 API并扩大耦合，破坏最小公开入口，因此不采用。

**替代方案 C：** 为单个启动动作新增 Hilt 接口绑定。由于启动需要当前 Compose UI Context，且只有一个调用点，回调比跨组件 DI 更清晰，也更易注入 fake 测试，因此不采用额外绑定。

### 3. 增加屏幕级聚合状态，但不重写领域状态机

在身份识别 ViewModel 中以 `combine`/`stateIn` 汇总现有 `identificationState`、`currentVerificationType`、`faceVerificationState`、`photoUploadState`、`faceSetupState`、不可丢失动作队列和厂商启动请求，形成单一 `IdentificationScreenUiState`。该状态保存业务枚举与待消费动作，不复制 Repository 数据，也不把 `Context`、Activity、导航控制器或控制器实例放入 ViewModel。

公开入口只在 screen/state-holder 层使用 `collectAsStateWithLifecycle()`。内部 `IdentificationScreenContent`、卡片和状态区域接收不可变 render state 与事件回调；重试先作为事件请求状态持有者重置对应状态，再触发验证，而不是由卡片直接调用 ViewModel。为减少一次性重构风险，现有领域状态类型和 ViewModel 方法先保留，屏幕事件在入口层映射到这些方法。

三个导航返回结果仍是外部 `StateFlow` 输入：效果层接收非空结果后先提交给 ViewModel，再调用对应 clear acknowledgment。不可丢失动作只处理队首，完成导航/提示后按 id 确认消费；厂商请求也按 id 触发。所有 `LaunchedEffect` 使用稳定 id 或结果值作为 key，避免普通重组重复执行。

**替代方案：** 在本 change 中把所有 ViewModel 方法改成单一 `onEvent` reducer 并重写状态机。长期形态更统一，但会扩大到验证、上传和人脸设置领域流程，显著增加回归面；本次只统一 UI 边界和渲染输入。

### 4. 资源随页面迁移，复用 Core 能力但不制造通用资源重构

`ic_service_person.webp`、`ic_elder_person.webp` 以及只被身份识别页面使用的 `identification_*` 字符串迁入 feature。仍被 app 内人脸引导页使用的 `face_recognition_guide_*`、通用 `common_back`/`common_next_step` 和其他页面共享的 `camera_permission_required` 保留在 app；feature 增加语义明确的 `identification_*` 等价值，避免 Android library 引用 app 的 `R` 或在本 change 中搬迁全局资源。

主题渐变、single-click、权限用途弹窗和返回键辅助继续使用 `:core:ui`/`:core:common` 的现有公开能力；通用安全区容器先以原包名和 API 从 `:app` 提升到 `:core:ui`，现有调用点无需改写。仅当编译证明 feature 缺少直接依赖时，才从 version catalog 增加最小 AndroidX/Compose 依赖，并同步 `module_dependency_allowlist.txt`；不得借此升级版本。

**替代方案：** 把所有通用文案和资源同时提升到 `:core:ui`。当前共享资源归属尚未形成全局规范，扩大迁移会影响大量 legacy 页面，因此不采用。

### 5. 复用现有 legacy allowlist，并增加身份识别专用快速边界守卫

迁移完成后从 `legacy_feature_files_allowlist.txt` 精确删除 9 个 app 身份识别 UI 路径；现有 allowlist 守卫自然阻止它们或其他新文件回流 legacy 目录。`verify_architecture_boundaries.sh` 的身份识别 UI 行数阈值改为 feature 路径，保留当前上限而不放宽。

另增加可独立运行的 focused 守卫及临时 fixture，验证：app 的身份识别 UI 目录不再包含 Kotlin 页面；feature 身份识别源码不得 import app-owned `navigation`、`platform` 或 app `R`；app 只使用 feature 的公开入口而不 import其内部 UI/VM。focused 守卫接入 `verify_architecture_boundaries.sh` 与 `preflight_local.sh`，错误输出包含违规文件、规则和修复方向。

**替代方案：** 只依赖 Gradle 编译失败。编译可以阻断非法 project dependency，却不能快速解释页面回流、过宽公开 API 或错误资源命名空间；显式守卫反馈更快且可用负向 fixture 验证。

### 6. 测试以“先刻画、再搬迁、最后设备验证”分层

实现前先为现有可观察结果补齐 characterization tests。feature JVM tests 覆盖聚合状态映射、卡片状态、重试事件、动作队列顺序、厂商请求一次消费和迟到 id；无状态 Compose UI tests 覆盖两类人员卡片、下一步启用条件、返回/验证/重试回调和进度/错误展示。app 侧测试覆盖 route 参数、三个结果 key 的读取/清除以及共享 ViewModel owner 不变。

设备层使用 API 36 模拟器验证：进入/返回；相机权限说明、拒绝后停留与授权后恢复；老人拍照结果只处理一次；默认/手动/腾讯人脸成功、失败、取消；前后台切换及页面重建；成功后只导航一次。厂商能力无法在模拟器完成的部分使用 app adapter fake 做自动化，并在支持厂商 SDK 的真机保留一次 smoke test。构建绿色不能替代这些流程证据。

## Risks / Trade-offs

- **[结果流在页面重建时被重复处理]** → 保留现有 key，按“提交结果后立即 clear”确认，并用重建测试断言每个值至多处理一次。
- **[厂商人脸请求因重组重复启动或迟到回调污染新请求]** → 以稳定 request id 驱动效果，启动接受后确认消费，协调器只接收活动 id；扩展现有 coordinator tests。
- **[聚合多个 StateFlow 改变短暂状态的显示顺序]** → 先刻画关键状态组合，聚合只做映射不改领域写入顺序，并保留原状态类型。
- **[资源移动导致缺图、错误文案或 app 其他页面资源被删除]** → 迁移前做全仓引用盘点，仅删除身份识别独占资源；对共享文案创建 feature-local 等价值，并执行资源 lint 与 UI 测试。
- **[共享订单 ViewModel 作用域变化]** → feature 入口继续在当前身份识别 back stack entry 下取得实例，不从 application scope 注入；用路由集成测试验证订单上下文。
- **[公开 Compose API 继续膨胀]** → 只公开一个屏幕入口、现有 actions 合约和一个平台启动函数；所有渲染组件、mapper 与 state holder 使用 `internal`。
- **[暂未实现完整目的地封装]** → 接受 app 继续持有 Navigation 2 route 注册的中间形态；在 route 等价测试与大参数清理完成后再独立评估 Navigation 3/目的地所有权。
- **[生产厂商限制被误判为本 change 已解决]** → 厂商 AAR、TLS、16 KB 对齐、consumer rules 和 Release fail-closed 结果保持原样，在验收记录中继续区分 debug 架构迁移成功与 production readiness。

## Migration Plan

1. 记录迁移前文件/资源/allowlist 快照，补齐状态、动作队列、SDK 请求和导航结果的 characterization tests；先在现状代码上证明测试有效。
2. 在 feature 增加公开屏幕/平台启动合约、聚合 UI state、纯 mapper 与无状态内容，扩展 coordinator/queue tests；不修改 route、结果 key 或领域接口。
3. 将 9 个页面文件和独占资源迁入 feature，在同一可构建步骤中删除 app 副本；把 app route lambda 改为提供导航、结果流与 `FaceSdkUiController` 启动回调。
4. 缩减 legacy allowlist，迁移行数阈值，增加 focused 边界守卫及负向 fixture；确认 project dependency allowlist 没有新增边或仅包含经核实的最小直接依赖。
5. 同步 `docs/architecture/system-overview.md`、`dependency-rules.md`、`ui-and-screen-map.md`、`roadmap-and-open-gaps.md` 以及受影响的质量文档。
6. 依次运行 focused shell fixture、feature/app 单元测试、feature/app lint、debug assemble、`preflight_local.sh --full`，再在 API 36 模拟器及厂商真机完成关键流程验证。已知 production Release 门禁必须保持 fail-closed，不得通过放宽守卫验收。

回滚时把 feature UI/资源、app route 适配、allowlist/守卫和文档作为同一原子变更整体还原。由于 route 字符串、结果 key、数据库、用户存储、网络契约和厂商制品均未变化，回滚不需要数据迁移，也不得清理用户数据。
