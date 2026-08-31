## 1. 固化迁移前 Home 契约

- [x] 1.1 记录实施起点的 branch、`git status --short`、既有 OpenSpec changes、Home/护理/Sales 源码与资源引用、`legacy_feature_files_allowlist.txt` 条目，形成仅保留在终端/PR 的基线；以 `git diff --name-only` 和逐路径 `rg` 确认后续不会覆盖既有 acceptance、R8、startup 或用户改动。
- [x] 1.2 运行现有 `HomeExperienceTest`、`HomeScreenPermissionPolicyTest`、`HomeSharedViewModelTest`、`MainDashboardViewModelTest`、Profile/布局 JVM tests 与 Home/护理 Compose androidTests；以对应 `:app:testDebugUnitTest`、`:feature:home:testDebugUnitTest` 和 `:app:compileDebugAndroidTestKotlin` 均通过确认迁移前基线，失败项必须先定位而不能删除测试。
- [x] 1.3 扩展 Home characterization tests，锁定用户未解析只显示 loading、角色值 `2` 只渲染 Sales、其他角色只渲染护理页、登录日志和 Startup-ready 信号在重组/配置变化中不重复；运行 focused JVM/Compose tests 并证明断言能区分错误角色或重复副作用。
- [x] 1.4 扩展护理端 characterization tests，锁定三页顺序、禁止手势横滑、adaptive nav、Dashboard 前台刷新/Tab/错误消费、Nursing 日期格式与订单状态动作、Profile 统计/版本/协议/隐私/登出；以 focused unit/Compose tests 在现有 app owner 下先全部通过。
- [x] 1.5 扩展 app 集成契约测试，覆盖同一 `HomeGraphRoute` 共享今日订单实例、Camera 返回 key 一次消费、开放 WebView URL 原样传递、退出/换号替换认证根以及 `SalesNavigationState` 配置恢复；运行 `run_entry_navigation_focused.sh` 与 Sales focused tests 验证迁移前行为。
- [x] 1.6 全仓盘点 `UserAvatar`、`EmptyView`、`ServiceOrderItemCard`、`TopHeader`、Profile 组件、日期工具及 Home 专属 drawable/string 的实际消费者，并在任务实现 diff 中给出 feature-local、`core:ui` 或 app renderer 的逐项归属；以 `rg --files`/`rg` 证明没有仅为消除 import 而扩大 Core 或误删跨功能资源。

## 2. 建立 Core 契约与 Home 公开边界

- [x] 2.1 在 `:core:domain` 增加 Android-free 的公司名称读取契约，字段仅表达当前用户的公司名称需求，不暴露 preference/DataStore key；以 `:core:domain:test`（或该模块现有 JVM test task）和依赖检查验证 Domain 不含 Android/Data 实现。
- [x] 2.2 在 `:core:data` 以现有 `SystemConfigManager` 实现并绑定公司名称契约，保持当前 key、空值和用户隔离语义；扩展 `SystemConfigManagerUserScopeTest`/binding test 覆盖换号、清空和重新拉取后读取，并运行 `:core:data:testDebugUnitTest` 验证。
- [x] 2.3 为 `:feature:home` 启用与现有 feature 一致的 Compose、Hilt、资源、JVM/Compose instrumentation 配置，只从 version catalog 增加实际 import 所需的 `:core:common`、`:core:domain`、`:core:model`、`:core:ui` 和 AndroidX 依赖；运行 `:feature:home:compileDebugKotlin :feature:home:compileDebugAndroidTestKotlin` 并核对依赖图不含 `:app`、`:core:data` 或其他 Feature internal。
- [x] 2.4 定义唯一公开的 `HomeFeatureScreen` 以及最小 `HomeActions`、app version/config、Sales renderer、Startup-ready callback 等不可变 API；以 API visibility/源码断言和 `:feature:home:compileDebugKotlin` 验证公开签名不包含 `NavController`、route、`SavedStateHandle`、`Activity`、`Context`、`Intent`、app `R`、Data 或厂商类型。
- [x] 2.5 在 Home API 中定义只映射现有 Dashboard 所需状态/命令的 `HomeOrderStateSource`（名称按项目约定落地），并用 fake 实现测试状态转发、刷新、Tab 和反馈确认；运行 `:feature:home:testDebugUnitTest` 验证端口不复制缓存、导航或订单业务规则。
- [x] 2.6 在 app 增加现有 HomeGraph `TodayOrderViewModel` 到订单端口的窄适配器，并补充实例身份/状态传播测试；以 app focused test 证明 Home、订单列表和服务返回仍使用同一图级 owner，清除 HomeGraph 后旧状态不可访问。

## 3. 收敛 Home 状态并迁移护理端 UI

- [x] 3.1 将 `HomeSharedViewModel` 收敛为 feature-internal 的屏幕级状态持有者，输出单一不可变 `StateFlow<HomeUiState>`，统一表示用户解析、角色体验和 Dashboard Tab；以 coroutine tests 覆盖加载、销售/护理分流、Tab 更新、换号和 scope 取消，并确认捕获协程异常时重新抛出 `CancellationException`。
- [x] 3.2 将登录日志、Startup-ready 和用户可见反馈改为带明确消费/去重语义的事件入口，Composable 使用生命周期感知收集；以重组、STOP/START、配置变化、已消费不重放及未消费仍可见 tests 验证每项副作用至多发生一次。
- [x] 3.3 将 Home 根、loading、角色 split 和护理端三页 adaptive navigation 迁入 `:feature:home`，内容 Composable 只接收 immutable state/events；运行 Home role 与 navigation Compose tests 验证角色 `2`、三页顺序、选择语义、禁止横滑及紧凑/宽屏布局与迁移前一致。
- [x] 3.4 将 `maindashboard` 业务 UI、preview 与 `MainDashboardViewModel` 迁入 Home feature，改为依赖公司名称 Domain 契约和订单状态端口；运行 Dashboard ViewModel/JVM/Compose tests 验证前台刷新、当前用户/公司名、进行中订单、Tab、错误消费和订单动作。
- [x] 3.5 将 `nursing` 的日期 tabs、订单列表、preview 与 `NursingViewModel` 迁入 Home feature，将独占时间格式逻辑改为 feature-local 且保持当前时区/月份语义；运行 `TimeUtilsDateSemanticsTest` 等迁移后 focused tests，覆盖跨月、选日、空态、刷新和不同订单状态动作。
- [x] 3.6 将 `profile` UI、preview 与 `ProfileViewModel` 迁入 Home feature，通过不可变 app version/config 和显式 actions 提供统计列表、协议、隐私和退出；运行 Profile JVM/Compose tests 验证用户信息、统计刷新/错误、版本展示、URL 原样传递和单次登出。
- [x] 3.7 按 1.6 的全仓证据迁移 Avatar、空态、订单卡片和日期组件：Home 独占项留在 feature，真正跨业务的纯展示项才提升到 `:core:ui`，Sales 需要的 Home/Profile 内容只暴露最小无状态组件/renderer；以模块编译、API guard 和引用扫描验证无业务 route/ViewModel 进入 Core。
- [x] 3.8 将 Home、Dashboard、Nursing、Profile 的专属 drawable/string、preview 和 test fixture 移到 `:feature:home`，共享资源保留原 owner；运行资源 merge、`:feature:home:lintDebug`、preview/Compose tests 与重复资源扫描验证无 app `R`、缺图、错文案或视觉语义漂移。
- [x] 3.9 删除护理、Sales 和兼容人脸页面接收/查找 `HomeSharedViewModel` 的路径，改为传入所需用户值、局部状态和回调；以全仓 `rg 'HomeSharedViewModel|hiltViewModel'` 的人工白名单、模块编译和 focused tests 验证只有 Home feature 屏幕入口创建内部状态持有者。

## 4. 切换 App 组装并删除双份实现

- [x] 4.1 将 `AppNavGraphsEntry.HomeDestination` 切换为调用 `HomeFeatureScreen`，注入现有 HomeGraph 订单适配器、app version、Home actions、Sales renderer 和 Startup callback；以 `:app:compileDebugKotlin` 与入口导航 tests 验证 `HomeRoute`/`HomeGraphRoute` 注册、back stack owner 和认证根保持不变。
- [x] 4.2 用 app-owned Sales renderer 接入现有 `SalesExperienceScreen`、`SalesViewModel` 与 `SalesNavigationState`，仅传当前用户和显式动作；运行 Sales back reducer/snapshot/ViewModel JVM tests 与 Sales Compose restoration test，验证 Tab、详情/评估返回目标、提醒和 Camera/WebView 结果未改变。
- [x] 4.3 将订单列表、服务流程、倒计时、Camera、协议、隐私、退出及 Startup fully-drawn 逐项映射到现有 app 导航/平台实现，并补充 fake action contract tests；验证 route payload、SavedStateHandle result key、开放 WebView host 策略和 callback 次数均与迁移前相同。
- [x] 4.4 扩展 HomeGraph 集成测试，覆盖 Dashboard 刷新后进入订单列表再返回、服务结果写回、配置重建以及退出/会话失效后的 scope 清理；运行 focused app tests 证明同图状态连续且新账号无法读到上一图实例。
- [x] 4.5 将 `NfcTestHelper*`、NFC 测试 Dialog/状态/监听代理保留并迁出 app 的 `features/maindashboard` 命名空间，Home 只通过 app action 请求；运行现有 `NfcTestHelperCopyActionTest`、app 编译和 Manifest/导出组件 guard，确认平台 helper 未进入 feature 且行为不变。
- [x] 4.6 在 feature 入口及 app 集成全部通过后，删除 app 中旧 `features/home/ui`、`maindashboard` 业务 UI/VM、`nursing`、`profile` 和已迁移独占资源/测试，保留 reporting 实现、Sales、navigation 和平台 adapter；以路径 `rg --files` 无业务旧副本、资源扫描和 `:app:assembleDebug` 验证唯一 owner。

## 5. 收紧架构、CI 与文档治理

- [x] 5.1 新增可独立运行的 `verify_home_feature_boundary.sh`，阻止 app 重建 Home/Dashboard/Nursing/Profile UI/VM、app 绕过公开 API 导入 feature internal、feature 引用 app/navigation/platform/app `R`/Data/Activity/Context/Intent/厂商类型；在真实工程正向运行并确认返回零。
- [x] 5.2 为 Home 边界守卫增加至少五组负向 fixture：app 重建 Home UI、app 导入内部 ViewModel、feature 引用 app 资源、feature 依赖 Data 实现、feature 直接使用平台/导航类型；逐组验证命令返回非零并输出违规文件、规则、允许 API 和修复方向。
- [x] 5.3 更新 `module_dependency_allowlist.txt`、模块 API visibility 和架构总守卫，仅加入 Home 实际 Core 依赖并固定 app 可见 API；运行 `verify_module_dependency_whitelist.sh`、`verify_module_api_visibility.sh`、`verify_architecture_boundaries.sh` 及其 fixtures，确认没有扩大通配豁免。
- [x] 5.4 更新 `affected-modules.sh` 与测试计划 fixtures，使 Home feature 的源码/资源/unit/androidTest、app 组装、Core 公司名称契约和质量脚本变化分别触发正确模块及下游验证；运行 affected-modules self-tests 验证 changed-path 计划无漏跑或无关全量膨胀。
- [x] 5.5 将 Home/Dashboard/Nursing/Profile instrumentation owner 迁到 `:feature:home`，同步 `instrumentation_test_modules.txt`、API 36 阻断 selector、API 37/adaptive selector、`target_platform_test_matrix.properties` 与 Android CI；运行 ownership/matrix/workflow guards 并构建 app/feature test APK，确认无 class-not-found 或旧 app selector。
- [x] 5.6 从 `legacy_feature_files_allowlist.txt` 精确删除所有已迁移 Home/护理文件且不增加替代豁免，把 Home focused guard 接入 `preflight_local.sh` 与 `quality_gate_registry.json`；运行 allowlist fixture、registry 和 local-fast 验证阈值只收缩不放宽。
- [x] 5.7 更新 `docs/architecture/system-overview.md`、`dependency-rules.md`、`ui-and-screen-map.md`、`roadmap-and-open-gaps.md`、`ci-quality-gates.md` 及维护矩阵涉及项，记录 Home/Sales/app owner、订单图级端口、公司名称契约、测试归属和明确非目标；运行文档链接检查与 `preflight_local.sh --local-fast` 验证长期文档一致。

## 6. 完成分层、构建与设备回归

- [x] 6.1 运行 Home 边界正负 fixture、legacy allowlist fixture、依赖/API/架构守卫、affected-modules、instrumentation ownership 和目标平台矩阵 focused 集合，确认所有正向工程检查通过且每个负向 fixture 稳定失败。
- [x] 6.2 运行 `./gradlew --no-daemon :core:domain:test :core:data:testDebugUnitTest :feature:home:testDebugUnitTest :feature:home:lintDebug :feature:home:compileDebugAndroidTestKotlin :app:testDebugUnitTest`（按模块实际 task 名调整 Domain JVM task），确认公司名称、Home 状态、护理三页、Sales adapter、HomeGraph 和资源 focused tests 全部通过。
- [x] 6.3 运行 `bash scripts/quality/verify_release_validation_entry.sh .`、`./gradlew --no-daemon :app:lintDebug :app:assembleDebug` 和 `bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt`，确认普通 Android CI 主路径无新增 lint warning、ignore、签名 fallback 或构建问题。
- [x] 6.4 运行 `bash scripts/quality/preflight_local.sh --full`，确认 Kotlin、测试、依赖、取消语义、模块边界、文档和构建综合门禁通过；检查 worktree 确认未生成/跟踪 build report、执行日志或无关格式化改动。
- [x] 6.5 使用 Android CLI 选择 API 36 模拟器，安装 debug/app 与 `:feature:home` instrumentation APK，验证 loading、销售/护理角色、护理三页切换、Dashboard 前台刷新、Nursing 选日/订单、Profile 统计/协议/隐私/登出、旋转和前后台恢复；结合 instrumentation、`android layout` 和关键截图证明无旧用户闪现、重复副作用或导航回归。
- [x] 6.6 在 API 37 或项目矩阵指定的大屏模拟器运行 Home adaptive focused tests，验证 compact/medium/expanded 的底部栏/导航栏、Dashboard cards、Profile 和大字体布局；以矩阵 selector 实际执行结果确认不是仅编译 test APK。
- [x] 6.7 运行 `bash scripts/quality/preflight_local.sh --release` 及 acceptance Release 静态验证，确认本迁移未新增发布阻断，同时 QLZ 固定配置/弱 TLS、腾讯人脸 16 KB 对齐与 consumer rules 等既有厂商门禁仍按预期 fail-closed，真实厂商设备 verdict 仍保持既有 `unverified`/独立状态。
- [x] 6.8 运行 `openspec validate --all --strict --no-interactive`、`git diff --check` 和最终范围扫描，确认 spec/task 一致、厂商 AAR/Room/用户存储/WebView host/Navigation 2/target/dependency 版本均未被修改，且没有通过豁免或双实现掩盖失败。
