# 页面与路由地图

最后核对：2026-09-12

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
| `LoginRoute` | `LoginScreen` | `:app` | 登录、协议、隐私/协议 WebView；Logo 无测试行为 |
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

开始服务直接进入 NFC/读卡流程；无入口的设备选择页、旧人脸引导页和正式应用腾讯测试路由已移除。腾讯验证仍保留在独立助手中。

默认服务人员核验结果通过调用者 entry 邮箱的 `DEFAULT_FACE_VERIFICATION_RESULT_KEY` 返回；长者照片和销售登记照片通过 `CAPTURED_IMAGE_URI_KEY` 返回；手动人脸补录通过 `FACE_IMAGE_PATH_KEY` 返回。图片输入/上传结果分别使用 `EXISTING_IMAGES_KEY` / `PHOTO_UPLOAD_RESULT_KEY`。邮箱与栈共同保存，接收页消费后清空 StateFlow；来源失效的迟到回调丢弃。服务完成后清除中间页并保留首页，普通返回到首页。

## 销售端嵌套页面

销售账号仍停留在 `HomeRoute` 内，`SalesExperienceScreen` 使用可保存的 `SalesNavigationState` 管理内部页面，不为每个页面注册 NavKey route：

- `HOME`：销售首页
- `REMINDERS` / `REMINDER_DETAIL`：待办列表和详情
- `CUSTOMERS` / `CUSTOMER_DETAIL`：客户列表和详情
- `REGISTRATION` / `REGISTRATION_CONFIRM` / `SUBMIT_SUCCESS`：客户登记链路
- `EVALUATION_CHOICE`：表单/设备评估选择
- `DEVICE_STATUS` / `EVALUATION_GUIDE` / `EVALUATION_COMPLETE`：应用自有 QLZ 扫描、连接、五指检测、上传与完成链路；不启动厂商 Activity

`EVALUATION_GUIDE` 使用手握、5 秒准备、沙漏进度三态单卡片；准备计时仅控制展示，真实 SDK 进度优先。
所有应用内 H5（表单、报告、普通网页、协议和隐私网页弹窗）统一调用 `window.NativeBridge.closeWebView()` 关闭当前网页。
路由网页经 source-bound entry 返回来源页面；隐私网页仅关闭自身弹窗，不触发同意或拒绝。
不会清空首页或重复弹栈。所有网页均注册同一接口；评估用途仅用于关闭后的业务展示，不控制接口是否注册，也不承载返回结果。
首次加载前直接以 `addJavascriptInterface` 注册 `NativeBridge`，仅暴露无参数 `closeWebView()`；不生成 JS 包装或通用分发框架。
回调进入主线程，检查前台生命周期、当前 entry 和容器是否已关闭；重复/失效容器调用无效。
内部 H5 不设置额外 URL 白名单、顶层导航或请求拦截；跨域导航保留接口，普通加载失败不禁用关闭。
NativeBridge 是后续方法的直接扩展入口，不使用独立关闭 Policy、注册表或分发器。
接口对所有加载页面及其 iframe 同样可见，不认证调用来源；未来敏感方法须独立设计授权。
不再使用现代消息桥能力检查、文档随机凭证或独立 JS 初始化状态，新旧内核共用平台接口。
网页容器与隐私政策网页弹窗在渲染进程退出时显示原生异常提示，移除并销毁失效 WebView，保留原生返回；
不自动重载、不退出应用，也不改变隐私同意状态。隐私网页启用 JavaScript，复用通用 `WebViewScreen` 的桥接和安全配置。

销售端的根页签为首页、我的客户和我的；个人中心复用护理端 `ProfileScreen`。表单和报告跳到应用级 `WebViewRoute`，登记照片跳到应用级 `CameraRoute`。只有相机使用返回结果；所有网页不增加返回结果关联机制。`DEVICE_STATUS` 与 `EVALUATION_GUIDE` 共享一个 UI 作用域的 QLZ 会话，切换两页不会释放连接；离开这两页、取消、完成或宿主销毁时必须释放。设备上传成功后刷新客户详情并自动打开服务端 `pgUrl` 的评估 H5，不直接显示完成页。客户/recordId/待打开 URL/消费状态通过 SavedStateHandle 保存，前台执行一次导航；失败仅重试取地址。评估网页以 `isEvaluation` 标识用途，复用现有首页 SalesViewModel；JS 关闭更新业务完成状态并 pop，原生/系统返回仅 pop。完成页直接请求 GetCheckResult 获取 pgResult/pgUrl，不二次确认完成、不写导航结果邮箱，完成/返回回保留的首页。普通网页与隐私弹窗只关闭自身。

## 非路由 UI

- `AppUpdateDialog`：由 `MainApp` 在当前页面上方显示。
- 倒计时/权限/确认 Dialog：由各服务页面的状态控制，不是独立 route。
- `PhotoPreviewDialog`：`:core:ui` 的统一全屏预览。
- `CountdownAlarmActivity`：锁屏/全屏提醒 Activity，不属于 Compose NavDisplay。

## 独立验证助手

正式登录页不提供长按 Logo 面板；验证只在独立包 `com.ytone.longcare.assistant` 中进行。助手不注册正式 App 导航图。

| 助手路由 | 能力 | 鉴权 |
|---|---|---|
| `AssistantHome` | 五项入口与最近结果/照片预览 | 隐私同意 |
| `AssistantLogin` | 独立短信登录，取消返回首页 | 复用 LoginViewModel；无 mock |
| `AssistantToolRoute(DEFAULT_FACE, orderId)` | 订单 ID 输入、默认人脸验证、JPEG 尺寸/字节数 | 需登录，ID 限 1..Int.MAX_VALUE |
| `AssistantToolRoute(NFC)` | 原生 NFC 或无 NFC 设备上的 R65C HID | 无需登录；页面前台才监听 |
| `AssistantToolRoute(CAMERA)` | 标准水印相机、压缩、照片预览 | 无需登录；按需相机权限 |
| `AssistantToolRoute(TENCENT_FACE)` | 共享备用腾讯人脸页面 | 需助手 userId |
| `AssistantToolRoute(MANUAL_FACE)` | 共享手动采集页面和文件结果 | 无需登录 |

助手使用独立的 Navigation 3 可保存栈；隐私状态、照片与会话在自己的沙箱，登录中断目标通过 SavedStateHandle 保存且消费一次。系统返回/取消登录返回助手首页，不进入正式 Home 或订单流程。同账号仍可能受后端单会话规则影响。

助手 Launcher 图标沿用主应用的蓝色与白色环形标识，以深蓝扳手徽章区分内部验证身份；提供 API 24 位图回退、API 26 自适应层和 API 33 单色主题层。

助手首页照片由共享图片管线保存在私有受管文件目录，清空结果、替换照片及退出账号时删除旧照片，不扫描其他文件。备用腾讯人脸成功后记录结果并返回首页。水印定位支持精确和近似授权；拒绝权限时显示不可用，从设置返回后重新检查。

订单 ID 无效时显示范围错误并禁止发起验证，不截断超长输入为另一个有效订单。标准相机在页面恢复时重新检查相机权限，支持从系统设置授权后返回原页面继续拍照。


## 路由类型清单

### Object routes

- `LoginRoute`
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

“legacy UI”在本文中只表示页面仍位于 `:app/features/**`，不表示功能已废弃。当前除默认人脸核验 UI 外，绝大多数 route-bound 页面仍由 `:app` 持有；`:feature:location` 没有独立页面，另外几个 Feature 主要持有动作接口、状态、用例或 delegate。

迁移 route 时必须保持：

1. route 类型和参数兼容。
2. entry 结果邮箱 key、类型与清理时机兼容。
3. Hilt ViewModel owner 和 Home 共享状态兼容。
4. 登录/退出、系统返回和进程重建行为兼容。
5. 本文、系统概览和相关测试同步更新。
