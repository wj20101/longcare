## Why

用户已明确接受本轮继续使用现有 QLZ 测试 key、测试模式及 QLZ/腾讯 SDK 已知风险，要求这些事项不再阻断正式构建。此次只调整已接受事项的发布策略，并验证实际 R8 正式产物，避免将构建成功或 Debug 测试通过当作正式包业务验收。

## What Changes

- 将当前已接受的固定 QLZ 测试配置、QLZ 1.3.0.5 弱 TLS、腾讯人脸 6.6.2 已知 native/consumer rules 问题由正式发布失败改为明确警告；不扩展为其他 SDK、未来版本或任意警告的通用豁免。
- 保留 production/acceptance 模式合法性、正式签名、Lint allowlist、导出组件、架构、双 APK 隔离及 CI 检查，不关闭 R8、不增加整包 keep 或 ignorewarnings。
- 对最终合法签名的 production Release 执行实际 R8 消息专项、启动/恢复/导航/H5 及可用账号的业务烟测，记录实际覆盖及未覆盖场景。
- 同步发布说明和门禁记录；验证通过后按已有授权提交、合入并推送 master。不自动上传应用商店、推送客户端更新或创建分发 Release。

## Capabilities

### New Capabilities

- `approved-vendor-release`: 已接受厂商风险的非阻断报告、其余发布检查及实际混淆产物验收。

### Modified Capabilities

- `dual-apk-packaging`: 更新验收构建与正式发布策略的关系，保留签名和双 APK 隔离；业务、接口和导航契约不变。

## Impact

- 影响现有发布检查脚本、Android Release workflow、质量门禁注册及相关文档；必要测试限于现有测试源集和脚本，不新增运行时框架。
- 安全审查结论：风险接受不修复弱 TLS，测试模式也不变成厂商正式环境；16 KB 兼容性不能靠 R8 保证，渠道可能拒绝产物或部分设备无法加载。以上保留为未解决的已接受风险，不宣称所有设备无崩溃。
- 不替换厂商 AAR、不改依赖、不清除设备数据、不重构业务逻辑；按验收发现补充详情页在进程恢复后按已保存 ID 重查的最小修复。不用未经验证的声明关闭已有异常场景验收任务。
