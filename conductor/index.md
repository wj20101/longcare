# LongCare Context Index

本目录用于沉淀 LongCare 项目的稳定上下文，减少后续会话重复摸底成本。
`conductor/index.md` 是内部稳定地图：连接当前真相、执行节奏与历史材料。

## Recommended Session Path

默认新会话应先读 `AGENT.md`。  
只有在需要更深上下文时，再从这里展开。

推荐展开顺序：

1. `AGENT.md`
2. `conductor/index.md`（当前页）
3. `docs/architecture/session-handoff-guide.md`
4. 按需再读：
   - `docs/architecture/system-overview.md`
   - `docs/architecture/business-capability-map.md`
   - `docs/architecture/ui-and-screen-map.md`
   - `docs/architecture/roadmap-and-open-gaps.md`
   - `conductor/product.md`
   - `conductor/tech-stack.md`
   - `conductor/workflow.md`

## Current Truth Set

- [system-overview.md](/Users/wajie/StudioProjects/longcare/docs/architecture/system-overview.md): 当前运行形态、模块拓扑、导航组装、平台边界与外部集成
- [business-capability-map.md](/Users/wajie/StudioProjects/longcare/docs/architecture/business-capability-map.md): 业务能力状态、主入口路由与关键依赖
- [ui-and-screen-map.md](/Users/wajie/StudioProjects/longcare/docs/architecture/ui-and-screen-map.md): 路由分组、屏幕清单、模块归属与迁移现状
- [roadmap-and-open-gaps.md](/Users/wajie/StudioProjects/longcare/docs/architecture/roadmap-and-open-gaps.md): 已交付、在途改造、技术债与功能缺口
- [session-handoff-guide.md](/Users/wajie/StudioProjects/longcare/docs/architecture/session-handoff-guide.md): 新会话恢复步骤与 5 分钟检查清单
- [product.md](/Users/wajie/StudioProjects/longcare/conductor/product.md): 产品目标与业务范围
- [tech-stack.md](/Users/wajie/StudioProjects/longcare/conductor/tech-stack.md): 技术与构建基线
- [workflow.md](/Users/wajie/StudioProjects/longcare/conductor/workflow.md): 本地验证、质量门禁、CI/CD 约定

## Internal Artifacts

- [product-guidelines.md](/Users/wajie/StudioProjects/longcare/conductor/product-guidelines.md): 术语与文案表达约束
- [tracks.md](/Users/wajie/StudioProjects/longcare/conductor/tracks.md): 当前主线和执行状态（执行/进度视角，不是稳定事实）

## Execution History (Not Primary Truth)

- `docs/superpowers/*`：阶段计划与执行记录，主要用于追溯。
- 历史 refactor/qa/security 报告：用于背景与证据，不作为当前实现真相入口。
- 当历史文档与当前代码或 “Current Truth Set” 冲突时，以当前代码和主真相文档为准。

## Notes

- 本目录记录的是“当前观察到的真实状态”，不是理想目标状态。
- 如果新增或调整模块、依赖、流程、质量门禁，请同步更新对应真相文档并回链到本索引。
