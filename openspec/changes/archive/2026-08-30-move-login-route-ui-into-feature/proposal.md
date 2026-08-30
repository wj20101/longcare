## Why

`:feature:login` 已拥有登录 ViewModel、动作契约和依赖注入，但登录路由的 Compose UI、协议弹窗与隐藏校验入口仍由 `:app` 持有，导致同一能力跨模块拆分、登录专属资源继续占用 legacy allowlist，且 feature 边界无法独立演进。前序变更已稳定认证根栈、登录成功跳转、Home 所有者与返回恢复契约；现在迁移登录 UI 能以较小风险继续让 `:app` 收敛为根导航和平台适配壳层，并符合 Jetpack 对高内聚 feature module、状态提升和导航回调的建议。

## What Changes

- 将登录路由的 Compose UI、协议交互、隐藏校验入口及登录专属资源迁入现有 `:feature:login`，不新增模块。
- 为登录 feature 建立窄而稳定的公开 Compose 入口；`:app` 不再直接导入登录内部 UI 或 ViewModel。
- 保持根 `NavHost`、类型安全 `LoginRoute`、认证根栈和目的地注册由 `:app` 组装；feature 仅通过显式动作回调请求登录成功跳转、WebView 打开及校验流程启动。
- 扩充隐藏校验入口的动作契约，使相机校验、备用人脸、手动人脸、正式人脸校验和 NFC 校验均由 `:app` 启动对应 route、Activity 或厂商能力；feature 不直接构造 `Intent`，也不依赖 `:app` 资源。
- 由 `:app` 向登录 feature 注入用户协议和隐私政策的兜底地址；服务端动态协议地址仍优先使用，避免复制全局协议常量或形成反向依赖。
- 保持手机号/验证码登录、协议勾选与弹窗、错误反馈、键盘焦点、登录成功动作、Release 隐藏校验入口及现有认证/恢复语义不变。
- 将登录 UI 测试迁移到 feature，更新 Release 校验守卫、目标平台测试选择器和受影响模块映射；从 legacy allowlist 删除已迁出的登录 UI 路径，并增加边界负向 fixture 防止回流。
- 同步系统架构、依赖规则、页面地图、质量门禁和路线图文档。
- 本 change 不迁移 Navigation 2 至 Navigation 3，不修改 route/`SavedStateHandle` 契约、登录接口、用户存储、数据库、WebView host 策略、权限声明、SDK/Gradle 版本、targetSdk 或厂商 AAR。

## Capabilities

### New Capabilities

- `login-feature-boundary`: 规定登录页面由 `:feature:login` 完整持有、`:app` 仅承担根导航和平台/厂商适配，并要求迁移期间保持登录、协议与隐藏校验入口行为兼容。

### Modified Capabilities

无。

## Impact

- **源码与资源**：`app/src/main/kotlin/com/ytone/longcare/features/login/ui/`、`app/src/main/kotlin/com/ytone/longcare/presentation/validation/LoginValidationEntrySheet.kt`、登录专属 `string`/`drawable` 资源、`feature/login` 的公开入口与内部 UI，以及 `:app` 的登录目的地装配。
- **公开边界**：新增或收敛 feature 级 Compose 入口、协议链接值对象和登录动作回调；现有 `LoginRoute`、认证根栈、WebView route 及登录成功语义保持兼容。
- **测试与治理**：登录 Compose instrumentation tests、Release validation guard、目标平台测试矩阵、受影响模块脚本、legacy allowlist、架构边界守卫及其 fixture。
- **依赖**：`:feature:login` 增加已有 version catalog 中的 Compose、`:core:ui` 与测试依赖；不新增第三方库或新的 project dependency 层级。
- **外部风险**：隐藏校验入口涉及腾讯人脸、NFC 等平台/厂商 Activity，但其实现和制品保持原样；生产 Release 的既有 fail-closed 条件不变，本 change 只调整 UI 所有权和调用边界。
