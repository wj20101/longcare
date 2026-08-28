## Why

当前 QLZ 1.3.0.2 AAR 含可达的弱 TLS trust manager，Android 端还把固定测试 appKey 与测试模式编入所有变体，因此生产 Release 必须 fail closed。现在需要用厂商修复包和服务端下发的非秘密初始化配置替代这条临时链路，在不改变销售评估业务契约的前提下消除 QLZ 侧 P0 发布阻断。

## What Changes

- 接入来源、版本和校验和可追溯的 QLZ 修复版 AAR，验证其可达网络路径不再信任任意证书，并复核依赖、Manifest、ABI、consumer rules 与 R8 行为。
- 从 Android Gradle/BuildConfig 中删除固定 QLZ appKey 与固定测试模式；`appSecret` 继续只存在 LongCare 服务端，不进入源码、资源、日志、持久化明文或安装包。
- 扩展现有 `/V1/System/Config` 加密第三方配置，先取得当前会话的 QLZ appKey 与环境信息，再初始化 SDK、读取设备 ID 并调用 `/V1/Sale/GetCheckToken` 获取一次性检测 Token，解除当前初始化与设备 ID 的循环依赖。
- 生产构建在服务端配置缺失、无效或要求测试模式时运行时拒绝初始化并给出可恢复错误；Debug/显式 Acceptance 只能使用对应环境服务端下发的测试配置，不再使用客户端固定 fallback。
- 保持客户选择、BLE 权限、设备检测、Token 过期后单次刷新、进度/取消/完成事件和报告查询行为；配置或账号变化时不得继续复用上一会话的 SDK 初始化状态。
- 更新 production config/vendor readiness/Lint 守卫及负向测试，使已知旧 AAR、固定 key、测试模式或弱 TLS 回退继续被阻断；腾讯人脸的独立 P0 必须保持原有 fail-closed，不能因 QLZ 修复被放宽。
- 用受控销售账号和 BLE 检测设备验证真实链路，并同步技术栈、QLZ 集成、产品发布状态、CI 门禁和路线图中的长期事实。
- 本 change 不升级腾讯人脸、不关闭 Jetifier、不独立迁移 legacy protobuf、不处理 QLZ 合并 Manifest 的额外权限最小化，也不重构销售页面或导航；这些继续由各自独立 change 管理。

## Capabilities

### New Capabilities

- `sales/qlz-device-assessment`: 定义 QLZ 初始化配置的服务端来源、安全 SDK 约束、设备评估/Token 恢复行为、会话隔离和生产发布的 fail-closed 验收契约。

### Modified Capabilities

无。

## Impact

- **Android 代码与契约**：`ThirdKeyReturnModel`/系统配置处理、QLZ 配置 provider、`QlzSdkClient`、销售设备 gateway/controller、`SalesViewModel` 及 Retrofit/Moshi 契约测试。
- **构建与供应链**：`app/libs` QLZ AAR、依赖声明、可能受厂商包影响的 R8/Manifest/network security 输出、Lint waiver、production config 与 vendor readiness 守卫。
- **服务端**：`/V1/System/Config` 的加密 `thirdKeyStr` 需要以向后兼容字段下发 QLZ appKey 和环境；部署顺序必须先服务端、后依赖该字段的新客户端。`/V1/Sale/GetCheckToken` 的请求/Token 语义保持不变。
- **外部依赖**：需要厂商提供修复版 AAR、版本说明和可验证来源，需要受控销售账号、BLE 设备及测试/生产环境配置；缺少任一项时不得宣称 QLZ 生产 readiness 完成。
- **兼容性**：不改变 route、SavedStateHandle key、Room schema、客户/报告接口或用户可见主流程；旧客户端应忽略新增系统配置字段。完成后全量 production Release 仍可能只因腾讯人脸 P0 按设计失败。
