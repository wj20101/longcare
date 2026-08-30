# 页面与路由地图

最后核对：2026-08-30

本文列出当前可运行的 Compose 路由、嵌套页面和现实模块归属。导航代码的机器真相位于 `app/src/main/kotlin/com/ytone/longcare/navigation/`。

## 根入口

- 隐私未同意：`MainApp` 直接显示 `PrivacyConsentDialog`，不创建业务 NavHost。
- 首次会话解析中：直接显示 `SplashScreen`，尚不创建认证 NavHost；不存在可返回的 Splash route。
- 认证 NavHost 已建立后再次解析会话：在原 NavHost 上覆盖不透明且拦截输入的 `SplashScreen`，保留当前 NavController/back stack。
- 未登录：`LoginRoute`。
- 已登录：`HomeGraphRoute`，start destination 为 `HomeRoute`。

认证协调器保证 `LoginRoute` 与 `HomeGraphRoute` 只保留一个根：相同会话信号不重复导航，登录成功、退出、Token 失效和换号使用类型安全 `popUpTo(inclusive = true)` 清除另一根，不能通过系统返回跨越账号边界。

`HomeGraphRoute` 承载 `TodayOrderViewModel` 和服务计划/记录列表，保证子页面复用同一份首页订单状态；找不到该 graph owner 时直接失败，不回退到目的页面 owner。`HomeSharedViewModel` 仍由 `HomeRoute` 持有，退出并重新登录会创建新的 HomeGraph owner，不复用旧账号保存状态。

## Entry / Home

| 路由 | 页面 | 现实归属 | 说明 |
|---|---|---|---|
| `LoginRoute` | `LoginFeatureScreen` | `:feature:login` | feature 拥有登录 UI/协议/内部验证面板；app 拥有 route、WebView 与平台动作适配 |
| `HomeRoute` | `HomeScreen` | `:app` | 身份为空时显示 Loading，恢复后选择护理端或销售端 |
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

`NfcSignInRoute` 通过 `SignInMode.START_ORDER` / `END_ORDER` 复用同一页面。开始服务从护理执行页直接构造 `START_ORDER` route 并进入 NFC，返回后直接回到原护理执行页，不经过中间设备选择页面。倒计时、结束选择和完成页通过类型安全 payload 传递项目 ID、图片列表和完成摘要。

## 身份、相机与支持路由

| 路由 | 页面 | 现实归属 | 当前用途 |
|---|---|---|---|
| `IdentificationRoute` | `IdentificationFeatureScreen` | `:feature:identification` | 订单身份核验主页面；app 只组装 route、结果和厂商 launcher |
| `DefaultFaceVerificationRoute` | `DefaultFaceVerificationScreen` | `:feature:identification` | 默认服务人员眨眼活体采集 + 服务端比对 |
| `ManualFaceCaptureRoute` | `ManualFaceCaptureScreen` | `:app` | 缺少登记照时的兼容补录路径 |
| `TxFaceRoute` | `FaceVerificationWithAutoSignScreen` | `:app` | 腾讯 SDK 兼容/验证路径，不是默认订单入口 |
| `FaceRecognitionGuideRoute` | `FaceRecognitionGuideScreen` | `:app` | 人脸相关引导 |
| `CameraRoute` | `CameraScreen` | `:app` | 护理和销售共用的水印相机 |
| `UserListRoute` | `UserListScreen` | `:app` | 已服务/未服务用户列表 |
| `UserServiceRecordRoute` | `UserServiceRecordScreen` | `:app` | 用户服务记录 |
| `WebViewRoute` | `WebViewScreen` | `:app` | 协议、隐私政策、销售表单/报告；合法 HTTP(S) 跨 host 导航由 WebView 内部处理 |

默认服务人员核验结果通过上一层 `SavedStateHandle` 的 `DEFAULT_FACE_VERIFICATION_RESULT_KEY` 返回；长者照片和销售登记照片通过 `CAPTURED_IMAGE_URI_KEY` 返回；手动人脸补录通过 `FACE_IMAGE_PATH_KEY` 返回。身份 route 将三个值作为 `StateFlow` 交给 feature，feature 提交处理后调用 acknowledgment，app 通过写入 `null` 同步清除流值，避免重建后重放。

页面返回按钮与系统返回键都调用同一个 `IdentificationActions.onNavigateBack`。腾讯 SDK UI 不进入 feature：`:app` 在 route lambda 中用当前 Context 和 `rememberFaceSdkUiController()` 实现 `IdentificationFaceSdkLauncher`，feature 只按 request id 交付和确认启动请求。

首次隐私同意弹窗中的内嵌 WebView 与 `WebViewRoute` 使用同一 URL policy 和失败/重试语义；隐私入口额外保持 JavaScript 关闭。两者均不使用 host 白名单，也不绕过 Android 的 HTTP 明文与 TLS 安全策略。

## 销售端嵌套页面

销售账号仍停留在 `HomeRoute` 内，`SalesExperienceScreen` 使用可保存的 `SalesNavigationState` 管理内部页面，不为每个页面注册 NavHost route：

- `HOME`：销售首页
- `REMINDERS` / `REMINDER_DETAIL`：待办列表和详情
- `CUSTOMERS` / `CUSTOMER_DETAIL`：客户列表和详情
- `REGISTRATION` / `REGISTRATION_CONFIRM` / `SUBMIT_SUCCESS`：客户登记链路
- `EVALUATION_CHOICE`：表单/设备评估选择
- `DEVICE_STATUS` / `EVALUATION_GUIDE` / `EVALUATION_COMPLETE`：QLZ 设备评估链路

销售端的根页签为首页、我的客户和我的；个人中心复用护理端 `ProfileScreen`。表单和报告跳到应用级 `WebViewRoute`，登记照片跳到应用级 `CameraRoute`，返回结果后恢复原销售页面状态。

`SalesNavigationState` 保存当前页面、根页签、详情返回页、评估返回页和提醒索引；未知枚举、非法页签或缺失字段恢复为安全默认值。系统返回键、页面返回按钮、提交成功和评估完成返回都进入同一 `reduceSalesBack` 规则，先更新导航快照，再执行登记草稿清理等一次性副作用。

## 非路由 UI

- `AppUpdateDialog`：由 `MainApp` 在当前页面上方显示。
- 倒计时/权限/确认 Dialog：由各服务页面的状态控制，不是独立 route。
- `PhotoPreviewDialog`：`:core:ui` 的统一全屏预览。
- `CountdownAlarmActivity`：锁屏/全屏提醒 Activity，不属于 Compose NavHost。

## 内部验证入口

登录页长按 Logo 会在 Debug 和 Release 打开“功能验证”面板。它用于真实设备回归，不是普通产品入口：

- `FaceVerificationValidationActivity`
  - 使用生产 `DefaultFaceVerificationScreen` 和真实 API 路径。
  - 显示最终 JPEG 尺寸和字节数。
  - main Manifest 中 `exported=false`；Debug Manifest 叠加独立 launcher 并设为 `exported=true`。
- `NfcValidationActivity`
  - 同时验证原生 NFC foreground dispatch 和 R65C 外接读卡输入。
  - Debug/Release 均 `exported=false`，从隐藏面板进入。
- 面板还提供标准相机、腾讯兼容人脸和手动人脸采集快捷入口。
- 五个入口严格映射为正式人脸、NFC、标准相机、备用腾讯人脸和手动人脸采集；选择后先关闭面板，再只触发对应的 app-owned action。
- Release 不包含 debug mock interceptor 或 debug DI。

`scripts/quality/verify_release_validation_entry.sh` 锁定 feature main 源集中的共享入口、五动作 app 适配、Manifest 声明和 Release-safe 导航契约。登录纯 UI/URL/effect tests 归属 `feature/login/src/{test,androidTest}`，认证根栈和平台动作 tests 继续归属 `:app`。

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

“legacy UI”在本文中只表示页面仍位于 `:app/features/**`，不表示功能已废弃。登录页已归属 `:feature:login`，身份主页面与默认人脸核验 UI 已归属 `:feature:identification`；绝大多数其他 route-bound 页面仍由 `:app` 持有。`:feature:location` 没有独立页面，另外几个 Feature 主要持有动作接口、状态、用例或 delegate。

迁移 route 时必须保持：

1. route 类型和参数兼容。
2. `SavedStateHandle` 结果 key 与清理时机兼容。
3. Hilt ViewModel owner 和 Home 子图共享状态兼容。
4. 登录/退出、系统返回和进程重建行为兼容。
5. 本文、系统概览和相关测试同步更新。
