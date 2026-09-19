## MODIFIED Requirements

### Requirement: 双应用构建输出两个 APK
项目 SHALL 提供文档化的双应用构建入口，为指定的受支持变体同时构建正式应用和助手应用，并 SHALL 在构建目录中产生两个名称与路径可明确区分的 APK。

#### Scenario: 构建 Debug 双 APK
- **WHEN** 开发者执行文档化的 Debug 双应用构建命令
- **THEN** 构建成功后同时存在一个正式 Debug APK 和一个助手 Debug APK
- **THEN** Android CLI 项目描述能够识别两个 application 构建目标及其 APK 输出

#### Scenario: 构建内部验收双 APK
- **GIVEN** 已提供现有验收构建所需的签名与显式验收配置
- **WHEN** 开发者执行文档化的验收双应用构建命令
- **THEN** 构建同时输出正式验收 APK 与明确标记为内部工具的助手 APK
- **THEN** 验收成功不代替正式签名、产物隔离及 production 构建验证；已接受厂商风险按当前生产发布策略报告，不得将验收产物直接冒称正式产物

#### Scenario: 单独构建正式应用
- **WHEN** 开发者或生产发布流程只执行正式应用构建任务
- **THEN** 构建系统不要求生成或发布助手 APK
