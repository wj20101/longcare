# android-build-baseline Specification

## Purpose

建立可被构建、质量守卫和维护文档共同验证的 Android 工程基线，避免模块级配置漂移、无依据升级和预览依赖长期滞留影响正式交付。

## Requirements

### Requirement: Android 基线具有单一事实来源
工程 SHALL 为 `minSdk`、`compileSdk`、正式 `targetSdk` 和 JDK 版本分别维护唯一的权威值，所有参与应用交付的 Android 模块、验证工具和长期技术栈说明 MUST 与这些值一致；任一模块重复定义或偏离时，质量验证 MUST fail-closed 并报告字段、期望值和偏离位置。

#### Scenario: 所有模块继承统一基线
- **WHEN** 维护者运行构建基线一致性验证
- **THEN** 验证确认 `minSdk 24`、`compileSdk 37`、`targetSdk 36` 和 JDK 21 在所有相关模块中一致生效
- **AND** 输出能够定位每个值的唯一权威来源

#### Scenario: 模块静默覆盖基线
- **WHEN** 任一应用、库或基准性能模块单独声明不同的 SDK 或 JDK 值
- **THEN** 质量验证 MUST 失败
- **AND** 失败信息列出冲突字段、权威值与冲突文件

### Requirement: 正式依赖默认采用稳定版本
工程 SHALL 默认只采用官方版本源认定的稳定依赖版本；任何 alpha、beta、RC、snapshot 或兼容过渡变体 MUST 进入显式豁免清单，并记录适用制品、负责人、使用原因、验证范围和可检查的退出条件。

#### Scenario: 引入新的预览依赖
- **WHEN** version catalog 出现未被豁免的预览版本
- **THEN** 依赖治理验证 MUST 失败
- **AND** 提示维护者改用稳定版本或补齐时限明确的豁免

#### Scenario: 已批准的 Baseline Profile 预览插件
- **WHEN** Baseline Profile 与 Benchmark 继续使用 `1.5.0-rc02`
- **THEN** 验证仅在其豁免包含负责人、原因、兼容验证和“稳定且兼容的 1.5.x 发布后退出”条件时通过
- **AND** 其他制品不得复用该豁免

#### Scenario: 豁免退出条件已经满足
- **WHEN** 官方已发布经项目验证兼容的稳定 `1.5.x`
- **THEN** 依赖治理验证 MUST 要求升级稳定版并移除对应预览豁免及警告抑制

### Requirement: 低风险稳定依赖按验证边界升级
工程 SHALL 将 Coil 解析为 `3.6.0`，并将 kotlinx-datetime 解析为稳定 `0.8.0` 而非 `0.8.0-0.6.x-compat`；升级后 MUST 保持现有图片加载结果、日期时间序列化、时区换算和业务时间判断不变，且不得出现同一依赖族的混合解析版本。

#### Scenario: 依赖解析结果正确
- **WHEN** 维护者检查应用运行时依赖图
- **THEN** 所有 Coil 制品统一解析为 `3.6.0`
- **AND** kotlinx-datetime 统一解析为稳定 `0.8.0`
- **AND** 依赖图中不存在旧版本或兼容变体残留

#### Scenario: 图片与时间行为回归
- **WHEN** 升级后的 focused tests 和关键业务验证运行
- **THEN** 网络图片、占位与错误状态保持既有行为
- **AND** 日期时间序列化、时区边界和倒计时相关判断保持既有结果

#### Scenario: 升级产生不兼容行为
- **WHEN** focused tests、编译或运行验证发现依赖升级引入不兼容
- **THEN** 该依赖升级 MUST 独立回滚到变更前版本
- **AND** 不得通过删除断言或放宽质量门禁接受回归

### Requirement: 厂商兼容边界阻断不安全工具链升级
工程 MUST 保持当前厂商 AAR、厂商 Maven 制品及其所需 Jetifier 行为不变；只要任一生产必需厂商制品仍依赖 Jetifier，工具链治理 SHALL 阻止进入移除 Jetifier 支持的 AGP 10 或更高版本。

#### Scenario: 当前 AGP 9 基线验证
- **WHEN** 工程使用 AGP `9.3.2` 且生产厂商制品仍依赖 Jetifier
- **THEN** 构建允许保留 `android.enableJetifier=true`
- **AND** 将其弃用警告登记为厂商外部依赖而非项目内可直接删除项

#### Scenario: 请求升级至 AGP 10
- **WHEN** 候选升级会移除 Jetifier 且厂商尚未提供已验证的纯 AndroidX 制品
- **THEN** 工具链升级验证 MUST 失败
- **AND** 不得修改、拆包或绕过生产必需厂商 AAR 来制造通过结果

### Requirement: 技术栈说明不得与可执行基线漂移
长期技术栈文档 SHALL 与权威 SDK/JDK 基线及 version catalog 中的实际解析版本一致；版本或基线变化 MUST 同步更新文档，漂移验证 MUST 对过期值 fail-closed。

#### Scenario: 技术栈事实一致
- **WHEN** 维护者运行文档与构建基线漂移验证
- **THEN** 文档中的应用版本、SDK/JDK、Navigation、CameraX 和 Baseline Profile/Benchmark 版本与可执行配置一致

#### Scenario: 文档保留旧版本
- **WHEN** 任一受管版本已变更但技术栈文档仍记录旧值
- **THEN** 漂移验证 MUST 失败
- **AND** 输出具体字段及配置值与文档值
