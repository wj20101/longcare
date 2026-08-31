## Context

参见 [proposal.md](proposal.md) 的 Why。当前两个活跃 change 各剩一项设备证据：R8 清理需要 API 36 minified acceptance 业务 smoke，Profile change 需要同一多核 ARM64 真机的两轮 None/Profile TTID/TTFD。现有脚本已经能构建和校验 acceptance APK/AAB、运行 connected instrumentation、归一化单轮 AndroidX Benchmark JSON，却没有统一验证设备资格、跨阶段构建身份、完整手工/外设场景和两轮收益预算。

当前在线真机 MI CC 9e 是 API 28、`arm64-v8a`、8 核，只满足部分硬件字段；在线 API 37 设备是模拟器。Android 官方说明性能结论应在真机测量，模拟器结果不代表用户设备；`StartupTimingMetric.timeToFullDisplayMs` 在 API 29 及以下可能不可用，而 Android 14/API 34 起 Macrobenchmark 编译状态重置不再依赖每次重装。因此本批选择 API 36 ARM64 真机作为两个剩余验收的共同设备，不从 API 28 或模拟器报告推导通过。依据：[Benchmark Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile)、[Macrobenchmark overview](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)、[Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)。

## Goals / Non-Goals

**Goals:**

- 用一个显式入口完成设备资格、构建身份、Release smoke、两轮启动比较和独立结论聚合。
- 让自动步骤、人工真实业务步骤和外部阻断都具有机器可读状态，禁止部分通过被误写为完整通过。
- 复用现有 acceptance、artifact verifier、内部验证入口、connected test 和 Startup JSON 工具，不新增生产测试组件。
- 使每项证据可定位到唯一设备、构建、场景和原始报告，并对秘密与个人数据做最小化处理。

**Non-Goals:**

- 不修改或替换 QLZ、腾讯人脸及其他厂商 AAR/JAR，不收窄厂商 consumer rules，也不解决现有 production blocker。
- 不改变用户流程、网络/存储契约、targetSdk、Navigation、WebView、数据库、权限策略或启动初始化顺序。
- 不把模拟器、API 28 真机、普通 Debug instrumentation、R8 Analyzer 分数或 acceptance 构建成功当作真机业务/性能结论。
- 不把需要真实账号、订单、NFC/R65C、QLZ BLE 设备和服务端环境的成功路径替换为 mock 成功。

## Decisions

### 1. 使用 API 36 ARM64 真机作为两个验收域的共同最小设备

新增单一设备预检，要求显式 `ANDROID_SERIAL`、`ro.kernel.qemu != 1`、API 与 `target_platform_test_matrix.properties` 的 current target 一致、主 ABI 为 `arm64-v8a`、核心数达到配置下限，并采集系统 fingerprint、型号、电量和热状态。报告使用原始 serial 的 SHA-256 作为同机比较键，不写出 serial 本身。性能阶段在每轮前重新检查电量/热状态；环境不稳定时返回 `blocked`，不传入 `suppressErrors`。

选择 API 36 而非“任意 ARM64 真机”，是因为它同时满足 R8 change 的当前 target smoke 和 TTFD/现代 Macrobenchmark 条件。未来 target 提升时由平台矩阵驱动新的变更，不在本批接受 API 37 模拟器或旧 API 真机替代。

**替代方案：** 分别使用 API 36 模拟器做 Release smoke、API 28 真机做性能。前者不能覆盖 BLE/NFC/厂商真实路径，后者可能缺 TTFD，且两套设备会继续留下无法聚合的证据，因此不采用。

### 2. 一个执行清单承载两个独立 verdict

实现一个显式本地入口，例如 `scripts/quality/run_real_device_acceptance.sh`，以及机器配置和无第三方依赖的结果验证器。每次运行在 `build/reports/real-device-acceptance/<execution-id>/` 生成 `manifest.json`，至少包含：

- 匿名设备身份、API/ABI/核心/fingerprint、电量与热状态；
- Git SHA、工作树状态摘要、应用版本、variant，以及 acceptance APK/AAB、benchmark 目标 APK、测试 APK和 Profile 文本的 SHA-256；
- 每个 Release smoke 的前置条件、结果、时间、目标节点、日志文件哈希和阻断原因；
- 每轮 Startup 原始/归一化 JSON 的路径与哈希、场景/模式/样本计数和中位数；
- `r8RuntimeAcceptance` 与 `startupProfileBenefit` 两个相互独立的 verdict；
- production readiness 仍由现有门禁提供的独立 fail-closed 状态。

聚合器不会输出模糊的单一绿色状态。R8 smoke 全部通过时可以单独关闭 R8 原 change 的最后任务；性能预算满足时才可单独关闭 Profile 原 change 的最后任务。任一 verdict 未通过时，本 change 相应真实设备任务也保持未完成。

**替代方案：** 只维护 Markdown 勾选表。它无法验证设备、构建与报告是否同源，也容易包含账号或本机信息，因此不采用。

### 3. acceptance 与 benchmark 使用不同产物但共享构建输入身份

R8 运行回归必须安装当次 minified acceptance APK，并以同次 AAB、mapping、资源收缩、ART Profile、签名和 Manifest verifier 作为前置条件。Macrobenchmark 继续使用非 debug、可 profile 的 `benchmarkRelease` 目标 APK和 `:baselineprofile` 测试 APK。二者不是同一二进制，清单分别保存 SHA-256，但要求 Git SHA、应用版本、Profile 文本和受管构建配置一致，防止把旧 acceptance 包与新 benchmark 报告拼接。

入口先构建再解析 Gradle 实际产物路径，禁止依赖固定文件名或目录中“最近一个”文件。每个阶段开始前重新计算校验值，阶段结束时再验证一次，发现变化即作废当前执行。

**替代方案：** 让 Macrobenchmark 直接测 acceptance APK。现有性能专用状态控制器只存在于受保护的 performance variant，强行合并会破坏 production 隔离或改变测量契约，因此不采用。

### 4. Release smoke 使用场景账本与日志双证据

新增机器可读场景目录，固定十类场景及各自前置条件、准确目标和允许的执行方式。可自动化的安装、启动、日志清理、进程监视、基础页面节点和状态恢复通过 ADB/现有 instrumentation 完成；需要账号、订单、相机、定位、NFC/R65C、腾讯 UI、QLZ BLE 或视频能力的步骤通过受控真机和现有内部验证入口执行，操作者只提交场景 id 与 `passed`/`failed`/`blocked`，不能自由写“总体通过”。

每个场景单独截取目标包日志并扫描 `ClassNotFoundException`、`NoSuchMethodException`、`NoSuchMethodError`、`UnsatisfiedLinkError`、序列化恢复错误、FATAL EXCEPTION、ANR 和进程死亡。UI 到达目标但出现禁止签名仍为失败。图片、Token、手机号、身份证字段、完整 URL query 和原始设备序列号不进入报告；外部条件仅以类型化缺失项记录。

**替代方案：** 只跑 Debug connected tests。Debug 不经过相同 R8/minify 图，也无法证明厂商反射/JNI 在 acceptance 包中存活，因此仅作为辅助，不作为本项结论。

### 5. 以“场景 × 指标”比较两轮中位数

复用现有 AndroidX JSON normalizer 和单轮结构 verifier，再新增双轮 comparator。runner 对四个 Startup 场景分别以同一 helper 执行 `None` 与 `Partial(BaselineProfileMode.Require)`、`StartupMode.COLD`、10 次迭代；两轮之间不更改应用、Profile、设备或环境配置。原始 JSON在复制后立即计算 SHA-256，再生成 `deviceType=physical` 的归一化报告。

comparator 把每个“场景 × TTID/TTFD”视为一个比较项：每轮 Profile 中位数相对 None 的退化不得超过 `startup_profile_quality.json` 中对应的 5% 预算，且至少一个比较项在两轮都严格改善。任一缺失、跨设备/构建、Profile 状态非 `required-applied`、回归超预算或无持续改善都返回 `unverified`。模拟器报告可以继续通过单轮结构验证，但双轮真机 comparator 必须拒绝 `deviceType != physical`。

**替代方案：** 合并 20 个样本计算一个平均值。它会隐藏单轮热状态或顺序偏差，并与既定“两轮均满足”契约不符，因此不采用。

### 6. 外部依赖显式阻断，不持久化秘密

机器配置只登记前置条件类别和非秘密 fixture 标识，不保存账号、验证码、Token、身份证、客户资料或厂商 secret。凭据由执行环境或操作者在运行时提供；报告只记录 `care-account`、`active-order`、`nfc-card`、`qlz-ble-device` 等是否可用。缺失条件生成可恢复 `blocked`，不会自动切换 Debug Mock 或放宽 Release/网络安全设置。

QLZ 真实评估仍依赖服务端一次性 Token 与 BLE 设备；腾讯、NFC、相机、定位等使用当前正式入口。现有 production vendor readiness 在最后单独执行并按已知原因失败，本入口不得捕获或改写其退出码为成功。

### 7. 真机入口保持显式执行，不加入普通 PR 阻断

配置、schema、聚合器和负向 fixture 接入 `local-fast`/治理测试，确保规则回退快速失败；真正的 acceptance 构建、外设 smoke 和 160 次冷启动只作为显式本地/受控 runner 入口，不加入每次普通 Android CI。报告进入 `build/` 或短期 CI artifact，Git 守卫继续拒绝跟踪设备报告、本机路径和凭据。

## Risks / Trade-offs

- [真实外设和账号无法随时获得] → 每项以类型化 `blocked` 保留，不伪造成功；准备清单在安装前输出，减少执行到中途才发现缺项。
- [长时间 smoke 后设备升温影响性能] → Release smoke 与性能分阶段；性能开始前重新验证热状态并按配置冷却，两个 verdict 不要求同一连续时段完成，但必须保持同设备和构建身份。
- [OEM 的电量/热状态输出差异] → 解析失败视为不完整资格并给出原始字段诊断；fixture 覆盖 AOSP 与至少一个 OEM 格式，不以未知值默认通过。
- [人工步骤误记或跳过] → 场景 id、前置条件和目标节点由配置固定，聚合器拒绝未知、重复、缺失或自由文本状态；日志异常可以推翻人工 `passed`。
- [日志含用户或服务端数据] → 只保留目标包时间窗和禁止签名上下文，写盘前脱敏；原始完整 logcat 不进入可上传 artifact。
- [R8 与性能产物来自不同 variant] → 分别记录产物哈希并绑定共同构建输入，禁止声称二进制相同；variant 专属行为仍由既有 production 隔离守卫证明。
- [新入口被误解为生产 readiness] → manifest 中保留独立 production verdict，最终仍运行现有厂商/配置门禁并原样 fail-closed。

## Migration Plan

1. 固化设备、场景、外部条件、禁止异常、报告 schema 和双 verdict 配置，并用合成正反 fixture 建立预期失败语义。
2. 实现设备预检、匿名身份和构建/产物清单，接入现有 acceptance artifact verifier；先在 API 28 真机与模拟器上验证它们会因正确原因被拒绝。
3. 实现 Release smoke 场景账本、受控结果记录、目标包日志扫描与脱敏；用合成日志和不完整场景验证 fail-closed。
4. 实现 connected benchmark runner 与双轮 comparator，复用现有归一化/结构 verifier；用模拟器报告和合成物验证模拟器、跨设备、跨构建、缺 TTFD、超预算和无持续改善均被拒绝。
5. 在具备全部账号、订单与外设的 API 36 ARM64 真机上构建一次明确的 acceptance/benchmark 输入，先完成全部 Release smoke，再在稳定设备状态下完成两轮基准。
6. 分别根据 verdict 复核两个原 change 的最后任务；未通过项保持原状态，全部通过后运行完整质量门禁、严格 OpenSpec 验证并更新长期文档的开放状态。

回滚只删除新增验收脚本、配置、fixture 和文档说明，不改应用二进制、用户数据或服务端契约。若实际设备执行失败，保留实现与失败报告用于诊断，不能通过回滚守卫、降低预算、切换模拟器或补写 `passed` 解决。
