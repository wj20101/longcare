## Why

> 2026-09-20 一致性校准：本文已对齐后续落地的 `device-h5-evaluation-flow`、`legacy-h5-close` 和 `approved-vendor-release` 主规格。任务 5.3 的完整真机异常矩阵仍未完成；文档更新不新增验收证据。

本变更提出时，设备自动评估通过 `SDKCall.openByToken(...)` 跳入 QLZ SDK 内置页面，应用无法统一视觉、准确呈现扫描/连接/五指接触/上传状态，也难以在当前 Compose 页面生命周期内处理错误恢复。项目需要采用 Demo 展示的底层回调方案，在不改变 LongCare Token 和报告契约的前提下，由应用完整拥有设备评估 UI 与交互。

## What Changes

- 将设备自动评估从 QLZ 内置 Activity 切换为应用内 Compose 流程，使用 SDK 的 `CheckIml`、`ScanDeviceIml` 与 `ConnectDeviceHelp` 完成鉴权、扫描、连接、检测和上传。
- 在现有销售评估链路中提供设备扫描与选择、连接反馈、五指接触状态、检测进度、上传状态、完成状态以及可恢复错误 UI。
- 对蓝牙权限、蓝牙关闭、扫描无结果、连接失败/掉线、低电量、弱信号、检测超时、需要支付和上传失败等状态提供明确反馈、退出或重试路径。
- 将 QLZ 会话限制在当前 UI 生命周期内；停止扫描并释放连接资源，隔离退出后的迟到回调，避免并发检测会话。
- 保留 `/V1/Sale/GetCheckToken` 获取一次性 Token；按已落地的后续规格，上传后刷新客户详情并自动打开服务端 `pgUrl` 的评估 H5，JS 主动关闭后才进入业务完成页，结果按有无 recordId 分别查询检测结果或最新客户详情。
- 同步 QLZ 集成说明和销售评估页面地图，记录自定义 UI 调用链及真机验收要求。
- 不修改 QLZ AAR、Sale 网络接口、表单评估流程、生产发布门禁或 SDK 测试配置；当前固定配置及已接受的 QLZ/腾讯厂商风险按统一 Release 策略告警，签名和其他质量检查保持阻断；风险接受不等于修复。

## Capabilities

### New Capabilities

- `qlz-custom-evaluation-ui`: 定义应用自有的 QLZ 设备扫描、连接、检测、上传、错误恢复和生命周期行为。

### Modified Capabilities

无。

## Impact

- 主要影响 `:app` 中的 QLZ 集成适配、销售评估 UI/controller、页面状态协调、字符串与相关单元/Compose 测试。
- `SalesViewModel` 继续只处理厂商无关事件，不持有 `Activity`、`BluetoothDevice` 或 QLZ 会话对象；厂商类型和资源释放仍封装在 app-owned UI/controller 边界。
- Android 12 及以上请求 `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT` 及成对的前台精确/粗略位置权限，扫描要求精确位置授权；Android 11 及以下使用精确位置权限。所有支持版本检查定位开关；不新增后台位置权限、位置采集或导出组件。
- 外部依赖为 QLZ 1.3.0.5 Lite AAR、LongCare Token/客户详情接口及支持 BLE 的真实检测设备。模拟器只能验证 UI 和错误状态，不能替代真机测量与上传验收。
- 风险包括厂商回调对象可变、回调晚于页面退出、全局单会话状态、手指索引语义需真机确认，以及厂商上传/支付分支缺少完整 Demo；设计和验收必须显式覆盖这些边界。
