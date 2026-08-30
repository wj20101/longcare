## Context

参见 [proposal.md](proposal.md) 的 Why。当前工程包含 13 个 Gradle 模块，但约 464 个 Kotlin 文件仍位于 `:app`，其中 legacy feature 区约 218 个文件；因此本轮的轻量化目标是减少无效维护面，而不是用合并模块或大规模搬迁制造更大的变更。

审计同时发现工作区已有 `prune-deterministic-project-r8-rules` 与 `separate-startup-and-baseline-profile-semantics` 两项未归档变更，且根导航、构建脚本、workflow、Profile 和 R8 文件存在未提交修改。本变更必须把这些内容视为外部基线，不能借清理之名重写或重新解释其结果。

Android 官方架构与模块化指引支持清晰的 UI/Data 边界和按职责拆分，同时明确模块越多会带来配置与构建开销；本项目当前 Feature/Core 模块仍具有明确所有权，故本轮只清理模块内部空壳和错误构建能力，不按文件数量合并模块。资源侧当前 Lint 未报告 `UnusedResources`，图片也没有发现字节级重复，因此资源压缩和视觉素材转换不进入本轮。

已复核的初始证据如下；实施时仍要在删除前重新扫描，避免工作区变化使结论失效。

| 类别 | 当前证据 | 处理决策 |
|---|---|---|
| Core 占位声明 | 四个声明零生产引用，Release usage 报告完整移除 | 直接删除，并禁止同名/等价空壳回流 |
| 空 Hilt 模块 | Core Data、Home、Identification、Login 四个模块没有 binding/provider，仅一个测试验证对象存在 | 删除模块与无业务断言的自证测试 |
| `FeatureEntry` 注册 | 三个字符串不是真实 typed route，只被私有集合及自证测试引用，Release usage 完整移除 | 删除入口、注册集合和四套自证测试；文档改用真实 route 事实 |
| 选设备流程 | `SelectDeviceRoute` 只注册不导航；名为 `navigateToSelectDevice` 的动作实际直接导航 NFC | 删除 route、页面、动作和专属资源；调用方改为明确的 NFC 动作并补契约测试 |
| 旧更新弹窗 | 旧 `UpdateDialog` 零引用，根导航使用另一套 `AppUpdateDialog` | 删除旧文件，保留实际弹窗及共用字符串 |
| Uri JSON 测试 | 两个同名测试覆盖高度重复，其中一套包含序列化、反序列化和 null 用例并集 | 合并为单一规范测试，保留全部有效断言 |
| 空 Manifest | 十个文件只包含空 `<manifest />`；定位模块清单声明真实 Service | 删除无声明的空文件并验证 merged manifests；保留定位清单 |
| 独立质量收集器 | 两个收集器无 workflow、注册表、文档或其他脚本入口，能力分别被现有 Profile/质量验证和 CI health monitor 覆盖 | 再次核对人工入口后删除；把仍有价值的 `test_affected_modules.sh` 接入现有治理测试 |

## Goals / Non-Goals

**Goals:**

- 用一致、可复核的证据门槛清理首批高置信冗余，并保持开始服务、身份核验、定位、拍照、倒计时和发布验收行为不变。
- 让目标 Feature 的 Gradle 配置表达源码实际需要，既移除无用构建能力，也补齐被传递依赖掩盖的直接依赖。
- 复用现有架构、构建和导航治理入口建立防回归检查，不为每个被删文件新增一个独立脚本。
- 让 frozen legacy allowlist、屏幕地图、模块说明和可执行工程重新一致。

**Non-Goals:**

- 不以模块数、文件数或单文件行数作为本轮验收指标，不合并 Core/Feature 模块，也不拆分销售端大文件。
- 不根据 R8 usage 单一信号删除反射、JNI、序列化、DI、Manifest、资源或厂商 SDK 可能触达的实现。
- 不删除 `feature:location` Service Manifest、Coil GIF/SVG/Video 解码器、用户存储 namespace cutover、Preview、厂商 AAR/consumer rules 或 Jetifier。
- 不改变 Navigation 版本、SDK/JDK、数据库 schema、用户数据、WebView host 行为、权限模型、网络协议、R8 策略或 Profile 语义。
- 性能前后对照延后；本轮只要求无构建/功能回归，不宣称启动速度、APK 体积或运行时性能收益。

## Decisions

### 1. 采用三层证据门槛，而非自动化“未引用即删除”

每个候选项依次通过：

1. **静态所有权**：扫描生产源码、测试、资源、Gradle、Manifest、workflow、文档和脚本入口；测试仅自证声明存在不算业务所有权。
2. **动态入口排除**：检查 Hilt/KSP、序列化、反射、JNI、typed route、`SavedStateHandle`、Android 组件、资源 ID、厂商 SDK 和人工脚本入口。R8 usage 只作辅助交叉证据。
3. **删除后证明**：运行受影响模块编译、focused tests、lint、Debug assemble 与显式非生产 Release acceptance；行为候选还必须有契约测试或设备 smoke 证据。

任一层存在不确定性，候选项就进入暂缓清单，不在本变更中删除。相比直接使用 IDE unused inspection 或 R8 usage 批量删除，该策略速度较慢，但能覆盖 Android 动态入口和厂商边界。

### 2. 按可独立回滚的清理切片实施

实施顺序固定为：工作区基线保护 → 纯空壳 → 导航/UI → 测试/Manifest → Gradle 依赖 → 质量脚本/守卫 → 文档与综合验证。每个切片完成自身验证后再进入下一片，避免一次删除数十个文件后无法定位失败来源。

- **纯空壳切片**：删除四个 Placeholder、四个空 Hilt module、三个 `FeatureEntry`、应用私有 registry 及对应自证测试；不删除实际 Hilt binding module。
- **导航/UI 切片**：让服务流调用显式的开始服务 NFC 导航动作，删除两个 `navigateToSelectDevice` 包装、`SelectDeviceRoute` 注册与页面；删除旧 `UpdateDialog`。实际 `AppUpdateDialog` 与 NFC typed route 参数保持不变。
- **测试/Manifest 切片**：保留 Uri adapter 用例并集后删除重复套件；删除十个空 Manifest，并用 merged manifest 确认定位 Service、APS Service 及应用组件未变化。
- **脚本切片**：删除无入口且被替代的 `collect_build_baseline.sh` 与 `collect_ci_run_metrics.sh`；保留 `affected-modules.sh` 及其自测，并由既有 Android build governance 测试执行该自测。

不选择“一次性删除所有搜索不到引用的类”，因为 Compose Preview、Hilt、序列化、清单组件和厂商回调很容易产生静态假阴性。

### 3. 以模块源码直接使用情况精简 Gradle

依赖变更采用下表的目标状态；实施中如果编译或测试暴露额外直接用途，补回最窄制品并记录证据。

| 模块 | 移除 | 保留/补齐 |
|---|---|---|
| `:feature:location` | Kotlin Compose plugin、`buildFeatures.compose`、Compose BOM/UI/Material3、Activity Compose、Lifecycle Runtime Compose、Hilt Navigation Compose、直接 Bugly | 保留 AndroidX Core、Hilt、AMap 与真实 Service Manifest；显式补齐 Lifecycle ViewModel，并将只使用 Default/Flow 的协程依赖收窄为 Coroutines Core |
| `:feature:photoupload` | AndroidX Core KTX、Coil bundle、直接 Bugly、过宽的 Lifecycle Runtime KTX | 显式使用 Lifecycle ViewModel 与 Coroutines Core；保留 Core 契约和 Hilt |
| `:feature:servicecountdown` | AndroidX Core KTX、Coroutines Android | 保留 Lifecycle ViewModel/Hilt，以 Coroutines Core 满足 Flow、delay 和 scope |
| `:feature:identification` | 直接 Bugly | 保留 Compose、Core KTX、CameraX、ML Kit、DataStore、OkHttp、Hilt Navigation Compose；显式补齐 Coroutines Core |

`CrashReportGateway` 由 Core 边界提供，因此 Feature 不应直接依赖 Bugly 实现。Location 使用 NotificationCompat、ServiceCompat、ContextCompat 和 LocationManagerCompat，不能删除 Core KTX。Identification 真实使用 Compose/CameraX，不能因其他模块无需 Compose 而套用同一规则。

不引入自动依赖分析插件：它会扩大工具链和告警治理范围，且对代码生成、Compose、反射和 Android resource 的误报仍需人工解释。本轮使用源码证据、Gradle dependency insight、独立编译和既有门禁完成确定性精简。

### 4. 防回归复用现有治理入口

- 在 `verify_architecture_boundaries.sh` 的现有规则体系中增加精确的退役指纹检查，覆盖 Placeholder、伪 `FeatureEntry`、选设备 legacy package/route 和旧更新弹窗；fixture 验证每类回流均能定位失败。
- 在 `verify_android_build_baseline.sh`/`test_android_build_governance.sh` 中验证四个模块的关键构建能力与依赖边界，并执行 `test_affected_modules.sh`，而不是新增平行的总入口。
- 在现有 entry navigation focused tests 中固定开始服务构造 `NfcSignInRoute(START_ORDER)`、订单参数透传和返回栈不含选设备目的地。
- `legacy_feature_files_allowlist.txt` 必须精确移除选设备三个文件；校验器继续禁止新增 legacy 文件，不能用重新登记 allowlist 规避清理。

守卫只检查具有架构意义的类别与关键配置，不保存整棵文件树快照，避免正常重命名和模块演进导致高维护成本。

### 5. 验收以行为等价与构建自洽为准

文件/行数减少仅作说明，不作为成功条件。成功条件是：新增规范场景通过；所有受影响模块不依赖偶然传递 API；Debug 与显式非生产 Release acceptance 产物可构建；merged manifest、R8/Profile/签名验证不退化；现有 production Release 仍只因已登记厂商风险 fail-closed。

不把本轮与延后的性能对照绑定，也不复用旧 APK、mapping 或 Profile 作为新产物证据。

## Risks / Trade-offs

- [工作区现有未提交修改与清理文件重叠] → 实施开始记录分支、`git status` 和相关 diff；只做精确小块编辑，不格式化整文件，不修改两个活动 change 的工件和待验收结论。
- [静态零引用遗漏 Android 动态入口] → 使用三层证据门槛；Manifest、Hilt/KSP、序列化、反射、JNI、资源、厂商回调或人工入口任一不确定即保留。
- [移除依赖后才暴露传递依赖] → 每个模块单独编译、测试和 lint；补回最窄直接依赖，禁止恢复整套 bundle 或依靠其他模块导出。
- [删除选设备 route 改变返回栈] → 先补直接 NFC route 契约测试，再删除 route；在 API 36 模拟器验证服务单开始、返回和 NFC 失败恢复。
- [删除空 Manifest 改变合并顺序或组件结果] → 对删除前后 merged manifests 做结构对照；任何非空差异立即恢复对应 Manifest。
- [精确指纹守卫变成另一种冗余] → 只把架构类别接入已有 verifier/fixture，不为单文件创建新脚本，也不检查无意义的总文件数。
- [删除人工质量入口影响个人流程] → 删除前再次搜索 shell history 之外可维护的仓库入口，并以现有 Profile/CI health 命令作为替代；若仍有明确负责人或独有输出，则保留并补文档，而不是强删。

## Migration Plan

1. 记录当前分支、脏文件和两个活动 OpenSpec 变更状态；保存受影响文件的基线 diff，并确认本变更不会覆盖其未提交内容。
2. 先增加导航、构建与退役指纹的正反验证，使清理前能够观察现状、清理后能够阻止回流。
3. 依次执行纯空壳、导航/UI、测试/Manifest、Gradle 依赖和质量脚本切片；每片运行 focused 验证并检查 `git diff --check`。
4. 收紧 legacy allowlist，更新屏幕地图、系统概览、依赖规则、CI 质量门禁和路线图中的长期事实。
5. 运行所有新增 fixture、受影响 Core/Feature/App 单元测试与 lint、`:app:assembleDebug`、`preflight_local.sh --full`，再执行显式非生产 `assembleRelease`/`bundleRelease` 及当次产物校验。
6. 在 API 36 模拟器验证开始服务直接进入 NFC、返回行为，以及定位、拍照上传、倒计时和应用更新 smoke；涉及厂商 NFC/定位的最终路径在可用真机上补证。

任一切片失败时只回滚该切片删除/依赖调整并保留新增回归测试，不整体恢复已验证切片；不得通过扩大 allowlist、恢复伪注册或放宽 production Release 守卫完成验收。
