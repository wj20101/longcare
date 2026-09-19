## Context

动机见 [proposal.md](proposal.md)。核查快照为 2026-09-19、主分支 `1098bb6b`；版本/PR 状态是当时证据，不是实施时永远有效的承诺。已检查版本目录 46 个版本键，发现 10 个稳定更新候选，其中 Protobuf 暂缓，实际升级 9 个键。

- 主分支 [CI 35365247081](https://github.com/wj20101/longcare/actions/runs/35365247081) 在 `:assistant:lintAnalyzeDebug` 的依赖下载中遇到 Maven Central HTTP 429，涉及 Lint 的 Groovy/Kotlin/HttpClient 传递依赖；外层 configuration-cache 错误不等于已定位为缓存缺陷，instrumentation 未运行。
- 旧依赖 PR 的失败多为旧基线上的 `AndroidGradlePluginVersion` / `UnusedResources` 门禁，不证明新版库本身不兼容。#122 最新复核已无冲突，但 verify-build 的成功记录仍早于 H5 新基线且 instrumentation 跳过，不能沿用旧绿色结果。
- #115 有冲突且无有效检查，主分支仍缺少其 KVM、稳定并发分组和 CI 路径 smoke 触发增量；#117 基于 `07173086`，早于当前 Navigation 3/H5 变更。
- 当前 QLZ 集成文档与 `app/build.gradle.kts` 保留厂商生成代码对应 Protobuf 4.28.3。生产 Release 的厂商 SDK 阻塞继续保留。

## Goals / Non-Goals

用户授权逐项更新、验证并合入 PR，Compose BOM 与 Wrapper 使用独立 PR；Protobuf 在本变更内暂缓，随后独立验收。最终合入与覆盖边界见 tasks.md，不保留逐次 head/base 和重跑日志。

**Goals:** 每批变更可独立定位问题和回滚；升级后的双应用、导航、H5、持久化、图片管线和代码生成可验证；PR 的最新候选提交与验收证据对应。

**Non-Goals:** 不趁机升级 SDK/AGP/Gradle/JDK，不改变业务、数据库 schema、接口、账号或 APK 身份，不采用预发布版，不处理全部历史重构，不绕过生产门禁，不自动批准 Protobuf 升级。

## Decisions

### 1. 版本目标与 PR 逐项对应

以下 PR 均位于 `wj20101/longcare`；“处理”表示后续实施计划，不表示已更新或已合入。实施前再次核对最新 head/base、发布说明与仓库状态。若出现新的主/次版本或厂商兼容影响，先修订计划，不自动扩大升级范围。

| 依赖 / PR | 当前版本 → 本次目标 | 处理及关键验证 |
| --- | --- | --- |
| Coil [#120](https://github.com/wj20101/longcare/pull/120) | 3.6.0 → 3.6.3 | 原 PR 3.6.2 已落后，更新现有候选；图片加载/取消/缓存与验收 Release R8 |
| Okio [#127](https://github.com/wj20101/longcare/pull/127) | 3.18.1 → 3.18.2 | 核对实际解析版本、OkHttp 网络和文件读写回归 |
| Room [#121](https://github.com/wj20101/longcare/pull/121) | 2.8.4 → 2.8.5 | DAO、Flow、关闭/取消及现有迁移测试；不主动改 schema |
| KSP [#126](https://github.com/wj20101/longcare/pull/126) | 2.3.11 → 2.3.12 | Hilt/Room/Moshi 生成、Lint 和跨模块编译 |
| Kotlin [#122](https://github.com/wj20101/longcare/pull/122) | 2.4.10 → 2.4.20 | 实施复核已无冲突，仍须整合最新基线，验证编译器/Compose 插件一致与完整单测 |
| Compose BOM | 2026.08.00 → 2026.09.00 | 当前无 PR，授权后新建独立 PR；导航/H5/弹窗/输入法/重建回归 |
| Robolectric [#128](https://github.com/wj20101/longcare/pull/128) | 4.16.1 → 4.17 | 主应用与助手完整 JVM 单测，核对 JDK 21 和测试 API 配置 |
| Baseline Profile [#124](https://github.com/wj20101/longcare/pull/124) | 1.5.0-rc02 → 1.5.0 | 与 Benchmark 协同验证，分别保留清晰差异 |
| Benchmark [#123](https://github.com/wj20101/longcare/pull/123) | 1.5.0-rc02 → 1.5.0 | 稳定版工具链与 profile 生成/消费验证 |
| Protobuf Lite [#125](https://github.com/wj20101/longcare/pull/125) | 保留 4.28.3 | PR 目标 4.36.1，核查时最新为 4.36.2；两者均不直接合入。记录厂商约束，另行授权后才做 SDK 初始化/测量/序列化/上传/报告真实验证 |
| CI [#115](https://github.com/wj20101/longcare/pull/115) | 行为修复 | 基于最新主分支解决冲突，保留助手、现代 actions 和现有门禁；通过真实 CI smoke 后再决定合入 |
| 性能配置 [#117](https://github.com/wj20101/longcare/pull/117) | 旧生成结果 → 最终基线重生成 | 不直接合旧 profile；在其他变更稳定后更新现有 PR，记录生成基准提交和设备 |
| AGP [#119](https://github.com/wj20101/longcare/pull/119) | 已关闭，9.4.0 被 9.4.1 替代 | 无合入动作，不降级、不重新打开 |

保留 `kotlinx-datetime` 的 `0.8.0-0.6.x-compat` 兼容变体，不仅按数字大小替换。AGP 9.4.1、Navigation 3、WebKit 等非本表升级项保持当前版本。

### 追加确认：AGP / Gradle 联动核验与 Wrapper 同步

2026-09-19 用户要求同步处理 Gradle，随后确认保留稳定版组合并单独提交 Wrapper PR。Android CLI `version-lookup agp gradle` 与 Google Maven/Gradle 官方发布源交叉核对：AGP 稳定版仍为 9.4.1，Gradle 稳定版仍为 9.7.1；[AGP 9.4 兼容表](https://developer.android.com/build/releases/agp-9-4-0-release-notes) 要求 Gradle 至少 9.6.0，当前组合满足。不升级 AGP 9.5 alpha 或 Gradle 预览版，不改变 JDK/SDK。

现有提交 `f88e252c` 仅更新 Wrapper properties；本地 JAR SHA-256 为 `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`，对应旧 9.5/9.6 Wrapper。按[官方校验清单](https://gradle.org/release-checksums/)，9.7.1 Wrapper JAR 应为 `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d`。当前 all 分发包的 SHA-256 `92c1a136d76b5017732a66d2e0a648ebff00dd3687d8bff0d0047a1bd904fdf2` 已正确，保留校验及 URL 验证，不把旧 Wrapper 误称为被篡改。

Kotlin PR 完成后，使用 Gradle Wrapper 生成任务同步 JAR/Unix/Windows 启动脚本与 properties，审查实际差异并核对官方 JAR/分发包摘要及 `./gradlew --version`。通过 build-logic 测试、双应用 Debug/Lint、完整 preflight 和最新 CI 后按新增授权独立合入；不混入 Kotlin 版本 PR，也不手工替换为不明来源二进制。

### 2. 按依赖风险分批而非一次混合升级

顺序为：H5 UI 验收 → 主分支 CI 下载故障定位/恢复 → #115 → Coil/Okio/Room → KSP/Kotlin → Wrapper 同步 → Compose/Robolectric → Baseline Profile/Benchmark 稳定版 → #117 重生成 → 最终回归。

每个库保留独立可审查差异和针对性测试；KSP 先验证当前 Kotlin，再验证目标 Kotlin，方便定位编译链变化。Baseline/Benchmark 在合入前联动验证，避免最终混用 RC 与稳定版。各阶段串行运行本地 Gradle；一项失败停在该项定位，不用随后升级掩盖问题。

不选择“一键合入全部 Dependabot PR”：旧检查、冲突和厂商约束需要逐项处理。不把版本升级与无关代码清理混进同一补丁。

### 3. CI 先恢复可信反馈

HTTP 429 与 KVM 是不同问题。先检查失败依赖/仓库/重试结果，必要时调整现有缓存使用或降低下载并行度；只做有证据支持的最小修复。不强制覆盖 Lint 内部 Kotlin 版本，不无依据关闭配置缓存，不无限重试。

#115 保留可观察的三项变化：模拟器启动前检查 `/dev/kvm` 存在且当前用户可读写；PR 并发分组不含会变化的 head SHA，新提交取消同一 PR 的旧运行而不干扰其他 PR；workflow、smoke runner、受影响模块检测脚本变化触发 instrumentation。用脚本/守卫测试加真实 GitHub Actions 运行证明，不能只靠 YAML 可解析判定通过。

主分支最新运行若仍因外部服务失败，记录失败阶段与证据，不将未运行的 smoke 标为通过，不进入依赖批量合入。

### 4. 升级验证按风险补充

- Coil：已核对 [3.6.3 发布记录](https://github.com/coil-kt/coil/blob/main/CHANGELOG.md#363---september-18-2026)，包含 AGP 9.4.0+ 的 R8/Kotlin 模块元数据问题修复；因此选择 3.6.3 而非现有 PR 的 3.6.2，但不宣称当前项目已复现该问题。检查统一图片管线及实际使用的图片类型/加载取消；具备合法签名与显式 acceptance 配置时执行混淆验收构建，不绕过生产 guard。
- Room：[2.8.5 发布说明](https://developer.android.com/jetpack/androidx/releases/room#2.8.5) 涉及数据库关闭后的挂起查询/失效通知异常行为。覆盖 DAO Flow 生命周期、取消/关闭及已有迁移，核对 schema 无意外变化；需要 schema 变更则另行扩展方案。
- KSP：[2.3.12 发布说明](https://github.com/google/ksp/releases/tag/2.3.12) 与当前 AGP 基线配合核验，不启用新的实验性生成特性。
- Compose：两版官方 BOM POM 对比中 animation/foundation/runtime/ui 为 1.12.0 → 1.12.1，Material 3 不变；验收 UI 的旧内核/现代设备、安全间距、键盘、弹窗和 Navigation 3 状态恢复。版本映射以 [Google Maven BOM POM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom) 再确认。
- Robolectric：[4.17 发布说明](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17) 涉及 API 37、移除 AndroidVersions 和 shadow API 调整；运行完整 JVM 测试，不以 CI 的 build-only 结果替代。
- 最终运行完整 preflight、双应用 assembleDebug/lintDebug、既有 warning allowlist、助手隔离，以及受影响 instrumentation。测试结果记录实际提交/设备/内核；缺失测试保持待验收。

### 5. 远端操作与 profile 来源

分支更新、Compose/Wrapper 独立 PR 创建和逐项合入已获上述用户授权。优先复用现有 PR，不重复创建；冲突解决需比对当前主分支，不覆盖新增的 Navigation 3、助手或 H5 逻辑，不盲目 force-push。必须替代旧 PR 或改写历史时先说明具体目标和影响。

每个 PR 以最新 head/base 的差异、必需检查及专项回归决定合入；基准变化后重新验证，不能以早期绿色结果代替当前候选。若创建或继续处理 PR，保持任务附件与实际 PR 对应。#117 只接受最终代码/依赖生成的 profile，检查生成流程可重复、baseline/startup 文件与来源一致。

## Risks / Trade-offs

- 依赖仓库限流使 CI 反馈不完整 → 有界重试与根因区分，未运行项保留未验收。
- 编译工具链改变生成代码/测试行为 → 分步升级、保存明确提交、运行正式应用完整单测与代码生成检查。
- QLZ 与新版 Protobuf 兼容性未知 → 本计划保留 4.28.3，#125 暂缓；不得把未知描述为已证实不兼容。
- 图片库仅 Debug 通过无法覆盖 R8 → 增补合法验收 Release；缺少签名/配置时明确阻塞，不使用 debug 签名兜底。
- profile 随代码/依赖过时 → 最后重生成，不直接合入 #117 旧产物。
- 真实数据、凭据暴露或无意提交 → 优先 mock/已有测试数据，日志脱敏，不记录 H5 token；新增真实业务提交另行授权。

## Migration Plan

按第 2 节顺序推进，每步更新相关版本/CI 文档和任务勾选，失败停止后续合入。无需数据库迁移、账号清除或包名变更。

回滚使用对应小批次提交的定向 revert，重新生成受影响 profile 并执行相关验证；不 hard reset 主分支、不清空用户数据库。远端已合入内容的回滚仍按受保护分支/PR 流程处理。
