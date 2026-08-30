## Context

参见 [proposal.md](proposal.md) 的 Why。当前 `release` 同时启用 minify 与资源收缩，并按顺序合并 AGP 9.3.2 的 `proguard-android-optimize.txt`、`app/proguard-rules.pro` 和腾讯人脸规则文件。当前全程序分析覆盖 275,825 个存活类/字段/方法，Optimization、Shrinking、Obfuscation 基线为 65.7%、72.7%、65.8%；其中项目手工 `-keepnames class *` 规则单独匹配 26,601 个类，且包括大量与 Kotlinx Serialization 无关的类型。

依赖合并结果已经确认：Release 解析到的 AndroidX Annotation 1.10.0 自带完整 `@Keep` consumer rules，Kotlinx Serialization 1.11.0 自带 common 与 R8 专用规则，AGP 默认优化文件自带 JNI、enum、Parcelable 和 WebView JavaScript 接口规则。项目副本不是这些入口继续工作的唯一来源。

Android 官方当前建议使用 Configuration Analyzer 优先处理本地 unused、identical 和 subsumed 规则，并在修改后重新构建 Release、运行受影响流程；默认 AGP 规则不应修改，consumer rules 会与应用规则做加法合并。反射与 JNI 入口仍需要精确规则，不能仅凭源码直接引用情况判断删除。依据：[Use R8 Configuration Analyzer](https://developer.android.com/topic/performance/app-optimization/r8-configuration-analyzer)、[About keep rules](https://developer.android.com/topic/performance/app-optimization/keep-rules-overview)、[Optimization for library authors](https://developer.android.com/topic/performance/app-optimization/library-optimization)。

## Goals / Non-Goals

**Goals:**

- 只删除能由当前构建证据确定无增量价值的项目规则，使每个保留规则都能说明来源和运行时入口。
- 让同一 Release 输入下的三个 Analyzer 分数不下降，并解除错误 Serialization 规则对无关类名的全局限制。
- 建立快速、确定、无需构建 Release 的项目规则守卫，同时用 Release Analyzer 和运行回归承担动态行为验证。
- 让每一规则组可独立定位和回滚，不把一次回归演变为新的整包 keep。

**Non-Goals:**

- 不修改、拆包、过滤或重写任何厂商 AAR、JAR、Maven 制品及其 consumer rules。
- 不收窄仍有命中的 Bugly、高德、ML Kit、QLZ、腾讯人脸或 COS package-wide 规则，也不解决这些制品已有的生产发布问题。
- 不升级 AGP、R8、SDK、Kotlin、Navigation 或依赖版本，不改变混淆字典策略。
- 不把已延期的 ARM64 真机启动性能收益对照设为本 change 的完成条件。
- 不承诺固定的 APK 字节数或绝对 Analyzer 分数；依赖图变化时必须重新建立可比较基线。

## Decisions

### 1. 按证据来源分组清理，而不是重写整个 ProGuard 文件

项目规则按以下四类处理：

| 类别 | 本批处理 | 依据 |
|---|---|---|
| 默认/依赖已提供 | 删除项目 JNI、enum、`@Keep` 和 Kotlinx Serialization 副本，以及 Serialization 区域重复的 `Signature` 属性 | 默认优化文件和已解析 consumer rules 已提供必要规则 |
| 当前零命中 | 删除 `@Keep` field、Serialization annotation field、`com.evenmed.util.**`、`TreatmentBaseAct` 等项目项 | 当前 Release Analyzer 匹配 0 items；删除不改变本次程序图 |
| 明确被覆盖 | 删除窄 `**$$serializer` member、`com.autonavi.aps.amapapi.model.**`、`com.comm.*`、`com.falth.data.*`；`@Keep` method 的实际匹配已人工确认仅为同时受 JNI 与依赖规则保护的 `FaceDetectorV2Jni` | Analyzer 的 `subsumedBy` 关系与实际命中项 |
| 仍有命中或厂商所有 | 原样保留其行为，仅补充项目侧审查元数据 | 本 change 没有足够厂商回归证据进行收窄 |

不会一次性“整理格式并顺便删除”。清理 diff 保持按规则组可读，避免注释移动掩盖行为变化。

**未采用：** 直接删除全部第三方规则。consumer rules 并非所有厂商都正确封装，且现有 JNI/反射链路需要真实运行证据。

### 2. 将错误的 Kotlinx Serialization 手工块整体移除

`kotlinx.serialization.internal.*`、两个 `**$$serializer` 全成员规则、annotation field 规则和 `-keepnames class *` 均由早期手工配置引入。当前 Kotlinx 1.11.0 已用条件规则精确保护 `INSTANCE`、`Companion`、`serializer(...)` 与 descriptor；保留手工块会阻止无关库与应用类的优化。实现时删除整个手工 keep 块，仅保留通用属性区域已有的一份 `Signature`，并通过 Navigation 2 类型安全 route 的序列化、进程/状态恢复和 minified smoke 证明行为。

**未采用：** 把 `-keepnames class *` 改成另一个自定义 annotation/package 规则。新增规则仍会与官方规则叠加，扩大长期维护面，且没有当前缺陷证明官方规则不足。

### 3. 用“静态项目规则政策 + Release 动态证据”分担不同风险

新增一个无第三方 Python 依赖的项目规则 verifier，并用 fixture 测试覆盖。静态守卫只检查仓库可确定的事实：

- Release 继续使用 `proguard-android-optimize.txt`、项目规则文件和腾讯人脸规则文件。
- 项目规则不得含 `-dontshrink`、`-dontoptimize`、`-dontobfuscate` 或本次已确认的冗余规则指纹。
- `app/proguard-rules.pro` 中每个仍存活的 package-wide keep 必须出现在带 owner 与理由的项目 allowlist；新增宽规则需要显式评审，而不是静默进入。
- 项目自定义 Retrofit suspend 返回包装 `ApiResult<T>` 必须保留精确的 full-mode 泛型签名约束；API 36 冷启动曾证明只依赖 Retrofit 的通用 consumer rule 会把嵌套类型退化为原始 `ApiResult`，因此守卫同时锁定该单类规则及其缺失负例。
- verifier 只审查项目自有规则，不解析、复制或过滤依赖/厂商 consumer rules。

守卫接入现有 android-build-governance/local-fast 路径，并在 `quality_gate_registry.json` 中登记独立来源。fixture 至少覆盖成功、全局禁用、已删除规则回归、未登记 package-wide keep、缺失默认优化文件和缺失厂商规则文件。

Release 动态证据继续由 `:app:analyzeReleaseR8Config`、acceptance APK/AAB 和设备 smoke 提供。静态守卫不尝试自行模拟 R8 规则语义，也不将 HTML/PB 报告解析格式固化为新的长期 API。

**未采用：** 在 CI 中维护自制 protobuf 解码器并把 Analyzer HTML/PB 当稳定机器接口。当前官方输出格式可能随 R8 演进，自制解析器会把一次清理变成新的工具链耦合；本批只保存生成物为 CI artifact，并比较官方报告中的项目来源与分数。

### 4. 以前后同输入对照和当前产物闭环验收

实施前在不改依赖图的工作树上重新生成基线报告，确认 65.7% / 72.7% / 65.8% 与关键规则命中；实施后用相同 Gradle、Release 属性和依赖缓存重新生成。三个分数不得下降，且错误 `-keepnames` 不再由项目来源限制无关类。由于相同依赖规则可能在删除项目副本后以 consumer origin 继续显示，验收判断“项目来源消失且必要合并规则仍在”，而不是要求全局规则文本完全消失。

随后构建显式 acceptance APK/AAB，并只使用当次产物的 mapping、资源 shrinking、`baseline.prof` / `baseline.profm`、R8/DEX、签名和 Manifest 证据。运行 smoke 时重点覆盖：

- `@Keep` + Kotlinx Serialization：类型安全 route、参数恢复和验证入口。
- JNI/反射：定位、ML Kit/腾讯身份核验、QLZ 设备评估与视频呼叫。
- 其余发布链路：登录、照片上传、倒计时和应用更新。

### 5. production Release 状态与真机性能验收保持正交

acceptance 构建仍必须显式使用非生产标记。QLZ 弱 TLS/固定测试配置、腾讯人脸 16 KB 对齐和 consumer rule finding 继续由既有 release-required 门禁 fail-closed；本 change 不把 R8 分数改善解释为生产可发布。Startup/Baseline Profile 的 ARM64 两轮收益验证继续留在原 change 中为 `unverified`，不会因本计划完成而被勾选。

## Risks / Trade-offs

- **Risk：依赖升级后 consumer rules 发生变化。** → 本批固定当前依赖图完成前后对照；未来升级必须重新运行 Analyzer 与相关 smoke，静态守卫不假设依赖规则永久不变。
- **Risk：零命中厂商类在未来制品中重新出现。** → 当前删除只针对本次程序图；厂商制品升级时重新审计，其新反射入口应由厂商 consumer rule 或单独评审的精准项目规则覆盖。
- **Risk：混淆结果改变导致线上堆栈无法用旧 mapping 还原。** → 每个发布产物绑定并保存同次 mapping，禁止跨构建复用；验收只引用显式产物路径。
- **Risk：设备 smoke 无法覆盖所有厂商反射路径。** → 不收窄仍有命中的宽厂商规则；发现回归只恢复对应删除项，并为后续厂商优化另建 change。
- **Risk：新增 allowlist 带来维护成本。** → allowlist 只登记项目文件中的 package-wide keep，并要求 owner/理由；它不复制完整 consumer rules，收益是新增宽规则会在本地快速 fail-closed。
- **Risk：当前工作树与另一个 Profile change 存在质量脚本重叠。** → apply 前重新读取 git diff 和最新门禁文件，以增量方式接入，不覆盖或回退已有 Profile 改动。

## Migration Plan

1. 在当前依赖图上重新生成并核对 R8 Analyzer 基线，记录在终端/CI artifact，不提交本机报告或路径。
2. 先增加项目规则 verifier、package-wide allowlist 和 fixture，使 fixture 能表达预期政策；再将其接入现有 android-build-governance 与 registry。
3. 分组删除默认/依赖副本、零命中项和被覆盖项；每组后运行 verifier，并保持 diff 可单独还原。
4. 重新生成 Analyzer 报告；若任一分数下降或项目来源仍含错误规则，先查明合并来源，不继续扩大删除范围。
5. 运行本地完整门禁、Debug/Lint、minified acceptance APK/AAB、当次产物校验和关键设备 smoke。
6. 更新 CI 与路线图文档，将“项目 R8 确定性清理”从下一批次移为已完成事实，同时继续保留厂商与真机性能开放项。

回滚以规则组为最小单位：恢复导致回归的原始规则，重新生成 Analyzer 与 acceptance 产物并复测。不得用 `-dont*`、整包 keep、AAR 修改或门禁豁免代替回滚。
