## Context

参见 [proposal.md](proposal.md) 的问题背景以及 [debug-mock-network-safety spec](specs/debug-mock-network-safety/spec.md) 的行为契约。当前 `:app` Debug source set 会注入 `MockInterceptor`；开关关闭时直接继续真实请求，开关开启时仅对若干 URL path 返回 asset，未命中仍继续真实请求。现状还包含三项影响可重复性的事实：Gradle 默认值为 `true` 而长期文档声明为 `false`，订单状态类响应由随机数决定，`system_config.json` 的密钥字段无法按运行时格式解析；获取人脸状态也未登记。

第一方 Retrofit/OkHttp 流量可以由 Debug 拦截器封闭，但 COS、AMap、WebView 和厂商 AAR 使用不同数据面，不能依赖该拦截器证明离线。Release 已有独立组装路径，performance/profile 变体已有优先执行的离线保护；两者都不能因本 change 被削弱。现有 production Release 门禁及厂商 AAR 问题不属于本 change。

## Goals / Non-Goals

**Goals:**

- 在不新增构建变体的前提下，使显式 Debug Mock 成为默认关闭、开启后对第一方 API fail-closed 的可验证模式。
- 用单一登记表连接方法、路径、场景、fixture 和语义校验，消除遗漏、重复分支和随机响应。
- 在现有应用网关边界提供最小化的 Debug/Test 替身，使上传、更新提示和核验后流程能够安全验证。
- 通过 source-set 与产物门禁证明 Release 不携带 Mock/fake，并保持 performance/profile 网络策略独立。

**Non-Goals:**

- 不修改生产服务契约、持久化格式、WebView host 策略、targetSdk 或依赖版本。
- 不修改、重打包或替代厂商 AAR，不解除 production Release 的 fail-closed 状态。
- 不在生产代码中绕过真实人脸/活体核验，也不把 COS、AMap 或真实 APK 下载伪装成已完成的离线端到端验证。
- 不把本次安全修复扩展为全项目网络层重写或新增 Mock 产品功能。

## Decisions

### 1. 保留单一 Debug 构建类型，Mock 默认关闭并显式开启

`debug.useMockData` 继续作为唯一入口，但仓库 fallback 改为 `false`，并由同一 provider 生成 `BuildConfig.USE_MOCK_DATA`。命令行或用户级 Gradle 属性仍可明确覆盖，因此构建/测试输出需要显示最终布尔值，API 36 冒烟命令必须显式传入 `-Pdebug.useMockData=true`。根 README 与技术栈文档只描述这一规则，质量检查验证代码默认值和文档不再漂移。

不新增 `mockDebug` flavor/build type。独立变体能提供更强的编译期区分，但会扩大任务矩阵、Manifest/签名/CI 组合，并与当前只在 Debug source set 中存在的实现重复；现阶段显式属性加 Release 产物门禁足以满足契约。

### 2. 用方法与原始编码路径的登记表取代分散的 path 分支

Debug 内建立唯一 `MockRouteRegistry`，键由 HTTP method 与 `HttpUrl.encodedPath` 组成。规范化只保证前导 `/` 并排除 query/fragment，不做 URL decode、大小写折叠或宽松后缀匹配；需要 query/body 决策时由该路由的处理器显式读取。登记阶段拒绝重复键，避免同一路由由分支顺序隐式覆盖。

`USE_MOCK_DATA=false` 时保持 `chain.proceed(request)`。为 `true` 时，命中项在进程内组装响应；未命中或 method 不匹配时抛出 Debug 专用、继承 `IOException` 的缺失路由异常。异常消息仅保留 method 与 encoded path，既能进入现有网络错误路径，也能由测试稳定分类；该分支绝不调用 `chain.proceed`。

相比返回自定义 5xx 响应，专用 I/O 异常不会被误解析为正常 API envelope，也不会让业务层把“测试契约不完整”理解为服务端故障。相比全 host 的通用假响应，显式登记能暴露遗漏并避免错误方法得到看似成功的结果。

### 3. 路由、fixture、场景和校验形成一个契约单元

每个登记项声明 method、path、fixture/响应生成器、适用场景及语义校验器。默认场景固定为最小成功/无副作用路径；订单状态、人脸状态和更新版本等分支通过 Debug/Test `MockScenarioProvider` 显式选择。生产 Release 不编译该 provider；单元测试或 Hilt instrumentation test 可替换它，手工 Mock 冒烟则使用稳定默认值。删除基于随机数的响应。

fixture 测试使用应用现有 JSON 配置解析为真实 API model，再执行业务消费所要求的语义校验：包括枚举和值域、版本关系、时间一致性，以及系统配置中十六进制/解密输入的可用格式。云凭据使用明确的非生产测试值并且只能交给测试上传器，不把“可被模型解析”误等同于“可访问真实 COS”。`GetFace` 增加已登记/未登记两套确定性响应，现有登录、服务单、系统配置、版本检查和文件元数据登记项一并纳入契约测试。

维护一份测试侧安全冒烟请求清单，以 route key 引用登记表。新增冒烟请求而未登记 route、fixture 或 validator 时，focused test 立即失败；应用运行时的未知请求防线作为第二层保护。暂不通过反射扫描全部 Retrofit 接口，因为并非所有生产 API 都属于离线冒烟范围，扫描会造成虚假的全量维护承诺。

### 4. 第三方路径在既有网关边界替换，不扩大 HTTP Mock 职责

- 照片上传：复用现有云上传器抽象。Debug DI 根据最终 `USE_MOCK_DATA` 选择确定性本地实现或真实实现；Release 只绑定真实实现。fake 返回符合业务契约但明确为测试域的文件结果，不初始化或调用 COS SDK。
- 人脸/活体：手工 API 36 冒烟继续验证权限、CameraX 预览/拍摄及无效输入拒绝。核验后导航与倒计时由 `androidTest` 替换身份核验业务网关或直接构造既有合法状态验证，不在可发布 Debug 主流程增加“跳过人脸”入口，更不修改 Release。
- 版本更新：默认 fixture 表示无更新。高版本场景用 repository/Worker focused test 和 UI instrumentation 验证持久化输出与提示；下载边界使用测试 fake 或在点击下载前结束。fixture 中不保存可被安全冒烟访问的真实 APK URL。
- 地图与 WebView：AMap 在离线冒烟中允许产生既有可恢复降级，WebView 继续使用现有不限 host 策略；二者不由第一方 route registry 接管。

把所有第三方请求硬塞进 OkHttp Mock 的替代方案被否决：它无法覆盖 SDK 自有客户端，反而会制造“已经离线验证”的错误信心。全局禁网仍作为设备级冒烟保护，但不能替代应用内 fail-closed 测试。

### 5. 以 source set、DI 图和产物检查构成变体隔离

路由表、fixtures、缺失路由异常及 fake 仅存在于 `src/debug` 或 `src/androidTest`。Release 的 DI 继续使用真实适配器并硬编码 `USE_MOCK_DATA=false`。现有 release validation guard 增加对 Mock 关键类名、asset 路径、fake 标记和配置值的检查；测试同时解析/检查 Release 合并资源或 APK，避免仅凭源目录推断隔离成立。

performance/profile 继续先应用其专用离线拦截器；增加顺序测试，证明 Debug Mock 不覆盖该路径。若当前变体继承 Debug 源集，则通过变体条件明确排除 Debug Mock，而不是复用其成功响应。

### 6. 分层验证安全属性和业务可达性

验证分为四层：

1. 纯 JVM focused tests：登记表唯一性、method/path 精确匹配、命中响应、未知请求零 `proceed`、异常脱敏、场景确定性、全部 fixture 模型/语义校验和冒烟清单覆盖。
2. 变体门禁：分别构建默认 Debug、显式 Mock Debug、显式真实 Debug，并检查有效 `BuildConfig`；执行 Release/Performance 隔离检查。
3. 业务 focused tests：上传 fake 不初始化 COS、版本 Worker/持久化输出、更新提示、人脸已登记/未登记分支及核验后导航/倒计时。
4. API 36 离线冒烟：先确认产物确实为显式 Mock，再在无外网条件执行约定流程；相机真实拒绝与测试替身覆盖分别记录，不能合并宣称为真实第三方成功。

实现完成后运行受影响 focused tests、`bash scripts/quality/preflight_local.sh --full`、普通 Android CI 主路径，以及必要的 API 36 instrumentation。仅构建绿色不能替代业务与零真实网络证据。

## Risks / Trade-offs

- [Mock 默认改为关闭后，开发者可能误以为正在使用本地数据] → 在构建输出和冒烟前置检查中明确最终模式，文档命令始终显式传参。
- [fail-closed 会让历史上依赖静默真实回退的 Debug 流程立即失败] → 缺失异常提供脱敏 method/path；只有确认属于安全冒烟清单的接口才补登记，真实联调使用显式 `false`。
- [fixture 仍可能与服务端演进漂移] → 通过真实 API model 解析、语义校验和路由清单门禁尽早暴露；本 change 不声称替代后端契约测试。
- [Debug DI 按布尔值选择上传器仍会把真实 SDK 编进 Debug APK] → 安全 Mock 路径保证不实例化/调用它，并用测试断言；Release 产物隔离重点检查 fake 不反向泄漏。
- [测试所有的已验证状态覆盖不到真实活体质量] → 将“真实相机/拒绝”和“核验后应用流程”作为两项独立证据，不把后者表述为真实身份认证成功。
- [设备级第三方 SDK 可能在启动时自行联网] → API 36 安全冒烟保持系统断网并记录可恢复降级；第一方零外联由拦截器单测单独证明。

## Migration Plan

1. 先落地登记表、缺失路由异常及 focused tests，在不改变默认值的临时阶段补齐现有 fixture 和安全冒烟路由。
2. 移除随机响应并加入显式场景 provider、fixture 语义校验和第三方网关 fake；完成业务 focused tests。
3. 将仓库 fallback 切换为 `false`，同步长期文档和构建模式检查；开发者若需要原有本地 Mock，改用显式 `-Pdebug.useMockData=true`。
4. 加入 Release/Performance 隔离门禁，执行 full preflight、变体构建与离线 API 36 冒烟，确认无第一方真实网络访问后完成变更。

若实施中 Debug 冒烟出现阻塞，可临时以显式 `debug.useMockData=false` 恢复真实联调；不得把未知路由回退真实网络作为回滚方式。Release 代码和产物在整个迁移中保持不变。
