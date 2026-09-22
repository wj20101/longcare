## Context

> 2026-09-21 已实施修订：设备上传成功后先进入原生结果页，通过 GetCheckResult 取得等级和报告地址，再由按钮打开报告。代码及主规格已同步，纯表单与最小 JS 关闭接口不变；自动化和构建不替代待完成的真机验收。

参见 [proposal.md](proposal.md) 的动机，以及 [qlz-custom-evaluation-ui spec](specs/qlz-custom-evaluation-ui/spec.md) 的行为要求。

本变更提出时，`QlzSdkClient` 以单例初始化 QLZ 1.3.0.5 Lite AAR，并由 `SalesSdkUiController` 调用 `SDKCall.openByToken(...)` 打开厂商 Activity。`SalesExperienceScreen` 已有 `DEVICE_STATUS`、`EVALUATION_GUIDE`、`EVALUATION_COMPLETE` 三个应用内页面，`SalesViewModel` 已负责获取/恢复一次性 Token、处理完成事件、刷新客户详情以及从服务端 `pgUrl` 打开报告。

Demo 证明自定义界面不需要复制或换肤厂商 Activity：先用 `CheckIml.startCheck(token)` 校验并建立检测上下文，再由 `ScanDeviceIml` 输出候选设备，以 `ConnectDeviceHelp` 连接、接收五指/进度回调并在 `onCheckEnd` 后调用 `sendData`。AAR 使用全局 `CheckIml` 状态；扫描列表、五指数组及部分检测集合为可变对象；`ConnectDeviceHelp` 持有 Context 和回调直到 `onDestroy()`。

2026-09-12 已通过 Android CLI 核对官方文档：`kb://android/develop/connectivity/bluetooth/bt-permissions` 要求 target Android 12+ 的扫描/连接分别使用运行时 `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT`，旧蓝牙权限限制到 API 30；`kb://android/develop/connectivity/bluetooth/ble/find-ble-devices` 要求扫描设置时间上限并在找到目标后停止，且蓝牙关闭时 `BluetoothLeScanner` 不可用。当前扫描权限以后续 `device-h5-evaluation-flow` 为准：Android 12+ 还需成对请求前台精确/粗略位置且取得精确授权，所有支持版本检查定位开关；不新增后台权限或位置采集。

## Goals / Non-Goals

**Goals:**

- 让应用拥有扫描、连接、握持引导、五指状态、进度、上传和错误恢复的 Compose UI。
- 以可测试的不可变状态表示厂商回调，并保证扫描、连接和回调资源随评估页面释放。
- 复用现有页面、Token 恢复、客户详情刷新和报告导航契约，不让厂商类型进入 ViewModel。
- 对所有 AAR 公开的关键回调给出确定处理，避免 Demo 中的空实现和硬编码测试数据。

**Non-Goals:**

- 不修改、反编译重打包或复制 QLZ AAR 内部 Activity/布局。
- 不采用 `setCustomCheckUI(BaseActHelp)`、`CheckPageFragment` 或 WebView 包裹厂商检测页。
- 不新增 LongCare 或厂商网络接口，不改变表单评估、客户登记和报告 URL 规则。
- 不在本变更内实现厂商支付；收到需要支付回调时安全阻断并退出。
- 不修复固定测试 key、测试模式、弱 TLS 及腾讯已接受风险；当前 Release 对明确接受的事项告警，其他失败继续阻断。
- 不把无法由模拟器验证的设备协议、五指语义或上传成功当作自动化测试已证明。

## Decisions

### 1. 采用 Demo 的直接控制链路，不使用 SDK 自定义 Activity 插槽

自定义流程按以下顺序执行：

```text
LongCare Token
  → CheckIml.startCheck
  → ScanDeviceIml（应用展示候选设备）
  → ConnectDeviceHelp（连接与检测）
  → onCheckEnd / sendData
  → UpDataCallback
  → 原生评估结果页
  → GetCheckResult（当前客户 ID、本次 recordId）
  → 展示 pgResult 和“确认并提交评估结果”按钮
  → 用户点击按钮打开本次响应 pgUrl 的报告 H5
  → closeWebView 或系统返回只关闭 H5，回到结果页
```

`CheckConfig.setCustomCheckUI(BaseActHelp)` 虽然存在，但需要公开无参构造和厂商旧式 Activity/View 生命周期，Demo 未给出完整契约，也会让 Compose UI 依附厂商宿主。`CheckPageFragment` 仍使用 legacy support Fragment 和厂商布局。直接控制链路能让 LongCare 完整拥有 UI，同时只依赖 AAR 的公开控制/回调 API，因此选择该方案。

### 2. 新增 UI 作用域的 QLZ 评估会话，单例只负责初始化和全局互斥

在 `:app` 的 QLZ integration/platform 边界新增 `QlzEvaluationSession`（名称可按现有约定微调）及 factory：

- `QlzSdkClient` 继续以 Application Context 初始化 SDK、提供设备 ID/权限集合，并增加线程安全的活动会话租约；不持有 Activity 或页面回调。
- `QlzEvaluationSession` 由当前 Activity/UI 创建，强持有 `ScanDeviceIml` 与 `ConnectDeviceHelp`，对外只暴露不可变 `StateFlow<QlzEvaluationUiState>` 和 start/scan/select/retry/cancel/close 动作。
- `SalesSdkUiController` 负责创建、持有和关闭当前会话；Compose 使用当前 Activity `remember`，在离开整个设备评估页面集合或 Activity 销毁时调用 `close()`。
- 会话使用递增 generation/session id。每个异步回调先验证会话仍活动且 generation 匹配；`close()` 先使 generation 失效，再停止扫描、清除可重试上传数据并调用 `ConnectDeviceHelp.onDestroy()`，最后释放全局租约。
- 不把 `BluetoothDevice`、`AssessedData`、`RecordInputData`、`Payment` 或 `ConnectDeviceHelp` 放入 `SalesViewModel`、SavedStateHandle 或持久层。

全局互斥是必要的，因为 AAR 的 `CheckIml` 配置、Token、患者和检测状态为进程级共享状态。重复点击只作用于现有会话；新会话无法获取租约时返回“检测正在进行”状态，不重新初始化 SDK。

### 3. 在厂商边界立即快照可变回调数据

`ScanDeviceIml.scanChange` 返回重复复用的可变列表，`onCheckState` 返回可变 `boolean[]`。适配层在回调栈内立即复制：

- 候选设备按 address 去重并转换为 `QlzDeviceOption(id, displayName, maskedIdentifier)`；实际 `BluetoothDevice` 只保存在会话内部的 id 映射中。
- 五指状态复制为固定五项的不可变值；少于五项时缺失项为未接触，多余项忽略并记录脱敏诊断。
- `onCheckEnd` 的检测集合复制为本次上传专用集合，并以原子标记保证只调用一次 `sendData`。
- `onUpFail` 的可重试对象只在当前活动会话内保存，成功、取消或关闭后立即清除。

SDK 回调当前由主线程 Handler 派发，但适配层不依赖这一隐含事实保证正确性；所有状态写入仍集中到会话 reducer，并以 generation 过滤迟到事件。

### 4. 使用显式状态机驱动现有两个进行中页面

`QlzEvaluationUiState` 使用 sealed state/data model 表达以下阶段，而不是用单个进度字符串推断：

```text
Idle / Preparing
  ├─ PermissionRequired
  ├─ BluetoothUnavailable
  └─ Scanning ──→ ScanEmpty
                  └─→ Connecting
                       └─→ Positioning / Measuring
                              ├─→ RecoverableError
                              ├─→ PaymentRequired
                              └─→ Uploading ──→ UploadFailed
                                                   └─→ Completed
```

- `DEVICE_STATUS` 承担运行条件、扫描、空结果和设备选择。
- 选择设备后进入 `EVALUATION_GUIDE`，承担连接、握持引导、五指接触、检测进度、电量/掉线/超时与上传状态。
- 设备检测并上传成功后直接进入 `EVALUATION_COMPLETE`，由该页查询结果并呈现等级和报告入口；测量进度达到 100% 本身不触发跳转。纯表单评估仍保持原流程。
- 从 `DEVICE_STATUS` 切到 `EVALUATION_GUIDE` 不释放会话；离开这两个活动评估页面、完成、取消或不可恢复错误退出时释放。
- Activity/进程重建不尝试序列化厂商连接对象。重建后如果原会话不能安全确认，展示检测已中断并要求重新开始，避免伪造续检。

UI 沿用销售端现有背景、卡片、按钮和设备/握持素材；新增五指接触与进度组件支持紧凑和宽屏约束，但像素级视觉稿微调不影响状态契约。

### 5. 分离页面局部状态与业务事件

扫描、连接、五指、进度、可恢复错误和上传重试保留在 UI 会话状态中，避免每个设备回调都污染业务 ViewModel。仅以下跨页面事件发送给 `SalesViewModel`：

- Token 无效：复用现有至多一次的 Token 恢复；恢复得到的 `sdkLaunchRequest` 改为重启自定义会话，不再打开 SDK Activity。
- 上传成功：转换为现有 `QlzSdkEvent.Completed` 或等价的厂商无关完成事件，保存当前客户及本次 recordId，进入原生结果页并释放已完成会话；不再创建待打开 H5 请求。重复事件不重复导航，查询失败只重试 GetCheckResult。
- 用户取消/终止错误：发送脱敏的取消或终止事件，用于现有提示和导航协调。

`connectedDeviceName` 与 `sdkProgressText` 不再作为检测 UI 的事实来源；在兼容迁移期可由会话状态同步，最终页面直接渲染会话状态。`SalesViewModel` 继续不导入任何 QLZ 或 Android 蓝牙类型。

### 6. 上传参数使用真实已有数据，不新增定位采集

会话启动时接收不可变 `QlzEvaluationUploadContext`：从当前客户详情提取 `liveLat`、`liveLng` 和 `liveAddress`，缺失时传空值；不使用 Demo 的占位坐标/地址，也不为了 QLZ 上传额外启动持续定位。新登记客户若详情尚未加载，可使用本次登记已经授权取得并保存在当前业务状态中的数据；无法可靠取得时仍使用空值，禁止猜测或互换经纬度。

参数在调用厂商 API 前按其语义映射为纬度、经度、地址。LongCare Sale API 中 `liveLat/liveLng` 的既有字段约定不因本变更改变；如真机联调证明厂商强制要求非空或字段语义不同，需要先更新本 change，而不是硬编码测试值。

### 7. 为每类回调定义恢复动作

- 扫描空结果：重新扫描或退出。
- 连接失败/连接错误/掉线/弱信号：优先重新连接已选设备；无法重连时返回扫描。
- 检测空闲超时：重新检测或退出；不自动无限重试。
- 充电状态：暂停性提示，条件恢复后继续采用 SDK 回调；低电量等终止状态重新连接或退出。
- 上传失败：用同一次 `RecordInputData` 调用厂商重试 API；按钮在请求中禁用。
- Token/鉴权失败：交给现有一次性 Token 恢复；第二次失败终止。
- 支付要求：展示应用固定分类文案并终止检测，只提供退出，不打开 `paymentUrl`/`btnUrl`。
- 未知或不可恢复错误：映射为通用用户文案；原始错误码可进入脱敏诊断日志，但 Token、MAC、SDK URL 和密钥不得输出。

### 8. 保持 Manifest、安全和报告边界不变

本方案不新增 Activity、Service、provider、intent-filter 或权限。继续覆盖 AAR 合并出的外部 deep link 导出状态，并保留 API 30 的 legacy 蓝牙权限上限。由于销售流程可能已有位置业务数据且 AAR 上传参数含位置，不新增 `neverForLocation` 断言。

SDK 上传成功回调中的 `recordId` 仅用于本次设备结果查询，score 不推导业务等级，SDK URL 不参与导航。设备流程直接进入原生结果页，由 `SalesViewModel` 使用当前客户 ID 和本次 recordId 请求 `/V1/Sale/GetCheckResult`，以响应 `pgResult` 展示“评估成功，评估等级为：{等级}”。查询失败或等级尚未返回时显示现有失败/待同步提示并允许手动刷新，不读取缓存旧结果、不切接口兜底、不重新检测或上传。

复用结果页已有“确认并提交评估结果”按钮和报告导航，仅使用本次结果响应 `pgUrl`；没有地址时不给出可打开空网页的动作，保留刷新入口。报告保持当前无原生标题栏和沉浸式适配，`window.NativeBridge.closeWebView()` 与系统返回仅弹出当前 H5，回到已有结果页，不改变完成状态、不新增结果邮箱或网页关联机制。结果页返回/完成仍回首页。

纯表单评估的 H5 入口、主动关闭后的完成页和客户详情查询保持现状，不能因设备流程清理而删除其必要逻辑。删除仅服务于设备自动打开 H5 的请求、重试与状态；保留直接的客户/recordId 结果上下文，不引入通用流程框架、自动轮询或兼容旧设备顺序的分支。正式发布策略、AAR 校验、签名及其余检查不变。

### 9. 测试以可替换的厂商适配器和状态 reducer 为中心

在 QLZ 边界内定义最小内部 adapter/factory，使 JVM 测试可以驱动扫描、连接、检测、上传及销毁回调而无需真实 BLE：

- reducer/会话单测覆盖成功路径、列表/数组复制、重复点击、扫描超时、连接失败/掉线、超时、电量、支付、上传失败重试、Token 恢复、完成和迟到回调。
- Compose 测试覆盖各状态文案、设备选择、五指可视状态、按钮 enablement、返回/重试动作及紧凑/宽屏布局。
- 保留 `SalesViewModel` Token 恢复测试，替换旧“上传后自动 H5”断言；新增上传后进入结果页、GetCheckResult 参数、成功/失败/空等级/空地址、重复回调和客户切换测试，验证按钮仅打开本次响应地址、报告返回保留结果页且纯表单不回归。
- 静态/架构测试确认 ViewModel/Feature 不导入厂商或蓝牙类型，Manifest 无新增导出面，源码不再调用 `SDKCall.openByToken` 或厂商报告 Activity。
- 运行 `:app` focused tests、lint/assemble、完整本地 preflight 和既有 release readiness 守卫；明确记录已接受厂商告警，缺失输入或其他未接受问题仍失败。
- 真机验收使用 QLZ 设备覆盖逐指接触映射、扫描停止、连接/掉线、完整检测、上传/报告、权限拒绝恢复、前后台和返回释放。模拟器仅覆盖 UI 与可注入错误状态。

## Risks / Trade-offs

- [AAR 是全局单会话且回调不可取消，旧回调可能污染新页面] → 使用全局租约、generation 过滤和先失效后释放顺序；不在活动会话中重新 `init`。
- [扫描列表和五指数组会被 AAR 复用] → 在回调入口立即复制，Compose 只观察不可变状态。
- [厂商五指索引没有可靠语义文档] → 先保持 Demo/AAR 一致的索引顺序，以真机逐指验收确认；确认前不在业务逻辑中硬编码“拇指/小指”语义。
- [`ConnectDeviceHelp` 强持有 Context，遗漏销毁会泄漏或后台继续检测] → 只传当前 Activity、集中 `close()`、Compose 离开评估流程和 Activity 销毁双重释放，并测试幂等销毁。
- [厂商上传失败对象只在内存中可重试] → 页面重建或进程死亡后明确要求重新检测，不尝试持久化不可序列化的 SDK 对象。
- [移除厂商 UI 后暴露更多错误分支和文案工作] → 建立穷尽状态映射和默认脱敏错误，未知错误安全终止；不保留静默空回调。
- [直接显示蓝牙地址可能泄露设备标识] → UI 只展示名称和掩码标识，不持久化、不上传日志；完整 address 仅用于当前会话连接。
- [自动化无法证明 BLE 协议和生理检测正确] → 将真实设备逐指、掉线、上传和报告设为完成前的人工验收项，不以 assemble 绿色替代。

## Migration Plan

1. 先新增厂商 adapter、会话状态机、全局租约和 focused unit tests，不切换现有入口。
2. 将 `DEVICE_STATUS`/`EVALUATION_GUIDE` 接入自定义会话，复用现有权限请求、Token 获取/恢复和 `EVALUATION_COMPLETE` 页面。
3. 将设备上传成功接到原生结果页与 GetCheckResult，复用“确认并提交评估结果”按钮，删除设备自动打开 H5 的旧请求和专用逻辑；不恢复 `SDKCall.openByToken` 或厂商 Activity 完成语义。
4. 更新字符串、Compose/架构测试、QLZ 集成说明与页面地图，执行 lint/assemble/preflight 及 Manifest/生产门禁检查。
5. 当前已使用应用自有 UI；在支持 BLE 的真机用 QLZ 设备补齐任务 5.3 的异常矩阵后，才能认定本变更完整验收。

本变更无数据库迁移。`device-h5-evaluation-flow` 主规格已同步设备顺序，保留纯表单与通用关闭接口要求；旧自动 H5 顺序不作为本次验收标准。不得恢复厂商内置页面、复制 Demo 密钥、放宽 Manifest 或绕过其他发布门禁。

## Open Questions

- 最终五指图标、动画和宽屏排版可在功能状态完整后按视觉稿微调，不改变本规格定义的流程与恢复行为。
- 厂商是否会在目标业务账号返回需要支付状态尚未确认；当前安全策略为阻断退出，后续若产品提供 LongCare 支付契约需单独建变更。
