## Context

动机见 `proposal.md` 的 Why；行为契约见两个 delta spec。

当前构建事实如下：

- `constants.gradle.kts` 同时保存 `compileSdk 37`、`targetSdk 36`、`minSdk 24`、JDK 21 和应用版本；`:app`、`:baselineprofile` 与 `AndroidLibraryConventionPlugin` 再次读取并写入部分相同字段。
- `scripts/quality/verify_target_sdk_upgrade.sh`、`run_target_sdk_local_smoke.sh` 和 Android CI 都直接解析旧常量文件，导致 SDK 来源无法独立演进。
- `docs/architecture/tech-stack.md` 已与可执行配置产生漂移：versionCode、Navigation、CameraX、Baseline Profile/Benchmark 至少四处仍记录旧值。
- Baseline Profile 受管设备使用 API 33，适合生成 Profile，但不能证明 target 36/37 平台行为兼容。
- `run_target_sdk_local_smoke.sh` 与 `affected-modules.sh` 仍引用仓库中不存在的 `ServiceTimeNotificationIntegrationTest`，说明 instrumentation 选择器本身缺少完整性守卫。
- 主 Manifest 的 MainActivity 和两个验证 Activity 仍声明固定竖屏及 `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`；这是 target 36 可用的过渡策略，而不是 target 37 readiness 已完成的证据。
- 项目已采用 Compose BOM、version catalog、Hilt、StateFlow 与 `collectAsStateWithLifecycle`，现有 Jetpack UI 状态收集方式无需为“形式统一”重写。`:app` 仍有较大的 route-bound legacy 区域，但已由单独架构路线图和 allowlist 管理。

2026-08-29 通过 Android CLI 核对的官方事实：

- `android studio version-lookup` 显示 AGP `9.3.2`、Gradle `9.7.1`、Kotlin `2.4.10`、Compose BOM `2026.08.00` 以及项目当前主要 Jetpack 依赖均为最新稳定版；明确可升级的稳定项只有 Coil `3.6.0` 和 kotlinx-datetime `0.8.0`。
- 同一官方版本查询显示 Navigation 3 runtime/UI 的稳定版为 `1.1.7`、preview 为 `1.2.0-beta01`，而项目当前 Navigation Compose `2.10.0` 也仍是稳定基线；Nav3 已具备生产版本不等于现有项目必须立即迁移。
- Google 的 `kb://android/build/android-settings-plugin` 说明 `com.android.settings` 可在 settings 层统一 `compileSdk`、`minSdk` 与 `targetSdk`，模块级声明会覆盖统一值，且 settings 插件版本必须与 AGP 相同。
- `kb://android/about/versions/17/release-notes` 当前最新条目为 Android 17 Beta 4.1，而非稳定版。
- `kb://android/about/versions/17/behavior-changes-17` 确认 target API 37 会启用新的 MessageQueue 行为、禁止修改 `static final`、强制本地网络权限、默认 Certificate Transparency、收紧 native 动态加载和后台音频，并取消大屏方向/可调整大小/宽高比限制的 opt-out。
- `kb://android/about/versions/16/behavior-changes-16` 确认 target API 36 已进入 edge-to-edge、predictive back 和大屏适配过渡阶段；现有 target 36 因此仍需真实设备或模拟器回归，而不能只看编译结果。

## Goals / Non-Goals

**Goals:**

- 将 SDK 配置收敛到 Google 官方 settings 层，并让现有 convention plugin 专注于 JDK与编译约定。
- 保持正式 target 36，同时用机器可检查的状态和测试证据阻止未经准备的 target 37 提升。
- 用两个可独立回滚的小批次升级 Coil 和 kotlinx-datetime，并固化稳定版优先规则。
- 修复 target smoke 选择器漂移，区分 Profile 生成、当前 target 验证与下一 API readiness。
- 让 version catalog、SDK/JDK 基线、CI 守卫和长期技术栈文档持续一致。

**Non-Goals:**

- 本 change 不把正式 target 提升到 37，也不提前声明 `ACCESS_LOCAL_NETWORK` 等尚未证明业务需要的权限。
- 不修改、拆包、替换或删除厂商 AAR/Maven 制品，不关闭 Jetifier，也不升级到 AGP 10。
- 不移除当前 Manifest 的竖屏/大屏过渡兼容声明；相关页面的实际自适应改造必须作为 readiness 阻断项由独立 change 完成。
- 不进行 Navigation 3 迁移、全量 Feature 搬迁、Compose 状态体系重写或启动 `runBlocking` 的无证据重构。
- 不修改 Room、用户存储、隐私状态、设备 ID、WebView host 策略、网络契约或业务流程。
- 不放宽现有生产 Release fail-closed 条件，也不把普通 debug/CI 构建成功表述为可生产发布。

## Decisions

### 1. SDK 使用 Android Settings Plugin，JDK 与应用版本继续各自保持唯一来源

在 `settings.gradle.kts` 应用与 AGP `9.3.2` 同版本的 `com.android.settings`，并在 settings 的 `android` 块声明：

- `compileSdk { version = release(37) }`
- `minSdk { version = release(24) }`
- `targetSdk { version = release(36) }`

随后从 `constants.gradle.kts`、`:app`、`:baselineprofile` 和 Android library convention 中删除 SDK 数值及模块级 SDK 赋值，使所有 Android 模块继承 settings 值。`constants.gradle.kts` 继续只保存 JDK 21 与应用 versionCode/versionName；现有 app 和 convention plugin 继续消费同一个 JDK 值，不在本 change 中重写 included build 的版本注入机制。

新增 focused 基线守卫，解析 settings DSL 并检查：三个 SDK 值完整且满足 `min <= target <= compile`；除 settings 外的生产 Gradle 脚本不存在模块级 SDK 覆盖；JDK 和应用版本仍只有一个权威声明；settings plugin 与 AGP 版本一致。现有 target 升级脚本与 smoke 脚本改为读取 settings，不保留对已删除 SDK extra 的兼容分支。

备选方案一是继续使用 root extra 并只修正文档；它仍允许 app、baselineprofile 和 convention 重复赋值，无法满足单一来源，因此不采用。备选方案二是同时重写全部 convention plugin 和版本注入；收益与本 change 无关且扩大构建风险，因此不采用。

### 2. target 37 采用“已批准 target + 候选 readiness”双状态门禁

在 `scripts/quality/` 保存一个可被 shell 严格解析的 target readiness policy，至少记录：已批准 target、候选 target、平台通道、候选是否允许提升，以及平台行为、厂商、大屏自适应和测试矩阵四类状态。初始状态固定为：

- 已批准 target 为 36；候选 target 为 37。
- Android 17 平台通道为 Beta，候选提升为 blocked。
- 厂商、大屏自适应和完整平台行为证据均不得预填为通过。

`verify_target_sdk_upgrade.sh` 在 target 等于已批准值时继续检查 SDK 顺序与当前 API 验证入口；一旦 target 高于已批准值，只有 policy 中的平台为 stable、全部必需状态为 verified、候选值与实际 target 相等且后续独立 OpenSpec change 标识存在时才允许通过。policy 是治理状态，不自动联网；平台状态必须根据 Android CLI 官方资料在评审变更中更新，避免 CI 的时序和网络不稳定。

当前 Manifest 的方向兼容属性保留，但 readiness policy 明确保持 adaptive 为 blocked。这样不会伪装成已经适配，也不会为了通过门禁提前改变生产窗口行为。

备选方案是现在直接把 target 升到 37，因为 compile 已是 37；compile 只提供编译 API，不能证明 target 行为兼容，而且 Android 17 尚为 Beta，因此不采用。另一个备选方案是只在文档写 checklist；它无法阻止误改 target，因此不采用。

### 3. 两个稳定依赖升级独立提交、独立验证、独立回滚

先将 Coil 版本别名从 `3.5.0` 升到 `3.6.0`，运行依赖解析、图片管线单元测试、相关 Compose instrumentation、lint 和 assemble；通过后再将 kotlinx-datetime 从 `0.8.0-0.6.x-compat` 切到稳定 `0.8.0`，运行编译以及日期时间、序列化、时区与倒计时 focused tests。只允许为新 API 的编译兼容做最小源代码调整，不在依赖升级中改变业务时间语义。

每批升级都用 `:app:dependencyInsight` 确认同一依赖族没有旧版或混合版本。任一批失败时只回滚对应 catalog 值及必要兼容代码，另一批不受影响。

不升级其他库的原因是官方 version lookup 已显示当前版本为最新稳定基线；“全部重新写一遍版本号”不会带来能力提升，只会增加无效 diff。kotlinx-datetime 的 compat 后缀是迁移过渡变体，项目代码已主要使用 `kotlin.time.Clock`/`Instant`，因此切回稳定 `0.8.0` 是需要验证但范围明确的收敛。

### 4. 预览依赖使用精确 allowlist，厂商约束形成 AGP 10 阻断条件

新增 focused 依赖政策守卫，扫描 version catalog 中 alpha、beta、RC、snapshot、dev 和 compat 标记。allowlist 采用一行一个版本别名的精确格式，并要求同时填写负责人、原因、验证范围与机器可检查的退出版本；不允许 glob、依赖组级豁免或“永久”等无退出条件描述。

初始只允许 `androidxBaselineProfile` 和 `androidxBenchmark` 使用 `1.5.0-rc02`，两者分别列项但共享“稳定且经 AGP 9.3 验证的 1.5.x 可用后退出”的条件。继续保留当前 `maxAgpVersion=false` 仅作为同一豁免的一部分；退出条件满足后必须移除。这里不降级到稳定 `1.4.1`，因为降级可能失去对当前 AGP 的兼容改进，且不能消除验证责任。

同一守卫读取 `android.enableJetifier=true` 与厂商依赖政策：只要生产必需厂商制品尚未提供已验证的纯 AndroidX 版本，AGP 主版本不得进入 10。Jetifier 的弃用警告保留为外部阻断事实，不能通过 suppress、修改 AAR 或删除生产能力处理。

备选方案是使用 Gradle 全局动态版本或自动升级到最新预览；这会让构建不可重复且违背正式环境稳定性，因此不采用。

### 5. 当前 target smoke 先修复测试选择器，再扩展 API 分层验证

先增加 smoke class 完整性守卫：`affected-modules.sh` 与 `run_target_sdk_local_smoke.sh` 输出的每个 instrumentation 类必须在 `app/src/androidTest` 中真实存在。删除不存在的 `ServiceTimeNotificationIntegrationTest` 引用，并选择现有、与倒计时/闹钟权限相关的 instrumentation test，或在现有包边界内补回等价 focused test；选择以能验证 API 36 行为而非仅使类名存在为准。

当前 API 36 使用阻断式 smoke：至少包括 MainActivity 启动、隐私/登录入口、WebView、通知/精确闹钟回退和已有自适应 Compose tests；对 NFC、相机、定位、QLZ 和腾讯人脸等硬件或厂商链路，CI 使用可替代的契约/拒绝恢复测试，正式 target 提升仍要求真实设备证据。standard PR 只在 affected detector 判定需要 instrumentation 时运行，Release 验证入口始终要求当前 API smoke 证据。

API 37 在 Beta 阶段作为独立的手动或可容错 readiness lane，复用相同 smoke 集合并额外检查：大屏窗口变化、MessageQueue/反射与 native 制品、局域网访问清单、Certificate Transparency、后台闹钟音频以及厂商 SDK 启动。该 lane 失败会保持 candidate blocked，但不额外阻断 target 36 发布；Android 17 稳定并提出后续升级时，再将其转为阻断门禁。

Baseline Profile 继续使用 API 33 生成，不为追求数字统一而改成 API 36/37；Profile 生成和 target smoke 在命名、CI summary 和验证结果中明确分开。

备选方案是把 API 33 Profile 成功当作平台兼容成功；两者验证目标不同，因此不采用。另一个备选方案是每个 PR 都运行完整厂商真机矩阵；当前基础设施与厂商可自动化程度不足，采用分层 CI + target 提升前真机证据更可执行。

### 6. 文档漂移由现有事实来源驱动，不再人工复制整张依赖清单

修正 `docs/architecture/tech-stack.md` 当前已知旧值，并新增 focused 漂移验证，只核对长期文档承诺展示的字段：应用版本、SDK/JDK、AGP/Gradle/Kotlin/Compose BOM、Navigation、CameraX、Coil、kotlinx-datetime 和 Baseline Profile/Benchmark。实际解析版本仍以 version catalog 与 Gradle dependency graph 为准；文档不扩张为每个传递依赖的镜像。

同步 `dependency-rules.md` 的稳定版/预览豁免/厂商阻断规则，以及 `roadmap-and-open-gaps.md` 的 API 37 状态。不开辟新的执行报告 Markdown；运行证据留在 CI artifact、PR 或终端。

备选方案是生成并提交完整依赖树；其平台差异大、噪声高且容易制造无意义变更，因此不采用。

### 7. 保留已符合 Jetpack 推荐的架构，不制造无目标重构

本次审查确认现有 UI 使用 lifecycle-aware Flow 收集，Core 的 model/domain 保持 Android-free，Hilt、Repository 和 feature 边界已有守卫，edge-to-edge 入口也已经启用。本 change 只让构建与平台升级治理补齐，不移动生产 Kotlin 文件。

`:app` 体量偏大和启动阶段同步切换仍是后续优化候选，但前者已有 legacy allowlist/路线图，后者涉及用户隔离 fail-closed 语义；它们应分别通过模块迁移 change 和性能证据驱动的启动优化 change 处理，而不是夹带在版本升级中。

### 8. Navigation 3 已可用，但当前不具备低风险原子迁移条件

Google 的 `kb://android/guide/navigation/navigation-3/migration-guide` 要求将 NavController/NavHost/NavGraph 整体替换为自主管理的 back stack、Navigator、entryProvider 和 NavDisplay，并明确把迁移视为单次原子变更。项目满足 `compileSdk >= 36`、纯 Compose destination 与类型安全 route 三项基础前提，且没有发现 deep link 或两层以上 nested graph，因此技术上可以迁移。

当前不迁移的决定来自项目契约风险，而不是版本稳定性：

- 主导航分散在 21 个 Kotlin 文件、约 23 个 destination，并依据 `SessionState` 在 Login 与 HomeGraph 之间动态选择起点，外层还有隐私同意 gate。
- HomeGraph 的 back stack entry 被用作 `TodayOrderViewModel` 共享 scope；Nav3 需要通过 ViewModel entry decorator 和明确的新 scope 模型保持同等生命周期。
- 三个导航注册文件通过 `previousBackStackEntry`/`SavedStateHandle` 传递图片、相机、人脸和服务结果，并要求消费后清除。Nav3 官方 event/state recipe 的持久化语义不同，其中 state recipe 明确不跨配置变化或进程死亡，不能直接替换现有契约。
- 现有导航动作包含 `popUpTo(LoginRoute)`、`popUpTo(HomeGraphRoute)`、清空到 id 0、替换当前 route、`launchSingleTop` 和 lifecycle-resumed 防重复导航；这些都必须在自定义 Navigator 中逐条重建。
- 多个 route 携带 `OrderNavParams`、`ServiceCompleteData`、`WatermarkData` 等复杂可序列化对象并配有自定义 NavType，增加 back stack 保存/恢复与升级兼容面。
- 当前测试只验证 Feature route 字符串唯一性，没有覆盖动态起点、返回键、清栈、结果返回、共享 ViewModel scope、配置变化和进程恢复，达不到官方“先有导航行为测试”的强烈建议。

因此本 change 保持 Navigation Compose `2.10.0`，不增加 Nav3 依赖，也不先做双栈共存。只有在独立准备工作完成后才提出 Nav3 change：先为上述行为建立可执行回归基线；将可避免的大对象 route 参数收敛为稳定 ID；明确 conditional navigation、结果通道与 ViewModel scope 设计；确认迁移收益来自可控 back stack 或真实的 adaptive multi-pane 需求；最后以单一分支原子替换并保留整体回滚路径。

备选方案一是只把依赖换成 Nav3 并逐页兼容；官方迁移模型和现有共享结果契约都不支持这种低成本渐进替换，因此不采用。备选方案二是因为 Nav3 已稳定就立即迁移；Nav2 仍在稳定维护且当前没有用户问题或 adaptive scene 需求能抵消回归风险，也不采用。

## Security / Privacy

- 本 change 不新增 Android 权限，也不提前申请 API 37 的 `ACCESS_LOCAL_NETWORK`；先盘点应用与厂商是否真实访问 LAN，再由后续 target change 决定最小权限和拒绝恢复流程。
- API 37 readiness 必须覆盖 Certificate Transparency、ECH/网络栈、native 动态加载、反射修改静态常量和后台音频约束；发现厂商二进制问题时保持 blocked，并向厂商升级，不修改二进制规避平台安全机制。
- 不触碰 Room、DataStore、用户文件或密钥，不发生数据迁移、重建或加密格式变化。
- CI/政策文件只记录版本与兼容状态，不保存厂商凭据、个人信息、设备标识或测试账号。

## Test Strategy

- shell fixture：SDK 单一来源、模块覆盖、非法 SDK 顺序、缺失 readiness 字段、未批准 target 提升、预览依赖缺少豁免、豁免字段缺失、文档漂移和 smoke class 不存在均应稳定失败。
- Gradle focused：build-logic tests、受影响模块单元测试、Coil 图片管线 tests、日期时间/序列化/倒计时 tests、`:app:dependencyInsight`、`:app:lintDebug` 与 `:app:assembleDebug`。
- instrumentation：API 36 上运行修复后的 smoke 集合和既有自适应 UI tests；API 37 Beta lane 作为 readiness 证据但保持非阻断正式 target 36。
- 性能：确认 API 33 Baseline Profile 生成和现有 journey guard 不因 settings 迁移或插件豁免治理退化。
- 综合门禁：`preflight_local.sh --local-fast`、必要的 `--full`、CI quality guards、Release validation entry contract 与严格 OpenSpec validation。

## Risks / Trade-offs

- [Settings Plugin 与 included build/convention 的生效顺序产生意外覆盖] → 先增加负向 fixture 和 Gradle 配置检查，再逐模块删除旧 SDK 赋值；保留可单提交回滚的旧基线迁移步骤。
- [kotlinx-datetime 稳定变体存在源或序列化差异] → 单独升级并运行时区、序列化和倒计时 focused tests；失败时只回滚 datetime 批次。
- [Coil 3.6 改变缓存或占位行为] → 运行统一图片管线、缓存策略和 Compose instrumentation；不以视觉差异不明显代替断言。
- [API 36 emulator 无法覆盖 NFC、相机真实性能或厂商设备] → CI 验证契约与拒绝恢复，target 提升和发布保留真机/厂商证据，不把模拟器结果泛化。
- [API 37 Beta 行为继续变化] → readiness lane 保持非阻断且 policy 标记 Beta；稳定版发布后重新用官方文档刷新清单。
- [预览版 allowlist 变成永久豁免] → 要求精确别名、负责人和退出版本，并让守卫在退出版本已经满足时失败。
- [Jetifier 弃用阻碍未来 AGP] → 明确登记为厂商外部依赖并阻断 AGP 10；持续向厂商获取 AndroidX 制品，但本项目不擅自处理 AAR。
- [新增 API 36 instrumentation 增加 CI 时间] → 使用 affected detector 选择性运行，完整矩阵放在 Release/手动 readiness，保持普通文档和无关模块变更成本可控。
- [暂缓 Nav3 可能推迟 adaptive scene 收益] → 保留明确准入条件；一旦出现多窗格产品需求或自主管理 back stack 的实际收益，创建独立迁移 change，而不是与 SDK/依赖升级耦合。

## Migration Plan

1. 先新增并验证 SDK、预览依赖、文档和 smoke class focused 守卫；在旧配置上证明能识别当前重复项、文档漂移和失效测试类。
2. 应用 Android Settings Plugin，将 SDK 值迁至 settings，逐个移除模块/常量中的 SDK 赋值，并同步 target/readiness 解析入口；运行配置、build-logic、lint 和 assemble 回归。
3. 建立预览依赖 allowlist 与 AGP 10/Jetifier 阻断，保留 Baseline Profile/Benchmark 当前 RC 及明确退出条件。
4. 单独升级 Coil 并验证；通过后再单独切换 kotlinx-datetime 稳定变体并验证。任一批次可独立回滚。
5. 修复 instrumentation smoke 选择器，建立 API 36 当前 target smoke 和 API 37 Beta readiness lane；确认 API 37 policy 仍为 blocked，且 API 33 Profile 生成独立通过。
6. 修正长期技术栈、依赖规则和路线图，接入 local-fast、CI 与 Release validation 入口，最后运行完整风险相称验证和 OpenSpec strict validation。

若 settings 迁移导致构建不可用，可整体回滚 settings plugin 与 SDK 消费改动，恢复原常量读取；若依赖升级回归，仅回滚对应版本批次。所有回滚均不修改用户数据、数据库 schema、Manifest 组件行为或厂商制品。
