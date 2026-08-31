## Why

`:feature:home` 已持有 Home 的共享状态、登录上报接口和导航动作契约，但 Home 根页面以及护理端仪表盘、护理工作、个人中心仍由 `:app` 持有，且 app 页面把 feature 的 `HomeSharedViewModel` 继续向下传递。该拆分让壳层承担约 20 个 route-bound UI/状态文件、跨模块暴露 ViewModel，并阻碍 legacy allowlist 收缩；现有入口导航、HomeGraph owner、角色分流和销售恢复契约已经稳定，现在适合用独立切片收敛 Home 所有权。

## What Changes

- 将 Home 根页面、护理/销售角色分流、加载态、护理端三页 adaptive 导航壳、护理仪表盘、护理工作和个人中心 UI/状态持有者迁入现有 `:feature:home`，不新增业务模块。
- 建立唯一公开的 `HomeFeatureScreen` 与显式动作/配置边界；`:app` 继续注册 `HomeRoute`/`HomeGraphRoute`、持有 `NavController`、Startup fully-drawn、开放 WebView、Camera 返回桥接和其他平台动作，但不得导入 Home 内部 UI 或 ViewModel。
- 将 `HomeSharedViewModel` 收敛为 feature 内部的屏幕级状态持有者，通过不可变 `StateFlow` 和用户动作驱动 UI；护理、个人中心、销售及兼容人脸页面不再接收或自行获取该 ViewModel，只消费所需的当前用户、Tab 状态和显式回调。
- 保持销售体验及 oversized `SalesViewModel` 在 `:app`，通过窄的 app-owned renderer 接入 Home feature；保留内部 `SalesNavigationState`、Camera/WebView 返回和销售页恢复行为，后续销售模块化另建 change。
- 为护理仪表盘所需公司名称建立 Domain 抽象并由现有 Data 实现绑定，避免 `:feature:home` 依赖 `SystemConfigManager` 等 Data 实现；共享且纯展示的 Avatar、空态或订单卡片只在真实跨页面复用时提升到 `:core:ui`，其余保持 feature 内部。
- 将 Home/护理仪表盘/护理工作/个人中心的专属资源和测试迁到源码所有者模块，更新受影响模块、instrumentation owner、API 36/37 selector、legacy allowlist 与架构守卫，并增加负向 fixture 防止 UI/VM 回流 `:app` 或 feature 反向依赖壳层。
- 保持当前视觉、三页顺序、角色值 `2` 的销售分流、HomeGraph 图级订单状态、返回栈、登录日志、刷新/登出、订单跳转、协议链接和启动 fully-drawn 语义等价。
- 本 change 不迁移销售 UI、不拆分 `SalesViewModel`、不升级 Navigation 2/targetSdk/依赖、不修改网络/Room/DataStore/用户存储/WebView host 策略、Manifest/权限、R8、厂商 AAR 或生产发布门禁。

## Capabilities

### New Capabilities

- `home-feature-boundary`: 规定 Home 根页面和护理端首页 UI/状态由 `:feature:home` 持有，应用壳层仅通过公开入口、销售 renderer 与平台动作完成组装，并要求角色分流、HomeGraph 状态和页面行为在迁移中保持兼容。

### Modified Capabilities

无。

## Impact

- **源码与资源**：`app` 中 `features/home/ui`、护理 `maindashboard`/`nursing`/`profile` 的 UI、ViewModel、专属资源和测试迁入 `feature/home`；app-owned NFC 校验辅助、Sales、Navigation、Startup reporter 与平台 adapter 保留或调整包归属但不改变行为。
- **公开边界**：新增或收敛 `HomeFeatureScreen`、Home actions/config、Sales renderer 和 app version/Startup reporter 等最小值对象或回调；禁止公开 `HomeSharedViewModel`、`NavController`、`Activity`、`Context`、app `R` 或厂商类型。
- **分层与依赖**：`:feature:home` 启用 Compose 并按实际 import 依赖 `:core:common`、`:core:domain`、`:core:model`、`:core:ui`；Domain 增加最小公司名称读取契约，Data 绑定现有实现；不允许 Feature 直接依赖 `:core:data`。
- **测试与治理**：Home/护理 Compose 与 ViewModel tests、入口导航/HomeGraph/Sales focused tests、模块依赖清单、affected detector、instrumentation owner、目标平台矩阵、CI selector、legacy allowlist、Home 边界守卫及长期架构文档。
- **兼容与外部风险**：用户数据、网络接口、导航 key、厂商能力和生产阻断不变；API 36 模拟器用于迁移回归，真实厂商成功和统一真机 acceptance 仍由既有 change 独立判定。
