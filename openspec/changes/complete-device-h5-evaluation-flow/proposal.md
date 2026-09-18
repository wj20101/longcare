## Why

设备上传后自动进入 H5 与 WebView 80 关闭接口已实现，本轮补齐关闭后的完成页与接口结果文案。用户确认：评估 H5 调用关闭即表示评估完成，所有网页不需要返回结果关联机制。

## What Changes

- 所有 H5 通过 addJavascriptInterface 注册 NativeBridge，仅暴露无参数 closeWebView()，不注入 JS、不设置内核能力门槛。
- 保留主线程、前台、当前容器、重复关闭和销毁保护；不使用 isSafeUrl、域名白名单或相关导航/请求拦截。
- 设备上传成功后获取客户 pgUrl，前台自动打开一次 H5；100% 采样不提前跳转，获取地址失败不重复检测。
- 评估 H5 主动关闭即进入完成页，完成页请求 POST /V1/Sale/GetCheckResult，直接显示 pgResult。不再核实是否完成，不建立网页返回结果邮箱或关联机制。
- 原生/系统返回回评估入口；普通报告、协议和隐私网页只关闭，不触发评估完成、不改变隐私同意。
- 结果失败/为空允许手动刷新，不伪造等级、不重复上传。完成/返回回保留的首页。
- 补齐真机联调发现的 BLE 扫描权限缺口：保留未声明 neverForLocation 的策略，Android 12+ 同时申请附近设备与前台精确/粗略定位，所有支持版本检查定位服务；不新增后台定位或启动位置采集。
- 按用户确认将 AGP 9.4.0 升级至 9.4.1 补丁版，重新验证双 APK 构建、单测和 Lint，不修改 warning allowlist。

## Capabilities

### New Capabilities

- `legacy-h5-close`: 最小统一关闭接口，覆盖项目支持的新旧 WebView 与容器生命周期。
- `device-h5-evaluation-flow`: 设备上传、H5、完成页接口文案及返回链路。

### Modified Capabilities

无。主 specs 尚未覆盖；依赖未归档的 align-evaluation-ui-and-h5-close。用户最新要求替代此前 JS 包装、来源限制、返回关联和二次完成确认方案。

## Impact

- 修改现有 WebView 容器、Navigation 3 页面关闭、销售业务状态和完成页，不新增模块或扩大 legacy feature 目录。
- 新接入已核对的 GetCheckResult：请求 id、可空 recordId，响应 pgResult、pgUrl。增加方法/路径/JSON 契约测试。
- H5 自行控制完成与关闭调用时机；仓库不含线上 H5，不发布服务端变更。所有加载页面及 iframe 可调用关闭；新增敏感方法须单独授权。
- 不改数据库、账号、助手业务逻辑、SDK 上传协议和生产发布门禁；保留仍被其他业务使用的导航结果机制、WebKit 与渲染保护。AGP 补丁升级同时影响主应用、助手和共享约定插件的构建。
