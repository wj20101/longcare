# 路线图与开放问题

最后核对：2026-08-27

本文只记录仍然成立的后续工作。已完成的任务、逐次 CI 结果和历史方案通过 Git/PR/Issue 追溯，不在主文档中保留执行日志。

## 当前判断

- 护理端和销售端的主要用户链路均已实现。
- Debug 和显式验收构建可用。
- 生产发布被已知且可验证的厂商 SDK/配置问题主动阻断。
- 架构演进重点是缩小 `:app`、稳定平台生命周期和提高关键业务回归信心。
- 文档当前真相集已经收敛；后续应随实现更新，不再累积 design/plan/progress 副本。

## P0：恢复生产发布条件

Owner 涉及移动端、服务端和厂商，完成条件必须全部满足：

1. 服务端下发 QLZ 初始化所需配置，移除 Android 中固定测试 key 和 `QLZ_TEST_MODE=true`。
2. 替换 QLZ 1.3.0.2，确保可达网络链路不再触发弱 TLS trust manager finding。
3. 替换腾讯人脸 AAR，确保 ARM64 native library 满足 16 KB ELF/page alignment，并移除危险的全局 consumer ProGuard 规则。
4. 新厂商包通过真实设备的登录、身份核验、QLZ 蓝牙评估、报告、COS 上传和 Release shrink 回归。
5. `verify_vendor_sdk_release_readiness.sh`、生产配置门禁、Lint 和 production Release 全部通过。
6. 重新评估 Jetifier；只有全部相关厂商包 AndroidX-only 后才可关闭。

验收 Release 只用于联调/验收，必须显式标记，不得作为生产包分发。

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
