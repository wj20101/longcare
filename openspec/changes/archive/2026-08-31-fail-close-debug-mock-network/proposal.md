## Why

当前 Debug 本地 Mock 模式在遇到未登记接口时会回退到真实网络，已导致测试凭据被发送到生产域名；同时 Mock 开关默认值与长期文档不一致、部分 fixture 无效或具有随机性，使离线冒烟既不安全也不可重复。需要先建立 fail-closed 的调试网络边界，才能继续安全覆盖 API 36 下的人脸、上传与版本更新链路。

## What Changes

- 为 `Debug + USE_MOCK_DATA=true` 建立显式的本地 Mock 安全模式：第一方 HTTP 请求必须由按“请求方法 + 规范化路径”登记的确定性路由处理，未知路由在进程内失败，禁止回退真实网络。
- **BREAKING**：显式 Mock 模式下，未登记接口由“可能访问真实服务”改为“返回可诊断的本地缺失 Mock 失败”；`USE_MOCK_DATA=false` 的 Debug 真实联调行为保持不变。
- 将仓库级 `debug.useMockData` 默认值与现有文档统一为 `false`，仅在显式启用时进入 Mock 模式，并增加构建配置与文档一致性门禁。
- 补齐当前冒烟所需的第一方路由与有效 fixture，至少覆盖获取人脸状态、系统配置、版本检查及相关服务单流程；移除正常冒烟路径中的随机响应，场景变体改由测试专用配置显式选择。
- 对 Mock fixture 增加模型解析和关键语义校验，避免无效密钥格式、过期伪凭据等数据在运行时才失败；诊断信息仅包含必要的方法与路径，不记录 token、请求体等敏感信息。
- 在云上传、地图和厂商 SDK 等第三方数据面保持真实边界：需要离线验证时，在既有业务网关/上传器抽象处注入 Debug/Test fake，不修改厂商 AAR，不添加生产人脸或活体绕过。
- 为版本更新提供无外网的检查与界面验证路径，禁止安全冒烟触发真实 APK 下载；为人脸后续倒计时提供测试所有的已验证状态入口，同时保留 CameraX/ML Kit 的真实拒绝验证。
- 增加路由登记、未知路由拒绝、fixture 有效性、Release/Profile 隔离及关键 API 36 冒烟的自动化验证与长期文档说明。

## Capabilities

### New Capabilities

- `debug-mock-network-safety`: 规定显式 Debug Mock 模式的网络封闭性、确定性路由与 fixture、第三方边界、变体隔离及安全冒烟契约。

### Modified Capabilities

无。

## Impact

- 主要影响 `:app` 的 Debug source set、OkHttp 调试拦截器与 DI、Mock assets、构建属性及相应单元/仪器化测试；Release 和现有 performance 离线拦截器行为不变。
- 可能在既有 `PhotoCloudUploader`、版本更新和身份验证测试边界增加 Debug/Test 实现，但不改变生产网络 API、持久化格式、路由 contract 或 WebView host 行为。
- 同步维护根 README、技术栈/系统概览、CI 质量门禁与开放缺口中与 Debug Mock 相关的稳定事实。
- 外部依赖仍包括腾讯/QLZ 厂商 AAR、COS、AMap 与真实人脸/活体环境；本 change 不升级依赖或 targetSdk，不解除 production Release 的 fail-closed 状态，也不宣称第三方真实数据面已被离线端到端覆盖。
