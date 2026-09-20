## MODIFIED Requirements

### Requirement: 删除模式不得削弱发布检查
正式构建 MUST 保留目标提交 CI、签名、Lint、导出组件、产物和读卡检测业务隔离检查；已接受厂商事项 SHALL 继续明确告警，其他失败 MUST 阻断。退役助手构建检查 MUST NOT 导致主应用正式业务与检测隔离的回归覆盖丢失。

#### Scenario: 检查失败
- **WHEN** 目标提交无成功 CI、正式签名缺失或其他质量检查失败
- **THEN** 发布失败且不创建成功发布结果，不以省略模式或旧模式参数绕过检查

#### Scenario: 已接受厂商事项仍存在
- **WHEN** 构建使用用户已经接受的现有 QLZ/腾讯配置
- **THEN** 明确输出不含凭据的风险警告，不扩展风险接受范围或宣称问题已修复

## REMOVED Requirements

### Requirement: 单一发布包含完整双应用产物
**Reason**: 独立助手退役，双应用产物不再是发布条件。
**Migration**: 新流程仅要求主应用 APK/AAB，移除助手构建、上传、签名和映射检查，保留主应用全部必要门禁。

## ADDED Requirements

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
