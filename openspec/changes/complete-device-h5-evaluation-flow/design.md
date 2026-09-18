## Context

统一 NativeBridge 和设备上传到 H5 自动导航已实现。用户最新确认：所有网页无需返回结果关联；评估 H5 主动调用 closeWebView 即表示业务完成，完成页直接拉取接口文案。该决定替代此前返回邮箱、历史结果比较和二次完成确认方案。

## Goals / Non-Goals

**Goals:** 保持最小关闭接口；设备上传后进入 H5；评估 H5 主动关闭后显示完成页并获取真实结果文案；普通返回和首页保留正常。

**Non-Goals:** 不引入网页返回结果协议、邮箱、关联 ID、通用分发框架、JS 包装、URL 白名单、DOM 监听或完成状态核实。不改线上 H5 提交逻辑、SDK 上传协议、数据库与发布门禁。

## Decisions

### 1. 最小统一关闭接口

所有 H5 首次加载前通过 addJavascriptInterface 注册 NativeBridge，仅暴露带 @JavascriptInterface 的 closeWebView()。H5 自行控制调用时机。回调投递主线程，保留当前容器、前台、重复关闭与销毁保护。ManagedWebViewClient 仅保留加载状态、错误和渲染退出处理。

不使用 isSafeUrl、域名白名单或相关请求拦截。接口对所有加载页面及 iframe 可见，不提供来源认证；保留平台 TLS、Safe Browsing、禁用文件/内容访问与混合内容配置。未来新增敏感函数需单独授权。直接注册支持项目 API 24+ 标准 WebView，不设现代 WebKit 能力门槛。

### 2. 设备上传后打开 H5

仅 UploadSucceeded / QlzSdkEvent.Completed 触发待打开请求，100% 采样不触发。保存客户、recordId、URL 与消费状态；从客户详情 pgUrl 获取业务地址，不使用厂商 URL。前台打开一次，失败只重试查询，不重复上传。

### 3. 关闭与完成页

WebViewActions 默认 JS 关闭与普通返回均只 pop 当前网页。评估入口只标识页面用途，并在其 JS 关闭回调中更新现有销售业务状态，然后 pop 网页；系统/顶部返回不标记完成。复用现有首页 ViewModel 作用域，不建立返回邮箱或结果关联。普通报告、协议、隐私页面不触发评估完成，也不改变隐私同意。

销售页面观察到业务完成即展示原生完成页。完成页进入时请求结果，H5 不传递等级或业务参数。完成/返回回保留的首页，不自动重开 H5。

### 4. 接口文案

已核对 https://careapi.ytone.cn/swagger/v1/swagger.json：POST /V1/Sale/GetCheckResult，请求 id（客户 ID）、recordId（可空检测记录 ID），响应 pgResult、pgUrl（均可空）。设备流程使用已有 recordId；纯表单无检测记录时传 null。不用客户详情旧结果兜底。

完成页直接显示“评估成功，评估等级为：%1$s”，原样使用 pgResult，不追加“级”、不按分数映射、不再判断是否完成。空文案显示等级待同步，失败显示获取失败并支持手动刷新；刷新不重复上传、不轮询。报告使用本次结果接口 pgUrl。保留简单请求取消，避免旧请求覆盖换客户后的文案。

### 5. BLE 扫描前台权限修正

真机发现仅授予附近设备权限但未授予定位时，扫描注册成功却没有设备结果。当前 Manifest 未声明 neverForLocation；按用户确认保留此策略，不新增该断言或后台定位。Android 12+ 在用户点击扫描时一起请求 BLUETOOTH_SCAN、BLUETOOTH_CONNECT、ACCESS_FINE_LOCATION、ACCESS_COARSE_LOCATION；旧系统继续请求精确定位。精确和粗略定位必须成对申请，即使粗略定位已授权也不从请求数组中删去。只有全部所需权限通过才启动会话，仅授予粗略定位或拒绝时保持阻断并允许重试。所有支持版本读取真实定位开关状态，关闭时提示开启后重试；不因 API 31+ 直接视为已开启，不额外启动位置采集。

依据 Android CLI 官方 Bluetooth permissions 与 Request location access at runtime 文档。该修正替代 use-qlz-custom-evaluation-ui 中“现有权限分支已满足要求”的判断，保留其无新增 Manifest 权限和生产发布门禁边界。

### 6. AGP 补丁升级

用户确认将版本目录的 AGP 从 9.4.0 升级至 9.4.1，主构建和 build-logic 继续共享同一版本来源。Android CLI 官方 AGP 9.4 兼容矩阵要求 Gradle 9.6.0+、JDK 17+，支持 API 37；保留现有 Gradle 9.7.1、JDK 21、Kotlin 与 SDK 基线，不顺带升级其他依赖。重新执行完整 preflight、双应用构建/Lint、warning allowlist 和关闭接口 instrumentation；不为版本提醒添加豁免。

## Risks / Trade-offs

- H5 需在业务完成时调用关闭；客户端按约定信任该通知，不再次校验完成。
- 所有 frame 均可调用关闭，当前仅暴露关闭能力，不宣称来源认证。
- pgResult 无固定等级枚举，按服务端文本展示；接口尚未返回结果时不伪造 A 级。
- 无法穷举所有内核；记录 WebView 80、现代内核的受控验证，缺少 API 24 设备及真实 BLE/H5 联调时如实保持待验收。

## Migration Plan

保持已完成的单一路径 NativeBridge 与设备自动导航。接入 GetCheckResult DTO、Repository 和契约测试，再接入评估用途回调、完成页文案及 mock 验证。删除旧计划中未实施的结果关联与二次确认要求，保留其他业务仍使用的 Navigation 3 邮箱。保留账号、数据库与首页状态。
