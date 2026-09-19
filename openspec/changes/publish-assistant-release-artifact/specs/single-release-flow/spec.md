## ADDED Requirements

### Requirement: 单一发布包含完整双应用产物
Android Release 流程 SHALL 使用同一次版本递增和同一源提交，生成主应用 Release APK/AAB 与助手 Release APK；助手 MUST 使用正式签名、不可调试、R8 和资源压缩配置。成功发布 MUST 同时上传三类安装产物至同一 GitHub Release，提供覆盖所有安装产物的 SHA-256 校验和，并在 Actions artifact 中留存助手 APK 和对应混淆映射。

#### Scenario: 双应用发布成功
- **GIVEN** 目标提交 CI、签名和质量检查通过
- **WHEN** Android Release 完成
- **THEN** 同一版本页包含主应用 APK/AAB、助手 APK 和对应 SHA-256 校验信息
- **THEN** 两个应用使用相同 versionCode，助手保留既有 versionName 助手标识
- **THEN** 无需选择额外发布模式，仍为正式 Release 和 Latest

#### Scenario: 助手产物缺失或无效
- **WHEN** 助手构建失败、APK 缺失、签名无效或包名不符合助手身份
- **THEN** 工作流在创建 GitHub Release 前失败，不以仅有主应用产物宣告发布成功

#### Scenario: 保留历史产物
- **WHEN** 新流程生效
- **THEN** 不回填、替换或删除既有 Release、标签和下载附件
