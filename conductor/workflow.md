# Workflow

## Methodology

当前仓库的工作方式更接近“持续重构 + 质量门禁驱动”：

- 先通过文档与边界规则明确目标结构。
- 在已有业务不回退的前提下渐进式下沉模块。
- 每次改动后通过本地脚本和 GitHub Actions 做构建、lint、测试和架构检查。

## Local Commands

常用本地命令来自 [README.md](/Users/wajie/StudioProjects/longcare/README.md)：

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
./gradlew :app:generateBaselineProfile
bash scripts/quality/run_quality_gate.sh --project-root .
```

## Quality Gates

| Gate | Requirement |
|---|---|
| Build | `:app:assembleDebug` 可通过 |
| Lint | `:app:lintDebug` 可通过 |
| Unit Test | 相关模块单测可通过 |
| Architecture | 通过 `verify_architecture_boundaries.sh` 等守卫 |
| Workflow Quality | CI workflow 不得破坏既有触发与版本约束 |

## CI/CD

当前关键 workflow：

| Workflow | Purpose |
|---|---|
| `Android CI` | 按受影响范围执行验证、产物上传、质量快照收集 |
| `Baseline Profile` | 生成并校验 baseline profile 产物 |
| `Android Release` | 生成 release APK/AAB、校验并创建 GitHub Release |

`Android CI` 的关键特征：

- 支持 `affected scope` 检测
- PR/Push/手动触发
- 构建后统一执行质量门禁脚本
- 可按条件执行 instrumentation smoke

## Git Conventions

- 当前观察到工作分支为 `master`，CI 对 `main/master/develop` 都有支持。
- 仓库中已有较完整的阶段性文档和执行日志，适合“代码改动 + 文档同步”一起提交。
- 做重构时优先保留历史行为，再逐步收敛边界，不建议大范围无验证搬迁。

## Architecture Rules To Preserve

- `feature` 只能依赖领域接口或允许的 `core` 模块，不能直接依赖 data impl。
- `domain` 保持纯 Kotlin，不引入 `android.*`。
- ViewModel 负责状态编排，不直接承载网络细节。
- 持续状态使用 `StateFlow`，一次性事件使用 `SharedFlow(replay = 0)`。

## Session Start Checklist

1. 查看 [conductor/product.md](/Users/wajie/StudioProjects/longcare/conductor/product.md)。
2. 查看当前相关模块的 build 文件和导航接线点。
3. 识别改动是否会触发架构守卫、CI workflow 守卫或 baseline 相关校验。
4. 修改前确认 README、架构文档、`conductor` 文档是否需要同步。

## Session End Checklist

1. 运行最小必要验证命令。
2. 若改变模块边界、依赖或流程，更新对应文档。
3. 若完成一条明显主线，更新 [conductor/tracks.md](/Users/wajie/StudioProjects/longcare/conductor/tracks.md)。
