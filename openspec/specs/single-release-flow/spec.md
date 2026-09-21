# single-release-flow Specification

## Purpose

定义 LongCare 单一正式 Release 的构建与发布契约，消除验收和生产模式造成的重复选项、名称与行为分支，同时保留签名、混淆、质量检查以及本地读卡检测的业务隔离边界。

## Requirements

### Requirement: 正式发布只有一个入口语义
发布流程 SHALL 仅提供手动正式 Release 入口，不提供 `production` 或 `acceptance` 模式、发布时依赖来源切换或 Baseline Profile 生成选项；标准 Release 构建 MUST 无需额外模式参数。Baseline Profile 生成 SHALL 保留独立维护入口。

#### Scenario: 执行正式构建
- **WHEN** 开发者执行 Release APK/AAB 构建或启动 Android Release 工作流
- **THEN** 不需要选择发布模式，产物始终按正式签名、不可调试、R8 和资源压缩配置构建
- **THEN** 既有版本递增逻辑继续有效，不产生另一种验收 Release

#### Scenario: 推送版本标签
- **WHEN** 外部推送版本标签
- **THEN** 不触发一个必然被拒绝的自动发布运行

### Requirement: 发布产物名称与状态统一
新发布的 APK/AAB、artifact 和 Release 说明 SHALL 不包含额外的 `production` / `acceptance` 模式标签；成功发布 SHALL 是非草稿、非预发布的正式 Release，并设为 Latest。

#### Scenario: 发布新版本
- **WHEN** 正式构建及发布检查全部通过
- **THEN** APK/AAB 文件名保留版本、日期、版本号和 `release` 标识，不带模式后缀
- **THEN** 标签为 `v<versionName>-<versionCode>`，GitHub 显示正式 Release 与 Latest
- **THEN** 不修改既有版本的文件、标签或下载链接

### Requirement: 删除模式不得削弱发布检查
正式构建 MUST 保留目标提交 CI、签名、Lint、导出组件、产物和读卡检测业务隔离检查；已接受厂商事项 SHALL 继续明确告警，其他失败 MUST 阻断。退役助手构建检查 MUST NOT 导致主应用正式业务与检测隔离的回归覆盖丢失。

#### Scenario: 检查失败
- **WHEN** 目标提交无成功 CI、正式签名缺失或其他质量检查失败
- **THEN** 发布失败且不创建成功发布结果，不以省略模式或旧模式参数绕过检查

#### Scenario: 已接受厂商事项仍存在
- **WHEN** 构建使用用户已经接受的现有 QLZ/腾讯配置
- **THEN** 明确输出不含凭据的风险警告，不扩展风险接受范围或宣称问题已修复

### Requirement: 单一发布只包含主应用安装产物
Android Release SHALL 使用同一次版本递增和同一源提交生成主应用 Release APK/AAB，保留现有主应用 applicationId、签名和更新协议。成功发布 MUST 提供两类安装产物及覆盖它们的 SHA-256 校验信息，并留存主应用混淆映射；MUST NOT 构建或上传新的助手 APK。

#### Scenario: 主应用发布成功
- **WHEN** 目标提交 CI、签名、质量与产物检查全部通过
- **THEN** 新版本页提供主应用 APK/AAB 和校验信息，不提供新的助手附件
- **THEN** 无额外发布模式，仍为正式 Release 和 Latest

#### Scenario: 主应用产物缺失或无效
- **WHEN** 主应用 APK/AAB 缺失、签名或身份不符合既有主应用要求
- **THEN** 在创建 GitHub Release 前失败，不以删除助手为由绕过检查

#### Scenario: 保留历史版本
- **WHEN** 单应用流程生效
- **THEN** 不删除、替换或回填历史 Release、标签和主应用或助手附件

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
