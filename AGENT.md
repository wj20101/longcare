# LongCare Agent Guide

## 项目概览

LongCare 是一个面向护理/长护服务执行场景的 Android 客户端，主链路覆盖：

- 登录
- 服务入口与派单处理
- 身份核验
- 到岗定位
- 拍照上传
- 服务计时
- 服务结束确认

这个仓库当前处于“持续重构 + 质量门禁驱动”的阶段。目标不是一次性重写，而是在不破坏既有业务行为的前提下，持续把历史实现从 `:app` 下沉到 `:feature/*` 和 `:core/*`。

## 技术基线

- Kotlin `2.3.20`
- JDK Toolchain `21`
- Android Gradle Plugin `9.1.0`
- Jetpack Compose BOM `2026.03.00`
- Hilt、Room、WorkManager
- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 24`

## 模块职责

- `:app`：应用壳层、导航组装、Android 组件声明、少量历史业务实现
- `:core:model`：通用模型与值对象
- `:core:domain`：领域接口、用例、规则
- `:core:data`：数据实现、数据源访问、DI 绑定
- `:core:ui`：通用 UI 能力
- `:core:common`：日志、错误模型、调度器约定等基础能力
- `:feature:login` / `:feature:home` / `:feature:identification`
- `:feature:location` / `:feature:photoupload` / `:feature:servicecountdown`

优先把 `:app` 当作壳层看待。新业务实现不要继续堆回 `:app`。

## 常用命令

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
./gradlew :app:generateBaselineProfile
bash scripts/quality/run_quality_gate.sh --project-root .
```

补充检查：

```bash
bash scripts/quality/verify_architecture_boundaries.sh .
bash scripts/quality/verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare .
bash scripts/quality/verify_ci_workflow_quality.sh
bash scripts/quality/verify_gradle_stability.sh
```

## 架构约束

- `feature` 只能依赖领域接口或允许的 `core` 模块，不能直接依赖 data 实现。
- `:core:domain` 必须保持纯 Kotlin，禁止引入 `android.*`。
- `:app` 不应继续承载新的业务流程实现。
- ViewModel 只做状态编排，不直接承载网络细节。
- 持续状态使用 `StateFlow`。
- 一次性事件使用 `SharedFlow(replay = 0)`。
- 业务协程调度器通过 DI 注入，避免硬编码 `Dispatchers.*`。
- Repository 接口放在 Domain，命名为 `*Repository`；实现放在 Data，命名为 `*RepositoryImpl`。

## 工作方式

开始较大改动前，先看这些文档：

- `conductor/product.md`
- `conductor/tech-stack.md`
- `conductor/workflow.md`
- `docs/architecture/dependency-rules.md`
- `docs/architecture/module-responsibility-map.md`

执行时遵循这些原则：

- 先保行为，再做结构收敛。
- 改动尽量小而闭环，避免无验证的大搬迁。
- 如果修改了模块边界、流程、命令或 CI 行为，同步更新对应文档。
- 如果发现 README 与实际模块不一致，优先相信实际构建文件和 `conductor/` 文档，并顺手修正文档漂移。

## 改动后验证建议

- 只改 UI / 单个 feature：至少跑相关模块构建、`lint`、单测。
- 改模块依赖、架构边界、共享基础层：跑 `run_quality_gate.sh` 和架构守卫脚本。
- 改 `.github/workflows`、Gradle、版本配置：补跑 workflow/Gradle 稳定性检查。
- 改性能或启动链路：关注 `baselineprofile` 相关任务与产物。

## CI / Release Awareness

仓库当前关键 workflow：

- `Android CI`
- `Baseline Profile`
- `Android Release`
- `Face SDK Migration Check`
- `CI Health Monitor`

发布链路依赖 release signing secrets；不要把 keystore 或敏感配置提交进仓库。

## 文档同步

以下类型的改动通常需要同步文档：

- 新增或调整模块职责
- 修改质量门禁命令
- 调整 CI / release 行为
- 明确新的架构例外或迁移策略

优先更新：

- `README.md`
- `conductor/*.md`
- `docs/architecture/*.md`
- `docs/qa/*.md`

## 一句话规则

在这个仓库里，最重要的不是“把代码改动做大”，而是“在业务不回退的前提下，让边界更清晰、验证更完整、文档更同步”。
