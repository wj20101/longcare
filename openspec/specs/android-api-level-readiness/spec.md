# android-api-level-readiness Specification

## Purpose

把正式 targetSdk 升级转化为有证据、可阻断且可重复验证的平台准入流程，确保 LongCare 在当前 API 与下一 API 上保持核心业务和厂商能力可用。

## Requirements

### Requirement: 正式 targetSdk 保持在已验证级别
正式应用 SHALL 在本 change 中继续使用 `targetSdk 36`，并允许使用 `compileSdk 37` 编译；任何模块不得单独提升正式 target，质量守卫 MUST 阻止在 API 37 准入条件未全部满足时发布 `targetSdk 37` 制品。

#### Scenario: 当前正式构建
- **WHEN** 维护者构建任一正式或准正式应用变体
- **THEN** 产物的 `targetSdk` 为 36 且 `compileSdk` 为 37
- **AND** 所有应用相关变体使用同一 target 值

#### Scenario: 未经准入直接提升 target
- **WHEN** 配置或模块尝试把正式 `targetSdk` 提升到 37
- **THEN** 发布前验证 MUST 失败
- **AND** 失败信息列出尚未满足的 API 37 准入项

### Requirement: API 37 提升必须满足完整准入证据
工程 SHALL 仅在 Android 17/API 37 发布稳定版、生产必需厂商 SDK 已确认兼容、目标行为变更清单已验证、大屏自适应验证通过且当前/下一 API 测试矩阵通过后，允许创建独立的 targetSdk 37 升级 change；任一证据缺失或过期时 MUST 视为未就绪。

#### Scenario: Android 17 仍为预览状态
- **WHEN** Android 17/API 37 尚未成为官方稳定平台
- **THEN** API 37 readiness 结果 MUST 为未就绪
- **AND** 预览设备上的兼容测试只能作为提前发现问题的证据，不能授权正式 target 升级

#### Scenario: 厂商兼容证据缺失
- **WHEN** QLZ、腾讯人脸、地图或其他生产必需厂商能力没有 API 37 兼容确认和项目验证
- **THEN** API 37 readiness 结果 MUST 为未就绪
- **AND** 不得通过移除、修改或绕过厂商制品完成 target 升级

#### Scenario: 全部准入项通过
- **WHEN** 平台稳定性、厂商兼容、平台行为、大屏自适应和测试矩阵证据均为当前且通过
- **THEN** readiness 检查允许提出独立的 targetSdk 37 升级 change
- **AND** 本 change 本身仍不修改正式 target 值

### Requirement: 性能配置生成与平台兼容测试相互独立
工程 SHALL 将 Baseline Profile 生成设备与 targetSdk 平台兼容设备视为两类不同验证：工程 SHALL 允许 API 33 设备继续生成性能配置，但该结果 MUST NOT 被用作 API 36 当前行为或 API 37 readiness 的替代证据。

#### Scenario: 生成 Baseline Profile
- **WHEN** API 33 受管设备成功生成并验证 Baseline Profile
- **THEN** 该结果只证明性能配置生成链路有效
- **AND** readiness 状态仍取决于 API 36/37 的独立兼容测试

#### Scenario: 当前 target 兼容测试失败
- **WHEN** API 36 兼容测试失败但 API 33 Profile 生成成功
- **THEN** 当前平台验证 MUST 失败
- **AND** 不得用 Profile 生成结果覆盖该失败

### Requirement: 当前 API 与下一 API 具有分层测试矩阵
工程 SHALL 在 API 36 上对登录、服务单、身份/NFC、定位、相机与图片、倒计时/闹钟、销售评估和 WebView 主链路执行阻断式验证；在 API 37 预览期 SHALL 维护独立 readiness 验证，并在平台稳定且准备升级时将同等范围转为阻断式验证。

#### Scenario: API 36 主链路通过
- **WHEN** 当前 target 的平台验证运行
- **THEN** API 36 上的核心链路、权限拒绝与恢复、前后台切换和资源释放均通过
- **AND** 正式候选构建才可继续后续发布门禁

#### Scenario: API 37 预览期发现问题
- **WHEN** API 37 readiness 验证发现平台或厂商兼容问题
- **THEN** 问题被记录为 target 37 阻断项
- **AND** 不影响仍以 API 36 为 target 的既有发布，除非同一问题也影响当前正式环境

#### Scenario: API 37 稳定后准备升级
- **WHEN** Android 17 稳定且项目提出正式 target 37 升级
- **THEN** API 37 核心链路验证 MUST 转为升级的阻断门禁
- **AND** API 36 回归仍保留以覆盖受支持设备

### Requirement: 大屏与受限方向行为在 target 37 前完成验证
由于 target 37 将取消大屏设备对方向、可调整大小和宽高比限制的兼容豁免，工程 MUST 在升级前验证核心页面在手机和 `sw600dp` 及以上可调整大小窗口中的可达性、状态保留和关键操作，不得依赖锁定竖屏作为唯一正确性条件。

#### Scenario: 大屏窗口改变尺寸或方向
- **WHEN** 核心链路在 `sw600dp` 及以上设备上发生方向或窗口尺寸变化
- **THEN** 当前页面、关键操作与必要状态仍可访问且不被裁切
- **AND** 不发生导航丢失、重复提交或流程无法继续

#### Scenario: 页面仍依赖大屏竖屏豁免
- **WHEN** 任一核心页面只在平台继续尊重固定方向或不可调整大小时才能完成
- **THEN** API 37 readiness MUST 失败
- **AND** 该页面必须在独立适配工作完成并回归通过后重新评估

### Requirement: targetSdk 提升必须原子化并可回滚
正式 targetSdk 提升 SHALL 通过独立 OpenSpec change 同时更新权威 target 值、测试矩阵、兼容守卫和长期文档，并提供不修改用户数据的回滚路径；不得以模块级覆盖或仅修改打包值的方式部分升级。

#### Scenario: 执行后续 target 37 升级
- **WHEN** 准入通过后实施独立升级 change
- **THEN** 所有应用变体、质量守卫、CI 测试环境和文档在同一变更中切换至新 target
- **AND** 构建验证确认不存在旧 target 或模块级覆盖残留

#### Scenario: 升级后出现平台回归
- **WHEN** 新 target 候选在发布前验证中出现阻断回归
- **THEN** 工程能够整体回滚 target 配置与对应门禁变更
- **AND** Room、用户存储和业务数据不因 target 回滚被删除或迁移
