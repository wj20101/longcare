## Why

Android Release 当前只发布主应用，助手虽可构建 Release APK，却无法在同一个版本页下载。用户已明确要求助手也作为 Release 产物统一放入 GitHub Release，减少分散查找和手动构建。

## What Changes

- 同一发布流程构建主应用 APK/AAB 与助手 Release APK，使用既有正式签名、R8 和资源压缩配置，不新增发布模式。
- 助手 APK 以独立文件名加入同一 GitHub Release，同时保留 Actions artifact；补齐助手校验和、混淆映射留存及发布前检查。
- 将“助手不得出现在对外 Release”调整为“允许作为独立助手附件分发”，保持双包身份、数据、依赖及主应用更新通道隔离。
- 同步工作流测试及现行文档；不修改业务代码、历史 Release、版本号或依赖，不新增助手 AAB/商店发布。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `dual-apk-packaging`: 允许助手 Release APK 与主应用在同一 GitHub Release 独立分发，保留应用隔离。
- `single-release-flow`: 发布成功必须同时具备主应用 APK/AAB 和助手 Release APK，以及对应校验信息。

## Impact

- 修改 Android Release 工作流、发布工作流专项测试及发布约定；普通 Android CI 已有助手 Debug 构建上传，无需重复新增。
- 更新 AGENT.md、OpenSpec 配置和受影响长期文档、助手 Gradle 注释中的旧分发限制。
- 风险与外部依赖：助手下载可见范围扩大到 GitHub Release 可访问者，应明确标注助手用途；依赖既有签名 secrets 和 GitHub 发布权限，不增加凭据或权限。
- 本 change 仅补齐流程；实际触发发布会递增版本并创建 Release，不在本次方案编写中执行。
