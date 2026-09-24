## MODIFIED Requirements

### Requirement: 客户端仅注册关闭能力

客户端 SHALL 为所有应用内 H5 注册 `NativeBridge` 对象并暴露无参数 `closeWebView()`。H5 SHALL 通过 `window.NativeBridge.closeWebView()` 主动请求关闭；评估表单及报告额外支持 evaluation-h5-close 定义的 `enterUserDetails(pingguuserid)`。扩展 SHALL 采用显式原生方法，客户端 MUST NOT 为此注入 JS 包装、建立通用方法分发框架或代替 H5 控制提交和调用时机。

#### Scenario: H5 主动请求关闭
- **WHEN** H5 根据自身交互流程调用 `window.NativeBridge.closeWebView()`
- **THEN** 客户端收到通知并执行当前网页返回，H5 不需要传递凭证、消息协议或等级参数

#### Scenario: H5 尚未请求关闭
- **WHEN** H5 显示弹窗、提交问卷或刷新页面但尚未调用原生导航方法
- **THEN** 客户端不通过注入脚本、监听 DOM 或模拟点击来代替 H5 发起关闭或客户详情跳转

#### Scenario: 评估 H5 的按钮语义
- **WHEN** H5 左上角返回调用关闭方法，而成功弹窗确认仅刷新数据
- **THEN** 客户端只响应实际调用，不要求确认按钮也调用接口，不把网页刷新当作关闭失败

### Requirement: 所有 H5 统一接口与扩展边界

客户端 SHALL 为所有加载的 H5 注册 NativeBridge，MUST NOT 使用 isSafeUrl、域名白名单或相关顶层跳转/请求拦截限制关闭能力。客户端 MUST NOT 将此接口声称为调用 frame 身份认证。客户详情跳转 SHALL 仅在销售评估表单和报告容器启用，不向 JS 返回客户数据，不替代服务端访问授权。未来新增敏感原生方法 SHALL 单独设计授权，不自动继承关闭方法的无限制暴露策略。

#### Scenario: 跨来源加载与跳转
- **WHEN** 容器首次加载任意 H5 来源，或顶层页面跳转到其他来源
- **THEN** 不因为域名不在白名单而拒绝加载或移除关闭接口

#### Scenario: 页面子框架
- **WHEN** 网页所嵌入的 frame 访问原生接口
- **THEN** 允许其请求关闭当前容器；评估容器内的 frame 同样可请求客户详情跳转，不能据此认定 frame 身份可信，接口不返回客户数据

#### Scenario: 协议和普通网页
- **WHEN** 非评估网页或隐私弹窗尝试调用客户详情方法
- **THEN** 不打开客户详情、不查询客户数据，也不关闭当前页面；原关闭方法仍可使用
