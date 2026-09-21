## Why

用户确认 QLZ 检测 Token 为一次性凭证，使用后必须重新获取。当前授权重试、检测页返回后重新搜索及 UI 会话重建仍可能重复使用缓存 Token；上传鉴权失败又被归并为普通上传失败，导致重试无法恢复。

用户同时确认切换 QLZ 正式环境，保留现有 SDK key，服务端 GetCheckToken 已签发正式环境 Token。客户端必须移除强制测试环境配置，避免凭证与请求环境不匹配。

## What Changes

- 每次新的 SDK 授权前获取新 Token，交付后消费，不把已用 Token 当作后续启动凭证；同一存活会话的扫描/重连不重复授权。
- 授权重试、取消后再次开始、宿主重建后的新会话，统一先请求 `/V1/Sale/GetCheckToken`，删除 `restart(currentToken)` 旧凭证重试路径。
- 上传明确报鉴权失效时，保留本次检测数据和 recordId，获取新 Token 并重新授权后重试同一份上传；普通网络失败保持原上传重试，不重新测量。
- 新 Token 获取失败时停止后续 SDK 调用；退出或切换客户后丢弃旧请求结果，避免启动错误客户的会话；保留已有有界自动恢复，不无限刷新。
- 补充离线回归、构建验证及 Token 生命周期文档，不新增通用凭证框架或持久化 Token。
- 删除 `CheckIml.setTestMode(...)` 调用，使用固定 AAR 默认正式地址；删除无用测试模式开关，同步构建检查和文档。Debug/Release 一致，不增加环境切换选项。

## Capabilities

### New Capabilities

- `qlz-single-use-token`：正式环境下的一次性检测凭证获取、消费、新会话重建及上传鉴权恢复。

### Modified Capabilities

无。既有设备上传后查询结果/手动打开报告的契约不变；本次细化 Token 生命周期，不重新定义 H5 流程。

## Impact

- 影响销售 ViewModel、设备页协调、`SalesSdkUiController`、QLZ session/driver 的授权与上传恢复分支及测试。
- 继续使用现有 GetCheckToken 请求/响应和 QLZ 1.3.0.5 Lite AAR；调整客户端 SDK 环境初始化及相关 Gradle 配置、发布检查和测试，不修改 AAR、依赖、数据库、业务结果查询或 H5。
- 正式 SDK 地址为 `https://openapi.qiaolz.com`，检测上传为 `POST /sdk/assess/upload`；LongCare API 地址不变。现有 key 的正式环境适用性及服务端 Token 环境来自用户确认，不等同于已完成真实联调。
- 外部依赖：服务端实际签发新 Token，厂商支持对保留的原记录重新授权上传。离线验证不能证明厂商端到端行为，真实上传须使用另行确认的测试客户和设备。
- 不记录 Token、客户敏感字段、检测原始数据；不扩展既有厂商风险放行范围。
