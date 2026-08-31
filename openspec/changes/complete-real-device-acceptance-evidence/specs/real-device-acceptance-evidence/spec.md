## Purpose

建立可复核、不可由模拟器或部分手工结果冒充的 Android 真机验收证据契约，把 minified Release 关键链路和 Startup/Profile 性能结论绑定到合格设备、明确构建与完整场景，同时安全保留外部阻断状态。

## ADDED Requirements

### Requirement: 验收开始前验证唯一真机资格
真机验收系统 SHALL 在执行任何 Release 运行结论或性能收益比较前验证唯一目标设备，设备 MUST 为当前目标 API 36、非模拟器、多核 `arm64-v8a`，并 MUST 记录可比较但不泄露原始设备序列号的设备、系统、CPU、电量和热状态元数据。

#### Scenario: 合格 API 36 ARM64 真机
- **WHEN** 唯一连接设备为 API 36、多核 `arm64-v8a` 真机，且电量和热状态满足预先固化的测试条件
- **THEN** 系统生成稳定的匿名设备身份和资格报告
- **THEN** 后续 Release smoke 与启动性能阶段 SHALL 绑定到该设备身份

#### Scenario: 模拟器或错误平台设备
- **WHEN** 设备为模拟器、API 不是 36、ABI 不是 `arm64-v8a`、CPU 少于两个核心，或同时存在多个未显式选择的设备
- **THEN** 真机验收 MUST 在安装或测量前失败并指出不合格字段
- **THEN** 该设备结果 MUST NOT 被标记为 Release 真机通过或性能收益已验证

#### Scenario: 设备状态不适合稳定测量
- **WHEN** 电量、充电、温控、可用核心或其他预设稳定性条件不满足性能配置
- **THEN** 性能阶段 MUST 暂停并报告可恢复条件
- **THEN** 系统 MUST NOT 通过抑制 Benchmark 环境错误继续生成收益结论

### Requirement: 设备证据绑定显式构建产物
验收系统 SHALL 只使用本次显式构建和校验的 minified acceptance APK/AAB 进行 Release smoke，并 SHALL 将性能目标 APK、测试 APK、Profile 文本、Git SHA、应用版本及各自产物 SHA-256 绑定到同一执行标识；旧产物存在或文件名相同 MUST NOT 作为构建一致性证据。

#### Scenario: 当前产物身份完整
- **WHEN** acceptance APK/AAB 和 benchmark 目标/测试 APK 均由声明的当前输入构建并通过既有产物校验
- **THEN** 证据清单记录各产物的规范路径、SHA-256、包名、版本、variant 和共同构建身份
- **THEN** 后续场景结果只能引用该执行标识下的产物

#### Scenario: 混用旧产物或不同构建
- **WHEN** 任一场景引用的 APK、AAB、Profile、Git SHA、应用版本或校验值与执行清单不一致
- **THEN** 对应验收结论 MUST 失败
- **THEN** 系统 MUST NOT 拼接不同构建的通过项形成完整结果

### Requirement: Minified Release smoke 覆盖完整风险场景
验收系统 SHALL 对同一 minified acceptance 构建执行登录、类型安全导航参数与状态恢复、定位、身份/ML Kit/腾讯核验、照片上传、倒计时、QLZ 评估、视频呼叫和应用更新场景；每个场景 MUST 具有明确前置条件、动作、目标状态、禁止异常和 `passed`、`failed`、`blocked` 三态结果。

#### Scenario: 全部 Release smoke 通过
- **WHEN** 所有声明场景均在目标真机和绑定产物上到达准确业务结果，且日志与进程状态未出现禁止异常
- **THEN** R8 运行验收结论 SHALL 标记为 `passed`
- **THEN** 结果 MUST 明确列出每个场景、执行时间、目标节点和日志证据摘要

#### Scenario: 外部账号、订单或外设缺失
- **WHEN** 某场景缺少测试账号、有效护理订单、NFC/R65C、相机/定位条件、QLZ BLE 设备、厂商 Token 或服务端环境
- **THEN** 该场景 MUST 标记为 `blocked` 并列出不含秘密的缺失条件
- **THEN** R8 运行验收 MUST NOT 汇总为 `passed`

#### Scenario: 收缩、反射、JNI 或恢复异常
- **WHEN** 场景或关联日志出现 `ClassNotFoundException`、`NoSuchMethodException`、`NoSuchMethodError`、`UnsatisfiedLinkError`、序列化恢复错误、目标进程崩溃或目标节点超时
- **THEN** 该场景和 R8 运行验收 MUST 标记为 `failed`
- **THEN** 失败信息 MUST 绑定场景、构建和最小异常摘要，不得用整包 keep、关闭 R8 或忽略日志制造通过

### Requirement: Startup/Profile 收益由两轮同条件真机结果判定
性能验收系统 SHALL 在同一合格设备、同一构建输入和同一场景契约上连续运行至少两轮四个 Startup 场景；每轮 SHALL 对 `None` 与要求 Baseline Profile 的模式各执行 10 次冷启动，并 MUST 同时保留 TTID 与 TTFD 原始样本和中位数。

#### Scenario: 两轮结果满足收益条件
- **WHEN** 两轮报告均具有四场景、双模式、每模式 10 个 TTID/TTFD 样本和一致设备/构建身份
- **THEN** 每个“场景 × 指标”的 Profile 中位数退化 MUST 不超过机器配置中的对应预算
- **THEN** 至少一个“场景 × TTID/TTFD 指标” MUST 在两轮中均相对 `None` 改善，性能收益才可标记为 `verified`

#### Scenario: 结果不完整或超过预算
- **WHEN** 任一轮缺场景、缺模式、缺样本、缺 TTFD、Profile 未被要求应用、跨设备/构建，任一比较超过退化预算，或没有指标在两轮持续改善
- **THEN** 性能收益 MUST 保持 `unverified`
- **THEN** 系统 MUST 输出具体轮次、场景、模式、指标和失败条件，不得以平均值或另一轮结果覆盖失败

#### Scenario: 模拟器或旧系统运行基准
- **WHEN** 基准运行在模拟器或 API 29 以下设备
- **THEN** 结果最多 SHALL 标记为旅程、依赖或报告格式证据
- **THEN** 绝对耗时、相对耗时和 TTFD 缺失 MUST NOT 用于真实性能收益结论

### Requirement: 聚合结论独立、可恢复且不泄露秘密
验收系统 SHALL 为 R8 运行验收与 Startup/Profile 收益分别生成独立结论，并 SHALL 保存机器可读的场景状态、证据哈希和可恢复阻断条件；报告 MUST 只进入被忽略的 `build/` 目录或受控 CI artifact，且 MUST NOT 包含账号密码、Token、身份证信息、照片内容、原始设备序列号或服务端秘密。

#### Scenario: 一个验收域通过而另一个未通过
- **WHEN** Release smoke 与性能比较中只有一个满足全部条件
- **THEN** 聚合结果 SHALL 保留一个 `passed`/`verified` 和另一个 `failed`、`blocked` 或 `unverified`
- **THEN** 系统 MUST NOT 用单一总成功状态关闭两个原 change 的剩余任务

#### Scenario: 阻断条件恢复后继续
- **WHEN** 维护者补齐外部账号、订单、外设或设备稳定条件后重新执行阻断场景
- **THEN** 系统可以在相同设备与构建身份仍有效时更新该场景证据
- **THEN** 设备或构建身份变化时 MUST 创建新执行，不得沿用旧执行中的通过项

#### Scenario: 生产厂商条件仍未满足
- **WHEN** acceptance smoke 或性能收益满足本能力要求，但 QLZ 固定测试配置、弱 TLS、腾讯 16 KB 对齐或 consumer rule 问题仍存在
- **THEN** production Release MUST 继续 fail-closed
- **THEN** 真机验收成功 MUST NOT 被解释为厂商制品已修复或生产可发布
