# 持续现代化优化清单（2026-02-17）

## 扫描结论

- 历史台账中的阶段性优化项已收口（无“未完成项”）。
- 按“现代化架构 + Kotlin/Jetpack API + CI/CD/测试自动化健壮性”进行全项目扫描后，识别出一批新的持续优化项。

## 优化任务清单

| ID | 优化项 | 对应目标 | 状态 |
|---|---|---|---|
| N1 | COS 凭证同步刷新增加超时与主线程防阻塞回退 | Kotlin 并发现代化、健壮性 | DONE |
| N2 | 倒计时闹钟 WakeLock 延迟释放从 `Handler` 迁移为协程 | Kotlin 现代化 API、健壮性 | DONE |
| N3 | Baseline Profile 启动/生成脚本补真实交互（等待、滑动、返回） | 测试自动化、性能基线有效性 | DONE |
| N4 | 新增 `verify_baselineprofile_journeys.sh` 守卫并接入共享 CI action | CI/CD 自动化与回归防护 | DONE |
| N5 | 质量快照脚本纳入 Jetpack/Lint/Baseline/CI 守卫 | 自动化可观测性与健壮性 | DONE |
| N6 | 统一替换剩余 `Handler(Looper.getMainLooper())` 工具类调用（`ToastHelper`、`ContextExtensions`） | Kotlin/Jetpack 兼容 API 收敛 | DONE |
| N7 | 为 COS 同步凭证刷新新增超时与回退路径单测 | 测试健壮性 | DONE |
| N8 | 构建基线采集脚本默认禁用缓存并强制重跑任务，消除耗时统计失真 | CI/CD 指标可信度、性能治理可重复性 | DONE |
| N9 | CI 健康监控将“非取消运行成功率”作为主稳定性指标，并将取消率超阈值默认降级为告警 | CI/CD 稳定性评估去噪、自动化可靠性 | DONE |
| N10 | 质量快照脚本在缺失 lint 报告时自动 bootstrap `:app:lintDebug` | 自动化健壮性（clean 环境可直接执行） | DONE |

## 下一批剩余优化项（持续推进）

| ID | 优化项 | 对应目标 | 状态 |
|---|---|---|---|
| R1 | 继续将主体业务从 `app/features/*` 迁移到 `feature/*` 模块（优先：`photoupload`、`identification`、`servicecountdown`） | 现代化模块架构、边界收敛 | IN_PROGRESS（photoupload 核心层 + identification vm 全量 + servicecountdown vm 已下沉，UI/平台适配层留在 app） |
| R2 | 对 `app/features/*` 超大目录做二次拆分（按 `ui/vm/domain/data` 纵向分层） | 主体代码质量、可维护性 | IN_PROGRESS（`ServiceCountdownScreen`、`PhotoUploadScreen`、`CameraScreen`、`ManualFaceCaptureScreen`、`NfcWorkflowScreen`、`NfcWorkflowViewModel`（契约层）、`FaceCaptureScreen`、`MainDashboardScreen`、`SelectServiceScreen`、`LoginScreen` 已完成阶段性拆分） |
| R3 | CI 健康监控告警项治理：将 Android CI 取消率从 50% 降到阈值内（触发策略与提交流水优化） | CI/CD 资源效率、稳定性 | IN_PROGRESS（已完成并发分组与取消策略治理，待远端新样本窗口验证取消率回落） |
| R4 | 在“无缓存 + rerun”基线口径下继续压降 `:app:assembleDebug` 冷构建耗时（当前 73s） | 构建性能目标收敛 | IN_PROGRESS（依赖精简后 `assembleDebug` 回落，但 `compileDebugKotlin` 波动仍需持续观测） |

## 增量执行记录（2026-02-17）

- `R1` 首批切片（`photoupload`）：
  - 新增 `feature:photoupload` 模块，迁移非 UI 核心类：
    - `api/*`
    - `tracker/CameraEventTracker.kt`
    - `utils/ImageCacheManager.kt`
    - `viewmodel/PhotoProcessingViewModel.kt`
  - 下沉跨模块模型契约到 `core:model`：
    - 新增 `core/model/src/main/kotlin/com/ytone/longcare/model/PhotoUploadModels.kt`
    - `app/features/photoupload/model/*` 改为兼容 `typealias`（平滑迁移）
  - 将可复用工具从 `app` 迁移到 `core:common`：
    - `CosConstants.kt`
    - `CosUtils.kt`
    - `StorageSpaceUtils.kt`
    - `UriExtensions.kt`
  - 去耦合修复：
    - `PhotoProcessingViewModel` 将 `currentCategory`（UI 枚举）改为 `currentTaskType`（`ImageTaskType`），解除对 UI 层类型依赖。
- `R1` 增量切片（`identification`）：
  - 将 `app/features/identification` 的非 UI 能力迁移至 `feature:identification`：
    - `api/IdentificationActions.kt`
    - `data/*`
    - `di/IdentificationUseCaseGatewayModule.kt`
  - `feature:identification/build.gradle.kts` 补齐 Hilt/KSP 注解处理链路：
    - `alias(libs.plugins.dagger.hilt)`
    - `alias(libs.plugins.ksp)`
    - `ksp(libs.dagger.hilt.compiler)`
    - `ksp(libs.hilt.compiler)`
  - 后续抽象与迁移（本轮新增）：
    - 新增 `core:domain` 接口：`FaceVerificationConfigProvider`。
    - `SystemConfigManager` 改为实现 `FaceVerificationConfigProvider`，并通过 `app/di/FaceVerificationConfigModule.kt` 绑定。
    - `IdentificationViewModel` 及相关 flow 改为依赖 `FaceVerificationConfigProvider`，移除对 `SystemConfigManager` 直接依赖。
    - `app/features/identification/vm/*` 全量迁移至 `feature:identification/vm/*`。
- `R1` 增量切片（`servicecountdown`）：
  - 新增 `feature:servicecountdown` 模块并接入：
    - `settings.gradle.kts` 新增 `include(":feature:servicecountdown")`
    - `app/build.gradle.kts` 新增 `implementation(project(":feature:servicecountdown"))`
  - 迁移 `servicecountdown` 首个非 UI 契约文件：
    - `api/ServiceCountdownActions.kt` 从 `app` 迁移到 `feature:servicecountdown`
  - 迁移 `servicecountdown` 共享状态定义（解除 UI->VM 反向耦合）：
    - 新增 `feature/servicecountdown/.../model/ServiceCountdownState.kt`
    - `vm/ServiceCountdownStateHolder.kt` 从 `app` 迁移到 `feature:servicecountdown`
    - `ServiceCountdownScreen` 与 `ServiceCountdownViewModel` 改为依赖 `feature` 模块中的状态定义
  - 模块依赖补齐：
    - `feature/servicecountdown/build.gradle.kts` 新增 `kotlinx-coroutines-core`（`StateFlow` 依赖）
  - 后续抽象与迁移（本轮新增）：
    - 新增 `feature:servicecountdown` 领域网关：`ServiceCountdownSystemGateway`。
    - `ServiceCountdownViewModel` 改为依赖该网关，隔离 `CountdownForegroundService/AlarmRingtoneService/CountdownNotificationManager` 平台实现。
    - 新增 `app` 侧实现与绑定：
      - `service/ServiceCountdownSystemGatewayImpl.kt`
      - `di/ServiceCountdownGatewayModule.kt`
    - `ServiceCountdownViewModel` 从 `app` 迁移到 `feature:servicecountdown/vm`。
- `R2` 增量切片（`servicecountdown`）：
  - `ServiceCountdownScreen` 页面内副作用流程继续拆分：
    - 新增 `ServiceCountdownPermissionHandler.kt`，抽离通知/精确闹钟/全屏通知权限检查与设置页跳转逻辑。
    - 新增 `ServiceCountdownServiceFlowHandler.kt`，抽离“结束服务”与“订单状态异常清理退出”流程。
    - 新增 `ServiceCountdownDialogs.kt`，抽离权限提示、提前结束确认、订单状态异常三个弹窗组件。
    - 将初始化核心流程下沉到 `ServiceCountdownViewModel.initializeCountdownSession(...)`：
      - 页面不再直接拼装“服务信息/前台服务/闹钟调度”；
      - 页面仅保留重入判断与权限触发，降低 UI 层编排复杂度。
  - `ServiceCountdownScreen` 改为调用上述处理器函数，页面逻辑进一步收敛为状态渲染与交互编排。
  - 本轮继续收敛（副作用与底栏）：
    - 新增 `ServiceCountdownLifecycleEffects.kt`，承接订单状态监听、初始化触发、生命周期刷新与页面销毁清理；
    - 新增 `ServiceCountdownBottomActionBar.kt`，抽离底部结束服务按钮区域与状态文案/颜色映射；
    - `ServiceCountdownScreen` 行数从 `390` 降至 `280`；
    - 页面销毁清理使用 `rememberUpdatedState(countdownState)`，避免 `DisposableEffect` 读取过期状态风险。
- `R2` 增量切片（`photoupload`）：
  - `PhotoUploadScreen` 页面副作用拆分：
    - 新增 `PhotoUploadScreenEffects.kt`，集中处理页面生命周期日志、订单初始化、既有图片回填与拍照回传 URI 监听。
  - 底部操作栏拆分：
    - 新增 `PhotoUploadBottomActionBar.kt`，抽离确认上传按钮与上传流程编排（Mock 返回、云端上传映射、异常提示）。
  - Mock 调试工具拆分：
    - 新增 `PhotoUploadMockDebugToolsCard.kt`，将 Debug 模式卡片组件从主屏抽离。
  - 收敛结果：
    - `PhotoUploadScreen.kt` 行数从 `709` 降至 `569`。
  - 继续收敛（本轮新增）：
    - 新增 `PhotoUploadGridComponents.kt`，下沉 `PhotoUploadSection`、`AddPhotoButton`、`ImageTaskItem`、`ImagePreviewDialog`。
    - 新增 `PhotoUploadScreenPreviews.kt`，集中承接 `PhotoUploadSectionPreview`、`AddPhotoButtonPreview`、`ImageTaskItemPreview`、`ConfirmAndNextButtonPreview`。
    - `PhotoUploadScreen.kt` 移除内联网格/预览实现，仅保留页面编排和状态联动。
  - 收敛结果（更新）：
    - `PhotoUploadScreen.kt` 行数从 `569` 进一步降至 `241`。
- `R2` 增量切片（`photoupload/camera`）：
  - 拍照处理流程拆分：
    - 新增 `CameraCaptureProcessor.kt`，承接 `takePhoto` 与水印渲染、EXIF 旋转、超时与资源回收逻辑。
  - 权限门禁拆分：
    - 新增 `CameraPermissionGate.kt`，集中处理相机权限申请与重试入口。
  - 页面内容拆分（本轮新增）：
    - 新增 `CameraScreenContent.kt`，下沉 `CameraContent` 与延迟拍照模式状态/倒计时编排。
    - `CameraScreen.kt` 收敛为页面入口与导航回调，移除内联大段拍照页面内容。
  - 页面布局组件拆分（本轮新增）：
    - 新增 `CameraScreenLayoutComponents.kt`，下沉顶部延迟拍照工具条、底部控制栏、倒计时遮罩与拍照中遮罩组件。
    - `CameraScreenContent.kt` 收敛为状态管理与拍照/切换摄像头业务逻辑编排。
  - 位图处理工具拆分（本轮新增）：
    - 新增 `CameraBitmapProcessingUtils.kt`，承接 EXIF 旋转、水平翻转、水印叠加、视图转位图与动态超时计算工具函数。
    - `CameraCaptureProcessor.kt` 聚焦拍照回调、协程流程与文件写入主路径。
  - 收敛结果：
    - `CameraScreen.kt` 行数从 `928` 降至 `511`，并在本轮进一步降至 `35`。
    - `CameraScreenContent.kt` 行数从 `431` 降至 `368`。
    - `CameraCaptureProcessor.kt` 行数从 `374` 降至 `229`。
- `R2` 增量切片（`face/manual-capture`）：
  - 副作用流程拆分：
    - 新增 `ManualFaceCaptureEffects.kt`，承接相机权限检查/申请与成功态回传路径监听。
  - 拍照处理流程拆分：
    - 新增 `ManualFaceCaptureProcessor.kt`，承接拍照回调、位图解码采样、旋转修正与异常处理。
  - 弹窗组件归拢：
    - `FaceFullScreenPreviewDialog` 从 `ManualFaceCaptureScreen.kt` 下沉到 `components/ManualFaceCaptureDialogComponents.kt`。
  - 反馈遮罩拆分（本轮新增）：
    - 新增 `ManualFaceCaptureFeedbackOverlays.kt`，抽离错误提示卡片与全屏加载遮罩。
    - `ManualFaceCaptureScreen` 改为组合 `ManualFaceCaptureErrorOverlay` / `ManualFaceCaptureLoadingOverlay`。
  - 收敛结果：
    - `ManualFaceCaptureScreen.kt` 行数从 `726` 降至 `498`。
- `R2` 增量切片（`nfc/workflow`）：
  - 副作用流程拆分：
    - 新增 `NfcWorkflowEffects.kt`，承接 NFC 能力检查、NFC 事件订阅、前台调度生命周期清理及签退后定位停止逻辑。
  - 底栏区域拆分：
    - 新增 `NfcWorkflowBottomBar.kt`，抽离成功/失败/等待态底部交互与提示卡片。
  - 弹窗编排拆分：
    - 新增 `NfcWorkflowDialogs.kt`，统一承接定位激活弹窗与结束工单确认弹窗的显示与回调。
  - 收敛结果：
    - `NfcWorkflowScreen.kt` 行数从 `684` 降至 `596`。
  - 补充收敛（本轮新增）：
    - 新增 `NfcWorkflowContentComponents.kt`，下沉 `SignInContentCard`、`StatusDisplay`、`ActionButton`，解耦页面渲染与组件实现。
    - `NfcWorkflowDialogs.kt` 补齐 `LocationActivationDialog` 与 `EndOrderConfirmDialog` 组件实现，消除拆分后未解析符号。
    - 新增 `NfcWorkflowContracts.kt`，将 `NfcSignInUiState`、`PendingNfcData`、`EndOrderParams`、`EndOrderSuccessData` 从 `NfcWorkflowViewModel.kt` 抽离。
    - 收敛结果：
      - `NfcWorkflowScreen.kt` 行数从 `596` 进一步降至 `265`。
      - `NfcWorkflowViewModel.kt` 行数从 `591` 降至 `546`。
- `R2` 增量切片（`facecapture`）：
  - 弹窗组件拆分：
    - 新增 `FaceCaptureDialogs.kt`，承接权限拒绝页与图片全屏预览交互（双击缩放/拖拽/点击关闭）。
  - 通用 UI 组件拆分：
    - 新增 `FaceCaptureUiComponents.kt`，承接人脸检测指示框与人脸缩略图组件。
  - 页面职责收敛：
    - `FaceCaptureScreen.kt` 删除内联弹窗与组件实现，仅保留相机编排与页面状态渲染。
  - 收敛结果：
    - `FaceCaptureScreen.kt` 行数从 `495` 降至 `396`。
- `R2` 增量切片（`maindashboard`）：
  - 页面骨架与业务编排收敛：
    - `MainDashboardScreen.kt` 仅保留页面入口、生命周期触发与主布局编排。
  - 头部/卡片区拆分：
    - 新增 `MainDashboardHeaderCards.kt`，承接 `TopHeader`、`HomeBannerCard`、`DashboardGridWithImages`、`InfoCard` 与 `ImageWithAdaptiveWidth`。
  - 订单 Tab 区拆分：
    - 新增 `MainDashboardOrdersSection.kt`，承接 `OrderTabLayout`、`InOrderServiceItem` 与 `CustomTabItem`。
  - 收敛结果：
    - `MainDashboardScreen.kt` 行数从 `586` 降至 `125`。
  - 补充问题修复（单测稳定性）：
    - 修复 `MainDashboardViewModelTest` 在 JVM 环境触发 `android.util.Log not mocked` 的失败；
    - 在失败路径用例中对 `Any.logE(...)` 扩展日志函数进行静态 mock，避免单测依赖 Android Log 实现。
- `R2` 增量切片（`selectservice`）：
  - 页面组件拆分：
    - 新增 `SelectServiceComponents.kt`，下沉 `TotalDurationDisplay`、`ServiceSelectionList`、`ServiceSelectionItem`、`SelectAllButton`、`NextStepButton`。
  - 预览拆分：
    - 新增 `SelectServicePreviews.kt`，统一承接页面组件预览。
  - 页面职责收敛：
    - `SelectServiceScreen.kt` 移除内联组件与预览，仅保留页面状态编排、订单加载与路由交互。
  - 收敛结果：
    - `SelectServiceScreen.kt` 行数从 `471` 降至 `283`。
- `R2` 增量切片（`login`）：
  - 页面组件拆分：
    - 新增 `LoginScreenComponents.kt`，下沉 `SendVerificationCodeButton` 与 `AgreementText`。
  - 预览拆分：
    - 新增 `LoginScreenPreviews.kt`，统一承接 `AgreementTextPreview`。
  - 页面职责收敛：
    - `LoginScreen.kt` 移除内联组件与预览，仅保留页面状态编排与登录路由交互。
  - 收敛结果：
    - `LoginScreen.kt` 行数从 `451` 降至 `337`。
- `R3` 增量治理（`android-ci` 取消率）：
  - 健康监控基线复测：
    - `monitor_ci_health` 最近 `50` 次样本显示 `Android CI` 取消率 `60%`，高于阈值 `20%`。
  - 并发分组优化：
    - 更新 `.github/workflows/android-ci.yml` 的 `concurrency.group`：
      - `pull_request` 维度加入 `head.sha`，降低同 PR 连续提交造成的互相取消。
      - `push` 维度保持分支级分组，继续保留串行保护。
  - 取消策略收敛（本轮新增）：
    - `concurrency.cancel-in-progress` 从固定 `true` 调整为表达式：
      - `${{ github.event_name == 'pull_request' }}`
      - 仅对 PR superseded 运行执行取消，降低 push 分支流水线被互斥取消的比例。
    - `scripts/quality/verify_ci_workflow_quality.sh` 调整为：
      - 校验 `cancel-in-progress` 已配置；
      - 禁止显式 `cancel-in-progress: false`。
  - 治理后快照（历史窗口）：
    - 最近 `50` 次样本仍为取消率 `62%`、非取消成功率 `68.42%`，仍高于阈值，需等待新策略覆盖更多样本。
  - 守卫验证：
    - `scripts/quality/verify_ci_workflow_quality.sh`：PASS。
- `R4` 基线复测（`assembleDebug`）：
  - 重新执行“无缓存 + rerun + clean”基线采集，结果已追加到 `docs/refactor/baseline-metrics.md`。
  - 最新样本（2026-02-17 21:03:11）：
    - `:app:compileDebugKotlin`：`49s`
    - `:app:testDebugUnitTest`：`74s`
    - `:app:assembleDebug`：`106s`（较上一轮 `79s` 上升，未达目标）
  - 继续治理（依赖收敛）：
    - 将 `app` 和 `feature/*` 的 Hilt 依赖从 `libs.bundles.hilt` 改为按需声明（`dagger.hilt.android` + 必要扩展），减少不必要传递依赖。
  - 最新样本（2026-02-17 21:21:47）：
    - `:app:compileDebugKotlin`：`103s`
    - `:app:testDebugUnitTest`：`81s`
    - `:app:assembleDebug`：`73s`（较 `106s` 回落 `33s`）
- 验证：
  - `./gradlew :app:compileDebugKotlin`：PASS
  - `./gradlew :app:testDebugUnitTest --tests "*ServiceCountdownViewModelTest" --tests "*ImageTaskSimplificationTest" --tests "*UriJsonAdapterTest" --tests "*JsonClassAnnotationTest" --tests "*FaceSdkBoundaryTest"`：PASS
  - `bash scripts/quality/verify_ci_workflow_quality.sh`：PASS
  - `bash scripts/quality/monitor_ci_health.sh yyg20101/longcare 50 scripts/quality/ci_health_thresholds.json build/ci-health-r3`：FAIL（按预期暴露治理前基线）
  - `BASELINE_CLEAN_BEFORE_RUN=true bash scripts/quality/collect_build_baseline.sh`：PASS

## 本轮已落地文件

- `app/src/main/kotlin/com/ytone/longcare/data/cos/repository/CosRepositoryImpl.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/countdown/receiver/CountdownAlarmReceiver.kt`
- `baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/StartupBenchmarks.kt`
- `baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/BaselineProfileGenerator.kt`
- `scripts/quality/verify_baselineprofile_journeys.sh`
- `core/common/src/main/kotlin/com/ytone/longcare/common/utils/ToastHelper.kt`
- `app/src/main/kotlin/com/ytone/longcare/common/utils/ContextExtensions.kt`
- `.github/actions/android-build-env/action.yml`
- `.github/workflows/face-sdk-migration-check.yml`
- `scripts/quality/verify_ci_workflow_quality.sh`
- `scripts/quality/collect_quality_snapshot.sh`
- `scripts/quality/collect_build_baseline.sh`
- `scripts/quality/monitor_ci_health.sh`
- `app/src/test/kotlin/com/ytone/longcare/data/cos/repository/CosRepositoryImplTest.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/facecapture/FaceCaptureScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/facecapture/FaceCaptureDialogs.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/facecapture/FaceCaptureUiComponents.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowContentComponents.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowDialogs.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowContracts.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/CameraScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/CameraScreenContent.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/CameraScreenLayoutComponents.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/CameraBitmapProcessingUtils.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/PhotoUploadGridComponents.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/PhotoUploadScreenPreviews.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/selectservice/ui/SelectServiceScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/selectservice/ui/SelectServiceComponents.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/selectservice/ui/SelectServicePreviews.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreenComponents.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreenPreviews.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardHeaderCards.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardOrdersSection.kt`
- `.github/workflows/android-ci.yml`
- `scripts/quality/legacy_feature_files_allowlist.txt`
- `docs/refactor/baseline-metrics.md`

## 本轮验收命令

- `./gradlew --no-daemon :app:compileDebugKotlin :app:testDebugUnitTest`
- `./gradlew --no-daemon :baselineprofile:assemble`
- `bash scripts/quality/verify_jetpack_compat_apis.sh`
- `bash scripts/quality/verify_architecture_boundaries.sh .`
- `bash scripts/quality/verify_baselineprofile_journeys.sh`
- `bash scripts/quality/verify_ci_workflow_quality.sh`
- `bash scripts/quality/collect_quality_snapshot.sh`
- `BASELINE_CLEAN_BEFORE_RUN=true bash scripts/quality/collect_build_baseline.sh`
- `bash scripts/quality/monitor_ci_health.sh yyg20101/longcare 50 scripts/quality/ci_health_thresholds.json build/ci-health`
- `bash scripts/quality/monitor_ci_health.sh yyg20101/longcare 50 scripts/quality/ci_health_thresholds.json build/ci-health-r3-after-cancel-policy`
- `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ytone.longcare.data.cos.repository.CosRepositoryImplTest"`
- `./gradlew --no-daemon :app:testDebugUnitTest --tests "*MainDashboardViewModelTest"`
- `./gradlew --no-daemon :app:testDebugUnitTest --tests "*MainDashboardViewModelTest" --tests "*ServiceCountdownViewModelTest" --tests "*ImageTaskSimplificationTest" --tests "*UriJsonAdapterTest" --tests "*JsonClassAnnotationTest" --tests "*FaceSdkBoundaryTest"`
