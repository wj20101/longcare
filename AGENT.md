# LongCare Collaboration Entry

`AGENT.md` 是协作入口，不是完整架构说明。  
具体事实请以 `docs/architecture/*` 与 `conductor/*` 为准。

## Document Roles

- `AGENT.md`：协作入口（你现在正在看的文件），告诉你先读什么、信什么、怎么开工。
- `README.md`：对外/新同事的轻量快速开始，不承载完整内部治理细节。
- `conductor/index.md`：内部稳定索引，连接“当前真相”与“执行历史”。

## Fresh-Session Reading Order (Exact)

1. `AGENT.md`
2. `conductor/index.md`
3. `docs/architecture/session-handoff-guide.md`
4. `docs/architecture/system-overview.md`
5. `docs/architecture/business-capability-map.md`
6. `docs/architecture/ui-and-screen-map.md`
7. `docs/architecture/roadmap-and-open-gaps.md`
8. `conductor/product.md`
9. `conductor/tech-stack.md`
10. `conductor/workflow.md`

如果你在这些文档与历史计划之间看到冲突：优先相信上述顺序里的文档 + 当前代码。

## Primary Truth Set

- `docs/architecture/system-overview.md`
- `docs/architecture/business-capability-map.md`
- `docs/architecture/ui-and-screen-map.md`
- `docs/architecture/roadmap-and-open-gaps.md`
- `docs/architecture/session-handoff-guide.md`
- `conductor/product.md`
- `conductor/tech-stack.md`
- `conductor/workflow.md`

## Useful Progress Context

- `conductor/tracks.md`：当前主线、执行状态和推进节奏（进度视角，不是稳定事实）

## Execution History (Read Only If Needed)

- `docs/superpowers/*`：计划、阶段任务、执行痕迹（历史上下文，不是主真相）
- 其他历史性 refactor/report/checklist 文档：用于追溯，不作为当前实现事实来源

## Guardrails For Collaboration

- 保行为稳定优先，再做结构收敛。
- 小步改动 + 最小必要验证，避免大范围无验证搬迁。
- 改动模块边界、流程、CI/命令后，必须同步更新相关文档。
- 不要在 `README.md` / `AGENT.md` 里复制整份架构事实，统一链接到主真相文档。

## Quick Validation Commands

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
bash scripts/quality/run_quality_gate.sh --project-root .
```

按需补充：

```bash
bash scripts/quality/verify_architecture_boundaries.sh .
bash scripts/quality/verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare .
bash scripts/quality/verify_ci_workflow_quality.sh
bash scripts/quality/verify_gradle_stability.sh
```

## One-Line Rule

在这个仓库里，优先交付“可验证且文档同步的正确改动”，而不是“看起来很大的改动”。
