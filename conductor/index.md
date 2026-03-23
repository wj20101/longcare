# LongCare Context Index

本目录用于沉淀 LongCare 项目的稳定上下文，减少后续会话重复摸底成本。

## Artifacts

- [product.md](/Users/wajie/StudioProjects/longcare/conductor/product.md): 产品目标、核心业务能力、用户与路线图
- [product-guidelines.md](/Users/wajie/StudioProjects/longcare/conductor/product-guidelines.md): 术语、文档表达与用户提示约定
- [tech-stack.md](/Users/wajie/StudioProjects/longcare/conductor/tech-stack.md): 当前技术栈、依赖、工程基础设施
- [workflow.md](/Users/wajie/StudioProjects/longcare/conductor/workflow.md): 本地开发、质量门禁、CI/CD 与发布约定
- [tracks.md](/Users/wajie/StudioProjects/longcare/conductor/tracks.md): 当前已识别的工作主线与完成状态

## Fast Start

1. 先读 [product.md](/Users/wajie/StudioProjects/longcare/conductor/product.md) 了解业务域和模块边界。
2. 再读 [tech-stack.md](/Users/wajie/StudioProjects/longcare/conductor/tech-stack.md) 确认构建链路、依赖与运行前提。
3. 开工前检查 [workflow.md](/Users/wajie/StudioProjects/longcare/conductor/workflow.md) 中的验证命令。
4. 若需要判断历史背景或当前主线，查看 [tracks.md](/Users/wajie/StudioProjects/longcare/conductor/tracks.md)。

## Source Documents

当前上下文主要基于以下仓库事实整理：

- [README.md](/Users/wajie/StudioProjects/longcare/README.md)
- [settings.gradle.kts](/Users/wajie/StudioProjects/longcare/settings.gradle.kts)
- [gradle/libs.versions.toml](/Users/wajie/StudioProjects/longcare/gradle/libs.versions.toml)
- [docs/architecture/module-responsibility-map.md](/Users/wajie/StudioProjects/longcare/docs/architecture/module-responsibility-map.md)
- [docs/architecture/dependency-rules.md](/Users/wajie/StudioProjects/longcare/docs/architecture/dependency-rules.md)
- [docs/architecture/project-optimization-refactor-master-plan.md](/Users/wajie/StudioProjects/longcare/docs/architecture/project-optimization-refactor-master-plan.md)
- [docs/refactor/final-refactor-report.md](/Users/wajie/StudioProjects/longcare/docs/refactor/final-refactor-report.md)
- [progress.md](/Users/wajie/StudioProjects/longcare/progress.md)

## Notes

- 本目录记录的是“当前观察到的真实状态”，不是理想目标状态。
- 如果后续新增模块、依赖、流程或质量门禁，应同步更新对应文档。
