## REMOVED Requirements

### Requirement: 两个应用具有稳定且不同的身份
**Reason**: 用户确认取消独立助手，双应用身份契约退役。
**Migration**: 新版本仅提供原主应用身份的安装包，历史助手安装和数据不自动处理。

### Requirement: 双应用构建输出两个 APK
**Reason**: 不再维护双应用构建。
**Migration**: 删除双包入口，使用标准主应用 Debug/Release 构建任务。

### Requirement: 正式 APK 不包含验证入口和专用组件
**Reason**: 用户明确要求主应用内提供 NFC/R65C 本地读卡入口。
**Migration**: 以登录页中央大 Logo 长按确认入口替代；仍不增加外部可启动的检测组件，保留正式业务回归检查。

### Requirement: 应用模块之间保持单向公共依赖
**Reason**: 独立助手应用模块删除，双应用依赖约束失去对象。
**Migration**: 新检测 Feature 遵守现有主应用模块边界，不依赖旧助手模块。

### Requirement: 助手以独立 Release 附件分发
**Reason**: 未来版本不再发布独立助手。
**Migration**: 新 Release 仅发布主应用 APK/AAB 及校验等辅助文件，不改动历史 Release 附件。
