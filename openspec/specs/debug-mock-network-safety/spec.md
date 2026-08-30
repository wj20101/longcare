# debug-mock-network-safety Specification

## Purpose

为 LongCare 的显式 Debug 本地 Mock 模式建立可验证的网络安全边界，使开发与 API 36 冒烟在无生产流量、数据确定且变体隔离的前提下覆盖关键业务，同时保留真实 Debug 联调能力。

## Requirements

### Requirement: Mock 模式必须显式启用
仓库在未提供 `debug.useMockData` 属性时 SHALL 生成 `USE_MOCK_DATA=false` 的 Debug 构建；仅当调用方显式设置 `debug.useMockData=true` 时 SHALL 启用本地 Mock 模式，并且构建配置、运行时行为和长期文档 MUST 对该规则保持一致。

#### Scenario: 未提供属性时构建 Debug
- **WHEN** 开发者未提供 `debug.useMockData` 构建属性
- **THEN** 生成的 Debug 应用 SHALL 使用真实联调模式且 `USE_MOCK_DATA` 为 `false`

#### Scenario: 显式启用 Mock
- **WHEN** 开发者以 `debug.useMockData=true` 构建 Debug 应用
- **THEN** 生成的应用 SHALL 启用本地 Mock 安全模式且 `USE_MOCK_DATA` 为 `true`

#### Scenario: 显式关闭 Mock
- **WHEN** 开发者以 `debug.useMockData=false` 构建并运行 Debug 应用
- **THEN** 应用 SHALL 保留现有真实第一方服务联调行为，不得因本能力改写为本地 Mock 响应

### Requirement: 显式 Mock 模式必须对第一方 API fail-closed
显式 Mock 模式下，由应用第一方 API 客户端发起的每个 HTTP 请求 MUST 按请求方法和规范化路径匹配显式登记项；已登记请求 SHALL 在进程内完成，未登记或方法不匹配的请求 SHALL 在进程内以可分类的“缺失 Mock”结果失败，并且 MUST NOT 建立真实网络连接。

#### Scenario: 命中已登记路由
- **GIVEN** 请求的方法和规范化路径存在显式登记项
- **WHEN** 第一方 API 客户端在显式 Mock 模式下发送请求
- **THEN** 应用 SHALL 返回该登记项的本地响应且不访问任何远端主机

#### Scenario: 路径未登记
- **GIVEN** 请求的规范化路径不存在登记项
- **WHEN** 第一方 API 客户端在显式 Mock 模式下发送请求
- **THEN** 请求 SHALL 以可识别的本地“缺失 Mock”结果失败，且 MUST NOT 回退到真实网络

#### Scenario: 方法与登记项不一致
- **GIVEN** 路径已登记但当前 HTTP 方法未登记
- **WHEN** 第一方 API 客户端在显式 Mock 模式下发送请求
- **THEN** 请求 SHALL 视为缺失 Mock 而在本地失败，不得复用其他方法的响应或访问远端主机

#### Scenario: 输出缺失路由诊断
- **WHEN** 显式 Mock 模式拒绝未登记请求
- **THEN** 诊断 SHALL 包含请求方法和规范化路径，并且 MUST NOT 包含授权头、cookie、token、请求体或用户隐私数据

### Requirement: Mock 路由和 fixture 必须确定且有效
同一显式场景、请求方法和规范化路径 SHALL 产生确定性响应；所有登记 fixture MUST 能被对应 API 契约解析并通过该业务实际消费的关键语义校验，场景差异 MUST 由测试专用输入显式选择而不得随机决定。

#### Scenario: 重复执行同一场景
- **GIVEN** Mock 场景选择和请求输入保持不变
- **WHEN** 测试重复执行同一路由
- **THEN** 响应状态、响应模型和影响流程分支的字段 SHALL 保持一致

#### Scenario: 选择不同业务分支
- **WHEN** 测试需要订单已结束、订单进行中、人脸已登记或未登记等不同分支
- **THEN** 测试 SHALL 通过明确的测试专用场景选择该分支，运行结果 MUST NOT 依赖随机数

#### Scenario: 校验 fixture 契约
- **WHEN** 运行 Mock 契约测试或质量门禁
- **THEN** 每个登记 fixture SHALL 解析为其声明的 API 响应模型，并验证运行时实际要求的格式、枚举、时间和加密字段语义

#### Scenario: fixture 无效
- **GIVEN** fixture 包含不可解析模型、无效十六进制密钥、与场景矛盾的时间或其他关键语义错误
- **WHEN** 运行 Mock 契约测试
- **THEN** 门禁 MUST 失败并定位到对应的方法、路径和 fixture

### Requirement: 关键第一方流程必须具备完整 Mock 契约
显式 Mock 模式 SHALL 覆盖安全冒烟清单所需的全部第一方 API，至少包括登录、服务单、系统启动/配置、版本检查、文件元数据以及人脸获取、设置和核验；清单与登记路由 MUST 由自动化验证保持同步。

#### Scenario: 获取未登记人脸状态
- **GIVEN** 测试显式选择“用户尚未登记人脸”场景
- **WHEN** 应用请求获取人脸状态
- **THEN** 本地响应 SHALL 引导至现有的人脸设置流程且不访问真实服务

#### Scenario: 获取已登记人脸状态
- **GIVEN** 测试显式选择“用户已登记人脸”场景
- **WHEN** 应用请求获取人脸状态
- **THEN** 本地响应 SHALL 提供契约有效的已登记状态且不访问真实服务

#### Scenario: 冒烟清单新增第一方请求
- **GIVEN** 安全冒烟清单引用了一个新的第一方 API 请求
- **WHEN** 运行路由覆盖门禁
- **THEN** 若不存在对应方法、路径、fixture 与契约测试，门禁 MUST 失败

### Requirement: 第三方数据面必须使用受控测试边界
显式 Mock 模式 MUST NOT 将第一方 HTTP Mock 误宣称为 COS、地图、WebView 或厂商 SDK 的第三方端到端替代；需要验证第三方调用之后的应用行为时，测试 SHALL 在应用已有网关边界使用 Debug/Test fake 或明确排除该外部步骤，并且生产实现 MUST 不包含认证、活体、上传或下载绕过。

#### Scenario: 离线验证照片上传成功分支
- **WHEN** 安全冒烟需要覆盖照片上传成功后的应用流程
- **THEN** 测试 SHALL 使用受控的 Debug/Test 上传网关返回确定性结果，且 MUST NOT 将 Mock 凭据发送到真实云存储

#### Scenario: 验证真实相机与人脸拒绝
- **WHEN** API 36 冒烟使用不满足真实人脸或活体要求的输入
- **THEN** 应用 SHALL 保留 CameraX/ML Kit 或厂商能力的真实拒绝行为，不得通过生产代码绕过验证

#### Scenario: 覆盖人脸核验后的倒计时
- **WHEN** 自动化需要验证已核验后的导航和倒计时
- **THEN** 测试 SHALL 从测试所有的已验证状态或业务网关进入后续流程，且该入口 MUST 不存在于 Release 产物

#### Scenario: WebView 导航
- **WHEN** WebView 在 Debug 或 Release 中打开业务允许的任意 host
- **THEN** 本能力 SHALL 不新增 host 白名单或改变既有 WebView 导航策略

### Requirement: 版本更新冒烟必须禁止真实安装包下载
显式 Mock 模式下，版本检查 SHALL 使用本地确定性响应；安全冒烟 MAY 验证无更新、发现更新及更新提示交互，但 MUST NOT 访问真实安装包地址或触发不受控下载。

#### Scenario: 无可用更新
- **GIVEN** 本地版本响应不高于当前应用版本
- **WHEN** 后台版本检查完成
- **THEN** 应用 SHALL 保持无更新提示且不发起安装包下载

#### Scenario: 存在可用更新
- **GIVEN** 测试显式选择高于当前应用版本的本地响应
- **WHEN** 测试验证更新提示及其状态传递
- **THEN** 应用 SHALL 使用受控测试下载边界或在下载前结束场景，MUST NOT 请求 fixture 中的真实外部 URL

### Requirement: Mock 能力必须与发布和性能变体隔离
Release 产物 MUST 不包含 Debug Mock 路由、fixture 或测试 fake，且 `USE_MOCK_DATA` MUST 为 `false`；现有 performance/profile 专用离线网络策略 SHALL 保持独立并优先于 Debug Mock 能力。

#### Scenario: 检查 Release 产物
- **WHEN** 运行 Release 产物隔离门禁
- **THEN** 产物 SHALL 不包含 Mock assets、Debug/Test fake 或可启用 Mock 的运行时入口，并且 `USE_MOCK_DATA` SHALL 为 `false`

#### Scenario: 运行 performance/profile 构建
- **WHEN** performance/profile 变体执行基准或离线保护
- **THEN** 该变体 SHALL 继续使用其既有专用策略，Debug Mock 路由 MUST NOT 覆盖或削弱该策略

#### Scenario: 运行 API 36 安全冒烟
- **GIVEN** Debug 应用已显式启用 Mock 且测试环境无可用外网
- **WHEN** 冒烟执行约定的登录、服务单、人脸、上传替身和版本检查路径
- **THEN** 可控应用流程 SHALL 完成或产生预期业务拒绝，不得观察到第一方真实网络访问
