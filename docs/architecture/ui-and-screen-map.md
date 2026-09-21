# 页面与路由地图

最后核对：2026-09-21（护理详情错误、重试和返回已做离线回归；非全路由真机验收）

本文列出当前可运行的 Compose 路由、嵌套页面和现实模块归属。导航代码的机器真相位于 `app/src/main/kotlin/com/ytone/longcare/navigation/`。

## 根入口

- 隐私未同意：`MainApp` 直接显示 `PrivacyConsentDialog`，不创建业务 NavDisplay。
- 会话解析中：直接显示 `SplashScreen`，不创建业务返回栈。
- 未登录：`LoginRoute`。
- 已登录：`HomeRoute`，恢复返回栈前校验账号身份。

Navigation 3 使用可保存的 `AppNavEntry` 包装业务路由，为相同参数的重复页面分配独立 ID。首页、服务计划/记录列表通过显式 Home owner 复用 `TodayOrderViewModel`；普通页面保持独立 owner。重置 Home、退出或换号清理旧作用域，配置重建保留。

导航仅使用 Navigation 3，不保留 Navigation 2 适配或旧临时栈转换。Navigation 3 自身保存与恢复 entry ID、参数和未消费结果；账号、数据库和服务数据不受框架替换影响。

## Entry / Home

| 路由 | 页面 | 现实归属 | 说明 |
|---|---|---|---|
| `LoginRoute` | `LoginScreen` | `:app` | 登录、协议、隐私/协议 WebView；中央大 Logo 长按确认进入读卡检测 |
| `HomeRoute` | `HomeScreen` | `:app` | 按 `userIdentity` 选择护理端或销售端 |
| `CarePlansListRoute` | `ServiceOrdersListScreen` | `:app` | 服务计划列表 |
| `ServiceRecordsListRoute` | `ServiceOrdersListScreen` | `:app` | 服务记录列表 |

护理账号的 `HomeScreen` 内有三个非 NavKey 页签：

- 首页：`MainDashboardScreen`
- 护理：`NursingScreen`
- 我的：`ProfileScreen`

顶层使用 `AdaptiveAppNavigationScaffold`，根据窗口尺寸选择底部导航或导航轨；三个页面由不可手势滑动的 `HorizontalPager` 保存页签状态。

## 服务执行路由

| 路由 | 页面 | 现实归属 |
|---|---|---|
| `ServiceRoute` | `ServiceHoursScreen` | `:app` |
| `NursingExecutionRoute` | `NursingExecutionScreen` | `:app` |
| `NfcSignInRoute` | `NfcWorkflowScreen` | `:app` |
| `SelectServiceRoute` | `SelectServiceScreen` | `:app` |
| `PhotoUploadRoute` | `PhotoUploadScreen` | `:app` |
| `ServiceCountdownRoute` | `ServiceCountdownScreen` | `:app` |
| `EndServiceSelectionRoute` | `EndServiceSelectionScreen` | `:app` |
| `ServiceCompleteRoute` | `ServiceCompleteScreen` | `:app` |

`NfcSignInRoute` 通过 `SignInMode.START_ORDER` / `END_ORDER` 复用同一页面。倒计时、结束选择和完成页通过类型安全 payload 传递项目 ID、图片列表和完成摘要。

护理执行与订单详情加载复用现有请求分类显示错误：业务提示原文、连接、超时、HTTP、数据异常和未知失败分别处理，空白业务提示使用兜底文案。护理执行错误页保留手动重试及系统返回，重试成功切回详情；不会因为错误文案变化而开单、自动重试或改变会话失效规则。

## 身份、相机与支持路由

| 路由 | 页面 | 现实归属 | 当前用途 |
|---|---|---|---|
| `IdentificationRoute` | `IdentificationScreen` | `:app` | 订单身份核验主页面 |
| `DefaultFaceVerificationRoute` | `DefaultFaceVerificationScreen` | `:feature:identification` | 默认服务人员眨眼活体采集 + 服务端比对 |
| `ManualFaceCaptureRoute` | `ManualFaceCaptureScreen` | `:feature:identification` | 缺少登记照时的兼容补录路径 |
| `CameraRoute` | `CameraScreen` | `:feature:photoupload` | 护理和销售共用的水印相机 |
| `UserListRoute` | `UserListScreen` | `:app` | 已服务/未服务用户列表 |
| `UserServiceRecordRoute` | `UserServiceRecordScreen` | `:app` | 用户服务记录 |
| `WebViewRoute` | `WebViewScreen` | `:app` | 协议、隐私政策、销售表单/报告页面；统一注册最小原生关闭接口 |

开始服务直接进入 NFC/读卡流程；无入口的设备选择页、旧人脸引导页和正式应用腾讯测试路由已移除。独立助手的腾讯测试入口亦已删除，正式业务仍需的共享实现保留。

默认服务人员核验结果通过调用者 entry 邮箱的 `DEFAULT_FACE_VERIFICATION_RESULT_KEY` 返回；长者照片和销售登记照片通过 `CAPTURED_IMAGE_URI_KEY` 返回；手动人脸补录通过 `FACE_IMAGE_PATH_KEY` 返回。图片输入/上传结果分别使用 `EXISTING_IMAGES_KEY` / `PHOTO_UPLOAD_RESULT_KEY`。邮箱与栈共同保存，接收页消费后清空 StateFlow；来源失效的迟到回调丢弃。服务完成后清除中间页并保留首页，普通返回到首页。

## 销售端嵌套页面

销售账号仍停留在 `HomeRoute` 内，`SalesExperienceScreen` 使用可保存的 `SalesNavigationState` 管理内部页面，不为每个页面注册 NavKey route：

- `HOME`：销售首页
- `REMINDERS` / `REMINDER_DETAIL`：待办列表和详情
- `CUSTOMERS` / `CUSTOMER_DETAIL`：客户列表和详情
- 客户详情在进程恢复后，按 SavedStateHandle 中的客户 ID 重查；已加载、请求中或已有错误时不重复请求，错误沿用手动重试。
- `REGISTRATION` / `REGISTRATION_CONFIRM` / `SUBMIT_SUCCESS`：客户登记链路
- `EVALUATION_CHOICE`：表单/设备评估选择
- `DEVICE_STATUS` / `EVALUATION_GUIDE` / `EVALUATION_COMPLETE`：应用自有 QLZ 扫描、连接、五指检测、上传与完成链路；不启动厂商 Activity

`EVALUATION_GUIDE` 使用手握、5 秒准备、沙漏进度三态单卡片；准备计时仅控制展示，真实 SDK 进度优先。
所有应用内 H5 共用 `NativeBridge.closeWebView()`：路由网页返回来源 entry，隐私网页只关闭自身弹窗，不触发同意/拒绝。桥接注册、线程、去重、frame 可见性和信任边界统一见[QLZ/H5 接入契约](../integrations/qlz-sdk.md#检测展示与-h5-关闭契约)。

评估表单和报告入口显式设置 `WebViewRoute.showNativeToolbar=false`，共用容器去除原生顶部栏；`WindowInsets.safeDrawing` 由 Scaffold 应用并消费，H5 填满剩余内容区。普通网页、协议与隐私弹窗默认保留原生栏。展示参数与 `isEvaluation` 独立，报告关闭不触发完成；参数随 Navigation 3 栈保存恢复，不按标题或 URL 推断。
无原生栏时，白色背景延伸至系统栏，页面使用 AndroidX 浅色系统栏样式实现沉浸式视觉；暂停/退出后恢复应用默认样式。仅做页面级适配，不隐藏系统栏、不注入 H5 脚本、不增加适配框架。
H5 左上角返回调用关闭接口；成功弹窗确认仅刷新网页属于正常行为，不注入脚本代为关闭。移除原生栏后仍由 Navigation 3 处理系统返回，网页错误时同样可退出。
渲染进程退出后，容器销毁失效 WebView 并展示可返回的异常状态，不自动重载或改变隐私选择。

销售端的根页签为首页、我的客户和我的，个人中心复用 `ProfileScreen`。表单/报告进入应用级 `WebViewRoute`，登记照片进入 `CameraRoute`；只有相机使用返回结果邮箱。

`DEVICE_STATUS` 与 `EVALUATION_GUIDE` 共享 UI 作用域的 QLZ 会话，切换两页保持连接，离开、取消、完成或宿主销毁时释放。上传成功后直接进入 `EVALUATION_COMPLETE`，使用当前客户和本次 recordId 调用 GetCheckResult 查询 pgResult/pgUrl；客户、recordId 和完成状态通过 SavedStateHandle 保存。用户点击“查看评估报告”才打开返回地址，无地址时按钮不可用并可刷新，失败不重新检测或上传。

纯表单网页用 `isEvaluation` 标识用途，复用首页 SalesViewModel；JS 主动关闭更新完成状态并 pop，原生/系统返回仅 pop。设备结果页打开的报告为非评估用途，JS/系统返回都只关闭报告并回到原结果页，不触发完成回调。完成和返回保留首页，不写导航结果邮箱。设备/纯表单的查询分支和失败恢复统一见[SDK 调用链](../integrations/qlz-sdk.md#sdk-调用链)。

## 非路由 UI

- `AppUpdateDialog`：由 `MainApp` 在当前页面上方显示。
- 倒计时/权限/确认 Dialog：由各服务页面的状态控制，不是独立 route。
- `PhotoPreviewDialog`：`:core:ui` 的统一全屏预览。
- `CountdownAlarmActivity`：锁屏/全屏提醒 Activity，不属于 Compose NavDisplay。

## 本地读卡检测

全局隐私同意后，登录页前台可交互时长按中央大 Logo，只弹出“打开助手”，点击“打开”才压入 `CardDiagnosticsRoute`。普通点击、小 Logo/背景长按均不触发入口，长按无震动；取消后需重新长按，进入后台或离开登录页清空待确认状态。无摇动监听、冷却计时、外部深链或登录后入口。

Logo 点击和长按均无波纹或按压高亮。弹窗使用 Material 3 默认样式，正文“可进行 NFC 和 R65C 读卡检测，检测数据仅在本机显示。”，按钮“取消”和“打开”。

检测 UI 属于 `:feature:carddiagnostics`，NFC/R65C 可主动切换。NFC 使用页面限定的 Reader Mode，不经过业务事件总线；R65C 只在当前模式前台捕获 HID 输入。结果只在本地显示、清空、复制，不上传、不签到、不修改会话。返回只弹出检测页，保留登录表单；退出和切换模式释放原监听。

独立助手及其他测试入口已删除，历史安装包不变。长按入口已有用户真机确认；NFC/R65C 贴卡、模式切换与资源释放仍待真实硬件验收，状态见[任务 4.3](../../openspec/changes/integrate-card-diagnostics-in-app/tasks.md)。

## 路由类型清单

### Object routes

- `LoginRoute`
- `CardDiagnosticsRoute`
- `HomeRoute`
- `CarePlansListRoute`
- `ServiceRecordsListRoute`
- `ManualFaceCaptureRoute`

### 参数化 routes

- `ServiceRoute`
- `NursingExecutionRoute`
- `WebViewRoute`
- `SelectServiceRoute`
- `PhotoUploadRoute`
- `IdentificationRoute`
- `DefaultFaceVerificationRoute`
- `UserListRoute`
- `UserServiceRecordRoute`
- `CameraRoute`
- `NfcSignInRoute`
- `ServiceCountdownRoute`
- `ServiceCompleteRoute`
- `EndServiceSelectionRoute`

共享 payload 包括 `OrderNavParams`、`EndOderInfo`、`ServiceCompleteData` 和 `WatermarkData`。

## 模块迁移判断

“legacy UI”在本文中只表示页面仍位于 `:app/features/**`，不表示功能已废弃。当前默认人脸核验、手动人脸采集与标准相机 UI 已在 Feature，其他多数 route-bound 页面仍由 `:app` 持有；`:feature:location` 没有独立页面，另外几个 Feature 主要持有动作接口、状态、用例或 delegate。

迁移 route 时必须保持：

1. route 类型和参数兼容。
2. entry 结果邮箱 key、类型与清理时机兼容。
3. Hilt ViewModel owner 和 Home 共享状态兼容。
4. 登录/退出、系统返回和进程重建行为兼容。
5. 本文、系统概览和相关测试同步更新。
