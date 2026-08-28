# 路线图与开放问题

最后核对：2026-08-28

本文只记录仍然成立的后续工作。已完成的任务、逐次 CI 结果和历史方案通过 Git/PR/Issue 追溯，不在主文档中保留执行日志。

## 当前判断

- 护理端和销售端的主要用户链路均已实现。
- Debug 和显式验收构建可用。
- QLZ 已按“批准 AAR 必需、正式配置外部注入、厂商内部风险非阻断”实施项目侧生产化；整体生产发布仍受腾讯人脸、签名和缺少受控正式/真机证据等独立条件约束。
- 架构演进重点是缩小 `:app`、稳定平台生命周期和提高关键业务回归信心。
- 文档当前真相集已经收敛；后续应随实现更新，不再累积 design/plan/progress 副本。

## P0：恢复生产发布条件

Owner 涉及移动端、服务端和厂商，完成条件必须全部满足：

1. 在受控 CI/发布环境提供正式 QLZ key，保持当前批准 AAR 原字节打包并完成 minified 产物与真实 BLE 销售链路证据；不得把 key 或 Token 写入仓库/日志。
2. 保留 QLZ 1.3.0.2 内部 TLS finding、厂商责任和复核触发条件；该已接受外部风险不再作为 QLZ production blocker，也不得写成已修复。
3. 替换腾讯人脸 AAR，确保 ARM64 native library 满足 16 KB ELF/page alignment，并移除危险的全局 consumer ProGuard 规则。
4. 受控真机通过登录、身份核验、QLZ 蓝牙评估、报告、COS 上传和 Release shrink 回归。
5. `verify_vendor_sdk_release_readiness.sh`、生产配置、批准制品、Lint 和 production Release 的各自可控根因全部通过。
6. 重新评估 Jetifier；只有全部相关厂商包 AndroidX-only 后才可关闭。

验收 Release 只用于联调/验收，必须显式标记，不得作为生产包分发。

## 体检后续 OpenSpec change 注册表

下表只登记本次全项目体检确认的独立整改或补证切片。名称是建议的 OpenSpec change id，并不表示已经实施；每项需要单独提案、单独验收。已有路线图批次继续作为详细范围来源，不把无关修复并入同一个 change。

| 优先级 | 建议 change | 独立范围 | 最低验收证据 | 依赖关系 |
|---|---|---|---|---|
| P0 | `productionize-qlz-integration-with-vendor-aar` | 保留批准 QLZ 1.3.0.2 AAR，外部注入正式配置，按项目/厂商责任拆分门禁并保持销售语义 | 配置/制品/adapter fixture、minified APK/AAB 检查、真实 BLE/token/报告回归；厂商内部风险仍登记 | 正式 QLZ key、受控销售账号和设备；厂商新包不是当前前置条件 |
| P0 | `upgrade-tencent-face-sdk-for-16kb-and-r8` | 替换腾讯人脸 AAR，修复 ARM64 16 KB 对齐并删除全局 consumer R8 选项 | ELF 对齐、R8 analyzer、minified production 构建和真机身份核验均通过 | 厂商新包、受控身份核验环境 |
| P1 | `remove-jetifier-after-vendor-modernization` | 在所有相关厂商包 AndroidX-only 后关闭 Jetifier | 全依赖解析、Debug/Release 构建、Lint 与厂商真机旅程通过且无 support-library 引用 | 相关厂商均提供兼容输入；不要求为当前 QLZ 业务强行停用 AAR |
| P1 | `resolve-navigation-dependency-lint` | 单独处理 Navigation 2.9.8 的 `GradleDependency` | Lint 不再报告该坐标；route、payload、返回 key 与 back-stack 回归通过 | 无；不得顺带迁移 Navigation 3 |
| P1 | `resolve-camerax-dependency-lint` | 单独处理 CameraX 1.6.1 的 `GradleDependency` | Lint 消项；预览、拍照、ML 坐标和 lifecycle 真机回归通过 | 受控相机设备 |
| P1 | `resolve-profile-tooling-dependency-lint` | 单独处理 Baseline Profile/Benchmark RC 版本提示 | Lint 消项；profile 生成、打包、安装和 benchmark 任务通过 | 代表性性能设备用于最终收益判断 |
| P1 | `replace-or-isolate-legacy-protobuf-lite` | 追踪并替换、升级或隔离运行时 `protobuf-lite:3.0.1` | dependencyInsight 无旧坐标；兼容性测试和可信漏洞扫描均通过 | 若由闭源 SDK 强制引入，则依赖对应厂商升级 |
| P1 | `align-ci-gate-registry` | 让 registry、Android CI summary 和文档覆盖实际 15 个 ci-required 门禁 | 自动比较三者集合相等，门禁名称、Owner 与修复提示完整 | 无 |
| P1 | `pin-github-actions-to-full-sha` | 将外部 GitHub Action 引用固定到审核过的完整 commit SHA | 六个 workflow 与复用 action 中无 tag/branch 引用；自动守卫拒绝非完整 SHA | 需要建立 Dependabot/Renovate 更新流程 |
| P1 | `govern-all-github-workflows` | 将 CI Health 与 Actions cleanup 纳入统一 workflow 质量策略 | 六个 workflow 全部检查 timeout、permissions、retention、触发与失败上传；负向 fixture 能使守卫失败 | 可在 SHA 固定前后独立实施 |
| P1 | `bind-release-artifacts-to-ci-verified-commit` | 保证被发布的 version bump/profile commit 正是 CI 验证过的 commit | 发布目标 SHA 与成功 Android CI SHA 相同；不再只验证触发 SHA | 需要明确版本提交/发布编排策略 |
| P1 | `stabilize-adaptive-instrumentation-contracts` | 修正 3 个陈旧或环境耦合的自适应断言，不改生产 UI 行为 | app 当前 51 个 instrumentation 全通过；手机/平板及字体缩放 fixture 可重复 | 无 |
| P1 | `avoid-empty-library-instrumentation-runs` | 聚合任务只启动确有 `androidTest` 的模块 | 空 Library APK 不再因 runner 缺失失败；app 与 core:data 用例仍实际执行 | 无 |
| P1 | `run-affected-business-tests-in-ci` | 让 affected scope 的 `run_instrumentation`/smoke 输入真正驱动测试 | 受影响 fixture 触发对应 unit/smoke，故意失败测试能阻断 CI，未受影响改动不无谓扩张 | 先完成测试契约稳定化与空模块编排 |
| P1 | `replace-destructive-room-migration` | 为 v1/v2 到当前 schema 提供保留数据的显式迁移 | migration instrumentation 证明未同步状态、图片索引和受管文件关系保留；生产配置不再 destructive fallback | 需先定义历史数据兼容与文件清理契约 |
| P1 | `isolate-and-clear-account-scoped-persistence` | 为 Room、DataStore、Work、闹钟、通知和受管文件建立账号 owner 与统一清理 | 退出、3002、换号和进程重建测试证明旧账号数据/任务不可见且可回收 | 与 Room 迁移 change 协调 schema，但独立验收 |
| P1 | `prune-and-enforce-legacy-feature-allowlist` | 删除 18 个失效路径并阻止同名文件被重新引入 | 双向集合检查为零差异；删除后路径的负向 fixture 被守卫拒绝 | 无 |
| P1 | `verify-release-signing-identity` | 把受控生产证书身份和 Play/AAB 发布身份纳入发布验证 | APK/AAB 签名证书指纹匹配受控期望值，发布目标与 Play 侧证据可追溯 | 生产签名材料与 Play 只读证据 |
| P1 | `add-dependency-vulnerability-and-sbom-gates` | 为直接、传递和本地 AAR 生成 SBOM 并接入可信漏洞源 | CI 产出可追溯 SBOM；高严重度策略负向 fixture 阻断且 waiver 有 Owner/退出条件 | 需要选定漏洞源与私有制品元数据策略 |
| P1 | `validate-care-device-journey` | 补护理端 NFC/R65C、相机、人脸、定位、COS、倒计时与 OEM 后台证据 | 受控真机矩阵覆盖成功、拒权、失败重试、后台/任务移除、退出换号 | 受控订单、账号、硬件和服务端 |
| P1 | `validate-sales-qlz-device-journey` | 补销售登记、三张照片、QLZ BLE/token、表单与报告 WebView 证据 | 受控真机端到端及权限/断网/Token 恢复通过 | 当前 QLZ productionization、销售账号和硬件 |
| P1 | `establish-representative-performance-baseline` | 用等价预置状态测 TTID/TTFD、jank、CPU、内存、网络和电量 | 多次 Profile/None 分布、关键旅程 trace 与设备信息可复核，不用 Debug 模拟器数值作阈值 | 代表性真机和受控业务数据 |
| P1 | `integrate-vitals-health-evidence` | 将 crash、ANR、启动、渲染和设备分布纳入发布后健康判断 | 只读平台证据有时间窗、版本和设备分层，并与本地结论分开 | Play/Vitals/崩溃平台访问 |
| P1 | `prepare-adaptive-layouts-for-api-37` | 移除大屏竖屏兼容依赖并验证折叠、多窗口、低高度和状态恢复 | tablet/foldable emulator 与至少一台真机通过 CameraX/ML/提醒/核心页面矩阵 | targetSdk 37 升级前完成 |
| P2 | `minimize-android-ci-token-permissions` | 把 `actions: write` 从普通 detect/verify job 移除 | 权限静态测试通过；构建 job 只读、cleanup job 仍能按策略工作 | 无 |
| P2 | `align-release-trigger-contract` | 在“仅手动生产”与“支持 tag 生产”中选一套并统一 workflow、守卫与文档 | 每个声明 trigger 有成功/拒绝 fixture；不再声明必然被拒绝的 `v*` 路径 | 与发布 commit 绑定策略保持一致 |
| P2 | `minimize-vendor-merged-manifest-surface` | 移除或有证据地限定 QLZ 合并的 advertise 权限与 legacy storage 标志 | 四个变体 merged Manifest 最小化；BLE/文件真机回归通过 | 需要厂商支持边界和当前 AAR 真机证据，不得修改 AAR 本体 |
| P2 | `restrict-webview-navigation-origins` | 为表单/报告 WebView 建立 host、scheme、redirect 和外跳策略 | 允许域正常工作，非允许 host/redirect 被拒绝或外部打开；注入与返回行为测试通过 | 需要服务端域名清单 |
| P2 | `cover-untested-domain-and-countdown-contracts` | 先识别零测试模块中的真实逻辑，再补 domain/servicecountdown 高价值契约 | 关键状态/失败/重建测试可发现且执行；纯模型/UI 容器不为追求数量强行加测试 | 依赖契约清单，不以覆盖率数字代替风险判断 |
| P2 | `decompose-oversized-core-and-sales-types` | 分批拆分 6 个超 500 行文件及 `SalesViewModel` 职责 | 每个切片保持公开 API、状态机和导航契约，focused tests 与架构守卫通过 | 独立小切片，不与模块迁移合并 |
| P2 | `separate-baseline-and-startup-profile-journeys` | 实施下文批次 B，分离 Startup 与完整业务 Profile | `startup-prof.txt` 成为 baseline 的严格子集；等价场景 benchmark 与打包检查通过 | 测试前置状态稳定后实施 |
| P2 | `prune-deterministic-r8-rules` | 实施下文批次 C，只删 0-match/确定重复的项目规则 | R8 analyzer 对应项消失，minified acceptance 与反射/JNI/厂商 smoke 通过 | 不修改厂商 consumer rules |

`CI Health Monitor` 最近窗口越阈值和当前 Android CI 的 Lint 失败是上述 `LINT-DEPS` 与 CI 治理问题的在线表现，不另建重复整改项。模拟器 System UI 的单次 ANR 属环境证据，不创建应用修复 change；只有在应用侧可复现后才重新分类。

## P1：低风险优化批次

目标是在不改变用户可见行为、route contract、数据契约和厂商 SDK 接入方式的前提下，先提高回归可信度，再修正性能产物，最后清理确定无效的项目 R8 规则。三个批次必须独立实现、独立验证和独立提交；前一批稳定后才能开始下一批。

### 批次 A：测试与 CI 可信度

- 修复 `DashboardGridCompactModeTest` 的陈旧硬编码文案，改为从当前 string resource 生成期望值。
- 将 `TopHeaderAdaptationTest` 拆为断点纯逻辑测试和与设备宽度匹配的 UI 测试，避免在 compact 模拟器中伪造不可满足的 645dp 根布局。
- 聚合 instrumentation 只运行实际拥有 `androidTest` 的模块，避免空 Library 测试 APK 因 runner 缺失而在执行前失败。
- 让 Android CI 真正消费 `run_instrumentation` 和 `smoke_test_classes`，复用现有 instrumentation smoke 脚本；仅在 affected scope 要求时执行。

完成条件：

- `:app` 当前发现的 instrumentation 用例全部通过，新增的布局断点 JVM 用例通过，`:core:data` 迁移用例继续通过。
- 空 Library 模块不再启动无测试的 instrumentation APK。
- 普通 Android CI 的 build-only 基线保持不变；受影响范围要求 smoke 时才增加业务验证。
- 不修改任何生产 Composable 文案、布局断点或业务分支，只修复测试表达和执行编排。

### 批次 B：Baseline 与 Startup Profile 语义

- 将首次启动、已同意隐私协议的典型启动和登录后关键业务旅程拆成明确场景，共用稳定的 journey helper。
- `includeInStartupProfile=true` 只覆盖初始显示必需路径；滚动、导航和异步业务加载只进入 Baseline Profile。
- 每个场景断言准确页面和关键节点，不再以 package root、盲滑或 `pressBack` 作为旅程成功证据。
- 在真实业务内容可交互时报告 fully drawn，同时保留 TTID，并增加 TTFD 验证。
- 加强 `verify_baselineprofile_journeys.sh`，要求隐私/会话前置条件、目标页面断言和 Startup/Profile 场景边界，而不只检查任意手势与等待调用。

完成条件：

- `startup-prof.txt` 只包含初始显示相关路径，`baseline-prof.txt` 作为其包含关键业务旅程的超集，不再近似完全相同。
- Benchmark 的 Profile/None 使用同一预置状态和同一旅程；模拟器用于稳定性与依赖链诊断，最终收益在多核真实设备确认。
- 生成、安装和 minified acceptance Release 均能识别并使用打包后的 `baseline.prof` / `baseline.profm`。
- 不改变隐私协议、登录态、页面路由或业务数据，仅修正测试预置状态、旅程和性能标记。

### 批次 C：项目 R8 确定性清理

- 先删除当前 Release 全程序分析中匹配 0 items 的项目规则。
- 删除确定被更宽项目规则覆盖的重复项，包括 `com.autonavi.aps.amapapi.model.**`、`com.comm.*`、`com.falth.data.*` 和 `**$$serializer` 的重复 member 规则。
- `@Keep` methods 的异常覆盖结果不纳入自动清理，必须先人工确认实际匹配关系。
- 不修改 COS、高德、Bugly、QLZ、腾讯人脸等厂商 consumer rules，不拆包或重写 AAR。
- 不在本批次收窄仍有实际匹配的 package-wide 规则；这类工作需要单独的 SDK 业务回归方案。

完成条件：

- `analyzeReleaseR8Config` 中对应 unused/subsumed 项消失，Optimization、Shrinking 和 Obfuscation 分数不得下降。
- minified acceptance Release 构建通过，并覆盖登录、导航参数恢复、定位、身份核验、照片上传、倒计时、视频呼叫和应用更新 smoke。
- mapping、资源 shrinking、Baseline Profile 打包和 APK 签名/Manifest 检查保持正常。
- 任一反射、序列化、JNI 或厂商流程回归时，立即回滚当前单条规则，不用新增整包 `-keep` 掩盖问题。

### 首期统一门禁与排除范围

每个批次至少执行：

```bash
bash scripts/quality/preflight_local.sh --full
bash scripts/quality/verify_release_validation_entry.sh .
./gradlew --no-daemon :app:lintDebug :app:assembleDebug
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt
./gradlew --no-daemon :app:assembleRelease \
  -Prelease.production=false \
  -Prelease.acceptance=true
```

再按批次补充 focused unit test、instrumentation、Baseline Profile Benchmark 或 R8 analyzer。每个批次使用独立提交；发现行为差异时只回滚该批次，不跨批次追加兼容分支。

首期明确不包含：厂商 SDK 替换或二进制修补、厂商 consumer rules 收窄、Jetifier 关闭、Navigation 迁移、Compose UI 重构，以及 WorkManager、定位、Bugly、DataStore 等启动初始化时序调整。这些事项分别保留在 P0、既有架构路线或后续受控性能实验中。

## P1：`:app` 壳层收敛

建议按可独立验证的小切片推进：

1. `:feature:identification`
   - 已拥有默认人脸核验页面和大部分用例/状态。
   - 下一步评估迁移 `IdentificationScreen` 和 route-facing UI，保持结果 key 与补录兼容路径不变。
2. `:feature:login`、`:feature:home`
   - 迁移 route UI 前先固定 Home 子图 ViewModel owner、角色分流、隐私/协议和销售页返回行为。
3. `:feature:photoupload`、`:feature:servicecountdown`
   - UI 下沉时保持 app-owned 相机/Service/闹钟平台网关，不让 Feature 直接依赖 Activity 或实现类。
4. 销售能力
   - 建立独立 feature slice，拆分 oversized `SalesViewModel`，同时保持内部 `SalesNavigationState` 和应用级 Camera/WebView 返回行为。

所有迁移都受 legacy 新文件冻结、模块依赖白名单和 API visibility 门禁约束。

## P1：关键链路验证深度

优先增加高价值、少重复的验证：

- 隐私未同意、协议未勾选、会话失效和账号切换。
- 原生 NFC 与 R65C 外接读卡的开始/结束服务。
- 定位权限拒绝/恢复、前后台切换、退出登录、结束接口失败和任务移除。
- 人脸眨眼状态机、相机失败、服务端拒绝、会话失效和登记照缺失补录。
- 服务照片数量配置、私有 URL 解析、上传重试和受管文件回收。
- 倒计时、精确闹钟、通知权限、设备重启恢复和服务完成。
- 销售登记草稿、三张照片上限、QLZ 权限/Token 恢复、表单/报告 WebView。
- Room 迁移、WorkManager 重建和 app 更新下载恢复。

正常 Android CI 当前是 build-only 阻断策略；这些业务验证应在本地 `--full`、专项 workflow、发布验收或真实设备矩阵中明确承担，而不是被误认为已由普通 CI 覆盖。

## P1：Android API 37 与自适应

当前 targetSdk 36 通过 Activity 级兼容属性暂时保留 sw600dp+ 的竖屏限制。API 37 不再允许该 opt-out，因此升级前需要：

- 清点所有 app-owned 和 vendor Activity 的方向策略。
- 验证护理/销售页面在横屏、折叠、展开、多窗口和桌面窗口中的可达性与状态恢复。
- 验证 CameraX 预览、ML Kit 坐标、人脸取景、水印和全屏提醒方向。
- 将固定宽度内容改为有最大宽度的响应式布局，并保证低高度窗口可滚动。
- 在 tablet/foldable emulator 和至少一台真机上增加回归证据。

不要用新的兼容绕过代替适配。

## P2：导航与模块 API

- 统一 feature entry、route key 和 app navigation registry 的所有权；当前 registry 只含 login/home/identification 三项。
- 评估 Navigation 3，但必须先建立 route/payload/back-stack 等价测试。
- Navigation 3 迁移不与业务接口变化、模块大搬迁或 targetSdk 升级合并。
- 继续缩小公共 API，优先 `implementation` 和 `internal`，避免跨模块访问实现包。

## P2：工程与可观测性

- 完成低风险优化批次 B 后，定期生成 Baseline Profile，并确保 Startup/Profile 场景边界和关键旅程不退化。
- 构建耗时、质量快照和 CI 健康报告只输出到 `build/reports/` 或 CI artifact，不回写长期 Markdown。
- 保持 Gradle/AGP/Kotlin 升级可单独回滚，并运行 wrapper、workflow、Lint 和架构守卫。
- 第三方 SDK 升级需要记录 AAR 校验和、权限变化、Manifest merge、consumer rules 和 native ABI 结果。

## 当前不做

- 不因为模块目录名称不理想而一次性搬迁所有页面。
- 不通过关闭 Lint、增加全局 `-ignorewarnings`、放宽 exported component 或复用 debug 签名绕过生产发布问题。
- 不恢复相册选图、跨重启定位自动恢复或离线定位补传，除非产品明确改变现有规则。
- 不把内部功能验证入口升级为普通用户导航。

## 更新触发

完成任一条目后：

1. 删除或改写本文件中已不成立的 gap。
2. 同步产品概览、系统概览、页面地图、技术栈或质量门禁中的相关事实。
3. 长期架构取舍写 ADR；执行过程留在 PR/Issue。
