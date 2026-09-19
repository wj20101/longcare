## REMOVED Requirements

### Requirement: 助手产物不得作为正式产品发布
**Reason**: 用户明确要求助手 Release APK 与主应用统一在 GitHub Release 下载，不再限制为内部 artifact。
**Migration**: 后续版本使用同一 GitHub Release 的独立助手附件；保留助手用途和主应用商店/更新通道隔离，不修改历史版本。

## ADDED Requirements

### Requirement: 助手以独立 Release 附件分发
发布系统 SHALL 在同一 GitHub Release 中提供主应用与助手 Release 产物，并 MUST 通过名称和发布说明明确区分用途。助手 MUST 保持独立应用身份，不得进入主应用的商店产物或应用内更新通道。

#### Scenario: 下载同一版本的两个应用
- **WHEN** 用户访问成功发布的新版本 GitHub Release
- **THEN** 可分别下载主应用 Release APK/AAB 和助手 Release APK
- **THEN** 助手附件名称包含 `assistant`，发布说明明确其为验证助手，不与主应用互相覆盖

#### Scenario: 主应用检查更新
- **WHEN** 主应用使用既有应用内更新通道获取更新
- **THEN** 本次新增的助手附件不改变主应用更新协议或其安装包身份
