## Why

当前 `baseline-prof.txt` 与 `startup-prof.txt` 均为 16,131 行且内容完全相同，生成器又把启动后的盲滑和返回操作整体标记为 `includeInStartupProfile=true`；现有门禁只检查任意等待、滑动和返回语句，因此能够在旅程没有证明正确页面、稳定前置状态或性能收益时仍然通过。项目已经具备 Profile 生成和打包基础，现在需要按照 Android 官方对 Startup Profile、Baseline Profile、TTID 与 TTFD 的定义，建立可重复、可解释、可发布验收的性能证据链。

## What Changes

- 将首次启动隐私页、已同意隐私后的未登录启动、已登录 Home 启动和登录后关键业务交互拆成命名明确的场景，并复用稳定的状态准备、启动和页面断言 helper。
- 仅把到达可见且可交互首屏所需的启动场景纳入 Startup Profile；滚动、导航和异步业务交互只进入 Baseline Profile，使 `baseline-prof.txt` 成为包含 `startup-prof.txt` 的业务超集而非相同副本。
- 为每个场景建立确定的隐私/会话前置状态和目标页面语义断言；移除以 package root、盲滑、固定等待或无条件 `pressBack` 作为成功证据的做法。
- 在真实启动目标达到可交互状态时报告 fully drawn，并让 Macrobenchmark 在相同预置状态和相同启动旅程下比较 `CompilationMode.None` 与要求 Baseline Profile 的 `CompilationMode.Partial`，同时产出 TTID 与 TTFD。
- 强化 Profile 守卫及其正反 fixture，验证场景分类、前置状态、页面断言、Startup/Baseline 边界和 benchmark 对称性；CI 继续使用 API 33 生成/诊断，真实收益必须由多核 ARM64 真机确认。
- 扩展生成与 Release 验收，验证提交的文本 Profile、APK/AAB 中的 `baseline.prof` / `baseline.profm`、R8 `r8.json` 的 Startup DEX 标记及 minified acceptance Release 均保持一致可用。
- 更新长期架构、技术栈/质量门禁和路线图中的 Profile 事实；不保存本机 benchmark 数字或一次性执行日志。
- 本变更不调整 `MainApplication` 的隐私、用户存储切换、设备标识、Bugly、定位或 WorkManager 初始化时序，不修改用户可见业务行为、导航、WebView、数据库、targetSdk、依赖版本、R8 项目规则或任何厂商 AAR/consumer rules，也不解除 production Release 的 fail-closed 条件。

## Capabilities

### New Capabilities

- `startup-performance-confidence`: 规定 Startup/Baseline Profile 场景语义、TTID/TTFD 报告与可比 benchmark、生成产物和 Release 打包证据，以及防止旅程退化的自动守卫。

### Modified Capabilities

无。

## Impact

- 主要影响 `:baselineprofile` 的 generator、journey helper 与 Macrobenchmark，`:app` 的启动完成报告点，以及生成出的 `app/src/release/generated/baselineProfiles/*.txt`。
- 影响 `verify_baselineprofile_journeys.sh` 及其 fixture、Baseline Profile workflow、Android CI/Release 的 Profile 验收步骤和相关质量门禁注册。
- 影响 `docs/architecture/roadmap-and-open-gaps.md`、`system-overview.md`、`tech-stack.md` 与 `ci-quality-gates.md` 中的长期事实。
- 不新增生产网络 API、持久化 schema、导出组件或 secret；如需要测试状态控制，只能存在于不可随生产 Release 分发的性能测试边界，并必须由守卫证明无法进入生产变体。
- 性能数字受设备、温度和系统负载影响；模拟器结果只用于旅程/依赖链稳定性，合并条件使用场景完整性、产物一致性和同设备相对比较，最终收益由受控真机证据确认。
