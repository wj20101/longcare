## Purpose

建立可重复验证的 Android 启动性能契约，使 Startup Profile 只优化初始显示路径，Baseline Profile 覆盖后续关键旅程，并以 TTID、TTFD、打包产物和真机对比共同证明配置真实有效。

## ADDED Requirements

### Requirement: 启动场景具有确定且隔离的前置状态
性能配置系统 SHALL 分别提供首次启动且未同意隐私、已同意隐私且未登录、已登录护理身份并进入 Home、已登录销售身份并进入 Home 的确定场景；每个场景 MUST 在启动前建立声明的唯一状态，并在到达准确目标页面的可交互节点后才判定成功。

#### Scenario: 首次启动停留在隐私协议
- **WHEN** 测试从无隐私同意、无有效会话的干净安装状态冷启动应用
- **THEN** 场景只在隐私协议标题和同意/不同意动作均可交互时成功
- **THEN** 场景 MUST NOT 自动同意隐私或初始化需要同意后才允许的业务状态

#### Scenario: 已同意隐私后进入未登录入口
- **WHEN** 测试预置有效隐私同意但不存在有效会话并冷启动应用
- **THEN** 场景只在登录页品牌节点、输入区和提交动作均可交互时成功
- **THEN** 场景 MUST NOT 依赖真实账号、短信、生产网络响应或上一次场景残留状态

#### Scenario: 已登录启动进入正确 Home 体验
- **WHEN** 测试分别使用隔离的护理或销售性能测试身份预置有效会话并冷启动应用
- **THEN** 每个场景只在该身份对应的护理或销售 Home 主内容达到可交互状态时成功
- **THEN** 旧 namespace、旧 session epoch 或错误角色页面 MUST NOT 被接受为成功

#### Scenario: 前置状态与目标页面不一致
- **WHEN** 任一隐私、会话、用户 namespace 或页面断言无法建立或在超时前不成立
- **THEN** 当前场景 MUST 失败并指出场景名称、期望状态和缺失节点
- **THEN** package root、固定睡眠、任意手势或窗口空闲 MUST NOT 单独作为成功证据

### Requirement: Startup Profile 与 Baseline Profile 按语义分层
性能配置系统 SHALL 只把从进程冷启动到目标首屏初始显示所必需的路径纳入 Startup Profile，并 SHALL 把启动路径及登录后的性能敏感业务交互纳入 Baseline Profile。

#### Scenario: 生成 Startup Profile
- **WHEN** 执行 Profile 生成任务
- **THEN** `startup-prof.txt` 只包含首次启动、未登录启动、护理 Home 启动和销售 Home 启动到各自初始可交互节点所需的规则
- **THEN** 首屏之后的列表滚动、业务导航、返回操作和异步业务加载 MUST NOT 被标记为 Startup Profile 场景

#### Scenario: 生成 Baseline Profile 业务超集
- **WHEN** 同一次生成覆盖启动场景和登录后关键业务旅程
- **THEN** `baseline-prof.txt` SHALL 包含 `startup-prof.txt` 的全部有效规则
- **THEN** `baseline-prof.txt` MUST 包含至少一个由声明的关键业务旅程产生且不属于 Startup Profile 的有效规则，因此两个文件 MUST NOT 完全相同

#### Scenario: 关键业务旅程发生变化
- **WHEN** 关键页面或业务优先级改变导致旅程需要增删
- **THEN** 变更 MUST 明确声明该旅程属于 Startup 或 Baseline-only，并同步更新场景断言和守卫 fixture
- **THEN** 不得仅通过新增盲滑、固定等待或无目标返回操作扩大 Profile

### Requirement: 应用报告可解释的 TTID 与 TTFD
应用 SHALL 保留系统自动测量的 TTID，并 SHALL 在当前启动场景的真实目标内容完成首帧且达到可交互状态后报告 fully drawn，使启动基准能够同时产出 `timeToInitialDisplayMs` 和 `timeToFullDisplayMs`。

#### Scenario: 隐私协议是当前可交互目标
- **WHEN** 首次启动必须等待用户选择隐私协议
- **THEN** fully drawn SHALL 对应协议内容与动作均已绘制并可交互的时刻
- **THEN** 该信号 MUST NOT 等待尚未获得隐私授权的 SDK、Worker 或网络任务

#### Scenario: 会话仍在解析
- **WHEN** 应用仍显示阻断输入的会话解析进度层
- **THEN** 应用 MUST NOT 把该中间状态报告为登录页或 Home 已 fully drawn

#### Scenario: 登录页或 Home 成为真实启动目标
- **WHEN** 会话解析完成且登录页或身份对应 Home 的主要内容已绘制并可交互
- **THEN** 应用 SHALL 为本次 Activity 启动报告 fully drawn
- **THEN** 非首屏更新检查、滚动后内容和非关键后台恢复 MUST NOT 延迟该信号

#### Scenario: 启动基准读取性能指标
- **WHEN** Macrobenchmark 完成任一受支持的冷启动场景
- **THEN** 结果 SHALL 同时包含 TTID 与 TTFD
- **THEN** 缺失 TTFD MUST 使该场景失败，而不是只保留 TTID 后继续宣称启动可用

### Requirement: Profile 与无 Profile 的比较条件等价
性能基准 SHALL 在同一设备、同一构建输入、同一预置状态和同一目标页面断言下比较无预编译与要求 Baseline Profile 的冷启动；两种编译模式之间唯一允许的测量差异是 Profile 编译状态。

#### Scenario: 比较无 Profile 与 Baseline Profile
- **WHEN** 对同一启动场景执行 `None` 与要求 Baseline Profile 的 `Partial` 测量
- **THEN** 两组运行 SHALL 使用相同的状态准备、启动动作、超时、目标节点和迭代策略
- **THEN** 结果 SHALL 保存每组 TTID/TTFD 的中位数、设备/系统标识和机器可读原始报告

#### Scenario: 要求的 Profile 不存在或未应用
- **WHEN** Profile 模式无法找到、编译或应用目标 Baseline Profile
- **THEN** Profile 基准 MUST 失败
- **THEN** 系统 MUST NOT 自动降级为无 Profile 后仍报告比较成功

#### Scenario: 模拟器完成性能基准
- **WHEN** API 33 受管模拟器成功生成 Profile 或运行基准
- **THEN** 结果只 SHALL 证明旅程、依赖链和报告格式稳定
- **THEN** 系统 MUST NOT 把模拟器绝对耗时或单次结果用作真实设备性能收益结论

#### Scenario: 真机确认性能收益
- **WHEN** 变更准备声明启动优化完成
- **THEN** 同一台受控的多核 ARM64 真机 SHALL 以重复测量确认 Profile 与无 Profile 的相对 TTID/TTFD
- **THEN** 未达到预先记录的抗噪声判定条件时，收益状态 MUST 保持未验证并阻止完成性能收益验收

### Requirement: 发布产物携带并使用有效 Profile
生成与发布验收 SHALL 同时验证文本 Profile、APK/AAB 中的 ART Profile 和 R8 Startup DEX 布局证据，并 MUST 对缺失、空文件、错误分层或构建后不一致 fail closed。

#### Scenario: 生成任务输出文本 Profile
- **WHEN** Profile 生成任务成功结束
- **THEN** 受支持的 variant SHALL 同时生成非空 `baseline-prof.txt` 与 `startup-prof.txt`
- **THEN** 两者 SHALL 满足 Startup 子集和 Baseline 业务超集契约

#### Scenario: 验收 Release 打包 ART Profile
- **WHEN** 构建 minified acceptance Release APK 与 AAB
- **THEN** APK/AAB SHALL 包含非空且可解析的 `baseline.prof` 与 `baseline.profm`
- **THEN** 打包验证 MUST 使用本次构建产物，不得用工作区中任意旧文件存在来代替

#### Scenario: R8 应用 Startup DEX 布局
- **WHEN** 检查 minified acceptance Release AAB 的 R8 元数据
- **THEN** 元数据 SHALL 声明 DEX layout 与 profile-guided optimization 已启用
- **THEN** 至少一个实际打包 DEX SHALL 标记为 Startup 且其 checksum MUST 与 AAB 中对应 DEX 一致

#### Scenario: 正式生产门禁仍被厂商条件阻断
- **WHEN** Profile 相关验收全部通过但既有厂商生产条件仍未满足
- **THEN** production Release MUST 继续 fail closed
- **THEN** Profile 成功 MUST NOT 被解释为厂商 AAR、生产配置或签名问题已解决

### Requirement: Profile 语义由可执行守卫防止退化
工程 SHALL 提供带正反 fixture 的自动守卫，验证场景清单、状态准备、准确页面断言、fully drawn、编译模式对称性、Profile 分层和生产变体隔离；仅检查源文件存在或任意交互调用不构成有效门禁。

#### Scenario: 合法场景和产物契约
- **WHEN** 启动与业务旅程符合所有场景、分层、指标和产物要求
- **THEN** focused 守卫及其正向 fixture SHALL 通过

#### Scenario: Startup 场景混入业务交互
- **WHEN** Startup Profile 场景包含首屏之后的滚动、业务导航、返回或异步业务加载
- **THEN** 负向 fixture SHALL 稳定失败并指出 Startup/Baseline 分类错误

#### Scenario: 场景缺少确定性证据
- **WHEN** 场景只等待 package root、固定时长、窗口空闲或执行无目标手势而没有准确页面断言
- **THEN** 负向 fixture SHALL 稳定失败并指出缺失的前置状态或目标节点

#### Scenario: 性能测试控制能力泄漏到生产
- **WHEN** 测试状态入口、假身份、测试凭据或 Profile 专用组件可被 production Release 包含或从外部调用
- **THEN** 安全边界守卫 SHALL 失败
- **THEN** 不得通过导出组件、源码 secret、debug 签名 fallback 或放宽现有发布门禁解决测试可控性
