# webview-open-host-navigation Specification

## Purpose

保证协议、隐私政策、销售表单和评估报告能够在服务端换域、跨域跳转或使用 CDN 时继续在应用内打开，同时把安全边界限定在协议与 WebView 能力而不是业务 host 白名单。

## Requirements

### Requirement: HTTP(S) 导航不限制 host
应用内 WebView SHALL 接受任意合法 host、子域、端口和 IP 形式的 `http` 或 `https` URL，不得维护业务域名 allowlist，也不得要求后续导航与初始 URL 同 host。

#### Scenario: 初始 HTTPS URL 使用新域名
- **WHEN** 协议、表单或报告入口收到一个此前未出现但格式合法的 HTTPS host
- **THEN** WebView 直接加载该 URL，不因 host 未登记而拒绝或跳转外部浏览器

#### Scenario: 页面跨域重定向
- **WHEN** 当前页面从业务域名重定向到另一个 HTTPS 域名、子域、CDN 或带端口的合法地址
- **THEN** WebView 在当前页面上下文继续导航且不进行 host 匹配

#### Scenario: 用户点击跨域链接
- **WHEN** 用户在页面中点击指向另一个合法 HTTP(S) host 的链接
- **THEN** WebView 返回“由自身处理”的导航结果，不因目标 host 不同而阻断

### Requirement: URL 协议边界保持 fail closed
WebView MUST 只把格式合法且具有网络 host 的 `http` 和 `https` URL 交给网页加载。空白、畸形、`file:`、`content:`、`javascript:`、`intent:`、`data:` 和未知自定义协议 MUST 被阻断，且不得自动转换为其他 URL 或通过隐式 Intent 启动外部组件。

#### Scenario: 本地文件协议
- **WHEN** 初始 URL、重定向或点击链接使用 `file:` 或 `content:`
- **THEN** WebView 拒绝加载且应用不授予本地文件或内容访问

#### Scenario: 可执行或外部协议
- **WHEN** 页面请求 `javascript:`、`intent:` 或未知自定义协议
- **THEN** 应用消费并阻断请求，不执行脚本 URL、不自动启动外部应用

#### Scenario: 无 host 的网络 URL
- **WHEN** URL 声明 HTTP(S) scheme 但没有合法 host
- **THEN** WebView 不发起加载并进入明确的失败状态

### Requirement: Host 开放不得削弱 WebView 安全设置
取消 host 白名单 MUST NOT 开启本地文件访问、内容访问、文件 URL 跨域访问、任意混合内容、SSL 错误绕过或面向不可信页面的原生 JavaScript bridge。隐私协议 WebView 的 JavaScript SHALL 保持关闭；业务 WebView 仅在页面功能需要时启用 JavaScript，且不得暴露原生对象。

#### Scenario: 任意 HTTPS host 加载业务页面
- **WHEN** 业务 WebView 加载一个新的 HTTPS host
- **THEN** 文件访问和内容访问仍关闭、混合内容仍拒绝、Safe Browsing 在受支持系统上保持启用，且页面无法调用未声明的原生 bridge

#### Scenario: TLS 证书错误
- **WHEN** 任意 host 的 HTTPS 页面发生证书校验错误
- **THEN** WebView 不调用继续加载或绕过证书校验，并向用户呈现加载失败

### Requirement: 明文 HTTP 遵循应用级网络安全策略
WebView URL policy SHALL 不按 host 额外拒绝 HTTP URL，但实际明文请求 MUST 继续服从 Android 应用级网络安全配置。本 change MUST NOT 将全局明文网络改为无条件允许；平台拒绝的 HTTP 页面应显示可重试的失败状态，而不是静默改写为 HTTPS 或伪报成功。

#### Scenario: 网络策略允许的 HTTP 页面
- **WHEN** WebView 收到一个 HTTP URL 且应用级网络安全策略允许该请求
- **THEN** WebView 不再执行额外 host 检查并正常加载页面

#### Scenario: 网络策略拒绝的 HTTP 页面
- **WHEN** WebView 收到一个 HTTP URL但应用级网络安全策略拒绝该 host 的明文请求
- **THEN** 页面显示明确加载失败和重试入口，应用不扩大全局明文权限

### Requirement: 两类应用内 WebView 使用同一导航规则
全屏业务 WebView 与首次隐私同意弹窗中的 WebView SHALL 共享同一可测试 URL 判定语义，避免一处允许跨 host 而另一处阻断。两者可以维持不同 JavaScript 配置，但 URL scheme 和 host 行为 MUST 一致。

#### Scenario: 同一跨域 URL 用于不同入口
- **WHEN** 相同的合法 HTTPS URL 分别从隐私协议入口和业务报告入口打开
- **THEN** 两个入口都接受任意 host 并在各自 WebView 内加载

#### Scenario: 同一危险协议用于不同入口
- **WHEN** 相同的危险或未知协议分别传入两个入口
- **THEN** 两个入口均阻断且不访问本地数据或启动外部组件

### Requirement: 加载失败可观察且可恢复
WebView SHALL 在主文档加载失败、SSL 失败或平台阻断时结束加载状态并展示用户可理解的失败反馈和重试操作。重试 MUST 使用原始目标 URL 并继续遵循相同的 scheme 与平台网络策略。

#### Scenario: 新 host 暂时不可达
- **WHEN** 任意合法 HTTPS host 因 DNS、连接或服务器错误加载失败
- **THEN** 页面停止加载指示、显示失败反馈，并允许用户重试原 URL

#### Scenario: 重试成功
- **WHEN** 用户在网络恢复后点击重试且原 URL 仍符合导航策略
- **THEN** WebView 重新加载原 URL并在成功后清除失败状态
