## Why

设备上传后自动进入 H5 与 WebView 80 关闭接口已实现，本轮补齐关闭后的完成页与接口结果文案。用户确认：评估 H5 调用关闭即表示评估完成，所有网页不需要返回结果关联机制。

2026-09-19 用户澄清：closeWebView 是 H5 左上角返回按钮的调用，并非成功弹窗确认按钮的调用；评估表单和报告已有网页标题，应去除重复的原生标题栏。

## What Changes

- 所有 H5 通过 addJavascriptInterface 注册 NativeBridge，仅暴露无参数 closeWebView()，不注入 JS、不设置内核能力门槛。
- 保留主线程、前台、当前容器、重复关闭和销毁保护；不使用 isSafeUrl、域名白名单或相关导航/请求拦截。
- 设备上传成功后获取客户 pgUrl，前台自动打开一次 H5；100% 采样不提前跳转，获取地址失败不重复检测。
- 评估 H5 主动关闭即进入完成页，有设备记录时请求 POST /V1/Sale/GetCheckResult，纯表单直接重新请求 GET /V1/Sale/GetUserLatentDetail，读取 pgResult、pgUrl。不使用缓存旧值或失败兜底链，不再核实是否完成，不建立网页返回结果邮箱或关联机制。
- 原生/系统返回回评估入口；普通报告、协议和隐私网页只关闭，不触发评估完成、不改变隐私同意。
- 评估表单和评估报告去除原生标题及返回栏，H5 填满系统安全区域内的内容空间；保留 H5 自带标题/返回、系统返回与键盘适配，其他协议/隐私容器布局不变。
- 成功弹窗确认后刷新由 H5 自行控制，不要求该按钮调用关闭，不新增 JS 注入、监听或结果协议。
- 沉浸式适配仅使用现有页面的白色背景和 AndroidX 系统栏样式，背景延伸至系统栏，保留必要安全间距；离开后恢复应用默认样式，不增加适配框架或 H5 脚本。
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
- 后续依赖升级和 PR 处理单独由 `upgrade-dependencies-and-review-prs` 管理，不混入本次 UI 实现。
