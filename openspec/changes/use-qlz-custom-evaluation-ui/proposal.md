## Why

当前设备自动评估通过 `SDKCall.openByToken(...)` 跳入 QLZ SDK 内置页面，应用无法统一视觉、准确呈现扫描/连接/五指接触/上传状态，也难以在当前 Compose 页面生命周期内处理错误恢复。项目需要采用 Demo 展示的底层回调方案，在不改变 LongCare Token 和报告契约的前提下，由应用完整拥有设备评估 UI 与交互。

## What Changes

- 将设备自动评估从 QLZ 内置 Activity 切换为应用内 Compose 流程，使用 SDK 的 `CheckIml`、`ScanDeviceIml` 与 `ConnectDeviceHelp` 完成鉴权、扫描、连接、检测和上传。
- 在现有销售评估链路中提供设备扫描与选择、连接反馈、五指接触状态、检测进度、上传状态、完成状态以及可恢复错误 UI。
- 对蓝牙权限、蓝牙关闭、扫描无结果、连接失败/掉线、低电量、弱信号、检测超时、需要支付和上传失败等状态提供明确反馈、退出或重试路径。
- 将 QLZ 会话限制在当前 UI 生命周期内；停止扫描并释放连接资源，隔离退出后的迟到回调，避免并发检测会话。
- 保留 `/V1/Sale/GetCheckToken` 获取一次性 Token、评估完成后刷新客户详情、仅使用服务端 `pgUrl` 打开报告的现有业务契约。
- 同步 QLZ 集成说明和销售评估页面地图，记录自定义 UI 调用链及真机验收要求。
- 不修改 QLZ AAR、Sale 网络接口、表单评估流程、生产发布门禁或 SDK 测试配置；本变更不会解除当前 QLZ 弱 TLS/固定测试配置造成的 production fail-closed 状态。

## Capabilities

### New Capabilities

- `qlz-custom-evaluation-ui`: 定义应用自有的 QLZ 设备扫描、连接、检测、上传、错误恢复和生命周期行为。

### Modified Capabilities

无。

## Impact

- 主要影响 `:app` 中的 QLZ 集成适配、销售评估 UI/controller、页面状态协调、字符串与相关单元/Compose 测试。
- `SalesViewModel` 继续只处理厂商无关事件，不持有 `Activity`、`BluetoothDevice` 或 QLZ 会话对象；厂商类型和资源释放仍封装在 app-owned UI/controller 边界。
- Android 12 及以上继续请求 `BLUETOOTH_SCAN` 与 `BLUETOOTH_CONNECT`，Android 11 及以下继续使用位置权限；不新增权限和导出组件。
- 外部依赖为 QLZ 1.3.0.5 Lite AAR、LongCare Token/客户详情接口及支持 BLE 的真实检测设备。模拟器只能验证 UI 和错误状态，不能替代真机测量与上传验收。
- 风险包括厂商回调对象可变、回调晚于页面退出、全局单会话状态、手指索引语义需真机确认，以及厂商上传/支付分支缺少完整 Demo；设计和验收必须显式覆盖这些边界。
