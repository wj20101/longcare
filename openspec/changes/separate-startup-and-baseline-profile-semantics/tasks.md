## 1. 固化当前基线与可执行契约

- [x] 1.1 在修改实现前记录当前两个文本 Profile 的行数、规范化规则数、SHA-256，现有 Release APK/AAB 的 `baseline.prof` / `baseline.profm` 条目和 `r8.json` Startup DEX 状态到 `build/reports/startup-profile/`，并验证报告明确显示两个文本文件当前完全相同且不提交该报告。
- [x] 1.2 为六个场景、四个 Startup/两个 Baseline-only 分类、必需页面 tag、双 compilation mode 和真机收益状态建立机器可读质量配置，运行解析测试确认遗漏、重复场景、未知分类和不完整预算字段都会失败。
- [x] 1.3 重写 `verify_baselineprofile_journeys.sh` 的输入契约并新增 `test_baselineprofile_journeys.sh`，先用当前实现证明缺状态准备、package-root-only、盲手势、Startup 分类污染、benchmark 模式分叉和 production 泄漏 fixture 会失败，再把测试接入 `preflight_local.sh --local-fast`、质量注册表和 CI-required 入口。

## 2. 建立仅性能变体可用的状态准备边界

- [x] 2.1 在 Gradle 中把一个共享 performance-only source root 精确绑定到 `nonMinifiedRelease` 与 `benchmarkRelease`，配置专用 signature permission 和 `ProfileScenarioSetupActivity` Manifest，并通过 merged Manifest 检查证明 `debug`/正式 `release` 不包含该 permission、组件或 source root。
- [x] 2.2 在 `:baselineprofile` 测试 APK 声明专用 permission 并实现显式 scenario id 协议，验证未知 id、缺参数和非同签名调用 fail closed，合法调用能观察到唯一完成/失败节点且不会静默超时。
- [x] 2.3 实现 performance-only 状态控制器：每次从清空的测试包数据开始，未登录状态只经 `PrivacyConsentManager` 与 `UserSessionRepository` 建立，护理/销售状态只经 `UserSessionRepository.login()` 写入两个纯虚构复合身份；用 focused instrumentation 验证加密 session、namespace、epoch、角色和公开 session 状态一致，且源码不直接写 SharedPreferences/DataStore/Room/session 文件。
- [x] 2.4 增加 performance source-set 与最终产物泄漏守卫，使用正反 fixture 及正式 Release Manifest/DEX/assets 检查确认状态 Activity、permission、虚构 token、fixture 标记和测试资源均不能进入 production Release。

## 3. 建立稳定页面节点与场景 helper

- [x] 3.1 为隐私协议、Login 根与输入/提交区、护理 Home 根/头部/入口卡片、销售 Home 根/客户入口、服务记录根和销售客户列表根补充稳定非本地化 test tag，并让应用根向 UiAutomator 暴露 test tag；运行现有 Compose tests 和新增 tag focused tests 验证节点唯一且可交互。
- [x] 3.2 在 `:baselineprofile` 实现单一 `ProfileScenario` 目录和共享的清理、准备、强制停止、冷启动、精确等待与失败诊断 helper；用测试验证每个场景只接受其隐私/会话/角色与目标节点组合，旧 namespace、错误角色、固定 sleep、package root 和窗口空闲均不能判定成功。
- [x] 3.3 实现护理 Home → 服务记录 → Home 与销售 Home → 客户列表 → Sales Home 两条 Baseline-only 旅程，所有点击、目标和返回都使用准确节点；在 API 33 设备上逐条运行并验证无需真实账号、短信或生产网络数据也能稳定结束。

## 4. 接入 TTID/TTFD 启动完成语义

- [x] 4.1 提取可测试的根页面 readiness 判定，覆盖隐私可交互、session resolving、Login、护理 Home 与销售 Home；运行 JVM/Compose focused tests 验证 resolving 永不完成、错误角色不完成、正确根页面完成且非首屏网络/更新/滚动状态不参与判定。
- [x] 4.2 使用现有 Activity Compose fully-drawn API 接线互斥根页面：Splash 持有未完成条件，隐私、Login 和对应 Home 在完成首帧且可交互时释放；运行 instrumentation 验证每次 Activity 启动只报告一次，配置变化或状态切换不会提前、重复或永久阻塞。
- [x] 4.3 增加 TTFD 结果断言，分别冷启动隐私、未登录、护理 Home 和销售 Home，验证 Macrobenchmark 输出同时包含 `timeToInitialDisplayMs` 与 `timeToFullDisplayMs`；删除任一根页面报告点的负向 fixture 必须稳定失败并指出具体场景。

## 5. 拆分 Profile 生成与对称 Benchmark

- [x] 5.1 将生成器拆为四个 `includeInStartupProfile=true` 的纯启动收集和两个 `includeInStartupProfile=false` 的业务旅程收集，确保状态准备发生在 `BaselineProfileRule.collect` 之前；运行源码守卫证明 Startup 收集不包含滚动、业务导航、返回或异步加载，Baseline-only 收集具有准确目标/返回断言。
- [x] 5.2 重构 `StartupBenchmarks`，让每个受支持启动场景的 `CompilationMode.None` 与 `CompilationMode.Partial(BaselineProfileMode.Require)` 只向同一个 `benchmark(scenario, mode)` 传递模式，并固定 cold start、10 次迭代、相同预置、启动、超时和页面断言；运行 fixture 验证任一模式改用不同 helper 或降级 Profile 都会失败。
- [x] 5.3 新增 benchmark JSON verifier，校验场景/模式成对、迭代元数据一致、TTID/TTFD 均存在、设备/API/ABI/构建 SHA 完整，并通过合成正反 JSON fixture 验证缺 TTFD、缺模式、模式状态不对称和跨设备比较稳定失败。

## 6. 验证文本 Profile 与发布产物

- [x] 6.1 新增文本 Profile verifier，规范化注释/空行后检查两个文件非空、Startup 是 Baseline 子集、Baseline 差集非空且六个场景都被生成任务收集；用相同文件、反向子集、空文件和合法严格超集 fixture 自验证。
- [x] 6.2 新增接受显式 APK/AAB 路径的 artifact verifier，检查当前构建中的 `baseline.prof` / `baseline.profm` 非空可解析、AAB `r8.json` 的 DEX layout/profile-guided 开关和至少一个 `startup: true`，并逐个比对元数据与实际 DEX SHA-256；用合成 archive fixture 验证旧工作区文件、缺条目、全 false、checksum 不符和测试能力泄漏均失败。
- [x] 6.3 在 API 33 GMD 重新生成并提交 `baseline-prof.txt` / `startup-prof.txt`，运行文本 verifier 确认 Startup 严格子集、两个文件不再完全相同，并通过 diff 审查确认 Baseline-only 差集来自声明的护理/销售旅程而非任意手势。
- [x] 6.4 更新 Baseline Profile workflow：生成后运行源码/fixture与文本 verifier，只在真实规则差异时创建 PR并上传机器可读结果；更新 Android CI/Release 治理测试，确认普通 CI 不把 GMD 数字当性能收益且 workflow 权限、timeout、retention 保持现有约束。
- [x] 6.5 更新 acceptance Release：先构建 minified APK/AAB，再把本次显式产物路径交给 artifact verifier；运行 workflow fixture 验证“仅统计 `app/src` 文件”或“缺 Profile 只 warning”不能通过，同时保持 production vendor gate 的原有条件和失败顺序。

## 7. 集成、设备与发布验收

- [x] 7.1 运行 `bash scripts/quality/preflight_local.sh --full`、受影响 app/login/home focused tests、`:baselineprofile` 编译以及 `:app:lintDebug :app:assembleDebug`，再运行 Lint warning allowlist；所有报告只留在 `build/` 或测试报告目录。
- [x] 7.2 运行 `:app:generateReleaseBaselineProfile` 的 API 33 GMD 路径和六个场景守卫，确认生成稳定、失败诊断包含场景/节点且 API 33 结果只标记 journey/dependency evidence，不标记真实设备收益。
- [x] 7.3 构建 `./gradlew --no-daemon :app:assembleRelease :app:bundleRelease -Prelease.production=false -Prelease.acceptance=true`，运行 artifact verifier，确认 shrink/mapping、资源收缩、签名/Manifest、`baseline.prof` / `baseline.profm` 和 R8 Startup DEX checksum 全部通过。
- [x] 7.4 在 API 36 设备执行隐私拒绝/同意、未登录页、护理 Home、销售 Home、账号切换和配置变化 smoke，确认 TTFD 接线与 performance tag 不改变用户行为、Navigation 2、会话/用户存储顺序或 WebView 开放 host 契约。
- [ ] 7.5 在同一台受控多核 ARM64 真机上按机器配置连续运行至少两轮四个启动场景的 None/Profile 各 10 次冷启动，保存原始 JSON 到 `build/reports/startup-profile/`，验证两轮都不超过预先固化的退化预算且至少一个 TTID/TTFD 指标持续改善；否则保持收益 `unverified` 并停止完成验收。
- [x] 7.6 单独运行现有 production 配置与 vendor readiness 门禁，确认已知 QLZ/腾讯问题仍按原因 fail closed；不得运行修改 AAR、放宽 consumer rules、忽略 Lint 或使用 debug 签名的替代路径。

## 8. 长期文档与最终一致性

- [x] 8.1 更新 `system-overview.md`、`tech-stack.md`、`ci-quality-gates.md` 和 `roadmap-and-open-gaps.md`：记录六个场景、TTID/TTFD、四层证据和真机/模拟器职责，移除已完成的本批次 gap并把下一独立批次推进到项目 R8 确定性清理；运行文档漂移/技术栈守卫确认事实与配置一致。
- [x] 8.2 最终运行 `bash scripts/quality/preflight_local.sh --full`、Profile focused guards/fixtures、OpenSpec `validate --all --strict --no-interactive` 与 `git diff --check`，检查变更只覆盖本 OpenSpec 范围且没有厂商 AAR、依赖版本、targetSdk、Navigation、WebView、数据库或启动初始化时序的夹带修改。
