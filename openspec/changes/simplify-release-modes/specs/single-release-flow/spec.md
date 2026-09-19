## Purpose

定义 LongCare 单一正式 Release 的构建与发布契约，消除验收和生产模式造成的重复选项、名称与行为分支，同时保留签名、混淆、质量检查以及内部助手的分发边界。

## ADDED Requirements

### Requirement: 正式发布只有一个入口语义
发布流程 SHALL 仅提供正式 Release，不提供 `production` 或 `acceptance` 模式选择；标准 Release 构建 MUST 无需额外模式参数。

#### Scenario: 执行正式构建
- **WHEN** 开发者执行 Release APK/AAB 构建或启动 Android Release 工作流
- **THEN** 不需要选择发布模式，产物始终按正式签名、不可调试、R8 和资源压缩配置构建
- **THEN** 既有版本递增逻辑继续有效，不产生另一种验收 Release

### Requirement: 发布产物名称与状态统一
新发布的 APK/AAB、artifact 和 Release 说明 SHALL 不包含额外的 `production` / `acceptance` 模式标签；成功发布 SHALL 是非草稿、非预发布的正式 Release，并设为 Latest。

#### Scenario: 发布新版本
- **WHEN** 正式构建及发布检查全部通过
- **THEN** APK/AAB 文件名保留版本、日期、版本号和 `release` 标识，不带模式后缀
- **THEN** 标签为 `v<versionName>-<versionCode>`，GitHub 显示正式 Release 与 Latest
- **THEN** 不修改既有版本的文件、标签或下载链接

### Requirement: 删除模式不得削弱发布检查
正式构建 MUST 保留目标提交 CI、签名、Lint、导出组件、产物和助手隔离检查；已接受厂商事项 SHALL 继续明确告警，其他失败 MUST 阻断。

#### Scenario: 检查失败
- **WHEN** 目标提交无成功 CI、正式签名缺失或其他质量检查失败
- **THEN** 发布失败且不创建成功发布结果，不以省略模式或旧模式参数绕过检查

#### Scenario: 已接受厂商事项仍存在
- **WHEN** 构建使用用户已经接受的现有 QLZ/腾讯配置
- **THEN** 明确输出不含凭据的风险警告，不扩展风险接受范围或宣称问题已修复
