## MODIFIED Requirements

### Requirement: 双应用构建输出两个 APK
项目 SHALL 提供文档化的双应用构建入口，仅使用标准 Debug/Release 变体同时构建正式应用和助手应用，并 SHALL 在构建目录中产生两个名称与路径可明确区分的 APK，不再提供独立验收模式。

#### Scenario: 构建 Debug 双 APK
- **WHEN** 开发者执行文档化的 Debug 双应用构建命令
- **THEN** 构建成功后同时存在一个正式 Debug APK 和一个助手 Debug APK
- **THEN** Android CLI 项目描述能够识别两个 application 构建目标及其 APK 输出

#### Scenario: 构建内部验收双 APK
- **GIVEN** 已提供合法的正式签名
- **WHEN** 开发者执行文档化的 Release 双应用构建命令
- **THEN** 构建同时输出正式 Release APK 与明确标记为内部工具的助手 Release APK
- **THEN** 两者遵守签名及隔离要求，不需要验收模式配置

#### Scenario: 使用旧验收入口
- **WHEN** 开发者向双应用构建脚本传入 `acceptance` 或 `production`
- **THEN** 脚本提示仅支持 `debug` / `release`，不将旧参数作为兼容别名

#### Scenario: 单独构建正式应用
- **WHEN** 开发者或正式发布流程只执行正式应用构建任务
- **THEN** 构建系统不要求生成或发布助手 APK

### Requirement: 助手产物不得作为正式产品发布
发布系统 MUST 将助手 APK 标记为内部验证工具，并 MUST NOT 将其混入面向终端用户的正式商店产物或正式应用更新通道。

#### Scenario: 执行正式生产发布
- **WHEN** 发布流程生成正式应用的 Release APK 或 AAB
- **THEN** 对外发布集合只包含正式应用产物
- **THEN** 助手产物仅在显式请求的内部 Debug/Release 构建或内部 artifact 中出现
