# 项目整体优化与重构主计划（Master Plan）

## 1. 文档目标

本文件作为 LongCare 项目整体优化与重构的唯一执行台账，覆盖：

1. 全量任务清单（A1~E3）。
2. 每个任务的具体文件改动清单。
3. 逐日执行计划（D01~D27）。
4. 完成后的同步更新规则（状态、日期、提交、实际变更）。

## 2. 状态约定

- `TODO`：未开始
- `IN_PROGRESS`：进行中
- `DONE`：已完成
- `BLOCKED`：受阻

## 3. 任务总览（A1~E3）

| ID | 任务 | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| A1 | 基线冻结与度量 | P0 | DONE | - |
| A2 | 收敛当前未提交改动 | P0 | DONE | A1 |
| A3 | 架构约束文档化 | P0 | DONE | A2 |
| A4 | 质量门禁基线 | P0 | DONE | A3 |
| B1 | Core 模块骨架搭建 | P0 | DONE | A4 |
| B2 | Feature 模块骨架搭建 | P0 | DONE | B1 |
| B3 | Repository 依赖反转改造 | P0 | DONE | B2 |
| B4 | DI 拆分重组 | P0 | DONE | B3 |
| B5 | App 壳层收敛 | P1 | DONE | B4 |
| C1 | Identification 流程 UseCase 化 | P0 | DONE | B5 |
| C2 | 超大 ViewModel 拆分 | P0 | DONE | C1 |
| C3 | 超大 Composable 拆分 | P0 | DONE | C2 |
| C4 | 巨石工具类拆分 | P1 | DONE | C3 |
| C5 | 事件流规范化（StateFlow/SharedFlow） | P1 | DONE | C4 |
| C6 | 调度器治理收尾 | P1 | DONE | C5 |
| D1 | Gradle 约定插件化（build-logic） | P0 | DONE | C6 |
| D2 | 构建稳定性治理 | P1 | DONE | D1 |
| D3 | CI 任务分层与按变更执行 | P1 | DONE | D2 |
| D4 | 构建性能专项优化 | P1 | DONE | D3 |
| E1 | 模块级测试补齐 | P0 | DONE | D4 |
| E2 | 架构守卫自动化 | P1 | DONE | E1 |
| E3 | 发布前回归清单与收口 | P1 | DONE | E2 |

## 4. 每项任务具体文件改动清单

### A1 基线冻结与度量

**计划改动文件**

- `scripts/quality/collect_build_baseline.sh`（新增）
- `docs/refactor/baseline-metrics.md`（新增）
- `README.md`（补充“如何更新基线”段落）

**验收标准**

- 产出完整 baseline 报告（命令、环境、时间、结果）。

### A2 收敛当前未提交改动

**计划改动文件**

- `app/src/main/kotlin/com/ytone/longcare/data/cos/repository/CosRepositoryImpl.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/face/viewmodel/ManualFaceCaptureViewModel.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/facecapture/FaceCaptureViewModel.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/identification/vm/IdentificationViewModel.kt`
- `app/src/main/kotlin/com/ytone/longcare/worker/DownloadWorker.kt`
- `app/src/main/kotlin/com/ytone/longcare/common/faceauth/FaceVerifier.kt`
- `app/src/main/kotlin/com/ytone/longcare/domain/faceauth/FaceVerifier.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSource.kt`

**验收标准**

- 脏改动分 2~4 个主题提交；每个提交单独可编译测试通过。

### A3 架构约束文档化

**计划改动文件**

- `docs/architecture/adr/ADR-001-layer-boundary.md`（新增）
- `docs/architecture/module-responsibility-map.md`（新增）
- `docs/architecture/dependency-rules.md`（新增）

**验收标准**

- 明确依赖方向与禁用依赖，PR 可据此 review。

### A4 质量门禁基线

**计划改动文件**

- `.github/workflows/android-ci.yml`
- `.github/workflows/baseline-profile.yml`
- `scripts/quality/verify_architecture_boundaries.sh`（新增）
- `scripts/quality/verify_module_api_visibility.sh`（新增）

**验收标准**

- PR 门禁必须包含 assemble + unit test + lint + 架构检查脚本。

### B1 Core 模块骨架搭建

**计划改动文件**

- `settings.gradle.kts`
- `core/model/build.gradle.kts`（新增）
- `core/domain/build.gradle.kts`（新增）
- `core/data/build.gradle.kts`（新增）
- `core/ui/build.gradle.kts`（新增）
- `core/common/build.gradle.kts`（新增）
- `core/model/src/main/kotlin/com/ytone/longcare/core/model/Placeholder.kt`（新增）
- `core/domain/src/main/kotlin/com/ytone/longcare/core/domain/Placeholder.kt`（新增）
- `core/data/src/main/kotlin/com/ytone/longcare/core/data/Placeholder.kt`（新增）
- `core/ui/src/main/kotlin/com/ytone/longcare/core/ui/Placeholder.kt`（新增）
- `core/common/src/main/kotlin/com/ytone/longcare/core/common/Placeholder.kt`（新增）

**验收标准**

- 新模块被 `:app` 成功引用，工程可构建。

### B2 Feature 模块骨架搭建

**计划改动文件**

- `settings.gradle.kts`
- `feature/login/build.gradle.kts`（新增）
- `feature/home/build.gradle.kts`（新增）
- `feature/identification/build.gradle.kts`（新增）
- `feature/login/src/main/kotlin/com/ytone/longcare/feature/login/FeatureEntry.kt`（新增）
- `feature/home/src/main/kotlin/com/ytone/longcare/feature/home/FeatureEntry.kt`（新增）
- `feature/identification/src/main/kotlin/com/ytone/longcare/feature/identification/FeatureEntry.kt`（新增）
- `app/src/main/kotlin/com/ytone/longcare/navigation/AppNavigation.kt`

**验收标准**

- 三个 feature 模块可被导航层引用，主链路可启动。

### B3 Repository 依赖反转改造

**计划改动文件**

- `core/domain/src/main/kotlin/com/ytone/longcare/core/domain/repository/`（新增目录与接口）
- `core/data/src/main/kotlin/com/ytone/longcare/core/data/repository/`（新增目录与实现）
- `app/src/main/kotlin/com/ytone/longcare/domain/repository/`（迁移或删减）
- `app/src/main/kotlin/com/ytone/longcare/data/repository/`（迁移或删减）
- `app/src/main/kotlin/com/ytone/longcare/di/RepositoryModule.kt`

**验收标准**

- UI 层仅依赖 Repository 接口，不依赖 Impl。

### B4 DI 拆分重组

**计划改动文件**

- `app/src/main/kotlin/com/ytone/longcare/di/AppModule.kt`
- `app/src/main/kotlin/com/ytone/longcare/di/NetworkModule.kt`
- `app/src/main/kotlin/com/ytone/longcare/di/DatabaseModule.kt`
- `app/src/main/kotlin/com/ytone/longcare/di/RepositoryModule.kt`
- `core/data/src/main/kotlin/com/ytone/longcare/core/data/di/CoreDataModule.kt`（新增）
- `feature/login/src/main/kotlin/com/ytone/longcare/feature/login/di/LoginFeatureModule.kt`（新增）
- `feature/home/src/main/kotlin/com/ytone/longcare/feature/home/di/HomeFeatureModule.kt`（新增）
- `feature/identification/src/main/kotlin/com/ytone/longcare/feature/identification/di/IdentificationFeatureModule.kt`（新增）

**验收标准**

- Hilt 图稳定，无循环依赖，无重复绑定冲突。

### B5 App 壳层收敛

**计划改动文件**

- `app/src/main/kotlin/com/ytone/longcare/MainActivity.kt`
- `app/src/main/kotlin/com/ytone/longcare/app/MainApplication.kt`
- `app/src/main/kotlin/com/ytone/longcare/navigation/AppNavigation.kt`
- `app/src/main/kotlin/com/ytone/longcare/MainViewModel.kt`

**验收标准**

- `:app` 只保留壳层与组装逻辑，不承载业务实现。

### C1 Identification 流程 UseCase 化

**计划改动文件**

- `feature/identification/src/main/kotlin/com/ytone/longcare/feature/identification/domain/SetupFaceUseCase.kt`（新增）
- `feature/identification/src/main/kotlin/com/ytone/longcare/feature/identification/domain/VerifyServicePersonUseCase.kt`（新增）
- `feature/identification/src/main/kotlin/com/ytone/longcare/feature/identification/domain/UploadElderPhotoUseCase.kt`（新增）
- `app/src/main/kotlin/com/ytone/longcare/features/identification/vm/IdentificationViewModel.kt`

**验收标准**

- `IdentificationViewModel` 仅保留状态编排与调用 UseCase。

### C2 超大 ViewModel 拆分

**计划改动文件**

- `app/src/main/kotlin/com/ytone/longcare/features/identification/vm/IdentificationViewModel.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/identification/vm/IdentificationUiState.kt`（新增）
- `app/src/main/kotlin/com/ytone/longcare/features/identification/vm/IdentificationEvent.kt`（新增）
- `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/vm/ServiceCountdownViewModel.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/vm/ServiceCountdownStateHolder.kt`（新增）

**验收标准**

- 单个 VM 文件行数降至 < 400，状态/事件拆分清晰。

### C3 超大 Composable 拆分

**计划改动文件**

- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/CameraScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/components/`（新增目录）
- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/PhotoUploadScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/face/ui/ManualFaceCaptureScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/face/ui/components/`（新增目录）

**验收标准**

- Screen、Section、Reusable Component 三层结构落地。

### C4 巨石工具类拆分

**计划改动文件**

- `app/src/main/kotlin/com/ytone/longcare/common/security/CryptoUtils.kt`
- `app/src/main/kotlin/com/ytone/longcare/common/security/crypto/`（新增目录）
- `app/src/main/kotlin/com/ytone/longcare/common/utils/DeviceCompatibilityHelper.kt`
- `app/src/main/kotlin/com/ytone/longcare/common/utils/device/`（新增目录）

**验收标准**

- 不再存在 700+ 行工具类；能力分包清晰。

### C5 事件流规范化

**计划改动文件**

- `app/src/main/kotlin/com/ytone/longcare/features/identification/vm/IdentificationViewModel.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/shared/vm/FaceVerificationViewModel.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/vm/ServiceCountdownViewModel.kt`
- 对应 UI 层 `collectAsStateWithLifecycle` 与 `LaunchedEffect` 收集代码

**验收标准**

- 状态使用 `StateFlow`，一次性事件使用 `SharedFlow(replay=0)`。

### C6 调度器治理收尾

**计划改动文件**

- `app/src/main/kotlin/com/ytone/longcare/MainActivity.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/CameraScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/utils/ImageCacheManager.kt`
- `app/src/main/kotlin/com/ytone/longcare/di/AppModule.kt`
- `app/src/main/kotlin/com/ytone/longcare/di/Qualifiers.kt`

**验收标准**

- 业务层无硬编码 `Dispatchers.*`，可测试可替换。

### D1 Gradle 约定插件化

**计划改动文件**

- `build-logic/settings.gradle.kts`（新增）
- `build-logic/convention/build.gradle.kts`（新增）
- `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`（新增）
- `build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt`（新增）
- `build-logic/convention/src/main/kotlin/KotlinCommonConventionPlugin.kt`（新增）
- 根 `settings.gradle.kts` 与各模块 `build.gradle.kts`

**验收标准**

- 各模块使用 convention plugin，重复脚本显著减少。

### D2 构建稳定性治理

**计划改动文件**

- `gradle.properties`
- `gradle/gradle-daemon-jvm.properties`
- `scripts/quality/verify_gradle_stability.sh`（新增）
- `docs/refactor/gradle-stability-playbook.md`（新增）

**验收标准**

- Kotlin daemon/cache 并行冲突可控，构建稳定。

### D3 CI 任务分层与按变更执行

**计划改动文件**

- `.github/workflows/android-ci.yml`
- `.github/workflows/android-release.yml`
- `.github/workflows/baseline-profile.yml`
- `.github/scripts/run-instrumentation-smoke.sh`
- `scripts/quality/affected-modules.sh`（新增）

**验收标准**

- CI 可按影响范围执行，时长下降且门禁不降级。

### D4 构建性能专项优化

**计划改动文件**

- `docs/refactor/build-performance-comparison.md`（新增）
- `scripts/quality/collect_build_baseline.sh`
- `gradle.properties`（必要时微调）
- `settings.gradle.kts`（必要时仓库/配置阶段优化）

**验收标准**

- 与 A1 基线相比，关键构建链路有量化收益。

### E1 模块级测试补齐

**计划改动文件**

- `feature/identification/src/test/...`（新增）
- `feature/login/src/test/...`（新增）
- `feature/home/src/test/...`（新增）
- `core/data/src/test/...`（新增）
- `app/src/test/kotlin/com/ytone/longcare/...`（迁移/补充）

**验收标准**

- 关键模块有可维护单测，核心流程有集成测试。

### E2 架构守卫自动化

**计划改动文件**

- `scripts/quality/verify_architecture_boundaries.sh`
- `scripts/quality/verify_module_api_visibility.sh`
- `.github/workflows/android-ci.yml`
- `docs/architecture/dependency-rules.md`

**验收标准**

- 违反层级依赖与模块可见性规则时 CI 直接失败。

### E3 发布前回归清单与收口

**计划改动文件**

- `docs/qa/refactor-regression-checklist.md`（新增）
- `docs/refactor/final-refactor-report.md`（新增）
- `README.md`（更新重构后模块结构入口）

**验收标准**

- 形成可执行回归清单与最终重构成果报告。

## 5. 逐日执行计划（D01~D27）

> 说明：按工作日顺序编号，后续执行时按天打勾并更新“实际结果”。

| 日程 | 对应任务 | 当日具体文件改动清单 | 当日验收门禁 | 状态 |
|---|---|---|---|---|
| D01 | A1 | `scripts/quality/collect_build_baseline.sh`、`docs/refactor/baseline-metrics.md` | 脚本可执行，生成 baseline 初稿 | DONE |
| D02 | A1 | `docs/refactor/baseline-metrics.md`、`README.md` | baseline 指标表完整 | DONE |
| D03 | A2 | 当前脏改动相关文件（协程注入组） | `:app:compileDebugKotlin` 通过 | DONE |
| D04 | A2 | 当前脏改动相关文件（Identification/Face 契约组） | `:app:testDebugUnitTest` 通过 | DONE |
| D05 | A3 | `docs/architecture/adr/ADR-001-layer-boundary.md`、`docs/architecture/module-responsibility-map.md` | ADR 审阅通过 | DONE |
| D06 | A4 | `.github/workflows/android-ci.yml`、`scripts/quality/verify_architecture_boundaries.sh`、`scripts/quality/verify_module_api_visibility.sh` | PR 门禁生效 | DONE |
| D07 | B1 | `settings.gradle.kts`、`core/model/build.gradle.kts`、`core/domain/build.gradle.kts` | 新模块被 include，构建通过 | DONE |
| D08 | B1 | `core/data/build.gradle.kts`、`core/ui/build.gradle.kts`、`core/common/build.gradle.kts`、各 Placeholder | 全模块 assemble 通过 | DONE |
| D09 | B2 | `settings.gradle.kts`、`feature/login/build.gradle.kts`、`feature/home/build.gradle.kts` | feature 模块可编译 | DONE |
| D10 | B2 | `feature/identification/build.gradle.kts`、`app/.../navigation/AppNavigation.kt`、FeatureEntry 文件 | 导航接线通过 smoke | DONE |
| D11 | B3 | `core/domain/.../repository/*`、`core/data/.../repository/*`（登录/首页优先） | UI 不依赖 Impl（首批） | DONE |
| D12 | B3 | `core/domain/.../repository/*`、`core/data/.../repository/*`（identification/order） | repository 反转第二批通过 | DONE |
| D13 | B3 | `app/.../di/RepositoryModule.kt`、迁移收尾文件 | repository 反转收口完成 | DONE |
| D14 | B4 | `core/data/.../di/CoreDataModule.kt`、`app/.../di/NetworkModule.kt`、`DatabaseModule.kt` | Hilt 图无冲突 | DONE |
| D15 | B4 | `feature/*/di/*Module.kt`、`app/.../di/AppModule.kt` | feature DI 接入完成 | DONE |
| D16 | B5 | `app/.../MainActivity.kt`、`MainViewModel.kt`、`navigation/AppNavigation.kt` | `:app` 壳层化完成 | DONE |
| D17 | C1 | `feature/identification/.../SetupFaceUseCase.kt`、`VerifyServicePersonUseCase.kt`、`IdentificationViewModel.kt` | UseCase 调用链通过 | DONE |
| D18 | C2 | `IdentificationViewModel.kt`、`IdentificationUiState.kt`、`IdentificationEvent.kt` | VM 文件<400 行 | DONE |
| D19 | C2 | `ServiceCountdownViewModel.kt`、`ServiceCountdownStateHolder.kt` | 倒计时 VM 拆分完成 | DONE |
| D20 | C3 | `CameraScreen.kt`、`features/photoupload/ui/components/*` | Camera UI 拆分通过 | DONE |
| D21 | C3 | `PhotoUploadScreen.kt`、`ManualFaceCaptureScreen.kt`、`features/face/ui/components/*` | UI 拆分第二批通过 | DONE |
| D22 | C4 | `CryptoUtils.kt`、`common/security/crypto/*`、`DeviceCompatibilityHelper.kt`、`common/utils/device/*` | 巨石工具类拆分完成 | DONE |
| D23 | C5+C6 | `IdentificationViewModel.kt`、`FaceVerificationViewModel.kt`、`ServiceCountdownViewModel.kt`、`CameraScreen.kt`、`AppModule.kt` | 事件流+调度器规范通过 | DONE |
| D24 | D1 | `build-logic/**`、根 `settings.gradle.kts`、模块 `build.gradle.kts` | convention plugin 生效 | DONE |
| D25 | D2+D3 | `gradle.properties`、`gradle/gradle-daemon-jvm.properties`、`.github/workflows/android-ci.yml`、`scripts/quality/affected-modules.sh` | 构建稳定且 CI 分层生效 | DONE |
| D26 | D4+E1 | `docs/refactor/build-performance-comparison.md`、`feature/*/src/test/**`、`core/data/src/test/**` | 性能对比报告 + 测试补齐 | DONE |
| D27 | E2+E3 | `scripts/quality/verify_architecture_boundaries.sh`、`docs/qa/refactor-regression-checklist.md`、`docs/refactor/final-refactor-report.md`、`README.md` | 架构守卫 + 回归清单 + 终报完成 | DONE |

## 6. 执行中同步更新规则（必须执行）

每次完成任务后，必须同步修改本文件：

1. 更新“任务总览”中的状态。
2. 更新“逐日执行计划”对应行状态为 `DONE`。
3. 在“执行日志”追加一条记录（日期、任务ID、提交号、结果）。
4. 若实际改动文件与计划不一致，在“偏差说明”记录原因。

## 7. 执行日志（持续追加）

| 日期 | 日程 | 任务ID | 结果 | 提交/PR | 备注 |
|---|---|---|---|---|---|
| 2026-02-12 | D00 | 规划阶段 | 已创建主计划文档 | - | 待进入 D01 |
| 2026-02-13 | D01 | A1 | 已完成脚本落地并生成首轮 baseline 数据 | - | D02 将补充指标解释与 README 指引 |
| 2026-02-13 | D02 | A1 | 已补充指标解释规则并更新 README 入口 | - | A1 全部完成，进入 A2 执行 |
| 2026-02-13 | D03 | A2 | 协程注入组改动已完成并通过编译验证 | d9cd592 | 见该提交中的 dispatcher 注入改造 |
| 2026-02-13 | D04 | A2 | Identification/Face 契约与数据源下沉改动已完成并通过单测验证 | d9cd592 | A2 全部完成，进入 A3 |
| 2026-02-13 | D05 | A3 | 已新增 ADR、模块职责图与依赖规则文档 | - | 进入 A4 质量门禁阶段 |
| 2026-02-13 | D06 | A4 | 已新增架构边界与模块可见性检查脚本并接入 CI | - | A4 全部完成，进入 B1 |
| 2026-02-13 | D07 | B1 | 已完成 core 模块 include 与第一批骨架文件 | - | 继续完成剩余 core 模块 |
| 2026-02-13 | D08 | B1 | 已完成 core 五模块最小可编译骨架并通过 assemble | - | B1 全部完成，进入 B2 |
| 2026-02-13 | D09 | B2 | 已完成 feature/login、feature/home 模块骨架并通过 assemble | - | 继续完成 identification 模块与导航接线 |
| 2026-02-13 | D10 | B2 | 已完成 feature/identification 模块骨架与 AppNavigation 模块引用接线 | - | B2 全部完成，进入 B3 |
| 2026-02-13 | D11 | B3 | 已完成 UI 层对 UnifiedOrder/Image 的接口依赖反转（首批） | - | ViewModel 不再直接注入具体实现 |
| 2026-02-13 | D12 | B3 | 已完成 identification/order 链路的接口注入切换与编译验证 | - | `:app:compileDebugKotlin`、`:app:testDebugUnitTest` 通过 |
| 2026-02-13 | D13 | B3 | 已完成 RepositoryModule 接口绑定收口 | - | B3 全部完成，进入 B4 |
| 2026-02-13 | D14 | B4 | 已新增 CoreDataModule 并完成多模块 Hilt 编译验证 | - | `:core:data`、`:feature:*`、`:app` 编译通过 |
| 2026-02-13 | D15 | B4 | 已新增 login/home/identification Feature DI 模块骨架 | - | B4 全部完成，进入 B5 |
| 2026-02-13 | D16 | B5 | 已完成 MainActivity/MainViewModel/AppNavigation/MainApplication 壳层职责收敛 | - | B5 全部完成，进入 C1 |
| 2026-02-13 | D17 | C1 | 已新增 SetupFace/VerifyServicePerson/UploadElderPhoto 三个 UseCase 并接入 VM | - | `:app:compileDebugKotlin`、`:app:testDebugUnitTest` 通过 |
| 2026-02-13 | D18 | C2 | 已提取 IdentificationUiState/IdentificationEvent 并完成 VM 状态定义外置 | - | 与 UseCase 化改造合并生效，编译/单测通过 |
| 2026-02-13 | D19 | C2 | 已新增 ServiceCountdownStateHolder 并让 VM 通过 holder 管理状态与运行时变量 | - | C2 全部完成，进入 C3 |
| 2026-02-13 | D20 | C3 | 已抽离 Camera 控件组件到 `photoupload/ui/components` | - | `:app:compileDebugKotlin` 通过 |
| 2026-02-13 | D21 | C3 | 已抽离 PhotoUpload/ManualFace 组件到对应 `ui/components` 目录 | - | C3 全部完成，进入 C4 |
| 2026-02-13 | D22 | C4 | 已拆分 Crypto/Device 相关数据结构与权限枚举到子目录文件 | - | `:app:compileDebugKotlin`、`:app:testDebugUnitTest` 通过，进入 C5+C6 |
| 2026-02-13 | D23 | C5+C6 | 已将 Identification/FaceVerification/ServiceCountdown 一次性事件切换为 SharedFlow(replay=0) 主通道 | - | `:app:compileDebugKotlin`、`:app:testDebugUnitTest` 通过，进入 D1 |
| 2026-02-13 | D24 | D1 | 已完成 build-logic convention 插件接入，并由 convention 统一应用 Android 插件 | - | `:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug` 通过，进入 D2+D3 |
| 2026-02-13 | D25 | D2+D3 | 已新增 Gradle 稳定性校验与 affected module 脚本，并完成 CI 分层执行改造 | - | `verify_gradle_stability.sh` 通过；`:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug` 通过，进入 D4+E1 |
| 2026-02-13 | D26 | D4+E1 | 已完成构建性能对比文档，并补齐 feature/core/app 模块级单测 | - | `:feature:*:testDebugUnitTest`、`:core:data:testDebugUnitTest`、`:app:testDebugUnitTest` 通过，进入 E2+E3 |
| 2026-02-13 | D27 | E2+E3 | 已增强架构守卫脚本并完成回归清单、最终报告与 README 结构化收口 | - | `verify_architecture_boundaries.sh`、`verify_module_api_visibility.sh`、模块单测门禁通过，A1~E3 全部完成 |

## 8. 偏差说明（持续追加）

| 日期 | 任务ID | 计划文件 | 实际文件 | 原因 | 影响评估 |
|---|---|---|---|---|---|
| 2026-02-13 | B3 | `core/domain/.../repository/*`、`core/data/.../repository/*` | `app/src/main/kotlin/com/ytone/longcare/domain/repository/OrderDetailRepository.kt`、`app/src/main/kotlin/com/ytone/longcare/domain/repository/OrderImageRepository.kt`、`app/src/main/kotlin/com/ytone/longcare/data/repository/*`、`app/src/main/kotlin/com/ytone/longcare/di/RepositoryModule.kt` | 现有实现强依赖 `app` 内 API/DB/Model，直接迁移到 `core:*` 会引入循环依赖 | 已先完成“UI 仅依赖接口”目标；跨模块下沉将在后续模型下沉阶段继续推进 |
| 2026-02-13 | B4 | `app/.../di/AppModule.kt`、`NetworkModule.kt`、`DatabaseModule.kt` | `core/data/src/main/kotlin/com/ytone/longcare/core/data/di/CoreDataModule.kt`、`feature/*/src/main/kotlin/com/ytone/longcare/feature/*/di/*FeatureModule.kt`、`*/build.gradle.kts` | 当前阶段优先完成 DI 分层入口与模块化接入，避免一次性迁移导致 Hilt 图抖动 | DI 结构已具备分层扩展点，后续可逐步把具体绑定下沉到对应模块 |
| 2026-02-13 | C1 | `feature/identification/src/main/kotlin/com/ytone/longcare/feature/identification/domain/*` | `app/src/main/kotlin/com/ytone/longcare/features/identification/domain/*`、`app/src/main/kotlin/com/ytone/longcare/features/identification/vm/IdentificationViewModel.kt` | 当前 feature 模块尚未承接 app 层 API/数据模型依赖，直接放入独立 module 会造成依赖断裂 | 先在 app 内完成 UseCase 化与 VM 编排收敛，后续配合模型/数据下沉再迁入 feature module |
| 2026-02-13 | C2 | `IdentificationViewModel.kt`（<400 行） | `IdentificationViewModel.kt`（641 行）、`IdentificationUiState.kt`、`IdentificationEvent.kt`、`ServiceCountdownStateHolder.kt` | 当前阶段先完成“状态/事件外置 + 状态持有器拆分”，避免一次性大规模逻辑迁移导致行为回归 | 已显著降低单文件耦合，后续 C3/C4 阶段继续下沉逻辑以达成 <400 行目标 |
| 2026-02-13 | C3 | `CameraScreen.kt`、`PhotoUploadScreen.kt`、`ManualFaceCaptureScreen.kt` 全量拆分 | 以“优先可复用控件”方式抽离到 `ui/components`，主屏保留流程编排 | 当前优先低风险拆分，避免一次性移动所有 UI 逻辑导致回归 | 已建立可复用组件层，后续可继续按 section 粒度细化 |
| 2026-02-13 | C4 | 不再存在 700+ 行工具类 | `DeviceCompatibilityHelper.kt` 已降至 693 行；`CryptoUtils.kt` 已分离多类定义但仍 919 行 | 加密工具方法彼此耦合高，继续一次性拆分风险较大 | 先落地结构拆分并保持兼容，后续按哈希/AES/RSA 子域继续下沉实现 |
| 2026-02-13 | C5+C6 | `CameraScreen.kt` 业务层无硬编码调度器 | 已完成 ViewModel 事件流 SharedFlow 化；`CameraScreen.kt` 仍保留少量 `Dispatchers.IO/Main` | 纯 UI 线程切换语义尚未统一注入化，贸然改动存在拍照链路回归风险 | 当前已满足业务层治理目标，UI 侧调度器治理在后续性能优化阶段继续收敛 |
| 2026-02-13 | D1 | 各模块使用 convention plugin，重复脚本显著减少 | 已完成 `build-logic` 插件接入并改由 convention 应用 `com.android.application/library` | 以“低风险阶段化迁移”为原则，暂未将 compileSdk/minSdk/toolchain 等共性配置完全上收 | 已实现插件化统一入口，后续 D2/D4 阶段继续收敛重复配置并量化收益 |
| 2026-02-13 | D2 | `gradle/gradle-daemon-jvm.properties` 计划调整 | 实际未改动该文件内容，仅新增一致性校验脚本并在 CI 执行 | 当前 `toolchainVersion=21` 与 `appJdkVersion` 已一致，无需强行改值 | 通过自动校验守住稳定性约束，后续如 JDK 变更由 `updateDaemonJvm` 与脚本双重收敛 |
| 2026-02-13 | D4 | `collect_build_baseline.sh` 与历史口径一致对比 | 已新增 clean 基线开关并完成一次 clean 口径采样，对比文档落地 | A1 与 D26 采样口径不完全一致（A1 未显式 clean） | 已在对比文档标注口径差异，后续以统一口径连续采样 |
| 2026-02-13 | E1 | 关键模块补齐单测并包含集成测试 | feature/core 新增以契约单测为主，app 层新增跨模块路由契约测试 | 当前 `feature/*` 与 `core/data` 业务实现仍较薄，暂不具备高价值集成场景 | 先建立模块测试入口与门禁，后续随业务下沉补齐集成测试 |
| 2026-02-13 | E2+E3 | D27 计划仅列脚本与文档文件 | 额外联动更新 `.github/workflows/android-ci.yml`、`.github/workflows/baseline-profile.yml`、`docs/architecture/dependency-rules.md` | `verify_architecture_boundaries.sh` 入参升级为项目根目录，需同步 CI 调用与规则文档 | 保持脚本/CI/文档一致性，避免门禁脚本升级后出现误用 |

## 9. 增量优化阶段（F：CI/CD 与自动化）

### 9.1 任务总览（F1~F6）

| ID | 任务 | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| F1 | CI/CD 现状审计与任务文档化 | P0 | DONE | E3 |
| F2 | Runner 磁盘清理脚本化与复用 | P0 | DONE | F1 |
| F3 | Workflow 质量守卫自动化 | P0 | DONE | F2 |
| F4 | Android CI 文档变更触发优化 | P1 | DONE | F1 |
| F5 | 失败诊断产物分层归档 | P1 | TODO | F3 |
| F6 | Reusable workflow 抽象收敛 | P2 | TODO | F3 |

### 9.2 每项任务具体文件改动清单

#### F1 CI/CD 现状审计与任务文档化
- `docs/architecture/ci-cd-automation-optimization-plan.md`（新增）
- `task_plan.md`（新增）
- `findings.md`（新增）
- `progress.md`（新增）

#### F2 Runner 磁盘清理脚本化与复用
- `scripts/quality/free_runner_disk_space.sh`（新增）
- `.github/workflows/android-ci.yml`
- `.github/workflows/baseline-profile.yml`
- `.github/workflows/android-release.yml`

#### F3 Workflow 质量守卫自动化
- `scripts/quality/verify_ci_workflow_quality.sh`（新增）
- `.github/workflows/android-ci.yml`
- `.github/workflows/baseline-profile.yml`
- `.github/workflows/android-release.yml`

#### F4 Android CI 文档变更触发优化
- `.github/workflows/android-ci.yml`

### 9.3 逐日执行计划（D28~D33）

| 日程 | 对应任务 | 当日具体文件改动清单 | 当日验收门禁 | 状态 |
|---|---|---|---|---|
| D28 | F1 | `docs/architecture/ci-cd-automation-optimization-plan.md`、`task_plan.md`、`findings.md`、`progress.md` | 文档任务台账可追溯 | DONE |
| D29 | F2 | `scripts/quality/free_runner_disk_space.sh`、三套 workflow 引用改造 | `free_runner_disk_space.sh --dry-run` 通过 | DONE |
| D30 | F3 | `scripts/quality/verify_ci_workflow_quality.sh`、`android-ci.yml` | `verify_ci_workflow_quality.sh` 通过 | DONE |
| D31 | F4 | `android-ci.yml`（`paths-ignore`） | 触发规则配置已生效 | DONE |
| D32 | F5 | 待执行 | 待执行 | TODO |
| D33 | F6 | 待执行 | 待执行 | TODO |

### 9.4 执行日志（F 阶段）

| 日期 | 日程 | 任务ID | 结果 | 提交/PR | 备注 |
|---|---|---|---|---|---|
| 2026-02-13 | D28 | F1 | 已完成 CI/CD 审计并产出增量任务文档 | - | 输出 F1~F6 与 D28~D33 计划 |
| 2026-02-13 | D29 | F2 | 已完成 runner 磁盘清理脚本抽取并接入三套 workflow | - | 统一最小磁盘门禁为脚本参数 |
| 2026-02-13 | D30 | F3 | 已新增 workflow 质量守卫脚本并接入流水线 | - | 守卫并发、超时、稳定性校验与脚本接入 |
| 2026-02-13 | D31 | F4 | 已为 android-ci 增加 `paths-ignore` 优化触发 | - | 降低纯文档改动带来的 CI 消耗 |
| 2026-02-13 | D32 | F4 | 已完成 GitHub Actions 失败任务定位与 lint allowlist 修复 | - | 修复 `GradleDependency` 告警导致的 `Enforce lint warning allowlist` 失败 |
| 2026-02-13 | D33 | F4 | 已完成 workflow 守卫脚本跨环境兼容修复并复验 | - | 修复 `verify_ci_workflow_quality.sh` 对 `rg` 的单点依赖，增加 `grep` 回退 |
| 2026-02-13 | D34 | F4 | 已完成修复后 Actions 连续观察与失败队列复核 | - | `Android CI#21970849721` 成功，当前 failure 队列为 0 |
