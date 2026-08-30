## Why

`:feature:identification` 已拥有身份识别的 ViewModel、用例、数据网关和默认人脸验证 UI，但主身份识别路由的 9 个 Compose 文件与专属资源仍留在 `:app`，导致同一能力由两个模块共同持有、UI 直接跨越平台适配边界，并使 legacy allowlist 长期冻结这批文件。项目路线图已将该页面列为下一批低风险迁移对象；此时收敛所有权可让 `:app` 回归根导航与厂商 SDK 适配壳层，也符合 Jetpack 对 feature module、UDF 和最小公开 API 的建议。

## What Changes

- 将主身份识别路由的 Compose UI、屏幕级状态装配、效果处理及身份识别专属资源迁入现有 `:feature:identification`，不新增模块。
- 为身份识别 feature 建立窄而稳定的公开 UI 入口；其余渲染组件保持 module-internal，并改为由不可变状态与用户事件驱动，子组件不再直接接收 ViewModel。
- 保持根 `NavHost`、类型安全 route 与目的地注册由 `:app` 组装；`:app` 只负责导航回调、`SavedStateHandle` 结果桥接和需要 Activity/厂商上下文的人脸 SDK UI 启动。
- 通过显式平台回调把腾讯人脸启动请求交给 `:app` 适配器，并把 SDK 事件返回 feature；ViewModel 与 feature UI 不依赖 `:app` 或厂商 UI 实现。
- 保持现有返回键、相机权限说明、老人照片、默认/手动/腾讯人脸验证、成功后进入服务选择、不可丢失动作队列及错误提示行为不变。
- 从 legacy feature 文件 allowlist 移除已迁出的身份识别 UI 路径，调整对应架构守卫，并增加负向 fixture，防止 UI 回流 `:app` 或 feature 反向依赖 `:app`。
- 补充状态映射、动作消费、导航结果消费/清理和关键路由回归测试，并同步系统架构、依赖规则、页面地图和路线图文档。
- 本 change 不迁移 Navigation 2 至 Navigation 3，不调整 route/结果 key，不修改用户存储、Room、WebView、权限声明、SDK/Gradle 版本、targetSdk 或任何厂商 AAR。

## Capabilities

### New Capabilities

- `identification-feature-boundary`: 规定身份识别页面由 `:feature:identification` 完整持有、`:app` 仅承担根导航与平台/厂商适配，并要求迁移期间保持现有业务与返回结果契约。

### Modified Capabilities

无。

## Impact

- **源码与资源**：`app/src/main/kotlin/com/ytone/longcare/features/identification/ui/`、`app/src/main/res` 中身份识别专属资源、`feature/identification` 的公开入口与内部 UI，以及 `:app` 的身份识别 route 注册。
- **公开边界**：新增或收敛 feature 级 Compose 入口与平台启动回调；现有类型安全 route、`IdentificationActions` 的导航语义和 `SavedStateHandle` 返回 key 保持兼容。
- **架构治理**：`legacy_feature_files_allowlist.txt`、架构边界/模块 API 可见性守卫、相关 fixture 与长期架构文档。
- **依赖**：预计不增加 project dependency；如迁移暴露缺失的 AndroidX/Compose 测试依赖，只能在 `:feature:identification` 内按 version catalog 引入并同步依赖 allowlist。
- **外部风险**：腾讯人脸和其他厂商制品保持原样，生产 Release 的既有 fail-closed 条件不变。本 change 只移动调用边界，不修改、拆包或替换厂商 AAR。
