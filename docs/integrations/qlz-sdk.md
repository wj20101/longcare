# QLZ SDK 1.3.0.2 接入说明

Last verified: 2026-08-14

## 接入范围

- 客户端 SDK：`app/libs/qlzsdk-1.3.0.2-protobufLiteRelease-ui.aar`
- SDK 封装：`QlzSdkClient`
- Sale 数据链路：`:core:model` → `:core:domain` → `:core:data`
- SDK AAR SHA-256：
  `572294bb71c513a685aa4a62aea6a2589a2da4979c80cf4b658a32d09467c688`

选用厂商建议的 protobuf Lite 包，并按接入文档使用
`com.google.protobuf:protobuf-lite:3.0.1`。工程已有的 OkHttp 和 AppCompat 版本继续统一
由版本目录管理，未额外引入厂商文档中的旧版本。

## 临时联调配置

测试阶段在 `app/build.gradle.kts` 中统一固化测试 appKey：

`debug` 与验收用途的 `release` 构建当前都会将该 appKey 写入 `BuildConfig.QLZ_SDK_KEY`，并设置
`BuildConfig.QLZ_TEST_MODE=true`，保证线上打包环境不依赖本机 `local.properties` 或 CI
环境变量。测试完成、Sale 接口提供 SDK key 后，应删除这段固定配置，改为使用接口返回值
初始化 `QlzSdkClient`。

普通 `release` 默认按生产包校验，不再默认生成测试验收包。需要临时生成验收 Release 时，
必须同时显式传入 `-Prelease.production=false -Prelease.acceptance=true`。生产构建只要仍存在
固定测试 key、`QLZ_TEST_MODE=true`、QLZ 1.3.0.2 弱 TLS 实现或未兼容 16 KB 的腾讯人脸包，
构建门禁都会直接失败。

GitHub 的 `Android Release` 手动工作流提供 `release_mode` 选项。当前测试阶段默认选择
`acceptance`，工作流会自动传入上述两个 Gradle 参数，并在 APK、AAB、GitHub Release 名称中
标记为验收包。只有厂商 SDK 与服务端下发配置全部达到生产要求后，才可选择 `production`；
生产模式始终执行 `verify_vendor_sdk_release_readiness.sh`，不会绕过安全门禁。

`appSecret` 只允许配置在 LongCare 服务端。它用于俏郎中 OpenAPI 请求签名，不得写入
Android 源码、资源、BuildConfig 或 APK。客户端通过
`/V1/Sale/GetCheckToken` 获取一次性检测 Token。

## Sale 接口

| 方法 | 路径 | 客户端方法 |
|---|---|---|
| POST | `/V1/Sale/GetCheckToken` | `SaleRepository.getCheckToken` |
| POST | `/V1/Sale/AddUserLatent` | `SaleRepository.addUserLatent` |
| GET | `/V1/Sale/GetRecentUserLatentList` | `SaleRepository.getRecentUserLatentList` |
| GET | `/V1/Sale/ToDoNum` | `SaleRepository.getToDoCount` |
| GET | `/V1/Sale/ToDoList` | `SaleRepository.getToDoList` |
| POST | `/V1/Sale/SearchUserLatentList` | `SaleRepository.searchUserLatentList` |
| GET | `/V1/Sale/GetUserLatentDetail?id=...` | `SaleRepository.getUserLatentDetail` |

当前 Swagger 对 `liveLng` 的说明写作“纬度”、`liveLat` 写作“经度”，与通用命名习惯相反。
客户端不擅自互换字段，按接口字段名原样传递；服务端确认含义后再统一修订。

厂商 `/openapi/server/user/sync` 的实际必填字段名是 `outUserid`。共享文档中的
`outUserId` 示例会返回错误码 3001，因此服务端签名代理应严格使用 `outUserid`。

## SDK 调用链

1. 在主应用完成登录，确保 LongCare API 会话有效。
2. 查询或新增潜在客户，获得客户 ID。
3. 初始化 SDK 并读取 `CheckIml.getDeviceId()`。
4. 调用 `/V1/Sale/GetCheckToken`，传入客户 ID 和检测设备 ID。
5. 请求 Android 12+ 的 `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT` 运行时权限
   （Android 11 及以下请求精确位置权限）。
6. 调用 `SDKCall.openByToken(...)` 打开检测页面。
7. 通过 `QlzSdkEvent` 接收进度、取消、关闭、错误和报告成功事件。
8. 检测完成后重新查询 `/V1/Sale/GetUserLatentDetail?id=...`；“查看评估报告”只使用
   接口返回的 `pgUrl`（Swagger 定义为“评估Web地址”），并通过应用内 `WebViewRoute`
   加载，不再使用 SDK 回调 URL 打开厂商报告 Activity。

表单评估同样只使用 `/V1/Sale/AddUserLatent` 或
`/V1/Sale/GetUserLatentDetail` 返回的 `pgUrl`，通过标题为“表单评估”的应用内
`WebViewRoute` 加载。只有设备自动评估进入 QLZ SDK 页面；应用不再调用
`SDKCall.goResultAcitivty(...)` 打开表单或报告。

SDK Token 当前有效期由服务端 `expireAt` 决定，客户端没有写死 20 分钟。

## 联调运行

使用销售账号登录后，从客户详情或评估入口进入 SDK。为了避免测试入口影响正式业务流程，
工程不再提供独立 Launcher 联调 Activity：

```bash
./gradlew :app:assembleDebug -Pdebug.useMockData=false
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

联调前必须先完成主应用登录。真机还需要支持 BLE 的俏郎中
检测设备。模拟器只能验证页面、接口与错误回调，不能完成真实蓝牙检测。

## 安全与清单处理

- 覆盖 SDK 自带的全局明文网络配置，只对白名单中的俏郎中测试/报告域名允许 HTTP。
- 将 SDK 自带的外部 deep link Activity 改为 `exported=false`；当前接入只使用显式 SDK 调用。
- 旧版 `BLUETOOTH`、`BLUETOOTH_ADMIN` 权限限制到 API 30。
- SDK 仅在联调页或未来业务入口按需初始化，不在 Application 启动阶段读取设备标识。
- QLZ 1.3.0.2 内置的遥测链路存在弱 TLS 校验；Debug/验收联调可继续使用，但生产发布已由
  `verifyProductionReleaseConfiguration` 和 `verify_vendor_sdk_release_readiness.sh` 双重阻断，
  直到厂商提供修复版本。
