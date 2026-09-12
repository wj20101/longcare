# 页面与路由地图

最后核对：2026-09-12

本文列出当前可运行的 Compose 路由、嵌套页面和现实模块归属。导航代码的机器真相位于 `app/src/main/kotlin/com/ytone/longcare/navigation/`。

## 根入口

- 隐私未同意：`MainApp` 直接显示 `PrivacyConsentDialog`，不创建业务 NavHost。
- 会话解析中：私有 `SplashRoute` 对应 `SplashScreen`。
- 未登录：`LoginRoute`。
- 已登录：`HomeGraphRoute`，start destination 为 `HomeRoute`。

`HomeGraphRoute` 还承载首页共享的 `TodayOrderViewModel` 和服务计划/记录列表，保证子页面复用同一份首页订单状态。

## Entry / Home

| 路由 | 页面 | 现实归属 | 说明 |
|---|---|---|---|
| `LoginRoute` | `LoginScreen` | `:app` | 登录、协议、隐私/协议 WebView；Logo 无测试行为 |
| `HomeRoute` | `HomeScreen` | `:app` | 按 `userIdentity` 选择护理端或销售端 |
| `CarePlansListRoute` | `ServiceOrdersListScreen` | `:app` | 服务计划列表 |
| `ServiceRecordsListRoute` | `ServiceOrdersListScreen` | `:app` | 服务记录列表 |

护理账号的 `HomeScreen` 内有三个非 NavHost 页签：

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
| `TxFaceRoute` | `FaceVerificationWithAutoSignScreen` | `:feature:identification` | 腾讯 SDK 兼容/验证路径，不是默认订单入口 |
| `FaceRecognitionGuideRoute` | `FaceRecognitionGuideScreen` | `:app` | 人脸相关引导 |
| `SelectDeviceRoute` | `SelectDeviceScreen` | `:app` | 路由仍存在；当前开始服务动作直接转入 NFC，实际跳过该页 |
| `CameraRoute` | `CameraScreen` | `:feature:photoupload` | 护理和销售共用的水印相机 |
| `UserListRoute` | `UserListScreen` | `:app` | 已服务/未服务用户列表 |
| `UserServiceRecordRoute` | `UserServiceRecordScreen` | `:app` | 用户服务记录 |
| `WebViewRoute` | `WebViewScreen` | `:app` | 协议、隐私政策、销售表单/报告页面 |

默认服务人员核验结果通过上一层 `SavedStateHandle` 的 `DEFAULT_FACE_VERIFICATION_RESULT_KEY` 返回；长者照片和销售登记照片通过 `CAPTURED_IMAGE_URI_KEY` 返回；手动人脸补录通过 `FACE_IMAGE_PATH_KEY` 返回。接收页消费后负责清除 key。

## 销售端嵌套页面

销售账号仍停留在 `HomeRoute` 内，`SalesExperienceScreen` 使用可保存的 `SalesNavigationState` 管理内部页面，不为每个页面注册 NavHost route：

- `HOME`：销售首页
- `REMINDERS` / `REMINDER_DETAIL`：待办列表和详情
- `CUSTOMERS` / `CUSTOMER_DETAIL`：客户列表和详情
- `REGISTRATION` / `REGISTRATION_CONFIRM` / `SUBMIT_SUCCESS`：客户登记链路
- `EVALUATION_CHOICE`：表单/设备评估选择
- `DEVICE_STATUS` / `EVALUATION_GUIDE` / `EVALUATION_COMPLETE`：QLZ 设备评估链路

销售端的根页签为首页、我的客户和我的；个人中心复用护理端 `ProfileScreen`。表单和报告跳到应用级 `WebViewRoute`，登记照片跳到应用级 `CameraRoute`，返回结果后恢复原销售页面状态。

## 非路由 UI

- `AppUpdateDialog`：由 `MainApp` 在当前页面上方显示。
- 倒计时/权限/确认 Dialog：由各服务页面的状态控制，不是独立 route。
- `PhotoPreviewDialog`：`:core:ui` 的统一全屏预览。
- `CountdownAlarmActivity`：锁屏/全屏提醒 Activity，不属于 Compose NavHost。

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

助手隐私状态、照片与会话在自己的沙箱；登录中断目标通过 SavedStateHandle 保存且消费一次。系统返回/取消登录返回助手首页，不进入正式 Home 或订单流程。同账号仍可能受后端单会话规则影响。

助手首页照片由共享图片管线保存在私有受管文件目录，清空结果、替换照片及退出账号时删除旧照片，不扫描其他文件。备用腾讯人脸成功后记录结果并返回首页。水印定位支持精确和近似授权；拒绝权限时显示不可用，从设置返回后重新检查。

订单 ID 无效时显示范围错误并禁止发起验证，不截断超长输入为另一个有效订单。标准相机在页面恢复时重新检查相机权限，支持从系统设置授权后返回原页面继续拍照。


## 路由类型清单

### Object / graph routes

- `LoginRoute`
- `HomeRoute`
- `HomeGraphRoute`
- `CarePlansListRoute`
- `ServiceRecordsListRoute`
- `TxFaceRoute`
- `ManualFaceCaptureRoute`

### 参数化 routes

- `ServiceRoute`
- `NursingExecutionRoute`
- `WebViewRoute`
- `SelectServiceRoute`
- `PhotoUploadRoute`
- `FaceRecognitionGuideRoute`
- `SelectDeviceRoute`
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
2. `SavedStateHandle` 结果 key 与清理时机兼容。
3. Hilt ViewModel owner 和 Home 子图共享状态兼容。
4. 登录/退出、系统返回和进程重建行为兼容。
5. 本文、系统概览和相关测试同步更新。
