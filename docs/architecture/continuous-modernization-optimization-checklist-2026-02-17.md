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
| R2 | 对 `app/features/*` 超大目录做二次拆分（按 `ui/vm/domain/data` 纵向分层） | 主体代码质量、可维护性 | TODO |
| R3 | CI 健康监控告警项治理：将 Android CI 取消率从 50% 降到阈值内（触发策略与提交流水优化） | CI/CD 资源效率、稳定性 | TODO |
| R4 | 在“无缓存 + rerun”基线口径下继续压降 `:app:assembleDebug` 冷构建耗时（当前 79s） | 构建性能目标收敛 | TODO |

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
- 验证：
  - `./gradlew :app:compileDebugKotlin`：PASS
  - `./gradlew :app:testDebugUnitTest --tests "*ImageTaskSimplificationTest" --tests "*UriJsonAdapterTest" --tests "*JsonClassAnnotationTest"`：PASS

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
- `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ytone.longcare.data.cos.repository.CosRepositoryImplTest"`
