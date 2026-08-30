# 路线图与开放问题

最后核对：2026-08-30

本文只记录仍然成立的后续工作。已完成的任务、逐次 CI 结果和历史方案通过 Git/PR/Issue 追溯，不在主文档中保留执行日志。

## 当前判断

- 护理端和销售端的主要用户链路均已实现。
- Debug 和显式验收构建可用。
- 生产发布被已知且可验证的厂商 SDK/配置问题主动阻断。
- 架构演进重点是缩小 `:app`、稳定平台生命周期和提高关键业务回归信心。
- `app/features` 冻结边界当前是 218 个 Kotlin 文件的精确快照；architecture rule-10 已执行实际文件、allowlist 与本路线图计数的一致性，后续只允许受控收缩，不允许静默扩张或积累陈旧条目。
- 文档当前真相集已经收敛；后续应随实现更新，不再累积 design/plan/progress 副本。
- 正式 `targetSdk` 保持 36；API 37 是 Android 17 Beta 候选且机器政策为 blocked，不因 `compileSdk 37` 或普通构建成功而自动提升。

## P0：恢复生产发布条件

Owner 涉及移动端、服务端和厂商，完成条件必须全部满足：

1. 服务端下发 QLZ 初始化所需配置，移除 Android 中固定测试 key 和 `QLZ_TEST_MODE=true`。
2. 替换 QLZ 1.3.0.2，确保可达网络链路不再触发弱 TLS trust manager finding。
3. 替换腾讯人脸 AAR，确保 ARM64 native library 满足 16 KB ELF/page alignment，并移除危险的全局 consumer ProGuard 规则。
4. 新厂商包通过真实设备的登录、身份核验、QLZ 蓝牙评估、报告、COS 上传和 Release shrink 回归。
5. `verify_vendor_sdk_release_readiness.sh`、生产配置门禁、Lint 和 production Release 全部通过。
6. 重新评估 Jetifier；只有全部相关厂商包 AndroidX-only 后才可关闭。

验收 Release 只用于联调/验收，必须显式标记，不得作为生产包分发。

## P1：后续低风险优化批次

剩余低风险工作是在不改变用户可见行为、route contract、数据契约和厂商 SDK 接入方式的前提下，先修正性能产物，再清理确定无效的项目 R8 规则。两个后续批次必须独立实现、独立验证和独立提交；Instrumentation 的当前所有权与执行入口以[CI 与质量门禁](ci-quality-gates.md)为准，不再作为路线图执行项重复维护。

### 下一独立批次：Baseline 与 Startup Profile 语义

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

### 后续独立批次：项目 R8 确定性清理

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

### 后续批次统一门禁与排除范围

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

上述低风险优化批次明确不包含：厂商 SDK 替换或二进制修补、厂商 consumer rules 收窄、Jetifier 关闭、Navigation 迁移、其他 Compose UI 重构，以及 WorkManager、定位、Bugly、DataStore 等启动初始化时序调整。身份与登录页面所有权迁移均由独立 OpenSpec change 完成，不改变这些批次的范围。

## P1：`:app` 壳层收敛

`:feature:identification` 已完成主页面下沉；`:feature:login` 也已完成页面、独占资源、ViewModel tests、协议链接选择和五动作面板下沉，并以唯一公开 screen/action 合约隔离 app-owned Navigation 2、WebView 与验证 Activity。两者是后续切片的边界参考。剩余工作按可独立验证的小切片推进：

1. `:feature:home`
   - 认证根协调、隐私 gate、Home 子图 ViewModel owner、角色分流和销售页返回/恢复契约已固定；下一切片可迁移 Home route UI，但必须保持入口导航 focused suite 全绿，不把 app-owned NavHost/route 实现暴露为 feature API。
2. `:feature:photoupload`、`:feature:servicecountdown`
   - UI 下沉时保持 app-owned 相机/Service/闹钟平台网关，不让 Feature 直接依赖 Activity 或实现类。
3. 销售能力
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

正常 Android CI 仍以 compile/lint/assemble 为基础，只在 affected detector 要求时追加 API 36 blocking smoke；login feature 变更会执行 feature compile/unit/lint，并让 app/feature UI tests 进入各自 test APK。CI 仍不自动运行全部项目单元测试、完整用户旅程或厂商真机链路，其余验证由本地 `--full`、专项 workflow、发布验收或真实设备矩阵承担。

## P1：Android API 37 与自适应

当前批准 target 为 36，候选 37 对应 Android 17 Beta，`candidate_promotion=blocked`。平台行为、厂商兼容与测试矩阵均为 unverified，adaptive 因正式 Manifest 仍有固定方向和 `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` 而为 blocked。API 37 不再允许该大屏 opt-out，因此升级前需要：

- 清点所有 app-owned 和 vendor Activity 的方向策略。
- 验证护理/销售页面在横屏、折叠、展开、多窗口和桌面窗口中的可达性与状态恢复。
- 验证 CameraX 预览、ML Kit 坐标、人脸取景、水印和全屏提醒方向。
- 将固定宽度内容改为有最大宽度的响应式布局，并保证低高度窗口可滚动。
- 在 tablet/foldable emulator 和至少一台真机上增加回归证据。

候选提升必须一次性满足：Android 17 stable；平台行为、厂商、大屏 adaptive、API 36/37 测试矩阵四类状态均为 verified；候选值与实际 target 一致；并由独立 OpenSpec target change 承担评审和回滚。API 33 Baseline Profile、API 36 smoke 或 API 37 Beta 单次成功均不能替代这些条件。

API 37 手动 CI lane 目前用 app 与 login feature 各自的 test APK 覆盖 adaptive window、登录 UI、MessageQueue/反射/native、局域网、Certificate Transparency/网络栈、后台闹钟音频和厂商启动范围；失败会上传 evidence 并保持 blocked，但不阻断 target 36 的正常构建。

不要用新的兼容绕过代替适配。

## P2：导航与模块 API

- 统一 feature entry、route key 和 app navigation registry 的所有权；当前 registry 只含 login/home/identification 三项。
- 当前保持 Navigation Compose `2.10.0`，不引入 Navigation 3 artifact。Nav3 后续 change 必须使用稳定版本并原子替换，不建立长期双栈。
- identification 当前有意保留 app-owned `IdentificationRoute` 注册和三个结果 key；不要把已完成的 UI 所有权迁移误写成 Nav3 或 feature-owned route 已完成。
- 动态 Login/Home 起点、隐私 gate、认证根 `popUpTo`/清栈、重复导航、配置变化、HomeGraph owner、Home 角色分流以及销售返回/保存恢复已建立 focused JVM 与 API 36 等价测试；登录 UI 已迁入 feature，后续 Login/Home 行为修改或 Nav3 迁移必须复用或等价替换这些契约，并继续覆盖结果返回与消费。
- 将可避免的 `OrderNavParams`、`ServiceCompleteData`、`WatermarkData` 等大对象 route payload 收敛为稳定 ID，再设计 Nav3 entry decorator、结果通道和 scope。
- Navigation 3 迁移不与业务接口变化、模块大搬迁或 targetSdk 升级合并。
- 继续缩小公共 API，优先 `implementation` 和 `internal`，避免跨模块访问实现包。

## P2：工程与可观测性

- 完成下一独立 Baseline/Startup Profile 批次后，定期生成 Baseline Profile，并确保 Startup/Profile 场景边界和关键旅程不退化。
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
