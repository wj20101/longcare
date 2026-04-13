# LongCare

面向护理/长护服务执行场景的 Android 客户端。

## Quick Start

常用本地命令：

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
bash scripts/quality/run_quality_gate.sh --project-root .
```

## Where To Read Next

- 协作入口（推荐先读）：`AGENT.md`
- 内部上下文索引：`conductor/index.md`
- 会话恢复指南：`docs/architecture/session-handoff-guide.md`

## Current Truth Docs

- 系统结构：`docs/architecture/system-overview.md`
- 业务能力：`docs/architecture/business-capability-map.md`
- 界面与路由：`docs/architecture/ui-and-screen-map.md`
- 路线图与缺口：`docs/architecture/roadmap-and-open-gaps.md`
- 产品/技术/流程基线：
  - `conductor/product.md`
  - `conductor/tech-stack.md`
  - `conductor/workflow.md`

## Useful Progress Context

- `conductor/tracks.md`：当前主线、推进状态和执行节奏（进度视角，不是稳定事实）

## Notes

- 本 README 保持轻量；详细内部治理与执行语境请进入 `AGENT.md` 与 `conductor/index.md`。
- 历史计划与执行痕迹（如 `docs/superpowers/*`）可用于追溯，但不作为当前实现主真相。
