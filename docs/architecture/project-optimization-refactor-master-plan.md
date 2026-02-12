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
| A3 | 架构约束文档化 | P0 | TODO | A2 |
| A4 | 质量门禁基线 | P0 | TODO | A3 |
| B1 | Core 模块骨架搭建 | P0 | TODO | A4 |
| B2 | Feature 模块骨架搭建 | P0 | TODO | B1 |
| B3 | Repository 依赖反转改造 | P0 | TODO | B2 |
| B4 | DI 拆分重组 | P0 | TODO | B3 |
| B5 | App 壳层收敛 | P1 | TODO | B4 |
| C1 | Identification 流程 UseCase 化 | P0 | TODO | B5 |
| C2 | 超大 ViewModel 拆分 | P0 | TODO | C1 |
| C3 | 超大 Composable 拆分 | P0 | TODO | C2 |
| C4 | 巨石工具类拆分 | P1 | TODO | C3 |
| C5 | 事件流规范化（StateFlow/SharedFlow） | P1 | TODO | C4 |
| C6 | 调度器治理收尾 | P1 | TODO | C5 |
| D1 | Gradle 约定插件化（build-logic） | P0 | TODO | C6 |
| D2 | 构建稳定性治理 | P1 | TODO | D1 |
| D3 | CI 任务分层与按变更执行 | P1 | TODO | D2 |
| D4 | 构建性能专项优化 | P1 | TODO | D3 |
| E1 | 模块级测试补齐 | P0 | TODO | D4 |
| E2 | 架构守卫自动化 | P1 | TODO | E1 |
| E3 | 发布前回归清单与收口 | P1 | TODO | E2 |

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
| D05 | A3 | `docs/architecture/adr/ADR-001-layer-boundary.md`、`docs/architecture/module-responsibility-map.md` | ADR 审阅通过 | TODO |
| D06 | A4 | `.github/workflows/android-ci.yml`、`scripts/quality/verify_architecture_boundaries.sh`、`scripts/quality/verify_module_api_visibility.sh` | PR 门禁生效 | TODO |
| D07 | B1 | `settings.gradle.kts`、`core/model/build.gradle.kts`、`core/domain/build.gradle.kts` | 新模块被 include，构建通过 | TODO |
| D08 | B1 | `core/data/build.gradle.kts`、`core/ui/build.gradle.kts`、`core/common/build.gradle.kts`、各 Placeholder | 全模块 assemble 通过 | TODO |
| D09 | B2 | `settings.gradle.kts`、`feature/login/build.gradle.kts`、`feature/home/build.gradle.kts` | feature 模块可编译 | TODO |
| D10 | B2 | `feature/identification/build.gradle.kts`、`app/.../navigation/AppNavigation.kt`、FeatureEntry 文件 | 导航接线通过 smoke | TODO |
| D11 | B3 | `core/domain/.../repository/*`、`core/data/.../repository/*`（登录/首页优先） | UI 不依赖 Impl（首批） | TODO |
| D12 | B3 | `core/domain/.../repository/*`、`core/data/.../repository/*`（identification/order） | repository 反转第二批通过 | TODO |
| D13 | B3 | `app/.../di/RepositoryModule.kt`、迁移收尾文件 | repository 反转收口完成 | TODO |
| D14 | B4 | `core/data/.../di/CoreDataModule.kt`、`app/.../di/NetworkModule.kt`、`DatabaseModule.kt` | Hilt 图无冲突 | TODO |
| D15 | B4 | `feature/*/di/*Module.kt`、`app/.../di/AppModule.kt` | feature DI 接入完成 | TODO |
| D16 | B5 | `app/.../MainActivity.kt`、`MainViewModel.kt`、`navigation/AppNavigation.kt` | `:app` 壳层化完成 | TODO |
| D17 | C1 | `feature/identification/.../SetupFaceUseCase.kt`、`VerifyServicePersonUseCase.kt`、`IdentificationViewModel.kt` | UseCase 调用链通过 | TODO |
| D18 | C2 | `IdentificationViewModel.kt`、`IdentificationUiState.kt`、`IdentificationEvent.kt` | VM 文件<400 行 | TODO |
| D19 | C2 | `ServiceCountdownViewModel.kt`、`ServiceCountdownStateHolder.kt` | 倒计时 VM 拆分完成 | TODO |
| D20 | C3 | `CameraScreen.kt`、`features/photoupload/ui/components/*` | Camera UI 拆分通过 | TODO |
| D21 | C3 | `PhotoUploadScreen.kt`、`ManualFaceCaptureScreen.kt`、`features/face/ui/components/*` | UI 拆分第二批通过 | TODO |
| D22 | C4 | `CryptoUtils.kt`、`common/security/crypto/*`、`DeviceCompatibilityHelper.kt`、`common/utils/device/*` | 巨石工具类拆分完成 | TODO |
| D23 | C5+C6 | `IdentificationViewModel.kt`、`FaceVerificationViewModel.kt`、`ServiceCountdownViewModel.kt`、`CameraScreen.kt`、`AppModule.kt` | 事件流+调度器规范通过 | TODO |
| D24 | D1 | `build-logic/**`、根 `settings.gradle.kts`、模块 `build.gradle.kts` | convention plugin 生效 | TODO |
| D25 | D2+D3 | `gradle.properties`、`gradle/gradle-daemon-jvm.properties`、`.github/workflows/android-ci.yml`、`scripts/quality/affected-modules.sh` | 构建稳定且 CI 分层生效 | TODO |
| D26 | D4+E1 | `docs/refactor/build-performance-comparison.md`、`feature/*/src/test/**`、`core/data/src/test/**` | 性能对比报告 + 测试补齐 | TODO |
| D27 | E2+E3 | `scripts/quality/verify_architecture_boundaries.sh`、`docs/qa/refactor-regression-checklist.md`、`docs/refactor/final-refactor-report.md`、`README.md` | 架构守卫 + 回归清单 + 终报完成 | TODO |

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

## 8. 偏差说明（持续追加）

| 日期 | 任务ID | 计划文件 | 实际文件 | 原因 | 影响评估 |
|---|---|---|---|---|---|
| - | - | - | - | - | - |
