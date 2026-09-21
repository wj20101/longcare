## Context

动机见 [proposal.md](proposal.md)。检查基线 `fb9955ca`，无需修改 API 或 AAR：

- `prepareEvaluation()` 每次调用都会请求 GetCheckToken，Repository 本身没有 Token 缓存；问题在后续消费者生命周期。
- `QlzEvaluationSession.retryAuthorization()` 调用 `restart(currentToken)`，明确重复提交旧凭证。
- `SalesExperienceScreen.back()` 从检测页返回设备页时取消 session，但 `uiState.checkToken` 未消费；再次搜索会创建 session 并传入旧值。Activity 重建后 UI controller 丢失但 ViewModel 仍保留 Token，也存在同类路径。
- `QlzVendorEvaluationDriver.onUpFail()` 将所有上传错误归为 `UPLOAD_FAILED`；普通 `retryUpload()` 直接重发 `RecordInputData`，不处理鉴权恢复。
- 1.3.0.5 AAR 的 `CheckIml.startCheck(Context, token, callback)` 设置 SDK Token 并授权；`ConnectDeviceHelp.sendData(RecordInputData)` 可重传原记录。AAR 上传响应逻辑将厂商业务码 401/2001 视为凭证失效，应用只使用自定义回调，不能依赖旧厂商 Activity 的全局回调。
- 当前 Gradle 将 `QLZ_TEST_MODE` 固定为 true，初始化调用 `CheckIml.setTestMode(isTestMode)` 强制使用测试服务；已核对本 AAR 默认基地址常量为 `https://openapi.qiaolz.com`，上传路径为 `/sdk/assess/upload`。用户已确认保留现有 SDK key 且服务端签发正式 Token。

“一次性”来自用户确认；以上代码证据证明存在复用路径，但未取得现场响应，不能把每一次历史上传失败均归因于 Token。厂商同一记录换凭证重传仍需真实设备验收。

## Goals / Non-Goals

**Goals:** 在现有 ViewModel → UI controller → session → driver 链路上消费一次性凭证，明确新授权与已授权会话继续操作的区别；上传鉴权恢复不丢数据；客户端 SDK 与已确认的正式凭证使用同一环境。

**Non-Goals:** 不新增 Token 管理框架、数据库、后台重试、通用任务队列、反射兜底、SDK 替换、新日志系统或环境选择框架；不修改客户登记、LongCare API 地址、结果查询或 H5，不替用户修改服务端部署。

## Decisions

### 1. 统一新授权入口，凭证交付后消费

沿用 `SalesSdkLaunchRequest` 的一次性消费方式整合初次启动与重新授权，去掉从 `checkToken` 默认取值再启动的分支。Token 获取应靠近真正的新授权时机：权限/运行条件满足、用户开始或恢复时才请求；活动 session 的扫描/重连直接执行，不重新授权。

ViewModel 负责网络请求，不持有 Activity。UI/controller 根据当前 session 决定是继续操作还是申请新授权；会话不保留可用于 `restart(currentToken)` 的旧 Token。返回、销毁后的下次开始，以及授权错误重试，均进入新凭证路径。启动请求在交给 SDK 前消费；若宿主已无效则丢弃，下一次重新获取。

不采用“发现旧 Token 被拒绝再兜底”作为正常启动策略，也不以 `expireAt` 未到期判断一次性凭证可复用。

### 2. 上传鉴权恢复不重建 driver

在厂商适配层区分明确鉴权错误（按本 AAR 的 401/2001 与现有 Token 失效回调核对）和普通上传错误，不能把 LongCare 自有业务错误码混入该分类。

会话仅暴露一个 `authorize(token)` 入口，由会话已有状态判断初次授权、授权重试或上传恢复；删除 `start/restart/resumeUpload` 多入口及跨 UI/ViewModel/event 传递的上传恢复标记。上传恢复时保留 driver、`QlzUploadBuffer`、原 `RecordInputData` 及 recordId；只调用厂商授权方法更新凭证，成功后发送原上传对象，不关闭 driver，也不回到扫描。

controller 只授权已经准备好的会话，不在消费凭证时隐式重建会话。UI 仅在宿主 STARTED 时消费待处理请求；后台暂存，返回前台后交付，无效宿主或会话则明确拒绝。活动检测、上传及终态不接受另一次授权，避免重复启动。

自定义 UI 已直接消费会话状态；删除迁移期遗留且只写不读的 ViewModel 设备名/进度镜像、对应 SDK 查询与进度转发，以及无消费者的厂商报告地址/分数字段。业务完成事件仅携带 recordId，等级和报告继续来自 GetCheckResult。未注册且无生产调用的厂商 Activity WindowInsets 补丁及其专属测试直接删除，不影响当前 Compose/H5 适配。

该处需把 driver 当前的“创建连接器”和“Token 授权”适度拆开复用，而不是在 ViewModel 中持有厂商对象。普通网络重传仍不发起一次新的 SDK 授权。若厂商不支持原记录换凭证重传，暂停该部分并报告实际限制，不静默改为重新测量或重复创建记录。

### 3. 有界恢复与请求失效

沿用每次检测最多一次自动凭证恢复的限制；用户明确开始另一检测才重置。请求失败、空 Token 或第二次凭证失效时停止自动链路，展示业务化提示，不打印原始 Token/响应。用户显式重试授权仍必须重新请求。

按评审要求精简状态：ViewModel 只保留一个检测流程 Job 和一次自动恢复标记。Token 请求和 SDK 事件处理均作为该 Job 的子协程，退出或切换客户时统一取消；请求返回检查取消状态，已取消协程的 finally 不再写 UI，继续抛出 CancellationException。不额外维护流程活动布尔值、流程序号或 Token 请求 Job。

重复点击使用现有 Token loading 与待消费启动请求阻止；Token loading 不再同时写入通用 isLoading/operation，展示层合并加载提示。启动请求按对象身份消费一次，无需额外请求序号。Android CLI 已核对[官方协程最佳实践](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)的作用域、可测试性和取消指导，不引入新并发框架。

### 4. 删除测试模式覆盖，统一默认正式环境

直接删除 `CheckIml.setTestMode(...)` 调用，而非留下被注释的旧代码；同时删除 `isTestMode`、`BuildConfig.QLZ_TEST_MODE` 和 `TEMPORARY_QLZ_TEST_MODE`，不改成另一套运行时开关。Debug/Release 均使用本 AAR 默认正式地址，不增加失败切回测试服务的逻辑。

现有 SDK key 值保持不变，仅修正与用户确认不符的“临时测试”命名、注释和风险说明，不读取或新增 appSecret。同步 `verifyReleaseConfiguration`、相关脚本与测试中失效的测试模式输入，保留 SDK 弱 TLS、腾讯 16 KB 等仍存在的风险检查及签名/质量门禁。历史风险接受记录保留其时间背景，不能继续描述为当前环境仍是测试。

不把本次环境切换扩大为全部网络安全白名单清理；既有 HTTP 报告地址是否仍使用另行核对，不因主上传改为 HTTPS 而误伤其他现有页面。

### 5. 验证分层

先用 fake Token 序列和 fake driver 精确断言授权传入值、接口次数及原 recordId，再做 Compose/controller 回退与重建测试；回归扫描、重连、上传去重、关闭后迟到回调及结果页顺序。离线测试不发起真实上传。

离线验证初始化不再调用测试模式覆盖，并核对固定 AAR 默认基地址与上传路径；回归 Release 配置检查，确保去掉已不存在的测试模式提示不会放宽其他风险检查。运行完整本地 preflight、Lint、Debug/Release 构建与文档校验。正式环境真实 QLZ 验收单独确认客户和写入授权，不覆盖安装或重启当前现场来获取测试通过。

## Risks / Trade-offs

- [换凭证重传依赖厂商服务端] → AAR 调用契约测试加真实设备专项，未验证前不得声称端到端修复。
- [错误恢复清空结果] → 上传恢复复用原 driver 和记录，测试恢复前后数据、recordId 相同且测量次数不增加。
- [页面返回/重建仍复用旧启动事件] → 一次交付后消费，旧请求失效；覆盖返回设备页、宿主重建及切换客户。
- [取 Token 时机调整影响按钮状态] → 明确获取中的禁用/加载与失败重试，不以已消费 Token 是否存在判断存活 session 可否扫描。
- [服务端重复返回已消费凭证] → 客户端不再主动重放；若现场证明接口未重新签发，作为服务端问题单独处理，不能靠无限请求解决。
- [正式 key/Token 与服务端实际配置不一致] → 用户已确认适用性，仍以授权和上传联调确认；构建通过不等于厂商正式环境鉴权通过，不自动回退测试地址。

## Migration Plan

无需持久数据迁移。实施凭证交付、上传鉴权恢复和正式环境配置，再完成本地门禁及经授权的真机验收。不修改服务端记录或 AAR；正式环境若不可用，暂停发布并核对配置，不将重新打开测试模式作为默认回滚手段。
