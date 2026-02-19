# LongCare 架构与代码质量优化清单（执行版）

更新时间：2026-02-18

## 1. 项目现状评估（基线）

- 模块结构已建立：`app + core/* + feature/*`。
- 业务重心仍在 `app/features`：
  - `app/features`：127 个 Kotlin 文件，约 19,291 行
  - `feature/*`：64 个 Kotlin 文件，约 5,190 行
- 本轮已完成：
  - `T1`：迁移映射文档（`docs/refactor/module-migration-map.md`）
  - `T3`：数据库与 DAO 下沉到 `core:data`
  - `T4`：`core:model` 纯化（移除 Room 注解与 Android 平台类型）
  - `T5`：`UnusedResources` 清理（当前 lint 无该类告警）
- 质量状态：
  - `:core:data:compileDebugKotlin`、`:app:compileDebugKotlin` 已通过
  - lint 现状：`0 errors, 11 warnings`（主要来自三方依赖 AAR/JAR）

## 2. 核心问题（按影响排序）

1. **业务归属不一致（高）**  
   `feature/*` 与 `app/features/*` 并存，入口与实现分裂，迁移收益未完全释放。
2. **层间边界仍有污染（高）**  
   `core:model` 纯化已完成，后续重点转为持续防回归与边界守护。
3. **app 壳层化不足（高）**  
   `theme/shared.vm/MainActivity` 耦合阻塞 feature UI 下沉。
4. **网络/数据职责仍部分集中在 app（中高）**  
   `app/api` 已迁至 `core:data`，但部分 repository 与网络封装仍在 app。
5. **外部依赖质量告警（中）**  
   16KB 对齐、consumer rules、TLS trust manager 等警告来自第三方库。

## 3. 优化任务清单（优先级 + 验收标准）

## P0（优先执行，建议 1~2 周）

- [x] `P0-1` 建立重叠模块迁移映射  
  验收：有 owner、阻塞项、执行顺序（已完成）。

- [x] `P0-2` `core:data` 接管数据库基础设施  
  验收：`LongCareDatabase + DAO` 已从 `app` 迁出并可编译（已完成）。

- [x] `P0-3` 清理确定性低价值告警（`UnusedResources`）  
  验收：lint 报告无 `UnusedResources`（已完成）。

- [~] `P0-4` 特性单源化（T2）  
  任务：
  - 抽离 `app/theme` 到 `core:ui`（已完成）
  - 抽离 `app/shared/vm` 到非 app 模块（已迁至 `core:ui`）
  - 为 `servicecountdown` 建立 `AppLauncher` 抽象，移除对 `MainActivity` 的直接引用（已完成）  
  验收：`login/home/identification/photoupload/servicecountdown` UI 实现归属单一模块，`app` 仅保留装配与导航。

- [x] `P0-5` `core:model` 纯化（T4）  
  任务：
  - 逐步移除 `core:model` 的 Room 注解实体依赖（改为纯模型或转移到 data 层模型，已完成）
  - 将 `Uri/Parcelable` 从核心模型中隔离到平台层或导航层适配类型（已完成）  
  验收：`core:model` 不依赖 `androidx.room` 与 Android 平台类型（已达成）。

- [x] `P0-6` 网络接口归位  
  任务：
  - 将 `app/api` 接口迁至 `core:data`（已完成）
  - 安全网络封装分批下沉（已完成：`AesKeyTag`、`RequestInterceptor`、`ResponseDecryptInterceptor`、`ResponseProcessor`、`SystemConfigResponseProcessor`）
  - app 仅保留注入装配，不定义业务 API 协议  
  验收：`app/api` 清空或仅保留壳层兼容薄封装。

## P1（建议 2~4 周）

- [~] `P1-1` 仓储分层收敛  
  本轮已完成：
  - `UnifiedOrderRepository` 下沉至 `core:data`
  - `ImageRepository` 下沉至 `core:data`
  - `LocationUploadQueueRepositoryImpl` 下沉至 `core:data`
  - `OrderMapper` 下沉至 `core:data`
  - `AppEventBus`、`safeApiCall`、`safeTencentApiCall` 下沉至 `core:common`
  - `DefaultUserSessionRepository` 下沉至 `core:data`
  - `IdentificationRepositoryImpl` 下沉至 `core:data`
  - `LocationRepositoryImpl` 下沉至 `core:data`
  - `LoginRepositoryImpl` 下沉至 `core:data`
  - `OrderRepositoryImpl` 下沉至 `core:data`
  - `ProfileRepositoryImpl` 下沉至 `core:data`
  - `SystemRepositoryImpl` 下沉至 `core:data`
  - `TencentFaceRepositoryImpl` 下沉至 `core:data`
  - `UserListRepositoryImpl` 下沉至 `core:data`
  - `AppDataStore` 限定符下沉至 `core:common` 并补齐 `core:data` DataStore 依赖
  - `RepositoryModule`（仓储 DI 绑定）从 `app` 下沉至 `core:data`
  - `CosRepositoryImpl` 与 `CosModule` 从 `app` 下沉至 `core:data`
  - 腾讯 COS 依赖迁移到 `core:data`，并移除 `app` 对该依赖的直接声明
  - 清理 `StorageModule` 中无引用的 `Gson` 提供器（消除隐式传递依赖导致的 KSP 失败）
  - `AppDataStore` 扩展与提供器从 `app` 下沉至 `core:data`（`AppDataStoreModule`）
  - 清理 `app` 内未使用的 `AppPrefs` / `OrderStorage` 限定符与对应 provider
  - `DeviceUtils` 从 `app` 下沉至 `core:common`
  - `DeviceIdStorage` 限定符下沉至 `core:common`，`app` 中仅保留 provider
  - `core:common` 补充 `window` 依赖以承载 `WindowMetricsCalculator`
  - `StorageModule` 从 `app` 下沉至 `core:common`（设备 ID 存储 provider 归位）
  - `DatabaseModule` 从 `app` 下沉至 `core:data`（Room DB/DAO provider 归位）
  - `ResponseProcessorModule` 与 `NetworkBridgeModule` 从 `app` 下沉至 `core:data`
  - `RequestCryptoProviderImpl`、`RequestDeviceInfoProviderImpl`、`ThirdKeyDecryptorImpl` 从 `app` 下沉至 `core:data`
  - `common/security/*` 与 `ThirdKeyDecryptUtils` 从 `app` 下沉至 `core:common`（补齐 `core:common` 的 `moshi` 依赖）
  - `NetworkModule` 拆分：网络基础 provider 下沉至 `core:data`（`NetworkDataModule`），`app` 仅保留 flavor 相关 `OkHttpClient` 组装（`AppNetworkModule`）
  - `Qualifiers`（`TencentFaceRetrofit` / `DefaultOkHttpClient`）从 `app` 下沉至 `core:data`
  - `core:data` 补齐 `okhttp-logging-interceptor` 与 `retrofit-converter-moshi` 依赖
  - `UnitJsonAdapter` / `UriJsonAdapter` 从 `app` 下沉至 `core:common`（`common/json`），并同步更新 `JsonExtensions` 引用
  - `NetworkDataModule` 的 `Moshi` 配置补齐 `Unit/Uri/KotlinJsonAdapterFactory`，保持原行为一致
  - 引入 `FlavorInterceptorApplier`（`core:common`）并将 `OkHttpClient` provider 迁回 `core:data`；`app` 仅保留 flavor 拦截器实现绑定（`AppFlavorInterceptorApplier` + `AppFlavorInterceptorModule`）
  - `ImageLoadingModule` 从 `app` 下沉至 `core:ui`，并改为通过 `RuntimeConfigProvider.isDebug` 控制日志行为（避免 `core:ui` 反向依赖 `app` BuildConfig）
  - `core:ui` 补齐 `coil` 依赖以承载全局 `ImageLoader` provider
  - `SystemConfigManager` 与 `FaceVerificationConfigModule` 从 `app` 下沉至 `core:data`；`FaceVerificationManager` 改为依赖 `RuntimeConfigProvider.isDebug`（去除 `BuildConfig` 直连）
  - `RandomUtils` 从 `app` 下沉至 `core:common`
  - `core:data` 与 `core:common` 补齐 Hilt/KSP 编译配置（`dagger.hilt` plugin + `ksp(hilt-compiler)`），修复下沉后 DI 聚合缺失
  - 编译与依赖白名单校验通过（`:core:data`、`:app`）
  验收：形成 `feature -> domain <- data` 稳定依赖方向。

- [ ] `P1-2` 超大文件拆分（重点 UI/VM）  
  验收：核心超大文件降至可维护规模（建议 <= 250~300 行/文件，按组件拆分）。
 进度（2026-02-18）：已完成 128 个文件拆分与验证：
  - `ServiceCountdownViewModel`：`678 -> 181`
  - `PhotoProcessingViewModel`：`581 -> 156`
  - `CountdownNotificationManager`：`515 -> 158`
  - `NfcWorkflowViewModel`：`546 -> 181`
  - `ServiceTimeNotificationManager`：`439 -> 145`
  - `UnifiedOrderRepository`：`422 -> 182`
  - `CosRepositoryImpl`：`520 -> 219`
  - `FaceVerificationManager`：`433 -> 173`
  - `FaceCaptureAnalyzer`：`458 -> 151`
  - `ManualFaceCaptureScreen`：`498 -> 148`
  - `FaceVerificationWithAutoSignScreen`：`443 -> 187`
  - `NursingExecutionScreen`：`414 -> 65`
  - `FaceCaptureScreen`：`396 -> 195`
  - `ServiceCompleteScreen`：`394 -> 231`
  - `NursingScreen`：`394 -> 196`
  - `PhotoPicker`：`391 -> 264`
  - `AlarmRingtoneService`：`388 -> 264`
  - `EndServiceSelectionScreen`：`383 -> 217`
  - `IdentificationCard`：`358 -> 86`
  - `CameraScreenContent`：`368 -> 297`
  - `ManualFaceCaptureCameraReviewComponents`：`363 -> 129`
  - `LoginScreen`：`337 -> 159`
  - `HomeScreen`：`330 -> 196`
  - `PhotoUploadGridComponents`：`319 -> 65`
  - `NfcTestHelper`：`315 -> 293`
  - `NfcUtils`：`308 -> 242`
  - `ServiceHoursScreen`：`303 -> 251`
  - `NursingExecutionComponents`：`298 -> 252`
  - `CameraScreenContent`：`297 -> 267`
  - `FaceCaptureScreenLayout`：`296 -> 231`
  - `SelectServiceScreen`：`283 -> 171`
  - `NfcTestHelper`（第二轮）：`293 -> 235`
  - `CountdownAlarmActivity`：`280 -> 169`
  - `AppUpdateDialog`：`279 -> 41`
  - `ServiceCountdownScreen`：`279 -> 205`
  - `TimeUtils`：`279 -> 195`
  - `FaceCaptureViewModel`：`278 -> 235`
  - `AppNavigation`：`272 -> 79`
  - `FaceVerificationWithAutoSignComponents`：`271 -> 128`
  - `ManualFaceCaptureViewModel`：`269 -> 241`
  - `NavigationRoutes`：`260 -> 163`
  - `PhotoUploadGridItemComponents`：`267 -> 168`
  - `CameraScreenContent`：`267 -> 239`
  - `NfcWorkflowScreen`：`265 -> 246`
  - `PhotoPicker`：`264 -> 220`
  - `AlarmRingtoneService`：`264 -> 197`
  - `MainDashboardOrdersSection`：`260 -> 121`
  - `NfcScanWorkflowDelegate`：`257 -> 222`
  - `AppUpdateDialogContent`：`256 -> 167`
  - `AppNavGraphsServiceFlow`：`255 -> 211`
  - `ManualFaceCapturePhotoReviewComponents`：`255 -> 203`
  - `NursingExecutionComponents`：`252 -> 221`
  - `ServiceHoursScreen`：`251 -> 230`
  - `ServiceOrdersListScreen`：`250 -> 199`
  - `NfcWorkflowScreen`：`246 -> 186`
  - `SelectDeviceScreen`：`243 -> 207`
  - `IdentificationCardComponents`：`243 -> 43`
  - `PhotoUploadScreen`：`241 -> 135`
  - `NfcUtils`：`242 -> 166`
  - `ManualFaceCaptureViewModel`：`241 -> 239`
  - `CameraScreenContent`：`239 -> 207`
  - `NfcOrderWorkflowDelegate`：`239 -> 210`
  - `ManualFaceCaptureViewModel`：`239 -> 156`
  - `NfcTestHelper`：`235 -> 194`
  - `FaceCaptureViewModel`：`235 -> 119`
  - `NfcManager`：`235 -> 106`
  - `ProfileScreenComponents`：`233 -> 152`
  - `ServiceCompleteScreen`：`231 -> 116`
  - `FaceCaptureScreenLayout`：`231 -> 73`
  - `ServiceHoursScreen`：`230 -> 154`
  - `NursingListComponents`：`230 -> 146`
  - `CameraScreenContentActions`：`226 -> 93`
  - `UserServiceRecordScreen`：`227 -> 105`
  - `CameraCaptureProcessor`：`229 -> 84`
  - `NursingExecutionComponents`：`221 -> 156`
  - `EndServiceSelectionComponents`：`222 -> 99`
  - `NfcScanWorkflowDelegate`：`222 -> 211`
  - `PhotoPicker`：`220 -> 142`
  - `EndServiceSelectionScreen`：`217 -> 110`
  - `CountdownAlarmDelegate`：`220 -> 168`
  - `MainDashboardHeaderCards`：`216 -> 108`
  - `LoginScreenContentSections`：`215 -> 167`
  - `ManualFaceCaptureDialogComponents`：`216 -> 154`
  - `IdentificationScreen`：`216 -> 93`
  - `AppNavGraphsServiceFlow`：`211 -> 16`
  - `SelectDeviceScreen`：`207 -> 126`
  - `IdentificationCardStatusComponents`：`210 -> 107`
  - `NfcOrderWorkflowDelegate`：`210 -> 189`
  - `NfcScanWorkflowDelegate`：`211 -> 157`
  - `AppNavGraphsServiceFlowRoutes`：`223 -> 139`
  - `ServiceCountdownScreen`：`205 -> 180`
  - `CameraScreenContent`：`207 -> 195`
  - `ManualFaceCapturePhotoReviewComponents`：`203 -> 184`
  - `ServiceOrdersListScreen`：`199 -> 53`
  - `StaticImageFaceDetector`：`198 -> 109`
  - `ServiceTimeNotificationScheduleDelegate`：`198 -> 168`
  - `NursingScreen`：`197 -> 122`
  - `AlarmRingtoneService`：`197 -> 144`
  - `HomeScreen`：`196 -> 165`
  - `TimeUtils`：`195 -> 192`
  - `FaceRecognitionGuideScreen`：`195 -> 87`
  - `FaceCaptureScreen`：`195 -> 156`
  - `CameraScreenContent`：`195 -> 145`
  - `ServiceCompleteComponents`：`195 -> 112`
  - `NfcTestHelper`：`194 -> 188`
  - `SelectServiceComponents`：`181 -> 110`
  - `BannerScreen`：`185 -> 126`
  - `NfcOrderWorkflowDelegate`：`189 -> 149`
  - `NfcTestScreen`：`189 -> 59`
  - `FaceVerificationParamAssembler`：`194 -> 92`
  - `FaceImageExtractor`：`193 -> 42`
  - `AppNavGraphsSupport`：`181 -> 18`
  - `CountdownForegroundService`：`189 -> 143`
  - `TimeUtils`：`192 -> 118`
  - `NfcTestHelper`：`188 -> 134`
  - `FaceVerificationWithAutoSignScreen`：`187 -> 171`
  - `NfcWorkflowScreen`：`186 -> 120`
  - `ServiceCountdownScreen`：`180 -> 158`
  - `FaceCaptureDialogs`：`183 -> 75`
  - `NfcWorkflowViewModel`：`184 -> 156`
  - `ManualFaceCapturePhotoReviewComponents`：`184 -> 59`
  - `FaceImageExtractorBitmapUtils`：`172 -> 129`
  - `CountdownEventTracker`：`177 -> 121`
  - `FaceVerificationViewModel`：`182 -> 136`
  - `CameraCaptureCallbackHandler`：`177 -> 86`
  - `FaceVerificationWithAutoSignScreen`：`171 -> 106`
  - `SelectServiceScreen`：`171 -> 115`
  - `IdentificationScreenScaffoldContent`：`170 -> 65`
  - `CountdownAlarmDelegate`：`168 -> 135`
  - `IdentificationScreenScaffoldSections`：`161 -> 78`
  - `CountdownAlarmDelegateHelpers`：`176 -> 58`
  - `HomeScreenContentSections`：`168 -> 93`
  - `SelectServiceScreenSections`：`164 -> 81`

- [ ] `P1-3` 测试补齐  
  验收：迁移模块具备基础单测，关键业务链路有集成回归。

- [ ] `P1-4` 质量门禁固化  
  验收：PR 必经 `compile + lint + unit test + 依赖边界校验`，不通过不可合并。

## P2（持续治理）

- [ ] `P2-1` 三方依赖风险治理（lint warnings）  
  验收：关键三方依赖完成升级路径或风险豁免登记（含负责人与到期时间）。

- [ ] `P2-2` 构建性能治理  
  验收：按周追踪 `assemble/compile/test` 耗时分位值，持续下降。

## 4. 推荐执行顺序

1. 完成 `P0-4`（解除 UI 迁移阻塞）。
2. 并行推进 `P0-5`（模型纯化）与 `P0-6`（网络归位）。
3. 进入 `P1` 做边界收敛、拆分与测试补齐。
4. `P2` 持续化，纳入每周治理节奏。
