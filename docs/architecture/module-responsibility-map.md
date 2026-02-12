# Module Responsibility Map

## 目标结构

| 模块 | 责任 | 允许依赖 | 禁止依赖 |
|---|---|---|---|
| `:app` | 应用壳层、导航组装、启动配置 | `:core:*`, `:feature:*` | 业务实现细节 |
| `:core:model` | 通用模型与值对象 | Kotlin stdlib | Android framework |
| `:core:domain` | 用例、Repository 接口、领域规则 | `:core:model` | Data 实现、Android framework |
| `:core:data` | Repository 实现、数据源访问 | `:core:domain`, `:core:model` | Feature/UI |
| `:core:ui` | 主题、通用 UI 组件 | `:core:model` | Data 实现 |
| `:core:common` | 通用基础能力（日志、错误模型、调度器约定） | `:core:model` | Feature |
| `:feature:login` | 登录业务 UI 与状态编排 | `:core:domain`, `:core:ui`, `:core:model` | Data 实现 |
| `:feature:home` | 首页业务 UI 与状态编排 | `:core:domain`, `:core:ui`, `:core:model` | Data 实现 |
| `:feature:identification` | 身份识别业务 UI 与状态编排 | `:core:domain`, `:core:ui`, `:core:model` | Data 实现 |

## 当前迁移优先级

1. 第一批：`:feature:identification`（当前复杂度最高，收益最大）。
2. 第二批：`:feature:login`、`:feature:home`。
3. 第三批：其余 feature 模块按业务耦合度递进迁移。

## 迁移判定标准

1. Feature 内不再出现 `data.repository.*Impl` 引用。
2. `:app` 不承载业务流程实现。
3. Repository 接口由 Domain 暴露，Data 仅实现。
