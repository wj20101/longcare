## 1. 固化迁移前登录契约

- [x] 1.1 运行并记录现有 `LoginViewModelPrivacyGateTest`、3 个登录 Compose tests、`EntryNavigationInstrumentationTest` 和 `verify_release_validation_entry.sh` 的迁移前结果；以对应 Gradle focused task、`compileDebugAndroidTestKotlin` 与 Release guard 均通过确认基线可用，失败项必须先定位而不能删除测试。
- [x] 1.2 扩展登录 ViewModel characterization tests，覆盖启动配置只加载一次、未确认隐私前验证码/登录均被拦截、反馈仅由匹配 id 消费及最近手机号读写语义，并运行 `./gradlew --no-daemon :app:testDebugUnitTest --tests '*LoginViewModelPrivacyGateTest'` 验证迁移前行为。
- [x] 1.3 为登录 UI 补齐 characterization tests，锁定“未勾选时弹窗且不提交、确认只勾选不自动重放、取消保持未勾选、发送成功请求验证码焦点、普通点击不打开而长按打开五项校验入口”；以 `:app:compileDebugAndroidTestKotlin` 和 API 36 focused instrumentation 验证。
- [x] 1.4 为用户协议动态 URL 优先、空值/加载失败使用 app 兜底、隐私政策使用 app 兜底且 URL 不做 host 过滤增加 focused tests；先在现状上证明断言能够区分动态与兜底分支。

## 2. 建立 Login Feature 的 Compose 与公开边界

- [x] 2.1 参照现有 Compose feature 配置为 `:feature:login` 启用 Kotlin Compose、`buildFeatures.compose` 和 instrumentation runner，按实际 import 从 version catalog 增加 `:core:ui`、Compose/lifecycle/Hilt/test 最小依赖；运行 `./gradlew --no-daemon :feature:login:compileDebugKotlin :feature:login:compileDebugAndroidTestKotlin` 并核对模块依赖图无 `:app`、Data 实现或其他 Feature internal 依赖。
- [x] 2.2 在 login 公开 API 中增加不可变 `LoginAgreementLinks`，扩充 `LoginValidationEntryActions` 为相机、备用人脸、手动人脸、正式人脸和 NFC 五个回调，保持现有构造调用兼容或在同一提交完成调用点切换；以 JVM tests 验证字段一一映射且默认 no-op 不产生副作用。
- [x] 2.3 增加唯一公开的 `LoginFeatureScreen` Compose 入口，在当前 back stack entry 中取得现有 Hilt ViewModel、生命周期感知收集状态并确认反馈；以 `:feature:login:compileDebugKotlin` 和 focused effect tests 验证公开 API 不接收 `NavController`、`Context`、`Activity`、`Intent`、app `R` 或厂商类型。
- [x] 2.4 抽出可测试的协议链接选择逻辑并接入公开入口/内部内容，保持服务端非空用户协议优先、app 兜底与隐私兜底语义；运行 focused unit tests 覆盖成功、空 URL、加载中和失败状态，确认 URL 原样返回且不限制 host。

## 3. 将登录 UI、校验面板与资源迁入 Feature

- [x] 3.1 将 `LoginScreen.kt`、`LoginScreenComponents.kt`、`LoginScreenContentSections.kt`、`LoginScreenPreviews.kt` 移入 `:feature:login`，保留现有 `com.ytone.longcare.features.login.ui` 业务包以减少无关重命名，并把 app 不需要的内容组件设为 module-internal；以 `rg` 和 `:feature:login:compileDebugKotlin` 验证内部 UI 不 import app 导航、平台实现、`AgreementUrls` 或 app `R`。
- [x] 3.2 将 `presentation/validation/LoginValidationEntrySheet.kt` 迁为 login feature 内部 UI，移除 `LocalContext`/`Intent`/app Activity 引用，使五个选项均在关闭面板后只调用对应 action；以 Compose tests 验证五个 test tag、五次一一映射、普通点击/长按行为和每次选择只触发一个回调。
- [x] 3.3 全仓盘点 `login_bg.webp`、`app_logo_name.webp`、`app_logo_small.webp` 与 `login_*`/校验文案引用，仅将登录独占资源迁入 feature；通过 `rg` 引用检查、Android resource merge、`:feature:login:lintDebug` 和 preview/Compose tests 验证无缺图、错误文案、app `R` 或误删共享资源。
- [x] 3.4 保持手机号/验证码、`remember`/`rememberSaveable`、协议弹窗、焦点、Snackbar 与登录成功效果的当前语义，不把纯 UI 状态写入 ViewModel 或用户存储；运行登录 UI focused tests 验证重组、配置重建、发送成功聚焦、反馈匹配消费和一次成功动作。
- [x] 3.5 将 3 个登录 Compose tests 迁入 `feature/login/src/androidTest`，将 `LoginViewModelPrivacyGateTest` 及必要的 dispatcher rule 迁入 feature JVM tests，不让测试反向依赖 app；运行 `:feature:login:testDebugUnitTest` 与 `:feature:login:compileDebugAndroidTestKotlin` 验证测试所有权和制品构建。

## 4. 切换 App 登录目的地与平台适配

- [x] 4.1 将 `AppNavGraphsEntry.LoginDestination` 切换为调用 `LoginFeatureScreen`，由 app 使用现有 `AgreementUrls` 构造 `LoginAgreementLinks` 并继续注入登录成功/WebView actions；以 `:app:compileDebugKotlin` 和路由契约测试验证 `LoginRoute`、认证 graph、`FeatureEntry.ROUTE`、Home owner 与开放 WebView 行为未改变。
- [x] 4.2 扩展 app-owned `LoginValidationEntryNavigationActions`，让前三个动作沿用现有类型安全导航、正式人脸和 NFC 动作启动现有非导出 Activity；以 fake/contract tests 验证五个动作映射正确、feature 不引用 Activity/厂商类型且组件导出配置未改变。
- [x] 4.3 扩展认证根栈测试，覆盖登录 success callback 与随后 session emission 仍只生成一个 Home graph、失败/加载不离开登录页、登出后旧受保护历史不可返回；运行对应 `:app:testDebugUnitTest`/`EntryNavigationInstrumentationTest` focused 用例验证。
- [x] 4.4 在 feature 入口与 app 适配均可构建后删除 `:app` 中 4 个旧登录 UI 文件、旧校验面板和已迁移独占资源，保留 `AgreementUrls`、导航、Activity 与厂商适配；以 `rg --files app/src/main/kotlin/com/ytone/longcare/features/login/ui` 无结果、旧面板路径不存在和 `:app:assembleDebug` 成功验证没有双份实现。

## 5. 收紧架构治理与目标平台测试

- [x] 5.1 新增可独立运行的 `verify_login_feature_boundary.sh`，阻止 app legacy 登录 UI 回流、feature import app `navigation`/`platform`/`presentation`/app `R`/Activity，以及 app 绕过 `feature.login.api` 导入 feature UI/VM；在真实工程正向运行并确认返回零。
- [x] 5.2 为登录边界守卫增加至少四组负向 fixture：app 重建登录 UI、feature 引用 app 资源、feature 直接启动 Activity、app 导入内部 UI/VM；逐组验证守卫返回非零并输出违规文件、规则和修复方向。
- [x] 5.3 更新 `verify_release_validation_entry.sh` 到 feature 路径，检查入口位于共享 main 源集、主 logo 长按存在、无 `BuildConfig.DEBUG` 限制且五个动作均由 app 回调执行；运行其自验证 fixture 与 `bash scripts/quality/verify_release_validation_entry.sh .` 确认正负路径。
- [x] 5.4 从 `legacy_feature_files_allowlist.txt` 精确删除 4 个登录 UI 路径，将 focused 登录守卫接入 `verify_architecture_boundaries.sh`、`preflight_local.sh` 与 `quality_gate_registry.json`，并运行 legacy allowlist fixture、架构守卫和 registry 校验确认没有放宽阈值/豁免。
- [x] 5.5 调整 `affected-modules.sh`，确保 `feature/login` 主源码或测试变化会执行 feature compile/unit/lint 并触发所需 instrumentation，而不是只验证 `:app`；扩展脚本 fixture 覆盖 app 登录装配、feature UI 与质量脚本三类 changed-path 计划。
- [x] 5.6 将 feature-owned 登录 UI tests 从 app instrumentation selector 中分离，为 `:feature:login` 配置 API 36（及非阻断 API 37）可运行目标并更新 `target_platform_test_matrix.properties`、矩阵验证脚本和 `.github/workflows/android-ci.yml`，使 app 与 feature class 分别由正确 test APK 执行；运行矩阵/工作流守卫并至少构建两模块 androidTest APK 验证不存在运行时 class-not-found。

## 6. 同步长期文档并完成集成验证

- [x] 6.1 更新 `docs/architecture/system-overview.md`、`dependency-rules.md`、`ui-and-screen-map.md`、`roadmap-and-open-gaps.md` 和 `ci-quality-gates.md`，记录 Login UI 所有权、app-owned route/WebView/Activity、五动作边界、测试归属与 Navigation 2/厂商 AAR 非目标；运行文档链接检查及 `preflight_local.sh --local-fast` 验证一致。
- [x] 6.2 运行登录治理 focused 集合：登录边界 fixture、Release validation fixture、legacy allowlist fixture、`verify_architecture_boundaries.sh .`、`verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare .` 和目标平台矩阵 fixture，确认所有正向检查通过且负向 fixture 稳定失败。
- [x] 6.3 运行 `./gradlew --no-daemon :feature:login:testDebugUnitTest :feature:login:lintDebug :feature:login:compileDebugAndroidTestKotlin :app:testDebugUnitTest`，确认登录状态、协议链接、反馈、五动作、根栈与资源检查全部通过。
- [x] 6.4 运行 `bash scripts/quality/verify_release_validation_entry.sh .`、`./gradlew --no-daemon :app:lintDebug :app:assembleDebug` 和 `bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt`，确认普通 Android CI 主路径无新增问题。
- [x] 6.5 运行 `bash scripts/quality/preflight_local.sh --full`，确认 Kotlin、模块边界、单测、依赖与构建综合门禁通过，且没有新增 ignore、豁免、host 限制或签名 fallback。
- [x] 6.6 使用 Android CLI 在 API 36 模拟器部署并运行 app/feature instrumentation，验证验证码/登录、协议确认与动态/兜底链接、发送成功聚焦、旋转/重建、长按五项面板以及登录成功单次进入 Home；通过 `android layout`/截图保留可观察证据，正式人脸和 NFC 在可用真机补充组件启动 smoke test。
- [x] 6.7 运行 `bash scripts/quality/preflight_local.sh --release`，确认本 change 未新增生产阻断，同时 QLZ 配置/弱 TLS、腾讯人脸 16 KB 对齐与 consumer rules 等既有厂商问题仍按预期 fail-closed，且未被修改、绕过或误报为已解决。
