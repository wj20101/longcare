# 依赖与架构规则

最后核对：2026-08-31

本文同时记录“当前允许的依赖”和“继续迁移的目标”。两者不能混写：当前 `:app` 仍有业务实现，但新代码不得因此扩大 legacy 边界。

## 项目模块依赖

精确机器真相是 `scripts/quality/module_dependency_allowlist.txt`。当前允许的项目模块边如下：

| 源模块 | 允许依赖的项目模块 |
|---|---|
| `:app` | `:baselineprofile`、全部 `:core:*`、全部现有 `:feature:*` |
| `:baselineprofile` | 无 |
| `:core:model` | 无 |
| `:core:domain` | `:core:model` |
| `:core:common` | `:core:model` |
| `:core:data` | `:core:common`、`:core:domain`、`:core:model` |
| `:core:ui` | `:core:common`、`:core:domain`、`:core:model` |
| `:feature:home` | `:core:common`、`:core:domain`、`:core:model`、`:core:ui` |
| 其他现有 `:feature:*` | `:core:common`、`:core:domain`、`:core:model`；identification 额外允许 `:core:ui` |

新增或修改 Gradle 项目依赖时，必须同步检查实际 build 文件和 allowlist；不能只更新本文。

## 分层边界

### Model

- `:core:model` 是 Kotlin/JVM 模块，只保存跨层模型和值对象。
- 禁止引入 Android framework。
- 网络字段注解只在确有共享序列化契约时保留；不要把 Retrofit 接口或数据源实现放入 Model。

### Domain

- `:core:domain` 是 Kotlin/JVM 模块，保存 Repository/网关契约和跨 feature 的领域规则。
- 禁止 `android.*`、Activity/Context、Retrofit、Room、具体 SDK 类型和 `*Impl`。
- Feature 依赖抽象，不依赖 `:core:data`。

### Data

- `:core:data` 实现 Domain 契约，拥有 Retrofit、Room、COS、DataStore 相关数据访问和绑定。
- 网络专用 DTO、接口路径、参数注解和数据源应隐藏在 Data 边界内。
- Data 不得依赖 Feature/UI，也不得反向调用页面导航。

### Common 与 UI

- `:core:common` 是 Android library，可持有真正跨业务复用的基础能力；它不是无边界的杂物目录。
- `:core:ui` 只保存通用 UI、主题/组件和 UI 支撑，禁止网络、数据库或 Repository 实现。
- 只被一个 feature 使用的 helper 优先留在该 feature，不要为了“复用可能性”提前放入 Core。

### Feature

- Feature 负责一组紧密相关的用户能力、状态和 UI/编排。
- Feature 只能使用允许的 Core 抽象，禁止直接依赖 Data 实现或另一个 Feature 的 internal 实现。
- 公共入口保持最小；非契约声明使用 `internal` / `private`。
- 新业务 UI 应优先进入 `:feature:*`，不要继续扩大 `:app/features/**`。
- `:feature:login` 拥有登录 Compose UI、独占资源、ViewModel 和状态 effect；`:app` 只能使用 `feature.login.api` 下的 `LoginFeatureScreen`、`LoginFeatureActions`、`LoginAgreementLinks` 与五动作契约，不得 import login `ui`/`vm`。feature 不得引用 app `R`、navigation/platform/presentation、Activity、Intent 或厂商类型。
- `:feature:identification` 拥有身份主页面、页面资源、聚合屏幕状态和 effect；`:app` 只能使用 `features.identification.api` 下的 `IdentificationFeatureScreen`、`IdentificationActions` 与人脸 launcher 合约，不得 import feature 的 `ui`/`vm`。
- `:feature:home` 拥有 `HomeFeatureScreen`、内部 `HomeSharedViewModel`、loading/角色 split、护理三页以及 Dashboard/Nursing/Profile UI/VM/资源/tests；`:app` 只能从 `features.home.api` 使用 screen、不可变 config/actions、Sales/startup renderer 和 `HomeOrderStateSource`，不得 import Home internal UI/VM。Home 不得引用 app `R`、navigation/platform/presentation、Data 实现、Activity/Context/Intent 或厂商类型。
- Dashboard 的公司名称通过 `:core:domain` `CompanyNameProvider` 读取；`:core:data` `SystemConfigManager` 实现/绑定该契约。Feature 不得读取具体 preference/DataStore key 或直接依赖实现。

### App

- 目标职责是启动、根导航、DI 组装、Manifest 和 Android/厂商平台适配。
- 当前仍有大量 route-bound UI 和流程代码，因此 `:app` 对 Core/Data/Feature 的依赖是现实允许边，而不是鼓励新业务继续堆入壳层。
- `app/src/main/.../features/**` 受冻结目录和文件 allowlist 保护；当前精确快照为 181 个 Kotlin 文件。`verify_legacy_feature_file_allowlist.sh` 同时拒绝“实际文件不在 allowlist”和“allowlist 路径已不存在”，优先在现有允许文件内做小修复，新增能力迁往 Feature。
- `LoginRoute`、协议兜底、WebView 导航和五个验证入口的平台实现仍属于壳层；app adapter 负责启动现有不可导出 Activity，不能把 `Context`/`Intent` 或厂商类型传回 feature。
- `IdentificationRoute` 的类型安全注册、`SavedStateHandle` 结果桥接和 app-owned 厂商适配仍属于壳层；业务渲染、结果处理和状态机不得回流 route lambda。
- `HomeRoute`/`HomeGraphRoute`、`TodayOrderViewModel` owner、服务/Camera/WebView 导航、Sales renderer 和 startup reporting 仍属于壳层；app 只能把同一图级订单实例经 remembered `HomeOrderStateSource` 窄适配器交给 `HomeFeatureScreen`，不得创建第二份 Home 订单缓存。

## UI、状态与生命周期

1. ViewModel 负责状态编排，不直接写 Retrofit/Room 调用细节。
2. 可持续 UI 状态使用 `StateFlow`。
3. 导航、确认结果和用户可见错误等不可丢失动作必须保持到 UI 明确消费；`SharedFlow(replay = 0)` 只用于可丢失的实时信号。
4. ViewModel 不持有 Activity；Context 只通过明确的应用级抽象或平台网关使用。
5. 协程调度器通过 DI 注入。捕获异常时必须重新抛出 `CancellationException`。
6. Compose 收集 Flow 使用 lifecycle-aware API；Activity/Service/传感器/相机/NFC 资源必须在对应生命周期释放。
7. 面向用户的文案来自资源或可测试的文本抽象，避免在业务状态类散落硬编码字符串。

## 平台能力

- Service、通知、闹钟、安装器、NFC 前台调度、Activity Result 和第三方 SDK UI 由 app-owned gateway/controller 或明确的平台模块封装。
- 需要当前 UI Context 的腾讯人脸 SDK 由 `:app/platform/face` 启动；identification feature 只能通过带 request id 的 suspending launcher 合约交付请求和接收事件。
- 前台 Service 必须声明匹配用途的 `foregroundServiceType` 和权限，并从满足运行时前置条件的用户可见流程启动。
- Release 组件默认 `exported=false`；新增导出组件需要最小 intent surface、安全审查和 allowlist 更新。
- Debug-only mock、launcher 或诊断能力必须放在 debug source set；共享验证入口进入 main source set 时仍需在 Release 保持不可导出。
- 权限请求要有用途说明、拒绝恢复和从设置页返回后的重新检查，不能在 Application 无上下文地批量申请。

## 构建与第三方依赖基线

- `settings.gradle.kts` 的 `com.android.settings` 是 `minSdk 24`、`targetSdk 36`、`compileSdk 37` 的唯一来源；模块和 convention plugin 不得覆盖。JDK 21 与应用版本继续由 `constants.gradle.kts` 管理。
- 模块只启用源码实际需要的构建能力并声明直接使用的 API：`:feature:location` 不启用 Compose，只直接依赖 Core KTX、Lifecycle ViewModel、Coroutines Core、Hilt 与 AMap；`:feature:photoupload` 和 `:feature:servicecountdown` 只直接保留 ViewModel、Coroutines Core 与 Hilt；`:feature:identification` 保留真实使用的 Compose、CameraX、ML Kit、DataStore、OkHttp、Hilt 与 Coroutines Core。不得以整套 bundle、直接 Bugly、偶然传递依赖或另一个 Feature 的导出掩盖缺失依赖。
- 版本目录优先使用稳定、精确版本，禁止动态版本。alpha、beta、RC、snapshot、dev 和 compat 只能进入 `dependency_preview_allowlist.txt` 的精确别名/精确版本豁免，并必须填写 Owner、原因、验证范围和稳定退出版本。
- 当前唯一预览豁免是 `androidxBaselineProfile` 与 `androidxBenchmark` 的 `1.5.0-rc02`，分别受管。稳定且经 AGP 9.3 验证的 `1.5.x` 可用后，两项豁免及 `maxAgpVersion=false` 必须一起退出。
- 厂商 AAR 仍依赖 Jetifier，因此 `android.enableJetifier=true` 暂时保留。该事实直接阻断 AGP 10+；不得通过 suppression、修改 AAR、关闭生产能力或删除 Jetifier 来制造升级成功。
- Coil 与 kotlinx-datetime 当前稳定基线分别为 `3.6.0` 和 `0.8.0`。升级依赖时按单一依赖族独立解析、测试和回滚，不夹带业务重构。

## 数据、网络和文件

- Room schema 变更默认必须提交 schema JSON、显式 Migration 和迁移测试；[ADR-002](adr/ADR-002-user-storage-cold-cutover.md) 是已确认的限定例外，只允许 schema 不兼容时破坏性重建当前用户的独立数据库，不得删除其他用户文件。
- 复合身份 `companyId + accountId + userId` 通过 v1 摘要命名空间隔离 Room、DataStore 和文件；业务层只能使用当前 `scope + sessionEpoch + generation` lease，禁止裸 `user_<userId>` 文件名和全局用户业务 SharedPreferences。
- 设备级、用户级和会话级状态必须明确分类；冷切换不兼容任何 legacy 值，正常退出则保留当前用户 Room/DataStore/persistent 文件并清理秘密、session 文件和该 epoch 后台任务。
- 需要跨进程/重建恢复的任务使用 WorkManager 或持久状态，不能只依赖进程内事件。
- 用户相关 Worker、Alarm、PendingIntent 和通知身份必须包含 namespace、epoch、任务类型和业务 ID；仅用 `orderId` 作为 unique name、requestCode、data URI 或通知 ID 属于架构违规。
- Retrofit 接口只使用稳定的网络契约；方法、路径、注解和关键 JSON key 变更必须同步契约测试。
- 会话写入必须等待持久化完成，不能先报告登录/退出成功。
- 应用自有持久 JPEG 统一经过 `UnifiedImagePipeline` 和 `ImageProcessingPolicies`。
- 受管文件的删除责任要与数据库/业务所有权绑定，不能依赖每个 UI 调用方各自补偿。
- 厂商 secret 不得进入源码、资源、BuildConfig、日志或 APK；客户端只接收受限 token/临时凭据。

## 导航

- 路由使用 Kotlin Serialization 类型安全定义，并在 `:app/navigation` 组装。
- 跨页面优先传稳定 ID/轻量 key；当前订单路由使用 `OrderNavParams(orderId, planId)`，不要传完整订单对象。
- 相机、人脸等结果通过 `SavedStateHandle` 返回，并由接收页消费后清除。
- 路由行为变更必须同步[页面与路由地图](ui-and-screen-map.md)并增加对应导航/状态测试。
- Login/Home 认证根由 app-owned 协调器管理，切换时必须清除另一根，重复目标必须幂等；`AppNavHost` 与入口 renderer 测试 seam 保持 `internal`，不得升级为 feature 或生产公共 API。
- `TodayOrderViewModel` 只以 `HomeGraphRoute` 为 owner，缺少 graph 时立即失败；不得回退到当前目的页面 owner。`HomeSharedViewModel` 仅由 `:feature:home` 的公开 screen 内部创建，不得由 app route 查找或跨页面传递。
- `androidx.navigation:navigation-testing` 只允许通过 `androidTestImplementation` 引入，并与生产 Navigation Compose 使用同一 version catalog 版本。
- 当前保持 Navigation Compose `2.10.0`，version catalog 不引入 Navigation 3 制品。Navigation 3 迁移必须作为使用稳定 Nav3 的独立原子 change，不能与业务 API、targetSdk 或大规模模块搬迁混在同一改动中。
- Nav3 change 必须保持现有 focused suite 对动态 Login/Home 起点、隐私 gate、`popUpTo`/清栈、HomeGraph scope、重复导航、配置变化和进程恢复的等价覆盖，并补齐结果返回/消费及大对象 route 参数收敛后才能实施。

## 自动守卫

| 守卫 | 保护内容 |
|---|---|
| `verify_architecture_boundaries.sh` | rule-0 拒绝已退役 Placeholder、伪 FeatureEntry、SelectDevice 导航/UI 和旧更新弹窗回流；其余规则保护 Android-free Domain、Feature/Data 方向、ViewModel/调度器/文件规模、legacy 快照、身份/登录/Home 页面所有权及 instrumentation test APK 所有权 |
| `verify_legacy_feature_file_allowlist.sh` | 保证 `app/src/main/.../features/**` 实际 Kotlin 文件与 `legacy_feature_files_allowlist.txt` 双向一致，拒绝新增未允许文件和陈旧条目 |
| `verify_identification_feature_boundary.sh` | 禁止 app 身份 UI 回流、feature 引用 app navigation/platform/R，以及 app 绕过 identification 公开 API |
| `test_identification_feature_boundary.sh` | 用正向和三类负向 fixture 验证身份边界守卫输出规则、文件与修复方向 |
| `verify_login_feature_boundary.sh` | 禁止 app 登录 UI/校验面板回流、feature 引用 app 壳层/平台组件，以及 app 绕过 login 公开 API |
| `test_login_feature_boundary.sh` | 用正向和四类负向 fixture 验证登录边界守卫输出规则、文件与修复方向 |
| `verify_home_feature_boundary.sh` | 禁止 Home/Dashboard/Nursing/Profile UI/VM 回流 app、app 绕过 Home 公开 API，以及 Home 反向引用 app/Data/平台/导航/厂商实现 |
| `test_home_feature_boundary.sh` | 用真实工程和八组正负 fixture 验证 Home 边界，并要求失败输出包含文件、规则、允许 API 与修复方向 |
| `verify_user_storage_boundaries.sh` | 限制用户 Room/DataStore 创建位置，禁止全局 DAO/数据库句柄、裸用户文件名、无 scope 业务偏好和 orderId-only 后台身份 |
| `test_user_storage_boundaries.sh` | 用 7 组 shell fixture 验证上述门禁既允许 registry/factory，也会拒绝每类回退 |
| `verify_module_dependency_whitelist.sh` | Gradle 项目模块边 |
| `verify_module_api_visibility.sh` | 跨模块 API 和 internal 实现边界 |
| `check_new_files_guard.sh` | 在 changed-files 场景快速提示冻结 legacy feature 目录不得新增文件；完整快照由 rule-10 校验 |
| `verify_cancellation_guards.sh` | 敏感协程取消处理 |
| `verify_no_empty_catch_blocks.sh` | 禁止吞异常 |
| `verify_android_build_baseline.sh` | Settings Plugin SDK 单一来源、JDK/应用版本与 AGP/plugin 一致性，以及目标 Feature 的最小构建能力/直接依赖边界 |
| `test_android_build_governance.sh` | 构建治理的单一 fixture 入口；同时执行模块最小化负例、`test_affected_modules.sh` 的 changed-path 映射自测及其他既有构建治理 fixtures |
| `verify_dependency_policy.sh` | 稳定版优先、精确预览豁免、`maxAgpVersion=false` 关联和 Jetifier/AGP 10 阻断 |
| `verify_target_sdk_readiness.sh` | target 36/37 双状态政策及 Manifest adaptive 一致性 |
| `verify_target_platform_test_matrix.sh` | API 33 Profile、API 36 blocking smoke 与 API 37 readiness 分离，并校验 app/Home/login feature 选择器各归属正确 test APK |
| `verify_instrumentation_smoke_classes.sh` | smoke 选择器指向真实 `androidTest` 类；target matrix 的 app/Home/login 字段必须分别属于自己的 test APK source root |
| `verify_instrumentation_test_ownership.sh` | `instrumentation_test_modules.txt` 与实际非空 `src/androidTest` 双向一致，并要求每个 owner 显式配置 runner、runner 依赖和模块限定聚合 task |
| `test_instrumentation_test_ownership.sh` | 用 fake Gradle 和正负 fixtures 验证稳定顺序、遗漏/陈旧/重复/未知 owner、runner 契约及禁止根级 connected task |
| `run_connected_instrumentation_suite.sh` | 只从所有权清单生成 `:app`、`:core:data`、`:core:ui`、`:feature:home`、`:feature:identification`、`:feature:login` 的 connected task；不复制 Managed Device/选择器矩阵 |
| `verify_entry_navigation_contracts.sh` | Navigation Testing 仅测试可见、入口测试 seam 保持 `internal`、入口/Home/Sales focused 测试类完整 |
| `test_entry_navigation_contracts.sh` | 用正向和依赖泄漏、renderer 公开、测试类缺失负向 fixture 验证入口导航守卫 |
| `verify_tech_stack_baseline.sh` | 长期技术栈字段与可执行配置同步 |

本地快速检查：

```bash
bash scripts/quality/preflight_local.sh --local-fast
```

## 例外处理

架构例外必须同时满足：

1. PR 中写明现实原因、影响范围和回收条件。
2. 只修改最小必要 allowlist/预算，不使用宽泛通配规则。
3. 增加能防止例外继续扩大的测试或守卫。
4. 将长期有效的取舍写入 ADR；短期计划留在 Issue/PR，不再新增执行日志文档。

已有分层决策见 [ADR-001](adr/ADR-001-layer-boundary.md)。
