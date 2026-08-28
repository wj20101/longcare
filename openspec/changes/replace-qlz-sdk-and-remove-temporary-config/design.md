## Context

参见 [proposal.md](proposal.md) 的动机和 [QLZ 行为规格](specs/sales/qlz-device-assessment/spec.md) 的验收契约。当前 `app/build.gradle.kts` 把固定 `QLZ_SDK_KEY` 和 `QLZ_TEST_MODE=true` 写入所有变体，并以旧 AAR 文件存在作为已知不安全 SDK 信号；`QlzSdkClient` 必须先用该 key 初始化才能读取设备 ID，而 `/V1/Sale/GetCheckToken` 又要求设备 ID。

工程已有可解密 `/V1/System/Config.thirdKeyStr` 的数据链路和 `ThirdKeyReturnModel`，因此能够在不新增启动接口的前提下先取得 SDK 初始化配置。现有 QLZ UI/controller、销售 ViewModel、Token 单次恢复和报告回查行为应保持；生产 Release 同时还受腾讯人脸独立 P0 阻断，QLZ 改动不得改变该事实。

新 QLZ 厂商包、正式版本说明、服务端配置字段和真实 BLE 设备不在仓库内。它们是实施输入，不得以修改旧 AAR、忽略 Lint 或继续使用固定 key 代替。

## Goals / Non-Goals

**Goals:**

- 建立“当前会话服务端配置 → SDK 初始化 → 设备 ID → 一次性 Token → SDK 页面”的无循环准备流程。
- 让 Debug、显式 Acceptance 和 production 对测试环境配置具有不可混淆、可测试的运行策略。
- 用来源、hash、静态检查、构建产物和真机链路共同证明 QLZ 修复包达到 production readiness。
- 把厂商静态 API 隔离在 app-owned adapter 内，并使配置、会话切换和 Token 恢复能用 focused tests 验证。
- 保持既有销售 route、客户/报告接口、权限请求和用户可见错误语义。

**Non-Goals:**

- 不升级腾讯人脸或放宽其 16 KB/R8 门禁。
- 不把 Jetifier、legacy protobuf、厂商合并权限、WebView origin 或账号全局数据清理并入本 change。
- 不修改 `/V1/Sale/GetCheckToken` 请求字段、Room schema、销售导航或报告来源。
- 不通过反编译修改、重新签名或网络 hook 修补厂商 AAR。

## Decisions

### 1. 使用加密系统配置完成 SDK bootstrap

服务端先向 `/V1/System/Config.thirdKeyStr` 的解密对象增加向后兼容字段，建议 JSON 名称为 `QlzSdkKey` 与 `QlzEnvironment`，其中环境只允许 `test` 或 `production`。Android 在任何 QLZ 设备调用前显式刷新/读取该配置，校验后再初始化 SDK。

这复用现有登录后系统配置、加密响应和缓存路径，且旧客户端会忽略新增字段。备选方案包括：

- 把 key 加到 `GetCheckToken`：该接口当前需要 SDK 设备 ID，会保留 bootstrap 循环。
- 使用 Gradle secret、CI 环境变量或 `local.properties`：最终值仍会进入 APK，且不能按会话/环境轮换。
- 新建独立 bootstrap endpoint：能工作，但在系统配置已具备同类第三方配置语义时增加了不必要的接口和缓存策略。

服务端部署必须先于新客户端；`appSecret` 不增加到任何 Android 可见响应。appKey 按厂商客户端标识处理，不被当作服务端秘密，但仍禁止固定、记录日志或出现在诊断输出中。

### 2. 用 Core 抽象向 app-owned 厂商适配器提供配置

在 Android-free 边界定义销售评估 SDK 配置值与 provider 契约，由 `:core:data` 基于 `SystemConfigManager` 实现；`:app` 的 QLZ adapter 只依赖该抽象和运行环境策略。销售 ViewModel 继续只使用 `SalesEvaluationDeviceGateway`，不直接依赖系统配置或厂商类型。

这样保持 Data 实现不泄漏到 Feature，也避免把 vendor SDK 引入 Core。直接让 `QlzSdkClient` 依赖 `SystemConfigManager` 虽改动更少，但会固化 app → data implementation 耦合并绕过现有 API visibility 方向，因此不采用。

### 3. 将同步初始化改为可串行化的准备状态机

QLZ adapter 暴露可挂起的设备准备操作，内部以互斥状态机完成：

1. 取得当前会话标识/配置 generation 和最新 QLZ 配置。
2. 按构建运行策略校验环境，生成不含原始 key 的配置 fingerprint。
3. 对新 fingerprint 调用厂商支持的 reset/reinitialize 契约并初始化。
4. 初始化成功后读取设备 ID；失败只返回脱敏领域错误。
5. UI controller 仅在准备状态与当前会话仍匹配时打开一次性 Token 对应页面。

同一会话/同一 fingerprint 可以复用初始化；退出、会话失效、换号或配置变化会使状态失效。若新 SDK 没有能证明安全的 reset/reinitialize 方式，切换配置时拒绝评估，而不是继续使用旧静态状态。备选方案是让 ViewModel 分别加载配置并调用多个 SDK 方法；这会把竞态、vendor 类型和恢复细节扩散到 UI 状态机，因此不采用。

### 4. 构建只携带环境许可，不携带 QLZ 配置

删除 `QLZ_SDK_KEY` 和代表实际 SDK 模式的固定 `QLZ_TEST_MODE`。构建可以注入一个不含凭据的策略值：Debug 与显式 Acceptance 允许服务端 `test` 配置，默认 production Release 只允许 `production`。服务端环境值和客户端构建策略必须同时允许，任一不匹配都拒绝初始化。

这保留 Acceptance 联调能力，同时防止错误服务端配置把 production 切到测试模式。只相信服务端布尔值会让生产安全依赖运行时配置；只相信构建模式又无法为 SDK 提供当前环境配置，因此采用双重校验。

### 5. 新 AAR 以不可修补的供应链输入处理

实施前固定厂商下载来源、版本说明、文件名和 SHA-256，并把旧 1.3.0.2 从依赖与产物中移除。通过 AAR 解包/字节码、Lint、dependencyInsight、merged Manifest、consumer rules、ABI、minified 构建和网络安全配置共同审查差异；不修改厂商二进制来获得绿色结果。

如果新 SDK 不再需要旧兼容 shim，可在 adapter 内删除对应 QLZ 专用代码；legacy protobuf 是否迁移、Jetifier 是否关闭及额外权限最小化仍由独立 change 决定。只有新厂商 API 明确要求的依赖变化属于本 change。

production 不接受 QLZ 专用 cleartext domain；若厂商正式链路仍要求 HTTP 或弱 TLS，则该包不满足输入条件。Acceptance 也不得被用来证明此类传输可生产发布。

### 6. 门禁按厂商根因隔离验证

保留 production config 与 vendor readiness 的 fail-closed 结构，并增加/更新负向 fixture，覆盖固定 key、固定测试模式、旧 AAR/hash、测试环境进入 production 以及 `TrustAllX509TrustManager` 回退。旧包不再出现后，移除仅属于 QLZ 旧版本的 waiver 范围，但保留 COS 和腾讯人脸各自有效的规则。

整包 production 检查可能仍因腾讯人脸失败，因此 QLZ 的验收以“输出中不再包含 QLZ 根因、QLZ 专项 fixture 通过、Face 根因仍被阻断”判断，不能要求本 change 把整个 production Release 变绿。

### 7. 自动化与真机证据分层

自动化至少覆盖：系统配置 JSON/解密映射、缺失/非法/环境不匹配、状态机并发与配置切换、会话失效、设备 ID 顺序、Token 单次刷新、错误脱敏、API 字段集合、旧配置/旧 AAR 负向门禁，以及 Debug/Acceptance/minified Release 构建。

厂商静态调用通过 app-owned facade 隔离后用 fake 验证顺序和状态；不尝试在 JVM 中模拟真实 BLE。最终由受控销售账号和检测设备覆盖授权/拒权、连接、完成、取消、Token 过期、断网恢复、换号和报告回查，并记录包版本与服务端环境。

## Risks / Trade-offs

- **[厂商修复包仍含不可见弱 TLS 或缺少来源证明]** → 同时使用来源/hash、Lint、字节码和受控网络证据；任一关键证据缺失就保持 P0 阻断。
- **[服务端先后发布不一致导致新客户端无法评估]** → 服务端先以附加字段上线并验证 test/production 配置，再发布依赖字段的新客户端；缺字段时明确失败且不 fallback。
- **[缓存配置过期或跨账号复用]** → fingerprint 包含会话 generation 与环境，退出/3002/换号主动失效；每次开始评估前检查当前状态。
- **[厂商静态单例无法安全 reset]** → 以厂商支持说明和真机换号验证为完成条件；无法证明时拒绝动态切换并不宣称完成。
- **[客户端 appKey 可从运行内存提取]** → appKey 只作为客户端标识；真正签名秘密留在服务端，一次性 Token 保持短期、客户与设备绑定。
- **[腾讯人脸失败掩盖 QLZ 结果]** → 门禁输出和测试按 vendor 根因断言，单独证明 QLZ 清零且 Face 仍 fail closed。
- **[新 AAR 改变 protobuf、权限或 UI 兼容行为]** → 记录完整 diff；只处理接入必需变化，非阻断项进入已登记的独立 change，不扩大本 P0。

## Migration Plan

1. 从厂商取得修复版 AAR、版本/安全说明、校验和与 reset/reinitialize 契约；服务端确认 `QlzSdkKey`/`QlzEnvironment` 的准确 JSON 和环境值。
2. 服务端先向测试环境、再向生产环境以附加字段部署加密系统配置，验证旧客户端兼容且响应不含 `appSecret`。
3. 在 Core 增加配置 provider 与契约测试，在 app adapter 中实现准备状态机和构建环境双重校验；保持旧 AAR 仅用于对照，不建立运行 fallback。
4. 替换 AAR、删除固定 BuildConfig 配置并更新依赖/兼容层；运行自动化、Lint、AAR/Manifest/R8/网络检查。
5. 更新门禁与负向 fixture，证明 QLZ 根因消失而腾讯人脸阻断仍在；构建显式 Acceptance 并完成真实 BLE 矩阵。
6. 同步当前真相文档。只有服务端、Android、厂商包和真机证据全部齐备后，才把 QLZ P0 标为完成。

回滚时可以撤回新客户端或关闭服务端 QLZ 配置字段，但不得把旧弱 TLS AAR或固定 key 重新作为 production fallback。若线上验收异常，保持 production 发布阻断并继续使用明确标识的受控 Acceptance 版本排查；服务端新增字段为向后兼容，可保留而不影响旧客户端。

## Open Questions

- 厂商最终提供的安全版本、文件名、SHA-256、依赖清单以及 reset/reinitialize API 是什么？这些值在实施任务开始时作为受控输入固化。
- 服务端是否接受建议的 `QlzSdkKey`/`QlzEnvironment` 字段名；若采用等价命名，必须在接口契约测试和集成文档中固定唯一 JSON 形式。
- 测试与生产环境是否都能提供独立 appKey 及可用的 BLE 设备/账号；缺少 production 环境真机条件时只能完成 Acceptance，不能关闭 P0。
