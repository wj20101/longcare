# Tracks

## Active

| ID | Title | Status | Priority | Notes |
|---|---|---|---|---|
| TRACK-ARCH-001 | 持续模块下沉与 `app` 壳层收敛 | in-progress | high | 目标结构已定义，现实代码仍有大量业务留在 `app` |
| TRACK-QA-001 | CI / 质量门禁持续稳态维护 | in-progress | high | 已有大量脚本与 workflow 守卫，后续改动需要避免回退 |
| TRACK-DOC-001 | 项目上下文与架构文档同步 | in-progress | medium | README 与实际模块清单已有轻微偏差 |

## Completed

| ID | Title | Completed | Evidence |
|---|---|---|---|
| TRACK-REF-001 | A1~E3 重构主计划 | completed | [docs/refactor/final-refactor-report.md](/Users/wajie/StudioProjects/longcare/docs/refactor/final-refactor-report.md) |
| TRACK-CI-001 | CI/CD 自动化优化首轮落地 | completed | [task_plan.md](/Users/wajie/StudioProjects/longcare/task_plan.md) |
| TRACK-CONTEXT-001 | `conductor` 项目上下文初始化 | completed | [conductor/index.md](/Users/wajie/StudioProjects/longcare/conductor/index.md) |

## Backlog Signals

- README 模块说明同步到 `feature:location`、`feature:photoupload`、`feature:servicecountdown`
- 继续把 `app` 中的 feature UI / ViewModel / Android 组件实现评估并下沉
- 对关键业务链路补更高价值的集成测试或 smoke 验证
