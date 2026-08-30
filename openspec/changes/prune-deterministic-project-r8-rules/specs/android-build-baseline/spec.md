## ADDED Requirements

### Requirement: 项目 R8 配置维持最小必要规则集
工程 SHALL 使用启用优化的 Android 默认规则和依赖随制品提供的 consumer rules，并 MUST 从项目自有配置中移除已经确认的零命中、相同、被覆盖或由上述来源完整提供的重复规则；项目自有宽泛规则只有在存在可验证的反射、JNI 或序列化入口及来源说明时才能保留。

#### Scenario: 清理确定无效的项目规则
- **WHEN** 维护者对相同依赖图和 Release 输入运行 R8 Configuration Analyzer
- **THEN** 已确认的项目零命中和被覆盖规则不再由项目配置提供
- **AND** Optimization、Shrinking 和 Obfuscation 分数均不得低于同一依赖图和 Release 输入建立的清理前基线
- **AND** 项目规则不得继续让与目标序列化类型无关的大量类名失去混淆和优化机会

#### Scenario: 依赖规则替代项目副本
- **WHEN** 项目删除已由 Android 默认规则、AndroidX 或 Kotlinx Serialization 制品提供的规则副本
- **THEN** Release 合并配置仍 MUST 包含依赖或默认来源的必要规则
- **AND** `@Keep` 类型、序列化 route 与 JNI 入口保持可用

#### Scenario: 冗余或危险规则重新进入
- **WHEN** 项目配置新增全局禁用 shrinking、optimization 或 obfuscation 的选项，重新加入已确认的重复规则，或新增没有来源和回归证据的宽泛规则
- **THEN** R8 配置质量验证 MUST fail-closed
- **AND** 失败信息指出规则、来源文件和违反的最小化约束

### Requirement: R8 清理保持 minified Release 运行与产物契约
每次项目 R8 规则清理 SHALL 使用当次 minified acceptance Release 产物完成验证，并 MUST 保持现有用户流程、反射/JNI/序列化入口、mapping、资源收缩、Baseline Profile、签名和 Manifest 契约；旧 mapping 或旧构建产物不得用于证明新产物有效。

#### Scenario: 验收 Release 产物完整
- **WHEN** 清理后的 acceptance APK 与 AAB 完成构建
- **THEN** 当次产物的 mapping、资源 shrinking、ART Profile、R8/DEX 一致性、签名与 Manifest 检查全部通过
- **AND** 验收产物继续被明确标识为非生产分发包

#### Scenario: 关键运行链路回归
- **WHEN** 维护者在清理后的 minified 构建上运行风险相称的业务验证
- **THEN** 登录、导航参数恢复、定位、身份核验、照片上传、倒计时、视频呼叫和应用更新流程保持既有行为
- **AND** 不出现由类、字段或方法被错误删除或重命名导致的反射、序列化或 JNI 错误

#### Scenario: 单条规则引发回归
- **WHEN** 某一删除项导致构建、产物校验或运行链路回归
- **THEN** 该删除项 MUST 能够独立回滚并恢复原有行为
- **AND** 不得通过新增整包 keep、关闭 R8 能力或放宽质量门禁掩盖问题

### Requirement: 厂商优化边界保持不变
本批次 MUST 保持生产必需厂商 AAR、Maven 制品及其 consumer rules 原样，不得过滤或重写厂商规则，也不得收窄仍有实际命中的厂商 package-wide 规则；现有 production Release fail-closed 条件 SHALL 保持有效。

#### Scenario: 删除项目内厂商重复项
- **WHEN** 一个项目自有厂商规则被同一项目中更宽且继续保留的厂商规则完整覆盖
- **THEN** 工程可以删除该窄重复项
- **AND** 更宽规则、厂商 AAR 和厂商 consumer rules 保持不变

#### Scenario: 厂商流程仍存在外部阻断
- **WHEN** QLZ 或腾讯人脸的既有生产安全、16 KB 对齐或 consumer rule 条件仍未解决
- **THEN** production Release MUST 继续 fail-closed
- **AND** acceptance Release 通过不得被解释为生产可发布

#### Scenario: 宽泛厂商规则需要进一步优化
- **WHEN** Analyzer 仍显示有实际命中的厂商 package-wide 规则阻碍优化
- **THEN** 本批次 MUST 保留该规则
- **AND** 后续优化必须另建包含厂商业务回归和替代制品评估的独立 change
