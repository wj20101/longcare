# QLZ SDK 1.3.0.5 接入说明

最后核对：2026-09-21（一次性凭证与正式环境配置；用户确认正式环境尚未部署，正式联调暂缓）

> 当前状态：QLZ 已移除测试模式覆盖，使用 AAR 默认正式环境及用户确认的现有 appKey。2026-09-19 接受的 QLZ 1.3.0.5 弱 TLS 及腾讯人脸 6.6.2 已知风险仍由 Release 报警；其他签名和质量检查仍阻断。环境切换不代表厂商风险已修复，也不等于完成正式服务联调。

2026-09-21 的 `df9451f2` Release 在 Pixel 10 / Android 17 上已通过测试客户登记、授权/扫描及取消后重新进入。连接 BMS105695 后出现设备未授权提示，应用映射对应固定 AAR `error_check_device_au = 21`，与 Token 失效 100/102 不同。用户随后确认正式环境尚未部署，正式联调暂缓，客户端保持当前正式配置、不切回测试环境。该提示只证明现场现象，不能据此确认厂商绑定或客户端 Token 复用问题；待正式环境部署后复验。尚未进入测量/上传，连续检测、GetCheckResult/报告及原记录换凭证恢复仍未验收。

## 接入范围

- 客户端 SDK：`app/libs/qlzsdk-1.3.0.5-protobufLiteRelease-ui.aar`
- SDK 封装：`QlzSdkClient`
- Sale 数据链路：`:core:model` → `:core:domain` → `:core:data`
- SDK AAR SHA-256：
  `5a0a5d647ceaf23d8660e4def556b6eb2caa73e3a0e2a0aa204bce69eb77b3ab`

选用厂商建议的 protobuf Lite AAR，厂商示例运行库基线为 4.28.3；本项目版本目录的
运行库现为 `com.google.protobuf:protobuf-javalite:4.36.2`，AAR 保持不变。该基线不是
已验证的精确 protoc 生成器版本。工程已有的 OkHttp 和 AppCompat 版本继续由版本目录管理。

Java Lite 不保证 API/ABI 稳定，因此升级必须测试 AAR 自带的真实消息与 Gzip 入口，
而非仅构建或 mock SDK 回调。`QlzProtobufCompatibilityTest` 在 JVM 和 Android 共用同一源文件，
使用合成记录覆盖嵌套采样、生理数据、旧版本 fixture、未知字段及损坏输入，不进入正式包。
4.28.3/4.36.2 的 JVM 专项、双向消息/Gzip 解析及 4.36.2 的 API 24/37 Debug 专项已通过；
另外，4.36.2 合法签名 Release（当时的 acceptance 模式）在 API 24/37 各通过 8 项实际 SDK 消息/Gzip
混淆专项。该组合另已完成授权真机 BLE 检测、上传、自动进入 H5、提交问卷、H5 返回与
原生接口等级展示验收；离线测试不替代后续 SDK/运行库升级时的真实链路复核。

默认 instrumentation 继续使用 Debug 和 AndroidJUnitRunner。仅做离线 QLZ 混淆专项时，
可显式启用 `-Ptest.qlzRelease=true`，测试源限定为共享消息代码和 `src/qlzReleaseTest/java`。
测试入口 `QlzReleaseTestRunner` 直接运行 JUnit，不初始化 AndroidX UI/Tracing；
不改变 Release 的签名、混淆或发布校验规则。构建需要合法签名：

```bash
./gradlew :app:assembleRelease :app:assembleReleaseAndroidTest \
  -Ptest.qlzRelease=true \
  -PLONGCARE_ALLOW_UNSIGNED_RELEASE=false -PALLOW_UNSIGNED_RELEASE=false
```

覆盖安装对应目标 APK 和测试 APK 后，运行：

```bash
adb -s DEVICE_SERIAL shell am instrument -w -r \
  com.ytone.longcare.test/com.ytone.longcare.integration.qlz.QlzReleaseTestRunner
```

完整 13 项 API 矩阵保留在 JVM/Debug。混淆包使用 `QlzReleaseProtobufTest` 的 8 项实际
SDK 消息/Gzip 合约，覆盖旧 fixture、嵌套字段、实例隔离、未知字段和错误输入；不要求 R8
为测试保留正式代码未使用的辅助 API，也不把 8 项结果冒称完整 13 项。原 AndroidJUnitRunner
需要的 Trace 方法及测试直接调用的部分 Protobuf 工厂方法在正式 R8 包中已被删除，故不适用
该场景。运行器必须报告非零用例、无跳过、无失败才成功；可加 `-e class java.lang.String`
做受控失败检查，预期 `FAILED` 且 `INSTRUMENTATION_CODE: 0`，不能只检查 adb 进程退出码。

同一离线测试源集提供 `NativeBridgeReleaseTest`，运行时指定 `-e class com.ytone.longcare.platform.webview.NativeBridgeReleaseTest`。它使用本地 HTML 调用目标 Release 中真实保留的 NativeBridge，验证数字/字符串客户 ID、非法参数和关闭交错；不增加正式包 keep 规则，不访问服务端。

设备验收须同时安装对应目标 APK 和测试 APK，确认消息/运行库来自目标包，不以测试包内副本
通过作为 R8 验收。个人设备只覆盖安装，不卸载或清数据，完成后恢复约定 Debug 包。

## 1.3.0.5 升级约定

- 不再调用 `SDKCall.openByToken(...)`。应用参考 Demo 的直接控制链路，以 Compose 实现扫描、连接、五指状态、进度、充电暂停、上传和错误恢复 UI。
- 初始化显式设置正常模式、竖屏、关闭自动断线续检，并启用实时检测数据回调；手动重连由当前 UI 会话触发，报告仍只使用业务接口返回的地址在应用内打开。
- Demo 同时提供 protobuf Java/Lite 两种 AAR；本项目只引入 Lite 版本，避免两套生成代码和运行时并存。
- Demo 的 `bugly_crash_release.jar` 不再复制；应用继续使用版本目录中的 Bugly Maven 依赖，避免重复类。
- 1.3.0.5 新增的生理数据 protobuf 字段由 SDK 内部处理，不改变当前应用侧回调模型。

## 正式环境配置

`app/build.gradle.kts` 保留用户确认可用于正式环境的现有 appKey，Debug 与 Release
统一通过 `BuildConfig.QLZ_SDK_KEY` 初始化；不依赖本机或 CI 的环境开关。
客户端不调用 `CheckIml.setTestMode` 或 `setLocalMode`，不提供失败回退测试环境逻辑。
固定 AAR 默认基地址为 `https://openapi.qiaolz.com`，检测上传为 `POST /sdk/assess/upload`。
LongCare API 地址不变，服务端正式 Token 配置已由用户确认，端到端可用性仍需真机验证。

正式包统一使用标准 `release`，不设置额外发布模式。测试模式和临时测试 key 的旧发布
输入已删除；QLZ 弱 TLS 和腾讯人脸已知风险仍明确告警，签名及其他质量检查保持不变。

GitHub 的 `Android Release` 手动工作流仅发布主应用 APK/AAB，生成正式 Release 并设为 Latest；
安装包不附加额外模式标签。独立助手及双包构建已退役，历史 Release 附件保持不变。工作流仍使用当前已接受的配置，并执行
`verify_vendor_sdk_release_readiness.sh` 报告剩余风险；缺失输入和未接受问题不自动放行。

`appSecret` 只允许配置在 LongCare 服务端。它用于俏郎中 OpenAPI 请求签名，不得写入
Android 源码、资源、BuildConfig 或 APK。客户端通过
`/V1/Sale/GetCheckToken` 获取一次性检测 Token。

## Sale 接口

| 方法 | 路径 | 客户端方法 |
|---|---|---|
| POST | `/V1/Sale/GetCheckToken` | `SaleRepository.getCheckToken` |
| POST | `/V1/Sale/GetCheckResult` | `SaleRepository.getCheckResult` |
| POST | `/V1/Sale/AddUserLatent` | `SaleRepository.addUserLatent` |
| GET | `/V1/Sale/GetRecentUserLatentList` | `SaleRepository.getRecentUserLatentList` |
| GET | `/V1/Sale/ToDoNum` | `SaleRepository.getToDoCount` |
| GET | `/V1/Sale/ToDoList` | `SaleRepository.getToDoList` |
| POST | `/V1/Sale/SearchUserLatentList` | `SaleRepository.searchUserLatentList` |
| GET | `/V1/Sale/GetUserLatentDetail?id=...` | `SaleRepository.getUserLatentDetail` |

2026-09-19 纯表单真机联调发现契约差异：Swagger 虽将 `GetCheckResult.recordId` 标为可空，但没有设备记录时，省略该字段或显式传 null 均返回业务码 2001“参数错误”。同一已完成问卷的最新客户详情可返回 `pgResult` 和 `pgUrl`。按用户确认，完成页有非空设备记录时使用 GetCheckResult，纯表单直接重新请求 GetUserLatentDetail；不使用缓存旧值、不在失败后切换接口兜底。

当前 Swagger 对 `liveLng` 的说明写作“纬度”、`liveLat` 写作“经度”，与通用命名习惯相反。
客户端不擅自互换字段，按接口字段名原样传递；服务端确认含义后再统一修订。

厂商 `/openapi/server/user/sync` 的实际必填字段名是 `outUserid`。共享文档中的
`outUserId` 示例会返回错误码 3001，因此服务端签名代理应严格使用 `outUserid`。

## SDK 调用链

1. 在主应用完成登录，确保 LongCare API 会话有效。
2. 查询或新增潜在客户，获得客户 ID。
3. 初始化 SDK 并读取 `CheckIml.getDeviceId()`。
4. 进入设备页只准备当前客户，不提前缓存 Token。
5. 用户点击搜索时，请求 Android 12+ 的 `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT` 及前台 `ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION` 权限
   （Android 11 及以下请求精确位置权限）。Manifest 未声明 `neverForLocation`，因此不能只授予附近设备权限；仅粗略定位也不能开始扫描。精确/粗略定位始终成对申请，拒绝后显示提示并允许重试。所有支持版本均检查系统定位服务开关，开启后重试重新检查；不新增后台定位或启动位置采集。
6. 运行条件满足后创建 UI 作用域的 `QlzEvaluationSession`；每次新授权前调用 `/V1/Sale/GetCheckToken`，传入当前客户 ID 和检测设备 ID。启动请求消费一次后交给 `CheckIml.startCheck(...)`；授权成功后使用 `ScanDeviceIml` 执行 30 秒有界扫描。
7. 页面只接收不可变的 `QlzEvaluationUiState`。蓝牙地址在 integration 边界内换成会话级不透明 ID，界面仅显示掩码；用户点选后由 `ConnectDeviceHelp` 连接，并映射五指、进度、电量、超时和掉线回调。
8. `onCheckEnd` 只触发一次 `sendData(...)`。纬度、经度和地址取自当前客户或本次登记的可靠字段，缺失时传空字符串；上传失败仅在内存中保留本次 `RecordInputData` 供重试。
9. 上传成功后关闭设备会话，直接进入原生评估结果页，以当前客户 ID 和本次 recordId 请求 `POST /V1/Sale/GetCheckResult`。客户、recordId 和完成状态通过 SavedStateHandle 保存；不自动打开 H5，不以客户详情中的旧地址代替结果查询。SDK URL 被忽略，不打开厂商报告 Activity。
10. 结果页展示本次响应 `pgResult`；用户点击“确认并提交评估结果”才打开 `pgUrl`，报告的 `window.NativeBridge.closeWebView()` 和系统返回都只关闭当前 H5，回到原结果页。查询失败、等级或地址未就绪时允许刷新同一接口，没有地址时报告按钮不可用；不重新上传、不读缓存旧值、不增加兜底链。结果页返回/完成只弹出当前页，回到上一有效来源页，不固定首页；已结束的设备/检测 entry 已被结果页替换。

纯表单流程保持不变：表单 H5 主动关闭后显示完成页并重新请求 `GET /V1/Sale/GetUserLatentDetail`；系统返回只回评估入口。报告/协议/隐私网页只关闭自身，所有网页都不使用返回结果邮箱或关联协议。成功弹窗确认仅刷新 H5，不要求调用关闭。

表单评估同样只使用 `/V1/Sale/AddUserLatent` 或
`/V1/Sale/GetUserLatentDetail` 返回的 `pgUrl`，通过应用内 `WebViewRoute` 加载。
表单和报告显式隐藏原生标题栏，仅保留 H5 自带标题/返回，填满系统安全内容区并避让键盘；协议和隐私页面保持原布局。表单、报告与协议共用统一关闭接口，展示开关不改变关闭用途。
设备自动评估也始终停留在应用自有页面；应用不调用
`SDKCall.openByToken(...)` 或 `SDKCall.goResultAcitivty(...)` 打开检测、表单或报告。

SDK Token 为一次性凭证，不能因为 `expireAt` 尚未到期而用于第二次授权；客户端不保存已消费 Token，不持久化或输出到日志。

### 检测展示与 H5 关闭契约

检测页按蓝湖三态显示单张白卡：手握线稿与五指接触灯、五指全绿的准备提示、沙漏与真实检测百分比。
五指全部接触且未收到有效进度时，`QlzGripPreparation` 以单调时钟展示 5 秒准备倒计时；
计时不会调用采集、上传或成功接口。`onCheckPro` 的有效总数到达后立即显示真实进度，优先于倒计时；
接触丢失、后台、断连、充电、错误和退出会取消准备，重组不会重新计时。
准备结束但尚无采样时显示等待进度，不虚构百分比。异常说明、重试与返回保持可用。
进度条为完整浅色底轨加比例填充，无分段间隙；0% 无绿色、100% 全覆盖，文字由整数采样数计算。

所有应用内 H5（包括表单、报告、协议及隐私网页弹窗）**主动调用**以下统一方法，通知客户端关闭当前 H5：

```javascript
window.NativeBridge.closeWebView();
```

客户端接收后通过当前 Navigation 3 entry 返回来源原生页面，保留首页和来源状态。
该调用无参数，由 H5 调用客户端；报告和普通网页只关闭自身，只有纯表单 H5 按业务约定以该调用进入完成页。
客户端不代替 H5 提交评估数据；设备上传后原生结果页直接查询 GetCheckResult，纯表单关闭后查询最新客户详情，不增加网页返回结果关联或二次完成确认。
容器在首次加载前通过 `addJavascriptInterface` 注册 `NativeBridge` 对象，
以 `@JavascriptInterface` 暴露 `closeWebView()` 和显式客户详情方法。客户端不注入 JS 包装、不检查现代消息桥能力，
不要求 H5 传凭证或协议字段；H5 自行决定按钮、弹窗、提交与关闭时机。
回调切回主线程后检查前台生命周期、当前 entry 和容器状态，成功关闭最多一次。释放时先使对象失效，
再移除接口并清理待执行回调。页面重组不重载当前网页，隐私网页只 dismiss 自身、不代表同意协议。
内部 H5 不设置额外 URL 白名单或导航/请求拦截，跨域跳转保留接口，普通加载失败不禁用关闭。
新增方法直接在 NativeBridge 中声明并添加注解，不使用独立关闭 Policy、注册表或分发器。
接口对所有页面及其 frame 可见，不认证调用来源；客户详情回调只在评估容器启用，不向 JS 返回客户资料，服务端继续负责客户访问授权，未来敏感方法须独立设计授权。
加载失败仍可系统返回（有原生栏的页面也保留原生返回），不放宽 TLS、文件访问或混合内容限制。
渲染进程退出时桥同步失效，销毁网页并显示原生异常提示，用户可返回后重新打开，不自动循环重载。

评估表单和报告可调用 `window.NativeBridge.enterUserDetails(pingguuserid)`，接受正整数或十进制字符串，ID 对应 `GetUserLatentDetail?id=...`。有效调用替换当前 H5 为客户详情；紧邻来源为同一客户详情时只关闭 H5 并刷新来源。其他有效页面保留，详情顶部/系统返回正常出栈，不固定跳首页。该调用不触发表单完成逻辑，不查询或修改来源结果。结果页和详情使用不同 entry 的 ViewModel，来源客户、recordId 和等级不被覆盖。

无效 ID、后台/失效容器及普通网页/隐私弹窗不执行客户跳转，也不消耗后续有效关闭机会；两个接口共享单次导航保护。不新增 JS 包装、H5 返回结果邮箱、路由分发框架或 URL 策略。

## 联调运行

使用销售账号登录后，从客户详情或评估入口进入自定义检测页面。为了避免测试入口影响正式业务流程，
工程不再提供独立 Launcher 联调 Activity：

```bash
./gradlew :app:assembleDebug -Pdebug.useMockData=false
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

联调前必须先完成主应用登录。真机还需要支持 BLE 的俏郎中
检测设备。模拟器只能验证页面、接口与错误回调，不能完成真实蓝牙检测。

### 服务端不可用时的 mock 验证

可直接运行测试源集中的 mock，无需登录或加载真实客户详情，也无需开启全局
`debug.useMockData`。`SalesMockEvaluationFlowTest` 使用内存中的客户详情、Token 和厂商
回调替身，串联真实 `SalesViewModel` 与 `QlzEvaluationSession`，覆盖上传后直接进入结果状态、
GetCheckResult 失败重试及上传去重；结果页测试覆盖报告未就绪和手动打开/返回。测试 URL 使用 `.invalid` 域名且不会发起网络请求。

`SalesEvaluationMockFlowTest` 使用真实会话状态机和 Compose 扫描/检测/完成组件，注入
厂商回调验证逐项接触状态、空扫描重试、后台停止扫描、连接异常重试、充电暂停、上传重试、
支付退出及退出后的迟到回调。测试宿主仅负责连接组件与会话，不替代正式入口导航、系统权限
弹窗、AAR 协议、真实上传和 WebView 报告验收；mock 不进入正式源码或 Release 包。

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.features.sales.SalesMockEvaluationFlowTest'

# 先启动隔离模拟器；ANDROID_SERIAL 改为该模拟器的实际序列号。
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.sales.SalesEvaluationMockFlowTest
```

测试结果输出至 `app/build/test-results/` 和 `app/build/outputs/androidTest-results/`；
这些 mock 验证不会把 OpenSpec 中的真实 BLE 验收项标记为完成。

`SalesEvaluationLiveResultTest` 是显式启用的只读联调测试：仅对已提交问卷的授权测试客户，使用真实登录态、SalesViewModel 和接口验证纯表单等级及完成页文案；不创建客户、不提交问卷、不打印报告 URL。须同时传入 `liveEvaluationCustomerId` 与 `liveEvaluationExpectedGrade`，默认跳过。该测试验证结果查询分支，不替代登记、H5 提交与返回的完整 UI 旅程。

`SalesEvaluationDesignTest` 提供三态确定性截图，`QlzGripPreparationTest` 用虚拟时间验证计时与取消。
`NativeWebViewCloseBridgeTest` 使用不启动 Activity 的真实 WebView 验证首次脚本可调用、重复/后台/失效回调、刷新与 frame 可见性；
`WebViewCloseBridgeTest` 验证统一容器、隐私同意不变、跨域加载及错误后关闭、重组不重载及 Navigation 3 返回，
测试均提供受控 HTTPS 内容，不关闭 TLS 校验，
不访问真实客户服务。真实 BLE 采样及服务端 H5 的域名、重定向、运行时兼容性仍需单独验收。

## 会话与错误处理

- `SalesSdkUiController` 在 `DEVICE_STATUS` 与 `EVALUATION_GUIDE` 之间持有同一会话；普通页面状态和 ViewModel 不保存 `Activity`、`BluetoothDevice`、MAC 全值或厂商可变回调对象。
- 每个进程最多持有一个活动检测租约。重复点击、迟到回调和旧 generation 不会创建第二条连接或覆盖新状态。
- QLZ 1.3.0.5 的 `ScanDeviceIml` 仅以弱引用保存内部回调，GC 后可能丢失设备列表及停止通知。
  `QlzVendorScanner` 在存活期间强持有该回调，重试时重新注册，关闭时停止扫描、清理引用并屏蔽迟到事件。
  此处依赖固定 AAR 的 `bluetoothLeScan.d`/注册方法；升级厂商 AAR 时必须重跑真实回调生命周期测试，
  不以 mock 状态机代替，也不添加反射兜底、轮询或自动无限重试。
- 离开活动评估页面、宿主销毁、取消或完成时，先使当前 generation 失效，再停止扫描、终止检测、调用 `ConnectDeviceHelp.onDestroy()` 并释放租约；即使 `stopScan()` 同步触发回调，也无法改写关闭后的状态。宿主进入后台时至少停止正在进行的扫描，Token 校验的迟到成功也不会在后台启动扫描。
- 初次开始、授权失败重试、取消后重进及宿主重建后的新会话均先获取新 Token；存活会话的普通扫描、重连不重新授权。获取中的重复点击合并，退出或切换客户使请求失效，迟到返回和旧请求收尾不能覆盖当前状态。
- ViewModel 用一个流程 Job 统一取消 Token 请求与待处理 SDK 事件，不另设流程序号或活动标记。启动请求按对象身份消费一次；Token loading 独立于其他操作的 loading，退出检测不改写其他操作状态。
- controller 只授权已准备好的会话，不在交付 Token 时隐式创建新会话；UI 在宿主 STARTED 后才消费待处理请求，后台暂存。session 统一通过 `authorize(token)` 根据已有状态执行授权或上传恢复，不保留多套启动入口或跨层传递恢复标记；活动检测、上传和终态拒绝重复授权。
- 每次检测最多自动恢复一次凭证。授权阶段失效使用新 Token 重建 driver；上传阶段厂商 401/2001 或 Token 失效回调则保留同一 driver、连接器与原 `RecordInputData`/recordId，重新授权成功后重传原记录，不重新测量。普通网络上传失败仍手动重传原记录，不刷新 Token。
- 新 Token 获取失败、空凭证、恢复授权失败或再次失效时停止自动操作，显示提示并保留退出入口；未退出前不主动清空待上传数据，不回退旧 Token。离线回归覆盖该调用链，但厂商正式服务是否接受同一记录换凭证重传仍须真实验收。
- 支付回调只显示阻断提示并中止本次检测，不自动打开 SDK 返回的支付 URL。厂商错误文本不会直接显示，所有错误按应用内固定分类映射。

## 安全与清单处理

- 覆盖 SDK 自带的全局明文网络配置，只对白名单中的俏郎中测试/报告域名允许 HTTP。
- 将 SDK 自带的外部 deep link Activity 改为 `exported=false`；当前接入只使用显式 SDK 调用。
- 旧版 `BLUETOOTH`、`BLUETOOTH_ADMIN` 权限限制到 API 30。
- SDK 仅在现有销售设备评估业务入口按需初始化，不在 Application 启动阶段读取设备标识。
- 自定义页面直接渲染会话状态；迁移期的 ViewModel 设备名/进度镜像、重复进度事件与厂商报告地址/分数字段已删除。完成事件仅携带 recordId；业务等级和报告仍由 GetCheckResult 提供。
- 已删除无生产调用的厂商 Activity 全局 WindowInsets 补丁、旧错误文案正则兜底及专属测试；AAR 中的 Activity 仍由 manifest merge 保留为不可导出组件，但业务路径不会启动它们。
- QLZ 1.3.0.5 内置遥测仍存在弱 TLS 校验。用户已接受当前风险，Release 检查明确告警；
  这不修复 SDK，也不免除其他签名、质量或业务验收。后续由厂商提供修复版本。
