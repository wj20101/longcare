## Purpose

为销售端 QLZ 蓝牙设备评估建立可生产发布的初始化、凭据、安全传输、会话隔离和失败恢复契约，在保持现有客户与报告流程的同时移除客户端固定测试配置及已知弱 TLS SDK。

## ADDED Requirements

### Requirement: QLZ 初始化配置由当前服务端环境提供
客户端 SHALL 在访问 QLZ 设备能力前，从当前登录会话可访问的加密系统配置取得非空 SDK appKey 与明确的测试或生产环境标识，并且 MUST 不使用源码、资源、BuildConfig、本机属性或打包环境中的固定 key 作为 fallback。

#### Scenario: 服务端配置可用
- **WHEN** 已登录销售用户进入设备评估，且当前环境返回合法的 QLZ appKey 与环境标识
- **THEN** 客户端 SHALL 先使用该配置初始化 QLZ SDK，再读取设备 ID 并请求一次性检测 Token

#### Scenario: 配置缺失或无法解析
- **WHEN** 服务端配置缺少 appKey、环境标识无效或加密配置无法解析
- **THEN** 客户端 MUST 拒绝初始化和打开 QLZ 页面，展示可重试的通用配置错误，且不得回退到任何内置测试值

#### Scenario: 生产包收到测试环境配置
- **WHEN** production 构建收到测试环境 QLZ 配置
- **THEN** 客户端 MUST 拒绝初始化并保持 fail closed，不得仅依赖服务端输入解除生产限制

#### Scenario: Debug 或验收环境使用测试配置
- **WHEN** Debug 或显式 Acceptance 构建收到对应环境服务端下发的测试配置
- **THEN** 客户端 SHALL 仅在构建策略允许时进入测试模式，且产物与运行证据 MUST 继续明确标识为非生产用途

### Requirement: QLZ 客户端不持有服务端秘密
QLZ 的 `appSecret` MUST 只用于 LongCare 服务端签名，不得进入 Android 源码、资源、BuildConfig、日志、持久化配置或安装包；客户端 SHALL 只持有初始化所需的非秘密 appKey 与服务端签发的一次性检测 Token。

#### Scenario: 生成任意 Android 产物
- **WHEN** 构建 Debug、Acceptance 或 production APK/AAB
- **THEN** 产物与生成配置中 MUST 不包含 QLZ `appSecret` 或固定测试 appKey

#### Scenario: 初始化或 Token 请求失败
- **WHEN** QLZ 配置、初始化或 Token 请求失败
- **THEN** 日志和用户提示 MUST 不输出 appKey、Token、设备标识或服务端内部错误详情

### Requirement: QLZ SDK 满足生产传输与来源要求
production 构建使用的 QLZ SDK MUST 来自已记录的厂商来源、版本和校验和，其所有可达 TLS 路径 MUST 执行正常证书与主机校验，并且不得依赖 QLZ 专用明文网络例外。

#### Scenario: 接入厂商修复包
- **WHEN** 新 QLZ AAR 被纳入应用依赖
- **THEN** 维护证据 SHALL 记录来源、版本、校验和、依赖、Manifest、ABI 与 consumer rules 差异，并证明旧 1.3.0.2 AAR 不再打包

#### Scenario: 执行 production 安全检查
- **WHEN** 对 production 依赖和产物运行 Lint、字节码与网络配置检查
- **THEN** QLZ 可达代码 SHALL 不出现信任任意证书、跳过主机校验或明文传输的路径

#### Scenario: 安全证据不足
- **WHEN** 闭源实现无法证明传输安全，或新包仍命中 QLZ 弱 TLS finding
- **THEN** QLZ production readiness MUST 保持失败，且不得通过 waiver、ignore 或网络安全例外使其变绿

### Requirement: 设备评估业务流程与恢复语义保持稳定
在安全配置和 SDK 更新后，客户端 SHALL 保持“选择客户、初始化设备、取得设备 ID、请求一次性 Token、请求蓝牙权限、打开检测页、处理结果并重新查询报告”的业务顺序，并保持现有错误与恢复边界。

#### Scenario: 完成真实设备评估
- **WHEN** 当前用户、客户、服务端配置、BLE 权限、检测设备与 Token 均有效
- **THEN** 用户 SHALL 能完成 QLZ 检测，应用 SHALL 接收进度/完成事件并从 LongCare 客户详情重新取得报告状态

#### Scenario: 蓝牙权限被拒绝后恢复
- **WHEN** 用户拒绝所需蓝牙或旧系统定位权限
- **THEN** 应用 MUST 不打开检测页，并 SHALL 在用户随后授权后允许重新发起同一客户的评估

#### Scenario: 一次性 Token 过期
- **WHEN** QLZ 返回 Token 缺失或过期错误
- **THEN** 应用 SHALL 对当前客户和设备最多刷新一次 Token 并发出一次重新打开请求，连续失败时停止重试并提示重新进入评估

#### Scenario: 用户取消或 SDK 页面关闭
- **WHEN** 用户取消检测或关闭 SDK 页面
- **THEN** 应用 SHALL 保留当前客户上下文并刷新可观察设备状态，不得把取消误报为完成

### Requirement: QLZ 初始化状态与登录会话隔离
客户端 SHALL 把已初始化的 QLZ 配置关联到当前登录会话和配置版本；账号退出、会话失效、换号或配置变化后 MUST 不继续复用旧会话的初始化状态、设备状态或 Token。

#### Scenario: 同一会话重复进入
- **WHEN** 同一登录会话使用同一有效配置重复进入设备评估
- **THEN** 客户端 SHALL 只复用与当前会话和配置一致的已验证 SDK 初始化，并且 MUST 为每次评估请求新的适用 Token

#### Scenario: 退出或切换账号
- **WHEN** 用户退出、会话失效或切换到另一账号
- **THEN** 客户端 MUST 使旧 QLZ 配置、设备状态和 Token 失效，并在新会话重新取得服务端配置后才允许评估

#### Scenario: 服务端配置发生变化
- **WHEN** 当前会话取得的 appKey 或环境标识与已初始化配置不同
- **THEN** 客户端 MUST 按厂商支持的重置或重新初始化契约切换配置；若无法安全切换则拒绝继续评估

### Requirement: QLZ 发布门禁独立且保持 fail closed
构建与发布检查 SHALL 能独立识别旧 QLZ AAR、固定客户端配置、生产测试模式和弱 TLS 回退；QLZ 整改完成后只移除 QLZ 对应阻断，不得降低腾讯人脸或其他 release-required 门禁。

#### Scenario: QLZ 整改证据全部满足
- **WHEN** 固定配置已删除、安全 AAR 已替换、服务端契约测试通过且 QLZ 真机验收完成
- **THEN** QLZ 专项 production 检查 SHALL 通过，整体 production Release SHALL 只允许继续因独立的腾讯人脸 P0 按设计失败

#### Scenario: 重新引入旧配置或二进制
- **WHEN** 后续改动重新加入固定 appKey、客户端固定测试模式、旧 1.3.0.2 AAR 或 QLZ 弱 TLS 路径
- **THEN** 自动化负向检查 MUST 阻断 production 构建并指出 QLZ 根因

#### Scenario: 生成验收产物
- **WHEN** 使用显式 Acceptance 模式构建并运行 QLZ 联调
- **THEN** 产物 MUST 保持 acceptance 标识，并且使用服务端测试配置不构成 production readiness 证据
