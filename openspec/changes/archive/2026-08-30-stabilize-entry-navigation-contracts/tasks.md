## 1. 测试基础与入口状态

- [x] 1.1 在 version catalog 增加与 `androidxNavigation` 同版本的 `navigation-testing` alias，并仅以 `androidTestImplementation` 接入 `:app`；用 `./gradlew --no-daemon :app:dependencies --configuration debugAndroidTestRuntimeClasspath` 确认存在该依赖、用 release runtime dependency report 确认不存在该依赖。
- [x] 1.2 提取不依赖 Compose/Android Context 的入口状态映射，覆盖未同意、`Unknown`、`LoggedOut`、`LoggedIn`；运行对应 `:app:testDebugUnitTest --tests '*EntryState*'` 验证四种状态与“未同意优先”规则。
- [x] 1.3 将隐私同意持久化和 post-consent 初始化收敛为本进程幂等动作，并增加首次同意、配置重建、已同意冷启动测试；运行 focused JVM/Robolectric 测试确认不提前创建业务入口且初始化不重复。

## 2. 可测试根导航与认证协调

- [x] 2.1 分离生产 `rememberNavController()` wrapper 与接受 `NavHostController` 的内部根 host，并为 Login/Home 内容增加最小 `internal` renderer 接缝；用 `./gradlew --no-daemon :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` 验证生产与测试调用均可编译。
- [x] 2.2 实现纯认证导航协调器，使目标登录态与当前根状态映射为 `ShowLogin`、`ShowHome` 或 `NoOp`；运行 focused JVM 测试覆盖首次解析、登录、登出/失效、重复成功信号和同态 emission。
- [x] 2.3 让 Login 成功回调与全局 session emission 统一调用协调器，并在跨认证边界时替换根 back stack；使用 `TestNavHostController` 验证登录后 Login 被移除、登出后受保护历史被移除且返回键不能越过认证边界。
- [x] 2.4 增加 Login/Home 实际 typed route 起点与重复信号 instrumentation 测试；在 API 36 目标运行 focused 测试，确认已登录冷启动不闪现 Login 且连续成功信号只产生一个 HomeGraph。

## 3. HomeGraph 作用域与角色分流

- [x] 3.1 将 Home 角色判断提取为 Loading/Care/Sales 的纯映射并由 `HomeScreen` 渲染结果；运行 focused JVM/Compose 测试覆盖销售角色 `2`、护理角色、空用户及换号时不显示旧角色内容。
- [x] 3.2 固定 `TodayOrderViewModel` 以真实 `HomeGraphRoute` entry 为唯一图级 owner，保持 `HomeSharedViewModel` 为 HomeRoute owner，并移除会创建第二个图级实例的静默 fallback；用 focused 测试验证同图 Home/订单子目的地共享 owner、缺失图 entry 明确失败。
- [x] 3.3 增加跨认证边界的 HomeGraph 生命周期 instrumentation 测试；验证登出清除旧 owner，再登录创建新 owner，旧账号图级内存状态不可被新图访问。

## 4. Sales 内部状态恢复与返回

- [x] 4.1 为 `SalesNavigationState` 建立包含当前页面、根 Tab、详情返回目标、评估返回目标和提醒选择的可保存 snapshot，并让 saver 容错恢复未知页面值；运行 focused JVM 测试验证 round trip 和默认回退。
- [x] 4.2 将 Sales 每个内部页面的返回目标表达为纯 reducer，并让系统返回与页面返回共用同一 handler；运行 focused JVM 测试覆盖提醒、客户、登记、提交结果、评估链和非首页根 Tab。
- [x] 4.3 使用 Compose 可保存状态恢复测试重建 Sales 非根页面，验证页面、Tab、返回目标和提醒选择恢复后仍执行相同返回路径。

## 5. 门禁、文档与综合验证

- [x] 5.1 增加可独立运行的入口导航 focused 验证并接入 affected-scope 质量编排，覆盖依赖仅测试可见、renderer 保持 `internal`、核心 JVM 测试及 API 36 instrumentation 类；运行门禁自身的正向与临时违规 fixture 验证检测有效。
- [x] 5.2 按 `docs/README.md` 维护矩阵更新系统概览、UI/路由地图、路线图和 CI 质量门禁，记录认证协调、HomeGraph owner 与 Login/Home UI 迁移前置条件；运行 `bash scripts/quality/preflight_local.sh --local-fast` 验证长期文档与边界一致。
- [x] 5.3 运行 `bash scripts/quality/preflight_local.sh --full`、受影响的 `:app:testDebugUnitTest`、API 36 focused instrumentation、`:app:lintDebug`、lint warning allowlist 和 `:app:assembleDebug`；确认所有结果通过且未修改 Navigation 版本、route payload、targetSdk、厂商 AAR 或生产发布门禁。
