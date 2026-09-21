## MODIFIED Requirements

### Requirement: 正式发布只有一个入口语义
发布流程 SHALL 仅提供手动正式 Release 入口，不提供 `production` 或 `acceptance` 模式、发布时依赖来源切换或 Baseline Profile 生成选项；标准 Release 构建 MUST 无需额外模式参数。Baseline Profile 生成 SHALL 保留独立维护入口。

#### Scenario: 执行正式构建
- **WHEN** 开发者执行 Release APK/AAB 构建或启动 Android Release 工作流
- **THEN** 不需要选择发布模式，产物始终按正式签名、不可调试、R8 和资源压缩配置构建
- **THEN** 既有版本递增逻辑继续有效，不产生另一种验收 Release

#### Scenario: 推送版本标签
- **WHEN** 外部推送版本标签
- **THEN** 不触发一个必然被拒绝的自动发布运行

## ADDED Requirements

### Requirement: 正式产物验证后才能写入远端版本
发布流程 MUST 在正式产物构建和必要验证成功后才推送版本提交；新发布请求 MUST NOT 自动取消正在进行的正式发布。最终标签、提交和产物版本 SHALL 一致。

#### Scenario: 构建或产物验证失败
- **WHEN** 正式构建、签名或产物检查失败
- **THEN** 不推送版本提交、不创建新 Release，也不修改历史发布

#### Scenario: 发布期间分支发生变化
- **WHEN** 已构建版本无法正常推送到目标分支
- **THEN** 明确失败，不强推、不把新源码提交关联到旧构建产物

#### Scenario: 新发布请求到达
- **WHEN** 已有正式发布正在执行
- **THEN** 不取消进行中的发布，不并发争用同一版本或 Latest 状态

### Requirement: 去重验证必须有可靠依据
Release SHALL 保留源提交成功 CI 及全部正式产物检查。重复检查仅在同源提交、同构建配置且成功证据可用时复用；MUST NOT 因删除重复任务而放行没有验证的配置。

#### Scenario: 目标提交没有有效成功 CI
- **WHEN** 找不到目标源提交对应的成功验证
- **THEN** 阻断发布，不使用其他提交的绿色结果替代

#### Scenario: 正式发布采用已验证配置
- **WHEN** 成功 CI 对应相同源提交和受版本控制的依赖配置
- **THEN** 不为发布重复生成无消费的 Debug APK，正式签名、R8、身份、导出组件和完整性检查仍执行
