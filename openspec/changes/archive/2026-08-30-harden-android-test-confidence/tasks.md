## 1. 固化现状与测试所有权基线

- [x] 1.1 核对干净工作树、当前 Android/Gradle 基线、所有非空 `src/androidTest` 目录及其 test APK，确认唯一实际所有者集合为 `:app`、`:core:data`、`:feature:identification`、`:feature:login`；以 `rg --files`、模块 build script 和 `android describe --project_dir=.` 的结果一致验证，不创建执行报告文档。
- [x] 1.2 在 API 36 设备运行 `DashboardGridCompactModeTest` 与 `TopHeaderAdaptationTest`，记录当前 3 项通过；再以 `connectedDebugAndroidTest --dry-run` 和单独运行无测试的 `:feature:home:connectedDebugAndroidTest` 证明根聚合会选择空 test APK 且在 0 tests 后发生 runner 失败，后续不得通过给空模块补伪测试来消除基线。
- [x] 1.3 运行现有 `InfoCardLayoutSpecResolverTest`、`:app:compileDebugAndroidTestKotlin`、instrumentation smoke/matrix 守卫及 affected-modules fixtures，确认开始实现前单测、选择器和 CI 计划基线可用，任何非预期失败先定位而不删除测试。

## 2. 修正 Dashboard 与 TopHeader 测试表达

- [x] 2.1 将 `DashboardGridCompactModeTest` 的标题、记录说明和格式化数量副标题全部改为从 instrumentation target context 的当前 `R.string` 获取，并以源码检查确认不再复制“待护理计划”“已服务记录”“查看过往服务记录”等产品文案，同时保留四项可见文本和同排卡片断言。
- [x] 2.2 从 `TopHeader` 当前条件提取 module-internal 不可变布局规格解析器，保持紧凑模式 `width < 340dp && fontScale >= 1.3` 及普通用户块 `width >= 480dp` 的逐值语义；以现有 Composable 只消费解析结果、生产分支与修饰符没有其他变化验证等价。
- [x] 2.3 新增 TopHeader JVM characterization tests，覆盖 339/340dp、fontScale 1.29/1.3 和 479/480dp 组合，并运行对应 `:app:testDebugUnitTest --tests '*TopHeader*Layout*Test'` 确认每个临界值只选择预期模式/宽度。
- [x] 2.4 将 `TopHeaderAdaptationTest` 改为使用 Compose test 的 `DeviceConfigurationOverride.ForcedSize` 与 `FontScale` 建立真实根配置，移除手动 `LocalDensity` 和固定宽度子 `Box`；以源码检查及 UI 断言确认长公司名、紧凑大字体用户名、头像与身份节点不重叠且均可达。
- [x] 2.5 运行 `:app:testDebugUnitTest`、`:app:compileDebugAndroidTestKotlin` 和 API 36 上两个 focused instrumentation 类，确认 resolver 边界测试及 3 个原 UI 行为全部通过；若当前 Compose BOM 不提供所需测试 API，先更新本 change 重新评审而不顺带升级依赖。

## 3. 建立 Instrumentation test APK 所有权与聚合入口

- [x] 3.1 新增可评审的 instrumentation 模块清单，精确登记 `:app`、`:core:data`、`:feature:identification`、`:feature:login`，并注明它只定义 connected test APK 所有权、不会复制 target matrix 的 Managed Device/选择器；以解析 fixture 验证顺序稳定、无重复和无未知模块。
- [x] 3.2 为 `:core:data` 显式声明 `androidx.test.runner.AndroidJUnitRunner`，核对四个已登记模块均具备 runner 声明与运行时依赖；以四个 `assembleDebugAndroidTest`/`compileDebugAndroidTestKotlin` task 成功且空模块 build script 未新增 runner 依赖验证。
- [x] 3.3 新增 `scripts/quality/run_connected_instrumentation_suite.sh`，从清单生成四个模块限定的 `connectedDebugAndroidTest` task，支持现有 `ANDROID_SERIAL` 和可测试的 Gradle 命令 seam，拒绝空清单/未知模块且绝不调用无模块限定的根 task；以 fake Gradle fixture 核对完整参数序列。
- [x] 3.4 新增 `verify_instrumentation_test_ownership.sh`，双向比较清单与实际非空 `src/androidTest` 模块，并验证 runner、runner 依赖、聚合 task 作用域及 source root；在真实工程正向运行确认四个 owner 全部通过。
- [x] 3.5 为所有权守卫增加正向 fixture，以及遗漏测试模块、陈旧空模块、缺 runner、缺 runner 依赖、重复/未知模块和根级聚合 task 至少六类负向 fixture；逐组确认非零退出并输出违规模块、证据路径和修复方向。
- [x] 3.6 收紧 `verify_instrumentation_smoke_classes.sh`/目标平台矩阵验证，使 app 与 login feature 字段只能解析到各自 test APK，而不能因在任意 feature 找到同名类而通过；扩展矩阵 fixture 覆盖跨 owner 选择器和正确分离的 app/login APK。
- [x] 3.7 将所有权守卫及 fixture 接入单一架构/预检入口和 `quality_gate_registry.json`，同步 workflow quality 守卫要求；运行 registry JSON、架构边界、local-fast 计划检查确认没有重复执行、豁免或放宽既有矩阵规则。

## 4. 保持 affected CI 成本并同步长期事实

- [x] 4.1 扩展 `test_affected_modules.sh`，覆盖 Dashboard/TopHeader 源码与测试触发 app instrumentation、login feature 变化只触发其 test APK、纯文档/JVM 逻辑不触发设备、质量脚本进入 full build 但不自动启动全量 instrumentation；运行 fixture 确认输出稳定。
- [x] 4.2 核对 `.github/workflows/android-ci.yml` 继续仅在 app/login 对应标志为 true 时运行各自 API 36 Managed Device task，更新摘要/报告守卫以明确 build-only、app focused 和 login feature focused 三种结果；以 workflow quality 与负向 fixture 验证没有新增无条件 emulator job。
- [x] 4.3 更新 `docs/architecture/ci-quality-gates.md`、`dependency-rules.md` 与 `system-overview.md`，记录四个 connected test APK owner、受支持聚合入口、app/login scoped GMD 和报告分离语义；运行文档相对链接检查并与清单、workflow、matrix 逐项比对。
- [x] 4.4 修正 `roadmap-and-open-gaps.md` 中 legacy 快照为当前 218，删除已完成后不再成立的 645dp/批次 A 执行项并把 Baseline/Startup Profile 标为下一独立批次；扩展文档漂移守卫或改用可执行事实引用，确保以后 allowlist 数量变化不会静默保留旧数字。

## 5. 完成综合验证

- [x] 5.1 运行 instrumentation 所有权、聚合入口、target matrix、smoke class、affected modules、workflow quality 和 legacy allowlist 的全部正负 fixture，确认真实工程正向通过且每类错误稳定 fail-closed。
- [x] 5.2 运行 `./gradlew --no-daemon :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :core:data:compileDebugAndroidTestKotlin :feature:identification:compileDebugAndroidTestKotlin :feature:login:compileDebugAndroidTestKotlin`，确认布局 resolver、Room/用户存储及各 test APK 均可编译。
- [x] 5.3 使用 Android CLI 确认 API 36 设备后执行新的 connected 聚合入口，验证 app 当前全部 instrumentation、core:data 数据库/用户存储、identification 和 login feature 四个模块分别执行且全部通过；检查 Gradle 报告确认没有 0-test 模块和 test APK 归属混淆。
- [x] 5.4 在 API 37 非阻断受管设备上运行 Dashboard/TopHeader focused selectors，并在 API 36 复跑相同集合，确认测试配置在手机/平板环境均可表达且失败只反映真实布局差异；API 37 结果不得改变 target 36 或 promotion=blocked 状态。
- [x] 5.5 运行 `bash scripts/quality/preflight_local.sh --full`、Release validation guard、`:app:lintDebug :app:assembleDebug` 和 Lint warning allowlist，确认普通 CI、架构、单测与构建主路径无新增问题。
- [x] 5.6 构建显式验收 Release：`./gradlew --no-daemon :app:assembleRelease -Prelease.production=false -Prelease.acceptance=true`，确认 R8、资源收缩、Baseline Profile 打包和签名流程不受测试重构影响；生产 Release 的既有 QLZ/腾讯厂商 fail-closed 条件保持原样。
- [x] 5.7 运行 `openspec validate --all --strict --no-interactive`、`git diff --check`、文档链接与敏感文件检查，确认 change 产物/实现一致、没有 build 报告或本机路径入库，且用户存储、开放 WebView、Navigation 2、targetSdk 和厂商 AAR 均未修改。
