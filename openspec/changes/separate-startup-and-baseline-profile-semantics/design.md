## Context

参见 [proposal.md](proposal.md) 的动机和 [startup-performance-confidence spec](specs/startup-performance-confidence/spec.md) 的行为契约。当前工程已经正确应用 Baseline Profile Gradle 插件，并能在现有 Release APK/AAB 中找到 `baseline.prof`、`baseline.profm`；当前 Release AAB 的 `r8.json` 也声明 DEX layout/profile-guided optimization 已启用且有一个 `startup: true` 的 DEX。因此本变更不是重新搭建性能模块，而是修正“生成了什么、如何证明正确”的语义。

当前实现的关键限制是：

- `BaselineProfileGenerator.generate()` 只有一个 `includeInStartupProfile=true` 收集块，启动后继续盲滑两次并返回；两个已提交文本 Profile 都是 16,131 行且 SHA-256 完全相同。
- `verify_baselineprofile_journeys.sh` 只要求 `Until.hasObject`、`device.swipe` 和 `device.pressBack` 的源码痕迹，当前错误旅程也会通过。
- `StartupBenchmarks` 只确认 package root 出现；`None` 和 `Partial(Require)` 虽共用函数，但没有场景状态契约、准确页面断言或 TTFD 缺失检查。
- `MainApplication` 在 `onCreate()` 内执行用户存储冷切换、日志、QLZ 窗口兼容、定位生命周期观察，并在隐私同意后初始化设备标识、Bugly 和更新 Worker。本批次不改变这些时序，避免把 Profile 语义修正与真实启动逻辑调优混在一起。
- Macrobenchmark 与目标应用运行在不同进程，不能依赖 Espresso 或直接操作目标应用内存。登录态又采用加密 session envelope、复合用户 namespace 和 session epoch，不能通过 shell 直接拼文件绕过正式存储路径。

已通过 Android CLI 核对以下官方资料：

- [Baseline Profile 与 Startup Profile 的区别](https://developer.android.com/topic/performance/baselineprofiles/difference-baseline-startup)：Baseline 应是 Startup 的业务超集，`includeInStartupProfile=true` 只用于初始显示必需场景。
- [创建 Startup Profile](https://developer.android.com/topic/performance/startupprofiles/dex-layout-optimizations)：应限制 Startup 路径并检查其是否进入首个 DEX。
- [Macrobenchmark 启动指标](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)与[启动时间](https://developer.android.com/topic/performance/vitals/launch-time)：TTID 由系统产生，TTFD 必须由应用在真正可交互时报告。
- [比较 Baseline Profile 收益](https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile)：`None` 与 `Partial(BaselineProfileMode.Require)` 应在同一旅程上比较，真实收益使用物理设备。
- [确认 Startup Profile](https://developer.android.com/topic/performance/baselineprofiles/confirm-startup-profiles)：AGP 8.8+ 可通过 AAB 的 `r8.json` 和 DEX checksum 验证布局优化。
- [Compose 与 UiAutomator 互操作](https://developer.android.com/develop/ui/compose/testing/interoperability#uiautomator)：启用 `testTagsAsResourceId` 后使用 `By.res(tag)`；不得传 package 参数，因为该重载会生成与 Compose test tag 不同的 `$package:id/$id`。

## Goals / Non-Goals

**Goals:**

- 用同一份场景目录和 helper 驱动 Profile 生成与 Macrobenchmark，覆盖隐私首启、未登录启动、护理 Home、销售 Home，以及两个角色各一条 Baseline-only 关键导航。
- 通过性能变体专用状态控制器使用正式隐私、会话和用户存储 API 准备状态；准备过程发生在采集/测量之外，既不污染 Profile，也不绕过加密与 namespace 契约。
- 为隐私、会话解析、登录和两类 Home 定义互斥的 fully-drawn 条件，并保证 TTFD 不等待非首屏网络、更新 Worker 或后续滚动。
- 让源码守卫、生成文件检查、APK/AAB 内容检查、R8/DEX 检查和真机 benchmark 形成逐层证据，而不是用任意文件存在替代有效性。

**Non-Goals:**

- 不优化或重排 `MainApplication`、Hilt、WorkManager、定位、Bugly、QLZ、用户存储冷切换等启动工作；发现耗时只能记录为后续独立 change 的证据。
- 不升级 Baseline Profile/Benchmark 依赖，不修改 Navigation 2、targetSdk 36、数据库、WebView 或任何厂商 AAR/consumer rules。
- 不为模拟器或跨设备耗时设置绝对 SLA，也不把本批次扩张为所有业务页面的性能基准。
- 不新增可进入 production Release 的测试 API、测试凭据、导出组件、mock 资源或明文用户 session。

## Decisions

### 1. 使用显式场景目录，分别收集 Startup 与 Baseline-only 旅程

在 `:baselineprofile` 建立单一 `ProfileScenario` 目录，每个场景声明：前置状态、预期根页面、准确语义节点、是否属于 Startup、可选的 Baseline-only 动作。初始集合为：

| 场景 | Profile 分类 | 成功节点/动作 |
|---|---|---|
| 首次启动隐私协议 | Startup | 协议标题、同意与不同意动作均可交互 |
| 已同意隐私且未登录 | Startup | 登录品牌、输入区和提交动作均可交互 |
| 护理身份 Home 冷启动 | Startup | 护理 Home 根、用户头部与关键入口卡片可交互 |
| 销售身份 Home 冷启动 | Startup | 销售 Home 根与客户/评估入口可交互 |
| 护理服务记录 | Baseline-only | 从 Home 准确进入服务记录根页面并返回 Home |
| 销售客户列表 | Baseline-only | 从 Sales Home 准确进入客户列表根页面并返回 Sales Home |

生成器使用多个命名测试/收集块：四个启动收集块设置 `includeInStartupProfile=true` 且只调用启动与首屏断言；两个业务收集块设置为 `false` 并执行精确点击、目标断言和返回断言。Gradle 插件继续负责合并最终规则。

场景 helper 按官方互操作契约通过 `By.res(tag)` 查找节点。应用根为 UiAutomator 暴露 Compose test tag，缺失的隐私、Home 与目标页面根节点补稳定 tag；不使用带 package 参数的 `By.res` 重载、本地化文案、坐标盲滑、固定 sleep 或 package root 作为成功条件。

**替代方案：** 保留一个大收集块，再按文本规则过滤 Startup。该方案无法可靠知道某条运行规则来自哪个交互，并继续让滚动/导航污染首个 DEX，因此不采用。

### 2. 用仅存在于性能变体的签名保护状态控制器准备隐私与会话

为 `nonMinifiedRelease` 和 `benchmarkRelease` 绑定一个共享的 performance-only source root；其中提供 `ProfileScenarioSetupActivity` 和专用 Manifest。该组件不进入 `debug` 或正式 `release`，并同时满足：

- 只接受显式 scenario id；Manifest 使用仅在性能变体声明的 signature permission，`:baselineprofile` 测试 APK请求同一权限。
- 每次场景测试先清理目标包测试数据；首次启动场景不再写入状态，其余场景在 `rule.collect` 或 `measureRepeated` 之前调用控制器。
- 未登录场景只经 `PrivacyConsentManager` 持久化同意并经 `UserSessionRepository` 确认登出；护理/销售场景使用两个固定的纯虚构 `SessionLoginPayload`，经 `UserSessionRepository.login()` 完成加密 envelope、namespace、epoch 和 storage Ready 顺序。
- 控制器只在 repository suspend 调用完成、公开 session 状态与期望一致后显示可观测完成节点并结束；准备后强制停止目标进程，再开始冷启动采集/测量。
- 场景准备在 Profile 收集块之外执行，避免把控制器、状态写入或测试身份代码录入 Baseline/Startup Profile。

Profile 专用 Manifest、类和虚构 token 必须由 source-set 守卫以及最终 Release Manifest/DEX/assets 检查证明不存在。当前关键旅程选择可以在空/失败业务数据下稳定到达的根页面，不为 benchmark 引入真实网络、生产账号或直接数据库写入；异步 rehydration 可以按既有逻辑运行，但不作为首屏成功与 TTFD 的依赖。

**替代方案：** 直接写 SharedPreferences、DataStore、session 文件或 Room。该方案会绕过 AES-GCM envelope、冷切换 marker、复合用户 namespace 和 lease/epoch，生成的状态与生产路径不等价，因此不采用。使用真实短信登录同样因外部网络和凭据不可重复而不采用。向 `MainActivity` 增加生产 intent extra 也会扩大外部攻击面，不采用。

### 3. 由互斥根页面报告 fully drawn，不让中间 Splash 抢先完成

使用现有 `androidx.activity` Compose fully-drawn API，不新增依赖：

- `ResolvingSession` 的 `SplashScreen` 持有未满足的 drawn 条件；离开该 composition 后自动释放，不把阻断式进度层当成完成。
- 隐私协议在标题、正文与两个动作完成 composition 后报告；它不等待同意后 SDK。
- Login 根在品牌、输入区和提交动作完成首帧后报告。
- Home 根只在用户身份已解析且对应护理/销售根内容完成首帧后报告；网络订单、更新检查、图片、后续列表滚动和非关键后台恢复不延迟信号。

每个启动时刻只有当前根页面注册完成条件，避免多个互不相关 reporter 永久阻塞。Compose focused tests 验证状态到完成条件的纯映射，Macrobenchmark JSON 再验证四个启动场景均实际产出 `timeToFullDisplayMs`。

**替代方案：** 在 `MainActivity.setContent()` 后立即调用 `reportFullyDrawn()`。这只会接近 TTID，无法区分 session Splash、登录和 Home，因此不采用。等待所有网络 rehydration 完成则会把非首屏 I/O 混入 TTFD并造成不可控波动，也不采用。

### 4. Profile/None 必须委托同一 benchmark 函数和同一场景 helper

每个受支持启动场景提供 `None` 与 `Partial(BaselineProfileMode.Require)` 两个测试入口，但二者只能把 compilation mode 传给同一个 `benchmark(scenario, mode)`；该函数固定使用 `StartupMode.COLD`、10 次迭代、相同准备状态、相同启动 intent、相同超时与相同目标断言。`Require` 缺 Profile 时沿用库的 fail-closed 行为。

Benchmark 机器可读 JSON 由 focused verifier 检查：每个模式必须有 TTID 与 TTFD，场景/迭代元数据必须对称。API 33 GMD 只运行生成与报告格式/稳定性检查；性能收益验收在同一台多核 ARM64 真机上连续执行至少两轮完整模式对比，比较每轮 10 次迭代的中位数。实施时先用未修改基线采集噪声范围并把判定预算固化到机器可读质量配置；验收要求两轮均不超过该退化预算，且至少一个启动指标在两轮中呈一致改善，否则状态保持 `unverified`，不得声称收益完成。

**替代方案：** 在 GitHub 模拟器上用绝对毫秒阈值阻断。共享宿主、虚拟化和温度/负载差异会产生错误结论，因此不采用。

### 5. 将 Profile 验证拆成源码语义、文本规则、发布包和设备四层

1. `verify_baselineprofile_journeys.sh` 验证场景目录、四个 Startup/两个 Baseline-only 分类、准确节点、共享 benchmark 委托、fully-drawn 接线及 performance source-set 隔离；新增 `test_baselineprofile_journeys.sh` 以正向和缺状态、盲手势、分类错误、模式分叉、生产泄漏负向 fixture 自验证。
2. 生成后 verifier 规范化空行/注释并按集合比较两个文本文件：文件非空、Startup 是 Baseline 子集、差集非空；同时检查场景输出均被 Gradle 任务收集。
3. minified acceptance Release 构建后，artifact verifier 接受显式 APK/AAB 路径，检查当前产物内非空 `baseline.prof` / `baseline.profm`；读取 AAB 的 `r8.json`，要求优化开关为 true、有 `startup: true` DEX，并把其中 checksum 与实际提取 DEX 的 SHA-256 对齐。它还检查生产 Manifest/DEX/assets 不含 Profile 状态控制器、permission、虚构身份或 fixture 标记。
4. 设备层用 `Partial(Require)` 的安装/编译和真机 TTID/TTFD 报告证明 Profile 可实际使用；报告只进入 `build/reports/` 或 CI artifact。

Baseline Profile workflow 在 GMD 生成后运行前两层并仅为真实规则差异创建 PR。Android CI 继续运行快速源码/fixture 守卫。Acceptance Release 在构建 APK/AAB 后运行 artifact verifier；现有 production vendor gate 的先后与失败语义不变。

**替代方案：** 继续在 `app/src` 中统计任意 Profile 文件数量。旧文件也能满足该检查，无法证明本次构建消费了它，因此不采用。

### 6. 不在本变更升级依赖或调整 R8 规则

当前 Baseline Profile/Benchmark `1.5.0-rc02` 已由 `android-build-baseline` 的精确预览豁免治理，AGP 9.3.2 已能输出所需 `r8.json`。本变更直接使用现有 API 和产物，不夹带依赖升级、`maxAgpVersion` 处理或项目 R8 规则清理。若稳定 1.5.x 可用，按既有依赖治理另开独立 change。

## Risks / Trade-offs

- [性能测试身份与真实账号有差异] → 使用正式 `UserSessionRepository`、加密 envelope 和 namespace 生命周期，只虚构不可外传字段；关键旅程选择不依赖服务端个性化数据，并在真实验收账号上做一次不计时 smoke。
- [performance-only 导出组件泄漏] → 独立 source root、signature permission、source-set 负向 fixture和最终 Release Manifest/DEX/assets 三重检查；任一泄漏立即阻断。
- [Startup 路径仍超出首个 DEX] → 严格缩小 Startup 场景，检查 `r8.json`/DEX checksum；若首个 DEX 仍溢出，先减少非必需 Startup 场景或另开启动代码瘦身 change，不把业务旅程重新塞入 Startup。
- [TTFD 过早或永不报告] → 中间 Splash 明确未完成、根页面互斥报告、Compose 状态测试与 Macrobenchmark TTFD 必填共同约束；超时输出场景与缺失节点。
- [状态准备使页缓存变热] → 状态控制在两种 compilation mode 中完全一致，准备后强制停止进程；收益以相对中位数和两轮真机结果判断，不以单次绝对值判断。
- [GMD/真机结果差异] → GMD 只承担生成与稳定性，真机承担收益；报告中强制记录设备、API、ABI、构建 SHA 和模式，禁止跨设备直接比较。
- [CI 时长增加] → local-fast 只运行 shell fixture；75 分钟 Profile 生成保持手动/定时 workflow；真机 benchmark 作为显式验收，不加入每次普通 PR。

## Migration Plan

1. 先建立新的场景目录、稳定页面 tag、performance-only 状态控制器与正反 fixture，并用 production 变体检查证明测试能力没有泄漏。
2. 接入互斥 fully-drawn 条件，运行 Compose/focused instrumentation，确认隐私、未登录、护理 Home、销售 Home 和 session resolving 的报告时机。
3. 拆分四个 Startup 与两个 Baseline-only 生成场景，重写 benchmark 为同函数双模式，生成新的文本 Profile 并验证严格子集关系。
4. 增加 APK/AAB/R8/DEX verifier，接入 Baseline Profile workflow 和 acceptance Release；先构建后验证显式产物路径。
5. 在 API 33 GMD 验证生成稳定性，在多核 ARM64 真机执行两轮 Profile/None 冷启动对比；满足判定预算后更新长期文档并从路线图移除本批次。

回滚时原子回退场景代码、fully-drawn 接线、守卫/workflow 和生成 Profile；本变更没有生产数据迁移或服务端契约，回滚不需要兼容分支。若只是真机收益未验证，则保持变更未完成并保留报告用于调整场景，不放宽 Profile 要求或发布门禁。
