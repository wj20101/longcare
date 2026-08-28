## Purpose

为销售端 QLZ 蓝牙设备评估建立可正式交付的运行与发布契约：保留业务必需的厂商 AAR，同时只约束项目能够控制的初始化配置、接入边界、失败恢复和业务回归。

## ADDED Requirements

### Requirement: 正式产物包含业务必需的 QLZ AAR
production APK/AAB MUST 包含并使用当前批准的厂商 QLZ AAR，不得仅因厂商二进制内部静态 finding 将其移除、替换为空实现或禁止正式打包。

#### Scenario: 构建正式产物
- **WHEN** 生成 production APK 或 AAB
- **THEN** 产物 SHALL 包含销售设备评估所需的 QLZ 实现，并且构建证据能够确认该能力未被裁剪或替换

#### Scenario: 必需 AAR 缺失
- **WHEN** production 构建未解析到批准的 QLZ AAR，或产物不再包含运行所需实现
- **THEN** 发布检查 MUST 失败并指出业务依赖缺失

#### Scenario: 命中厂商内部静态 finding
- **WHEN** 当前批准 AAR 命中已经登记且由厂商负责的内部静态 finding
- **THEN** 项目 SHALL 保留 finding、版本和责任记录，但 MUST 不仅因此阻断 production 产物

### Requirement: QLZ 正式配置与非生产配置隔离
production 构建 SHALL 使用非空的正式 QLZ SDK key 并强制关闭测试模式；Debug 或显式 Acceptance 的测试配置 MUST 不得进入 production，且项目 MUST 不使用内置测试值作为 production fallback。

#### Scenario: 正式配置完整
- **WHEN** production 构建取得明确的正式 SDK key 且测试模式关闭
- **THEN** 客户端 SHALL 使用该配置初始化 QLZ，并且发布检查不得因当前批准 AAR 的存在而失败

#### Scenario: 正式配置缺失或仍为测试模式
- **WHEN** production 构建缺少正式 SDK key、使用已知测试配置或启用测试模式
- **THEN** 发布检查 MUST 失败，并且不得自动回退到 Debug、Acceptance 或源码内置值

#### Scenario: 处理配置与诊断信息
- **WHEN** 构建、初始化、记录日志或生成诊断信息
- **THEN** 配置值和一次性 Token MUST 不进入版本库、普通日志、异常文案或提交的测试证据

### Requirement: 项目接入不改变厂商 AAR 内部行为
项目 SHALL 把 QLZ 当作不可修改的厂商输入，仅通过厂商公开且业务已在使用的接口完成接入；项目 MUST 不反编译修补 AAR、不植入网络 Hook、不伪造设备标识，也不调用未经厂商确认的内部绕行接口来改变厂商行为。

#### Scenario: 处理厂商内部问题
- **WHEN** 发现 AAR 内部 TLS、遥测、权限或其他闭源实现问题
- **THEN** 项目 SHALL 登记影响、厂商责任和复核条件，并继续验证自身可控业务逻辑，而不是在项目侧修改二进制

#### Scenario: 厂商提供新输入
- **WHEN** 厂商提供新 AAR、正式说明或受支持的配置能力
- **THEN** 项目 MUST 重新评估兼容性和风险，再通过独立变更决定是否升级或调整接入

### Requirement: QLZ 设备评估主流程保持可用
客户端 SHALL 保持“初始化 SDK、取得设备 ID、请求一次性 Token、请求所需 BLE 权限、打开检测页、处理检测事件并重新查询报告”的既有业务顺序，并对项目侧可识别失败提供可恢复反馈。

#### Scenario: 完成真实设备评估
- **WHEN** 当前销售用户、客户、正式配置、BLE 权限、检测设备和 Token 均有效
- **THEN** 用户 SHALL 能完成 QLZ 检测，应用 SHALL 接收进度与完成事件并从 LongCare 客户详情重新取得报告状态

#### Scenario: 蓝牙权限被拒绝后恢复
- **WHEN** 用户拒绝所需蓝牙或适用旧系统定位权限
- **THEN** 应用 MUST 不打开检测页，并 SHALL 在用户随后授权后允许重新发起同一客户的评估

#### Scenario: 一次性 Token 过期
- **WHEN** QLZ 返回项目能够识别的 Token 缺失或过期错误
- **THEN** 应用 SHALL 对当前客户和设备最多刷新一次 Token，连续失败时停止重试并提示用户重新进入评估

#### Scenario: 用户取消或 SDK 页面关闭
- **WHEN** 用户取消检测或关闭 SDK 页面
- **THEN** 应用 SHALL 保留当前客户上下文并刷新可观察设备状态，不得把取消误报为完成

#### Scenario: 厂商能力不可用
- **WHEN** AAR 初始化、设备连接或厂商页面调用失败
- **THEN** 应用 SHALL 给出脱敏且可恢复的业务错误，不得崩溃、无限重试或伪造检测成功

### Requirement: QLZ 发布检查只阻断项目可控缺陷
QLZ 专项发布检查 SHALL 阻断缺失 AAR、production 配置缺失、固定测试 fallback、production 测试模式及项目接入回退；对于已登记的当前厂商 AAR 内部 finding，检查 SHALL 报告但不阻断。其他厂商和通用发布门禁 MUST 保持各自原有语义。

#### Scenario: 项目可控条件全部满足
- **WHEN** production 使用正式配置、测试模式关闭、批准 AAR 已打包且销售业务回归通过
- **THEN** QLZ 项目侧 production readiness SHALL 通过，即使厂商内部已知 finding 仍作为非阻断风险存在

#### Scenario: 项目重新引入测试 fallback
- **WHEN** 后续改动重新加入固定测试 key、默认测试模式或允许 production 复用 Acceptance 配置
- **THEN** 自动化负向检查 MUST 阻断 production 并指出具体项目根因

#### Scenario: 其他发布门禁失败
- **WHEN** 腾讯人脸、签名、16 KB 对齐、R8 或其他独立 release-required 检查失败
- **THEN** 其原有阻断结果 MUST 保持，不得因 QLZ 风险边界调整而被放宽
