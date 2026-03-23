# Product Guidelines

## Voice & Tone

- 项目文档优先直接、准确、可执行。
- 面向开发者时，优先写清楚模块边界、约束和验证命令。
- 面向用户的提示文案保持短句，先说明结果，再说明下一步动作。

## Terminology

| Term | Use | Don't Use |
|---|---|---|
| 壳层 | `app` 中仅负责启动、导航、组装的部分 | 宏观业务模块 |
| 领域接口 | `core:domain` 中的 repository / rule / contract | 数据实现 |
| 数据实现 | `core:data` 中的 repository impl、network、db | 领域层 |
| feature 模块 | 单个业务能力的 UI/状态编排单元 | 任意 app 包目录 |
| 服务单 | 业务服务订单/执行对象 | 工单、任务单（除非代码已明确） |

## Documentation Conventions

- 引用模块时使用 Gradle path，例如 `:core:data`、`:feature:servicecountdown`。
- 描述验证命令时给出完整可运行命令。
- 区分“目标结构”和“当前现实”，避免把规划误写成现状。

## User-Facing Error Style

格式：`发生了什么 + 用户现在该做什么`

示例：

- `登录已失效，请重新登录`
- `定位权限未开启，请先授权后重试`
- `图片上传失败，请检查网络后重试`

## Architectural Messaging

- 讨论架构时默认强调依赖方向，而不是目录归属。
- 讨论重构时默认先说明“已完成什么”与“仍留在 app 的内容”。
