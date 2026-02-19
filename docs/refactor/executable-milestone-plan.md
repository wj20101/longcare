# LongCare 主体架构与代码质量优化可执行里程碑计划（8周）

更新时间：2026-02-18  
适用范围：`/Users/yuyingui/StudioProjects/longcare`

## 1. 目标与完成定义

### 总目标

在 8 周内完成“以模块化架构为核心”的工程治理，优先解决安全、CI 稳定性、架构边界和大文件维护性问题，再完成业务迁移、测试补齐与性能收口。

### 8 周完成定义（Done）

1. `app` 层只保留壳层与导航，不再承载主体业务实现。
2. `app/features` 代码量较当前下降 >= 50%。
3. CI 取消率 < 20%，非取消成功率 >= 90%。
4. 每个 `core/*` 与 `feature/*` 至少具备基础单测。
5. `keystore.jks` 从仓库移除并完成密钥轮换。
6. 发布门禁生效：静态检查、测试、构建不达标禁止合入/发布。

## 2. 角色分工（Owner 角色）

- `ARCH`（架构负责人）：模块边界、依赖规则、迁移方案把控
- `PLAT`（平台/CI 负责人）：CI 稳定性、质量门禁、构建性能
- `SEC`（安全/发布负责人）：密钥治理、发布安全基线
- `DEV-F`（业务开发负责人）：feature 迁移、超大文件拆分、业务回归
- `QA`（测试负责人）：测试策略、关键链路自动化、回归覆盖
- `TL`（技术负责人）：里程碑推进、风险决策、跨角色协调

## 3. 里程碑总览（按周）

| 周次 | 里程碑主题 | 主要 Owner | 周退出标准 |
|---|---|---|---|
| W1 | 安全与门禁底座 | `SEC` `PLAT` `ARCH` | 完成密钥风险清理方案、CI 基线与门禁入口统一 |
| W2 | CI 稳定 + 架构硬约束 | `PLAT` `ARCH` | CI 取消率明显下降，依赖违规可阻断 |
| W3 | 迁移 Wave1（NFC/倒计时） | `DEV-F` `ARCH` | Wave1 主链路迁移落地，`app/features` 明显下降 |
| W4 | Wave1 收口 + 测试补齐 | `DEV-F` `QA` | Wave1 回归稳定，模块单测基线建立 |
| W5 | 迁移 Wave2（登录/首页/共享） | `DEV-F` `ARCH` | `app` 壳层化推进，业务下沉到 feature/core |
| W6 | 架构收敛 + 质量扩面 | `ARCH` `QA` | 依赖方向固定，关键链路集成测试可跑通 |
| W7 | 性能优化与可观测 | `PLAT` `DEV-F` | 构建波动收敛，baseline/profile 指标改善 |
| W8 | 发布就绪与治理固化 | `TL` `PLAT` `QA` | 发布门禁闭环，文档与质量看板可持续运行 |

## 4. 周执行计划（任务清单）

## W1（安全与门禁底座）

- [ ] `A01` 移除 `keystore.jks` 并轮换证书（`SEC`，已完成移除，待轮换）  
  验证：仓库历史与当前分支均不再暴露可用密钥；发布签名验证通过。
- [x] `A05` 统一质量入口命令（`PLAT`）  
  验证：本地与 CI 使用同一入口脚本，输出统一报告。
- [x] `A02` CI 稳定性基线采集（`PLAT`）  
  验证：产出取消率、成功率、失败原因分布报告（作为后续对照）。
- [x] `A03` 模块依赖白名单规则初版（`ARCH`）  
  验证：违规依赖在 CI 中可直接失败。

## W2（CI 稳定 + 架构硬约束）

- [ ] `A02` 落地 CI 并发/取消优化（`PLAT`）  
  验证：取消率周均下降到 <= 35%，关键流水线无随机取消风暴。
- [x] `A03` 强化壳层约束（`ARCH`）  
  验证：`scripts/quality/verify_architecture_boundaries.sh` 已强制冻结 `app/features` 新增文件，且统一质量入口 `run_quality_gate.sh` 本地验证通过。
- [ ] `A04` Top 超大文件拆分批次 1（`DEV-F`）  
  验证：首批 4 个 >400 行文件拆至 <=250 行且测试通过。
  进度（2026-02-18）：已完成 `ServiceCountdownViewModel` 第一轮拆分（`678 -> 384`），抽出：
  - `ServiceCountdownTimerDelegate`
  - `ServiceCountdownOrderStatePollingDelegate`
  - `ServiceCountdownImageDelegate`
  并通过 `:feature:servicecountdown:compileDebugKotlin` 与 `ServiceCountdownViewModelTest`。
  进度（2026-02-18，追加）：完成 `ServiceCountdownViewModel` 第二轮收口（`384 -> 181`），并完成 `PhotoProcessingViewModel` 拆分（`581 -> 156`），抽出：
  - `PhotoTaskQueueDelegate`
  - `PhotoUploadDelegate`
  验证通过：
  - `:feature:photoupload:compileDebugKotlin`
  - `:app:compileDebugKotlin`
  - `:app:compileDebugUnitTestKotlin`
  进度（2026-02-18，再追加）：完成 `CountdownNotificationManager` 拆分（`515 -> 158`），抽出：
  - `CountdownAlarmDelegate`
  - `CountdownNotificationUiDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  - `:app:testDebugUnitTest --tests "com.ytone.longcare.features.countdown.manager.CountdownNotificationManagerIntentExtrasTest"`
  - `:app:testDebugUnitTest --tests "com.ytone.longcare.features.countdown.receiver.DismissAlarmReceiverTest"`
  进度（2026-02-18，再追加）：完成 `NfcWorkflowViewModel` 拆分（`546 -> 181`），抽出：
  - `NfcOrderWorkflowDelegate`
  - `NfcScanWorkflowDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  - `:app:compileDebugUnitTestKotlin`
  当前状态：批次 1 已完成 4/4（达成周目标）。

## W3（迁移 Wave1：NFC/倒计时）

- [ ] `A06` NFC/倒计时业务下沉至 `feature/*`（`DEV-F` `ARCH`）  
  验证：对应业务从 `app/features` 移除，功能回归通过。
- [ ] `A11` Wave1 模块级单测补齐（`QA` `DEV-F`）  
  验证：迁移模块具备基础单测且纳入 CI。
- [ ] `A13` 错误处理统一（Wave1 范围）（`DEV-F`）  
  验证：关键错误路径采用统一 Result/错误模型。

## W4（Wave1 收口 + 测试补齐）

- [ ] `A04` Top 超大文件拆分批次 2（`DEV-F`）  
  验证：累计 8 个超大文件完成拆分。
  进度（2026-02-18）：完成 `ServiceTimeNotificationManager` 拆分（`439 -> 145`），抽出：
  - `ServiceTimeNotificationScheduleDelegate`
  - `ServiceTimeNotificationRecordDelegate`
  - `ServiceTimeNotificationUiDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  - `:app:testDebugUnitTest --tests "com.ytone.longcare.features.service.ServiceTimeNotificationManagerTest"`
  - `:app:testDebugUnitTest --tests "com.ytone.longcare.features.service.ServiceTimeNotificationManagerBasicTest"`
  进度（2026-02-18，追加）：完成 `UnifiedOrderRepository` 拆分（`422 -> 182`），抽出：
  - `OrderInfoMemoryCache`
  - `OrderRoomSyncDelegate`
  验证通过：
  - `:core:data:compileDebugKotlin`
  - `:app:testDebugUnitTest --tests "com.ytone.longcare.data.repository.UnifiedOrderRepositoryTest"`
  进度（2026-02-18，追加）：完成 `CosRepositoryImpl` 拆分（`520 -> 219`），抽出：
  - `CosConfigCache`
  - `CosDynamicCredentialProvider`
  - `CosObjectOperationDelegate`
  验证通过：
  - `:core:data:compileDebugKotlin`
  - `:core:data:testDebugUnitTest --tests "com.ytone.longcare.data.cos.repository.CosRepositoryImplTest"`
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceVerificationManager` 拆分（`433 -> 173`），抽出：
  - `FaceVerificationParamAssembler`
  验证通过：
  - `:app:compileDebugKotlin`
  - `:app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.FaceVerificationManagerCancellationTest"`
  - `:app:testDebugUnitTest --tests "com.ytone.longcare.common.utils.FaceVerificationManagerInputValidationTest"`
  - `:app:testDebugUnitTest --tests "com.ytone.longcare.architecture.FaceSdkBoundaryTest"`
  进度（2026-02-18，追加）：完成 `FaceCaptureAnalyzer` 拆分（`458 -> 151`），抽出：
  - `FaceCaptureQualityEvaluator`
  - `FaceImageExtractor`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ManualFaceCaptureScreen` 拆分（`498 -> 148`），抽出：
  - `ManualFaceCaptureCameraReviewComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceVerificationWithAutoSignScreen` 拆分（`443 -> 187`），抽出：
  - `FaceVerificationWithAutoSignComponents`
  - `FaceVerificationPhotoProcessor`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NursingExecutionScreen` 拆分（`414 -> 65`），抽出：
  - `NursingExecutionComponents`
  - `NursingExecutionPreviews`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceCaptureScreen` 拆分（`396 -> 195`），抽出：
  - `FaceCaptureScreenLayout`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ServiceCompleteScreen` 拆分（`394 -> 231`），抽出：
  - `ServiceCompleteComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NursingScreen` 拆分（`394 -> 196`），抽出：
  - `NursingListComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `PhotoPicker` 拆分（`391 -> 264`），抽出：
  - `PhotoPickerLaunchActions`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `AlarmRingtoneService` 拆分（`388 -> 264`），抽出：
  - `AlarmRingtonePlaybackController`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `EndServiceSelectionScreen` 拆分（`383 -> 217`），抽出：
  - `EndServiceSelectionComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `IdentificationCard` 拆分（`358 -> 86`），抽出：
  - `IdentificationCardComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CameraScreenContent` 拆分（`368 -> 297`），抽出：
  - `CameraScreenContentActions`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ManualFaceCaptureCameraReviewComponents` 拆分（`363 -> 129`），抽出：
  - `ManualFaceCapturePhotoReviewComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `LoginScreen` 拆分（`337 -> 159`），抽出：
  - `LoginScreenContentSections`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `HomeScreen` 拆分（`330 -> 196`），抽出：
  - `HomeScreenContentSections`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `PhotoUploadGridComponents` 拆分（`319 -> 65`），抽出：
  - `PhotoUploadGridItemComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcTestHelper` 拆分（`315 -> 293`），抽出：
  - `NfcTestDialogContent`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcUtils` 拆分（`308 -> 242`），抽出：
  - `NfcNdefUtils`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ServiceHoursScreen` 拆分（`303 -> 251`），抽出：
  - `ServiceHoursScreenPreviews`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NursingExecutionComponents` 拆分（`298 -> 252`），抽出：
  - `NursingExecutionStateScreens`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CameraScreenContent` 拆分（`297 -> 267`），动作逻辑下沉至：
  - `CameraScreenContentActions`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceCaptureScreenLayout` 拆分（`296 -> 231`），抽出：
  - `FaceCaptureScreenChrome`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `SelectServiceScreen` 拆分（`283 -> 171`），抽出：
  - `SelectServiceScreenSections`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcTestHelper` 第二轮拆分（`293 -> 235`），抽出：
  - `NfcTestHelperInternals`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CountdownAlarmActivity` 拆分（`280 -> 169`），抽出：
  - `CountdownAlarmScreenContent`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `AppUpdateDialog` 拆分（`279 -> 41`），抽出：
  - `AppUpdateDialogContent`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ServiceCountdownScreen` 拆分（`279 -> 205`），抽出：
  - `ServiceCountdownScreenScaffold`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `TimeUtils` 拆分（`279 -> 195`），抽出：
  - `TimeDisplayDateDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceCaptureViewModel` 拆分（`278 -> 235`），抽出：
  - `FaceCaptureStorageDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `AppNavigation` 拆分（`272 -> 79`），抽出：
  - `AppNavigationActions`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceVerificationWithAutoSignComponents` 拆分（`271 -> 128`），抽出：
  - `FaceVerificationStepCard`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ManualFaceCaptureViewModel` 拆分（`269 -> 241`），抽出：
  - `ManualFaceCaptureStorageDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NavigationRoutes` 拆分（`260 -> 163`），抽出：
  - `NavigationServiceRoutes`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `PhotoUploadGridItemComponents` 拆分（`267 -> 168`），抽出：
  - `PhotoUploadPreviewDialog`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CameraScreenContent` 拆分（`267 -> 239`），抽出：
  - `CameraWatermarkOverlay`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcWorkflowScreen` 拆分（`265 -> 246`），抽出：
  - `NfcWorkflowDebugSection`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `PhotoPicker` 拆分（`264 -> 220`），抽出：
  - `PhotoPickerModels`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `AlarmRingtoneService` 拆分（`264 -> 197`），抽出：
  - `AlarmRingtoneActivityLauncher`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `MainDashboardOrdersSection` 拆分（`260 -> 121`），抽出：
  - `MainDashboardOrderCards`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcScanWorkflowDelegate` 拆分（`257 -> 222`），抽出：
  - `NfcActivityAndLocationDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `AppUpdateDialogContent` 拆分（`256 -> 167`），抽出：
  - `AppUpdateDialogActions`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `AppNavGraphsServiceFlow` 拆分（`255 -> 211`），抽出：
  - `AppNavGraphsServiceOrdersList`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ManualFaceCapturePhotoReviewComponents` 拆分（`255 -> 203`），抽出：
  - `ManualFaceCaptureFaceSelectionItem`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NursingExecutionComponents` 拆分（`252 -> 221`），抽出：
  - `NursingExecutionConfirmButton`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ServiceHoursScreen` 拆分（`251 -> 230`），抽出：
  - `ServiceHoursProjectSelection`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ServiceOrdersListScreen` 拆分（`250 -> 199`），抽出：
  - `ServiceOrderStateTags`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcWorkflowScreen` 拆分（`246 -> 186`），抽出：
  - `NfcWorkflowLayoutSections`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `SelectDeviceScreen` 拆分（`243 -> 207`），抽出：
  - `SelectDeviceScreenPreviews`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `IdentificationCardComponents` 拆分（`243 -> 43`），抽出：
  - `IdentificationCardStatusComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `PhotoUploadScreen` 拆分（`241 -> 135`），抽出：
  - `PhotoUploadScreenContent`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcUtils` 拆分（`242 -> 166`），抽出：
  - `NfcIntentDataUtils`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ManualFaceCaptureViewModel` 拆分（`241 -> 239`），抽出：
  - `ManualFaceCaptureFacePipelineDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CameraScreenContent` 拆分（`239 -> 207`），抽出：
  - `CameraContentOverlayScaffold`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcOrderWorkflowDelegate` 拆分（`239 -> 210`），抽出：
  - `NfcOrderCompletionDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ManualFaceCaptureViewModel` 拆分（`239 -> 156`），抽出：
  - `ManualFaceCaptureStateTransitions`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcTestHelper` 拆分（`235 -> 194`），抽出：
  - `NfcTestListeningDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceCaptureViewModel` 拆分（`235 -> 119`），抽出：
  - `FaceCaptureStateDelegate`
  - `FaceCaptureBitmapCacheDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcManager` 拆分（`235 -> 106`），抽出：
  - `NfcForegroundDispatchDelegate`
  - `NfcEnableDialogDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ProfileScreenComponents` 拆分（`233 -> 152`），抽出：
  - `ProfileOptionsComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ServiceCompleteScreen` 拆分（`231 -> 116`），抽出：
  - `ServiceCompleteModels`
  - `ServiceCompleteScreenPreviews`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceCaptureScreenLayout` 拆分（`231 -> 73`），抽出：
  - `FaceCaptureBottomPanel`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ServiceHoursScreen` 拆分（`230 -> 154`），抽出：
  - `ServiceHoursRecordComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NursingListComponents` 拆分（`230 -> 146`），抽出：
  - `NursingListPreviews`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CameraScreenContentActions` 拆分（`226 -> 93`），抽出：
  - `CameraSwitchActions`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `UserServiceRecordScreen` 拆分（`227 -> 105`），抽出：
  - `UserServiceRecordComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CameraCaptureProcessor` 拆分（`229 -> 84`），抽出：
  - `CameraCaptureCallbackHandler`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NursingExecutionComponents` 拆分（`221 -> 156`），抽出：
  - `NursingExecutionInfoCard`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `EndServiceSelectionComponents` 拆分（`222 -> 99`），抽出：
  - `EndServiceSelectionActionComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcScanWorkflowDelegate` 拆分（`222 -> 211`），抽出：
  - `NfcScanWorkflowHelpers`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `PhotoPicker` 拆分（`220 -> 142`），抽出：
  - `PhotoPickerCameraLaunchers`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `EndServiceSelectionScreen` 拆分（`217 -> 110`），抽出：
  - `EndServiceSelectionSuccessContent`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CountdownAlarmDelegate` 拆分（`220 -> 168`），抽出：
  - `CountdownAlarmDelegateHelpers`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `MainDashboardHeaderCards` 拆分（`216 -> 108`），抽出：
  - `MainDashboardCardPrimitives`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `LoginScreenContentSections` 拆分（`215 -> 167`），抽出：
  - `LoginNfcTestButtons`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ManualFaceCaptureDialogComponents` 拆分（`216 -> 154`），抽出：
  - `ManualFaceCaptureFullScreenPreviewDialog`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `IdentificationScreen` 拆分（`216 -> 93`），抽出：
  - `IdentificationScreenScaffoldContent`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `AppNavGraphsServiceFlow` 拆分（`211 -> 16`），抽出：
  - `AppNavGraphsServiceFlowRoutes`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `SelectDeviceScreen` 拆分（`207 -> 126`），抽出：
  - `SelectDeviceComponents`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `IdentificationCardStatusComponents` 拆分（`210 -> 107`），抽出：
  - `IdentificationCardStatusPrimitives`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcOrderWorkflowDelegate` 拆分（`210 -> 189`），抽出：
  - `NfcOrderWorkflowEndOrderExecutor`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcScanWorkflowDelegate` 拆分（`211 -> 157`），抽出：
  - `NfcScanLocationActivationWorkflow`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `AppNavGraphsServiceFlowRoutes` 拆分（`223 -> 139`），抽出：
  - `AppNavGraphsServiceFlowImageRoutes`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ServiceCountdownScreen` 拆分（`205 -> 180`），抽出：
  - `ServiceCountdownDialogsHost`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CameraScreenContent` 拆分（`207 -> 195`），抽出：
  - `CameraDelayMode`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ManualFaceCapturePhotoReviewComponents` 拆分（`203 -> 184`），抽出：
  - `ManualFaceCapturePhotoReviewDialogs`
  - `ManualFaceCapturePhotoReviewActions`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ServiceOrdersListScreen` 拆分（`199 -> 53`），抽出：
  - `ServiceOrdersListScreenLayout`
  - `ServiceOrderItemCard`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `StaticImageFaceDetector` 拆分（`198 -> 109`），抽出：
  - `StaticImageFaceQualityUtils`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ServiceTimeNotificationScheduleDelegate` 拆分（`198 -> 168`），抽出：
  - `ServiceTimeFallbackScheduler`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NursingScreen` 拆分（`197 -> 122`），抽出：
  - `NursingDateTabsRow`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `AlarmRingtoneService` 拆分（`197 -> 144`），抽出：
  - `AlarmRingtoneForegroundStarter`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `HomeScreen` 拆分（`196 -> 165`），抽出：
  - `HomeScreenPermissions`
  - `HomeScreenPreview`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `TimeUtils` 拆分（`195 -> 192`），抽出：
  - `TimeCoreDateDelegate`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceRecognitionGuideScreen` 拆分（`195 -> 87`），抽出：
  - `FaceRecognitionGuideScreenSections`
  - `FaceRecognitionGuideScreenPreview`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceCaptureScreen` 拆分（`195 -> 156`），抽出：
  - `FaceCaptureCameraSelector`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CameraScreenContent` 拆分（`195 -> 145`），抽出：
  - `CameraScreenContentLifecycle`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ServiceCompleteComponents` 拆分（`195 -> 112`），抽出：
  - `ServiceCompleteActionComponents`
  - `ServiceCompleteComponentsPreviews`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcTestHelper` 第三轮拆分（`194 -> 188`），补齐并下沉：
  - `renderNfcTestTagDialog`
  - `onNfcTestHelperResume`
  - `onNfcTestHelperPause`
  - `onNfcTestHelperDestroy`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `SelectServiceComponents` 拆分（`181 -> 110`），抽出：
  - `SelectServiceActionButtons`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `BannerScreen` 拆分（`185 -> 126`），抽出：
  - `BannerIndicators`
  - `BannerScreenPreview`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcOrderWorkflowDelegate` 拆分（`189 -> 149`），抽出：
  - `NfcOrderWorkflowResultHandlers`
  - `NfcOrderWorkflowOperations`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcTestScreen` 拆分（`189 -> 59`），抽出：
  - `NfcTestScreenLifecycle`
  - `NfcTestScreenContent`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceVerificationParamAssembler` 拆分（`194 -> 92`），抽出：
  - `FaceVerificationParamDelegates`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceImageExtractor` 拆分（`193 -> 42`），抽出：
  - `FaceImageExtractorBitmapUtils`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `AppNavGraphsSupport` 拆分（`181 -> 18`），抽出：
  - `AppNavGraphsSupportRouteRegistrations`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CountdownForegroundService` 拆分（`189 -> 143`），抽出：
  - `CountdownForegroundServiceNotifications`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `TimeUtils` 精简重构（`192 -> 118`），保持 `TimeCoreDateDelegate/TimeDisplayDateDelegate` 门面不变。
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcTestHelper` 拆分（`188 -> 134`），抽出：
  - `NfcTestDialogState`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceVerificationWithAutoSignScreen` 流程收敛（`187 -> 171`），统一拍照处理收尾逻辑并复用 `FaceVerificationWithAutoSignHandlers`。
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcWorkflowScreen` 拆分（`186 -> 120`），抽出：
  - `NfcWorkflowScreenHandlers`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ServiceCountdownScreen` 拆分（`180 -> 158`），抽出：
  - `ServiceCountdownScreenHandlers`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceCaptureDialogs` 拆分（`183 -> 75`），抽出：
  - `FaceCaptureImagePreviewDialog`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `NfcWorkflowViewModel` 流程收敛（`184 -> 156`），统一 OrderDelegate launch 调度并精简委托转发方法。
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `ManualFaceCapturePhotoReviewComponents` 拆分（`184 -> 59`），抽出：
  - `ManualFaceCapturePhotoReviewPreviewPane`
  - `ManualFaceCapturePhotoReviewStatePanel`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceImageExtractorBitmapUtils` 拆分（`172 -> 129`），抽出：
  - `FaceImageExtractorYuvUtils`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CountdownEventTracker` 重构收敛（`177 -> 121`），统一 trackEvent/trackError 上报路径并保持 `CountdownEventTracker.EventType` 外部 API 不变。
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceVerificationViewModel` 重构收敛（`182 -> 136`），抽出：
  - `FaceVerificationCallbackFactory`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `CameraCaptureCallbackHandler` 拆分（`177 -> 86`），抽出：
  - `CameraCaptureImageProcessing`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `FaceVerificationWithAutoSignScreen` 拆分（`171 -> 106`），抽出：
  - `FaceVerificationWithAutoSignScaffold`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `SelectServiceScreen` 拆分（`171 -> 115`），抽出：
  - `SelectServiceTopBar`
  - `SelectServiceScreenState`
  验证通过：
  - `:app:compileDebugKotlin`
  进度（2026-02-18，追加）：完成 `IdentificationScreenScaffoldContent` 拆分（`170 -> 65`），抽出：
  - `IdentificationScreenScaffoldSections`
  验证通过：
  - `:app:compileDebugKotlin -x lint`
  进度（2026-02-18，追加）：完成 `CountdownAlarmDelegate` 拆分与跟踪逻辑收敛（`168 -> 135`），抽出：
  - `CountdownAlarmDelegateTracking`
  验证通过：
  - `:app:compileDebugKotlin -x lint`
  进度（2026-02-18，追加）：完成 `IdentificationScreenScaffoldSections` 拆分（`161 -> 78`），抽出：
  - `IdentificationScreenBodyContent`
  验证通过：
  - `:app:compileDebugKotlin -x lint`
  进度（2026-02-18，追加）：完成 `CountdownAlarmDelegateHelpers` 拆分（`176 -> 58`），抽出：
  - `CountdownAlarmDelegateScheduling`
  - `CountdownAlarmDelegateStateStore`
  验证通过：
  - `:app:compileDebugKotlin -x lint`
  进度（2026-02-18，追加）：完成 `HomeScreenContentSections` 拆分（`168 -> 93`），抽出：
  - `HomeScreenPermissionDialogs`
  验证通过：
  - `:app:compileDebugKotlin -x lint`
  进度（2026-02-18，追加）：完成 `SelectServiceScreenSections` 拆分（`164 -> 81`），抽出：
  - `SelectServiceScreenSupportViews`
  验证通过：
  - `:app:compileDebugKotlin -x lint`
  当前状态：批次 2 已完成 129/4（累计完成 133/8）。
- [ ] `A12` 关键链路自动化回归（登录前置 + Wave1）（`QA`）  
  验证：关键链路自动化可在 CI 稳定执行。
- [ ] Wave1 技术债关闭（`ARCH`）  
  验证：迁移遗留 TODO 为 0，接口边界文档更新。

## W5（迁移 Wave2：登录/首页/共享）

- [ ] `A07` 登录/首页/共享模块迁移（`DEV-F` `ARCH`）  
  验证：`app` 仅保留页面组装和导航入口。
- [ ] `A08` domain 接口与 data 实现边界收口（`ARCH`）  
  验证：依赖方向稳定为 `feature -> domain <- data`。
- [ ] `A11` Wave2 模块级单测补齐（`QA`）  
  验证：新增迁移模块均具备基础单测。

## W6（架构收敛 + 质量扩面）

- [ ] `A09` 处理 `core:data`、`core:ui` 空壳/职责问题（`ARCH`）  
  验证：模块要么被有效使用，要么合并删除。
- [ ] `A10` ViewModel/UseCase 分层规范强制化（`ARCH`）  
  验证：UI 层无直接仓储/网络访问。
- [ ] `A12` 识别/上传/倒计时链路集成测试（`QA`）  
  验证：关键业务链路端到端回归可复现。

## W7（性能优化与可观测）

- [ ] `A14` Gradle 性能治理（`PLAT`）  
  验证：`assembleDebug` 与 `compileDebugKotlin` 的 P95 较 W1 明显下降。
- [ ] `A15` Baseline Profile 回归（`DEV-F` `PLAT`）  
  验证：启动关键指标可量化提升并形成记录。
- [ ] `A17` 质量快照看板自动生成（`PLAT`）  
  验证：每次 CI 自动输出质量趋势报告。

## W8（发布就绪与治理固化）

- [ ] `A16` ADR 与架构文档收口（`ARCH`）  
  验证：核心架构决策具备可追溯文档。
- [ ] `A18` 发布门禁正式启用（`TL` `PLAT` `QA`）  
  验证：不达标（测试/静态检查/构建）无法发布。
- [ ] 项目收官评审（`TL`）  
  验证：8 周目标完成度评审与下一阶段路线确认。

## 5. 风险清单（含应对）

| 风险ID | 风险描述 | 触发信号 | 影响 | Owner | 缓解策略 |
|---|---|---|---|---|---|
| R1 | 密钥轮换引发发布中断 | 新旧签名不兼容 | 高 | `SEC` | 双通道验证 + 灰度签名演练 |
| R2 | CI 调整后波动仍高 | 取消率无明显下降 | 高 | `PLAT` | 降并发 + 分离慢任务 + 重试策略 |
| R3 | 迁移导致业务回归缺陷 | 关键链路失败上升 | 高 | `DEV-F` `QA` | 分批迁移 + 每批自动化回归 |
| R4 | 大文件拆分引发行为偏差 | 拆分后线上异常 | 中 | `DEV-F` | 先抽纯函数 + 保持等价测试 |
| R5 | 模块边界争议导致停滞 | PR 长期阻塞 | 中 | `ARCH` `TL` | ADR 先行 + 决策时限 |
| R6 | 测试补齐速度不足 | 模块长期无测试 | 中 | `QA` | 先关键路径，后补边缘场景 |
| R7 | 性能优化收益不稳定 | 指标波动大 | 中 | `PLAT` | 固定采样口径（3次中位数） |
| R8 | 范围膨胀导致延期 | 周任务持续外溢 | 中 | `TL` | 严控“本周必做/可延期”边界 |

## 6. 周节奏与检查点

- 每周一：里程碑计划确认（30 分钟）
- 每周三：中期风险检查（20 分钟）
- 每周五：周退出验收（30 分钟）
- 每周输出物：
  - 里程碑完成率
  - 风险状态（新增/关闭）
  - 指标变化（CI、测试、构建）

## 7. 追踪指标（统一看板）

- 架构类：`app/features` 行数、超大文件数量、依赖违规数
- 质量类：静态检查通过率、模块单测覆盖进展、关键链路通过率
- CI类：取消率、非取消成功率、平均排队时长
- 性能类：`compileDebugKotlin` P95、`assembleDebug` P95、启动指标

## 8. 任务导入建议（Jira/Linear）

按周创建 8 个 Epic（W1-W8），每个任务按 `Axx` 作为 Story Key 前缀，统一字段：

- `OwnerRole`：`ARCH/PLAT/SEC/DEV-F/QA/TL`
- `DoD`：引用本计划“周退出标准”
- `RiskLink`：关联 `R1-R8`
- `MetricLink`：关联对应指标看板项
