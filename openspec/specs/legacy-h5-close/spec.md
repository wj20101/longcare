# legacy-h5-close Specification

## Purpose

让项目支持的 Android 系统中的新旧 WebView 通过客户端注册的最小原生接口关闭当前网页；H5 自行控制交互与调用时机，客户端不注入业务或接口包装脚本，保留容器生命周期、重复关闭和隐私同意隔离，并准确说明页面内容的信任边界。

## Requirements

### Requirement: 客户端仅注册关闭能力

客户端 SHALL 为所有应用内 H5 注册 `NativeBridge` 对象，仅暴露无参数 `closeWebView()`。H5 SHALL 通过 `window.NativeBridge.closeWebView()` 主动请求关闭；通用扩展 SHALL 通过 NativeBridge 类新增显式注解方法实现；当前仅实现关闭。客户端 MUST NOT 为该能力注入 JS 包装、建立通用方法分发框架或代替 H5 控制提交和调用时机。

#### Scenario: H5 主动请求关闭
- **WHEN** H5 根据自身交互流程调用 `window.NativeBridge.closeWebView()`
- **THEN** 客户端收到通知并执行当前网页返回，H5 不需要传递凭证、消息协议或等级参数

#### Scenario: H5 尚未请求关闭
- **WHEN** H5 显示弹窗、提交问卷或刷新页面但尚未调用关闭方法
- **THEN** 客户端不通过注入脚本、监听 DOM 或模拟点击来代替 H5 发起关闭

#### Scenario: 评估 H5 的按钮语义
- **WHEN** H5 左上角返回调用关闭方法，而成功弹窗确认仅刷新数据
- **THEN** 客户端只响应实际关闭调用，不要求确认按钮也调用接口，不把网页刷新当作关闭失败

### Requirement: 支持系统范围内统一关闭

应用 SHALL 在 Android API 24 及以上、具备正常 JavaScript 执行能力的标准 WebView 中提供同一关闭接口，MUST NOT 因缺少现代消息桥能力要求用户升级 WebView。接口 SHALL 在首次加载前完成注册，不提供拼写别名。

#### Scenario: WebView 80 调用
- **WHEN** WebView 80 加载网页，H5 调用已注册的关闭方法
- **THEN** 当前网页关闭一次，不要求更新内核或等待额外 JS 包装初始化

#### Scenario: 普通页面与隐私弹窗
- **WHEN** 报告、普通网页、协议或隐私网页调用关闭接口
- **THEN** 仅关闭当前网页或网页弹窗，保留来源页和首页，不退出应用、不改变隐私同意状态、不触发评估完成；评估表单按 device-h5-evaluation-flow 的业务关闭行为显示完成页

#### Scenario: 网页异常
- **WHEN** 网页加载或渲染失败
- **THEN** 显示相应原生异常提示并保留返回操作，不伪装为业务成功；评估表单和报告隐藏原生标题栏后仍能通过系统返回退出

### Requirement: 有效容器与单次关闭

客户端 SHALL 在主线程执行关闭，并确认对应容器有效、处于前台且可执行其来源绑定的返回操作。重复请求以及后台、已关闭、已销毁或渲染失效容器的请求 MUST NOT 关闭其他页面。

#### Scenario: 连续关闭
- **WHEN** 当前有效网页连续调用关闭方法
- **THEN** 最多执行一次返回，后续请求不弹出来源页

#### Scenario: 后台请求
- **WHEN** 网页容器位于后台时收到关闭请求
- **THEN** 不执行导航，恢复前台后新的合法关闭请求仍可处理

#### Scenario: 失效容器的迟到回调
- **WHEN** 旧容器已经退出、销毁或渲染失败后仍收到其接口回调
- **THEN** 忽略回调，不影响来源页或新容器

#### Scenario: 普通加载错误后仍可关闭
- **WHEN** 网页发生普通网络或 HTTP 错误，但容器仍有效且 H5 能执行关闭调用
- **THEN** 客户端仍处理关闭，不因网页加载错误禁用原生接口

### Requirement: 所有 H5 统一接口与扩展边界

客户端 SHALL 为所有加载的 H5 注册 NativeBridge，MUST NOT 使用 isSafeUrl、域名白名单或相关顶层跳转/请求拦截限制关闭能力。客户端 MUST NOT 将此接口声称为调用 frame 身份认证。未来新增敏感原生方法 SHALL 单独设计授权，不自动继承关闭方法的无限制暴露策略。

#### Scenario: 跨来源加载与跳转
- **WHEN** 容器首次加载任意 H5 来源，或顶层页面跳转到其他来源
- **THEN** 不因为域名不在白名单而拒绝加载或移除关闭接口

#### Scenario: 页面子框架
- **WHEN** 网页所嵌入的 frame 访问原生接口
- **THEN** 允许其请求关闭当前容器；当前不提供关闭以外的原生能力
