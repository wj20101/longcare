## Context

动机及范围见 [proposal.md](proposal.md)。本次涉及 Model、Data、UI 的契约修正，因此保留技术设计，但不引入新的架构层。

已核对的实现基线为 `8005ba33`：

- `NursingExecutionScreen` 通过 `SharedOrderDetailViewModel` 强制刷新 `POST /V1/Service/OrderInfo`；后者将所有 `ApiResult.Exception` 替换为同一网络提示。
- `ApiRequestException.Kind` 已有 CONNECTION、TIMEOUT、HTTP、INVALID_RESPONSE、UNKNOWN；HTTP 状态码已保留，不需要新增结果类型。
- 标准响应以 `resultCode=1000` 为成功，业务失败保留 `resultMsg`；业务码 1001/3002 由全局处理会话失效。HTTP 非成功分支不解析错误体。
- 2026-09-21 读取的[在线 Swagger](https://careapi.ytone.cn/swagger/v1/swagger.json) 表明 `UserInfoM` 的 name、identityCardNumber、gender、address、lng、lat，`ServiceProjectM.projectName`，以及今日订单中客户端已使用的 name、callPhone、identityCardNumber、liveAddress 允许为空。现有非空字符串默认值不能接收显式 JSON null；生成的 Moshi adapter 会抛出解析异常。
- Swagger 对详情仅列成功 payload，未完整声明统一包装和错误码含义；不能凭它虚构业务原因或解析任意 HTTP 错误体。尚无本次真机失败响应。
- Android CLI 已核对[官方数据层指南](https://developer.android.com/topic/architecture/data-layer)，本方案沿用既有 Repository/状态层职责，不引入新的网络或平台 API。

## Goals / Non-Goals

**Goals:** 将已经存在的错误信息正确用于护理详情；明确空值在 API、展示和本地实体之间的处理位置，补充可复现测试。

**Non-Goals:** 不改变成功码、服务状态、订单标识、Room schema、页面跳转、签入签退规则；不改造所有调用方；不为单个页面添加错误路由器、插件系统或自动重试机制。

## Decisions

### 1. 使用小型 UI 映射函数，不重建请求框架

在 `:core:ui` 用一个内部函数将现有请求异常分类映射为字符串资源，护理详情入口 `SharedOrderDetailViewModel` 及同属订单详情的 `OrderDetailViewModel` 共用。仅替换它们的详情加载失败分支，不改工单开始等其他操作。资源解析继续使用 `ResourceTextResolver`。

| 输入 | 提示 |
|---|---|
| 标准业务失败且消息非空白 | 原 `resultMsg` 普通文本 |
| 业务消息为空或未知异常 | 护理计划加载失败，请稍后重试 |
| CONNECTION | 无法连接服务器，请检查网络后重试 |
| TIMEOUT | 请求超时，请重试 |
| HTTP 5xx | 服务暂时不可用，请稍后重试 |
| 其他 HTTP 失败 | 请求未成功，请稍后重试 |
| INVALID_RESPONSE | 服务数据异常，暂时无法加载护理计划 |

不直接使用 `exception.message`；不依据 HTTP 401 额外退出登录。不捕获取消异常并转换成文案。既有数据层分类、会话处理不动。

拒绝的替代方案：仅把统一提示改成“加载失败”仍会丢失可用原因；新增全局错误框架超出此小业务需要；猜测 HTTP 错误体中的提示没有完整契约依据。

### 2. 仅对齐明确可空的现有字段

上述模型字段声明为可空类型，缺失时默认 null；直接消费者使用 `orEmpty()` 或既有缺省展示。`OrderMapper` 映射到现有非空本地实体时规范化缺失文字，不修改数据库结构。坐标保持缺失语义，不填成 `0,0`；身份和定位校验不放宽。

优先在现有模型和直接消费点修正，不新增重复 DTO 层、不配置全局“null 自动转空字符串”适配器。时间字段没有在本次已核对的 Schema 中声明 nullable，不因猜测将全部字段放宽。错误类型、破损 JSON、成功但缺少整个详情等仍是 INVALID_RESPONSE。

### 3. 保留恢复路径并用最小诊断证据定位实际失败

错误页布局和手动重试不变。用现有诊断能力或调试环境获取错误类别、HTTP 状态及必要字段路径，禁止持久记录 Token、身份资料或完整响应。实现阶段可用合成响应验证缺陷，不依赖真实客户数据；真机可用时仅复现读取详情，不进行开单、签到或数据提交。

### 4. 验收以契约和可观察状态为主

- 通过真实 Moshi/Retrofit 转换链测试字段 null、缺失及正常值，不只手工构造成功模型；包含非法字段类型、业务失败、HTTP、超时及空响应。
- ViewModel 测试覆盖分类提示、空白业务消息、失败后重试成功、取消及正常详情成功；保持会话失效测试。
- 核对直接 UI/实体消费者的缺失资料处理，再进行编译、Lint 和 Release 构建验证。未执行的真机复现单独标明，不作为已定位本次根因的证据。

## Risks / Trade-offs

- [可空类型影响共享模型消费方] → 编译定位直接受影响点，只做必要缺省处理，补充实体映射和 UI 回归；不顺带重构页面。
- [在线定义与实际响应可能不完全一致] → 以已声明的 null 契约修正确定问题，实际故障保留证据边界；其他契约变化先确认。
- [部分业务消息为空] → 使用本地兜底，不向用户输出原始异常信息。
- [共享详情页面提示同步变化] → 两个详情入口使用相同映射，其他网络调用方不批量迁移。

## Migration Plan

无需服务器、数据库或配置迁移。先补接口和提示测试，再修正模型、映射及详情消费方，通过本地检查后安排只读页面验收；提交、推送和发布按用户另行指示执行。回滚仅撤回本次代码及资源，不触碰服务数据。

## Open Questions

- 本次失败究竟是解析、HTTP、连接或业务失败，待取得实际异常类别后确认，不阻断已证实问题的测试与修正。
- `TodayOrder` 定义包含 planId，但本地今日模型未接收且点击固定为 0；共享详情仓库也丢失该参数。服务端何时需要它尚未核实；仅记录并单独评估，不包含在本次实现授权内。
