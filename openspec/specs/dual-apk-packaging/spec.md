# dual-apk-packaging Specification

## Purpose

定义同一 LongCare 工程中正式应用与内部助手应用的构建、身份、产物和隔离契约，确保一次明确的双应用构建稳定产出两个可区分 APK。

## Requirements

### Requirement: 两个应用具有稳定且不同的身份
构建系统 MUST 为正式应用和助手应用配置不同且稳定的 applicationId，并 SHALL 保持正式应用现有 applicationId 不变。

#### Scenario: 检查安装包身份
- **WHEN** 构建同一变体的正式 APK 与助手 APK
- **THEN** 正式 APK 的 applicationId 与变更前一致
- **THEN** 助手 APK 使用独立 applicationId、独立应用名称和可区分的 Launcher 图标

#### Scenario: 覆盖安装各自的新版本
- **GIVEN** 设备已同时安装两个应用的旧版本
- **WHEN** 分别安装相同 applicationId 且签名兼容的新正式 APK 和新助手 APK
- **THEN** 两个应用各自原位升级且不会互相替换

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

### Requirement: 正式 APK 不包含验证入口和专用组件
正式应用的所有受支持变体 MUST 不再包含登录 Logo 长按验证行为、功能验证页面、验证专用 Activity、验证专用导航契约、NFC 测试会话或助手专属资源与依赖。

#### Scenario: 使用正式登录页 Logo
- **WHEN** 用户在正式应用登录页点击或长按主 Logo
- **THEN** 系统不打开功能验证 UI、不触发触觉反馈，也不启动任何验证组件

#### Scenario: 检查正式 Manifest 与 APK
- **WHEN** 自动守卫检查正式应用的合并 Manifest、源码边界和 APK 内容
- **THEN** 检查结果不包含助手 Launcher、验证专用 Activity 或可从外部启动的验证组件
- **THEN** 正式业务所需的相机、人脸与 NFC 实现仍可正常构建和使用

### Requirement: 应用模块之间保持单向公共依赖
助手应用 MUST NOT 依赖正式应用模块，正式应用 MUST NOT 依赖助手应用或只服务于助手的验证实现；两者需要复用的能力 SHALL 通过明确的 Core 或 Feature 公共契约提供。

#### Scenario: 校验模块依赖图
- **WHEN** 架构守卫分析项目模块依赖
- **THEN** 正式应用和助手应用之间不存在直接项目依赖边
- **THEN** 新增公共依赖边均被最小化记录并通过模块边界检查

### Requirement: 助手以独立 Release 附件分发
发布系统 SHALL 在同一 GitHub Release 中提供主应用与助手 Release 产物，并 MUST 通过名称和发布说明明确区分用途。助手 MUST 保持独立应用身份，不得进入主应用的商店产物或应用内更新通道。

#### Scenario: 下载同一版本的两个应用
- **WHEN** 用户访问成功发布的新版本 GitHub Release
- **THEN** 可分别下载主应用 Release APK/AAB 和助手 Release APK
- **THEN** 助手附件名称包含 `assistant`，发布说明明确其为验证助手，不与主应用互相覆盖

#### Scenario: 主应用检查更新
- **WHEN** 主应用使用既有应用内更新通道获取更新
- **THEN** 本次新增的助手附件不改变主应用更新协议或其安装包身份
