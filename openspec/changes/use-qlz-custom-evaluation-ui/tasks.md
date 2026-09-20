## 1. 建立可测试的 QLZ 自定义会话边界

> 2026-09-20 一致性校准：本文已对齐后续落地的 `device-h5-evaluation-flow`、`legacy-h5-close` 和 `approved-vendor-release` 主规格。任务 5.3 的完整真机异常矩阵仍未完成；文档更新不新增验收证据。

- [x] 1.1 在 `:app` 的 QLZ integration 边界定义不可变设备选项、五指状态、上传上下文、评估阶段与 UI 状态，以及最小厂商 adapter/factory 契约；新增 reducer 单测并运行对应 `:app:testDebugUnitTest` focused tests 验证合法/非法状态转换。
- [x] 1.2 使用 `CheckIml.startCheck` 和 `ScanDeviceIml` 实现 Token 校验与有界扫描适配，回调入口立即复制列表、按 address 去重并只暴露掩码标识；用 adapter/helper 单测验证空列表、重复设备、列表复用、停止/重扫和蓝牙不可用映射。
- [x] 1.3 使用 `ConnectDeviceHelp` 实现连接、五指/进度/电量/超时/掉线回调适配，复制可变数组并穷尽映射已知错误码；用 focused tests 验证五项归一化、连接失败/重连、弱信号、低电量、充电、超时和未知错误脱敏。
- [x] 1.4 实现检测结束后的单次 `sendData`、上传中防重复、`RecordInputData` 内存重试和上传成功事件；用 fake adapter 单测验证重复结束只上传一次、失败只重传本次数据、成功清除重试数据以及 Demo 硬编码值不会进入参数。
- [x] 1.5 实现 `QlzSdkClient` 活动会话租约与 UI 作用域 session 的幂等 `close()`/generation 过滤，按失效回调、停止扫描、释放连接、释放租约的安全顺序清理；用单元测试验证重复点击、并发创建、返回退出、Activity 重建模拟和迟到回调均不会创建第二会话或修改已关闭状态。

## 2. 实现应用自有扫描与检测 UI

- [x] 2.1 将 `SalesDeviceStatusScreen` 改为自定义设备准备/扫描页面，呈现权限或蓝牙阻断、扫描中、候选设备、空结果、重新扫描和退出操作；新增 Compose 测试验证各状态、设备选择、按钮 enablement 和重复点击防护。
- [x] 2.2 将 `SalesEvaluationGuideScreen` 改为连接与测量页面，复用现有设备/握持素材并新增五个接触点、检测进度、电量/充电、连接错误、支付阻断、上传中和上传重试 UI；新增 Compose 测试覆盖关键状态、恢复动作和不暴露原始 SDK 文本。
- [x] 2.3 为扫描和检测组件补齐紧凑与宽屏约束、无障碍描述及销售端一致的颜色/卡片/按钮样式；运行相关 Compose 测试或预览检查，验证小屏不裁切、宽屏不过度拉伸且五个接触点可辨识。

## 3. 接入销售评估导航和业务契约

- [x] 3.1 重构 `SalesSdkUiController` 与 `SalesExperienceScreen`，在 `DEVICE_STATUS` 和 `EVALUATION_GUIDE` 之间共享同一 UI 会话，并在离开活动评估页面、完成、取消或宿主销毁时关闭；新增生命周期/导航测试验证页面间切换不误释放、退出必释放且后台不启动新扫描。
- [x] 3.2 调整现有权限请求流程，使授权成功后启动自定义扫描而非 SDK Activity，并补齐 BLE 不支持、蓝牙关闭、权限拒绝与再次授权恢复；运行 API 30/31 权限策略单测和 Compose 交互测试。
- [x] 3.3 将现有 `sdkLaunchRequest` Token 恢复结果接到当前自定义会话，保留至多一次恢复和错误脱敏规则；扩展 `SalesViewModelSdkTokenRecoveryTest` 验证首次过期重启自定义会话、第二次失败终止且不调用厂商内置页面。
- [x] 3.4 从当前客户/登记业务状态构造上传上下文，可靠字段缺失时传空值且不猜测经纬度；上传成功后触发客户详情刷新和自动评估 H5，H5 主动关闭后才进入 `EVALUATION_COMPLETE`，扩展 ViewModel/导航测试验证报告始终只使用服务端 `pgUrl`。
- [x] 3.5 移除业务路径对 `SDKCall.openByToken`、厂商检测 Activity 完成/关闭事件和厂商报告 URL 的依赖，更新 QLZ 架构边界测试；用 `rg` 守卫和 `:app:testDebugUnitTest` 验证厂商/蓝牙类型不进入 Sales ViewModel 或可持久化 UI 状态。

## 4. 文案、文档与静态安全验证

- [x] 4.1 增加自定义扫描、连接、握持、检测、上传和错误恢复所需中文资源，复用通用文案并删除不再可达的内置页专用文案；运行 Android resource merge 和相关文案测试，确认用户界面不包含 Token、MAC 全值、SDK URL、错误堆栈或测试凭据。
- [x] 4.2 更新 `docs/integrations/qlz-sdk.md` 和 `docs/architecture/ui-and-screen-map.md`，把内置 `SDKCall.openByToken` 链路改为自定义会话链路，并记录权限、生命周期、支付阻断、服务端报告和真机限制；运行相对链接检查及 `bash scripts/quality/preflight_local.sh --local-fast`。
- [x] 4.3 检查 merged manifest、AAR SHA/consumer rules 和 production readiness 守卫，确认没有新增权限、导出组件、`neverForLocation` 声明、secret、Lint/ProGuard 忽略或发布绕过；运行现有 Manifest/厂商 SDK 安全脚本并按当前 Release 策略记录已接受厂商告警，其他失败如实保留。

## 5. 集成与真实设备验收

- [x] 5.1 运行 QLZ session、销售 ViewModel、导航和 Compose focused tests，再执行 `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 与 lint warning allowlist，确认自定义 UI 主路径可构建且现有销售评估回归通过。
- [x] 5.2 运行 `bash scripts/quality/preflight_local.sh --full` 和 `openspec validate --all --strict --no-interactive`，检查最终 diff 不包含 build 产物、凭据、Demo 测试值或对用户现有未提交改动的覆盖。
- [ ] 5.3 在支持 BLE 的真实 Android 设备上使用 QLZ 检测设备验收扫描/停止、设备选择、逐指映射、完整检测、上传和服务端报告，并覆盖权限拒绝恢复、蓝牙关闭、掉线、弱信号、低电量、前后台及返回释放；验收证据保留在 PR/CI artifact，不在仓库新增报告文档。
