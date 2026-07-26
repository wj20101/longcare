# QLZ SDK 1.3.0.2 接入说明

Last verified: 2026-07-25

## 接入范围

- 客户端 SDK：`app/libs/qlzsdk-1.3.0.2-protobufLiteRelease-ui.aar`
- SDK 封装：`QlzSdkClient`
- Sale 数据链路：`:core:model` → `:core:domain` → `:core:data`
- Debug 联调入口：`QlzSdkDemoActivity`
- SDK AAR SHA-256：
  `572294bb71c513a685aa4a62aea6a2589a2da4979c80cf4b658a32d09467c688`

选用厂商建议的 protobuf Lite 包，并按接入文档使用
`com.google.protobuf:protobuf-lite:3.0.1`。工程已有的 OkHttp 和 AppCompat 版本继续统一
由版本目录管理，未额外引入厂商文档中的旧版本。

## 本地配置

在被 Git 忽略的根目录 `local.properties` 中配置：

```properties
QLZ_SDK_KEY=<测试 appKey>
QLZ_TEST_MODE=true
```

也可以使用同名 Gradle property 或环境变量覆盖。
生产构建使用独立的 `QLZ_PRODUCTION_SDK_KEY`，未配置时保持为空，避免测试 appKey
误连生产环境。

`appSecret` 只允许配置在 LongCare 服务端。它用于俏郎中 OpenAPI 请求签名，不得写入
Android 源码、资源、BuildConfig 或 APK。客户端通过
`/V1/Sale/GetCheckToken` 获取一次性检测 Token。

## Sale 接口

| 方法 | 路径 | 客户端方法 |
|---|---|---|
| POST | `/V1/Sale/GetCheckToken` | `SaleRepository.getCheckToken` |
| POST | `/V1/Sale/AddUserLatent` | `SaleRepository.addUserLatent` |
| GET | `/V1/Sale/GetRecentUserLatentList` | `SaleRepository.getRecentUserLatentList` |
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

SDK Token 当前有效期由服务端 `expireAt` 决定，客户端没有写死 20 分钟。

## 联调运行

Debug 构建会提供一个独立 Launcher 入口“QLZ SDK 联调”，Release 不包含该 Activity：

```bash
./gradlew :app:assembleDebug -Pdebug.useMockData=false
android run \
  --activity=com.ytone.longcare.features.qlz.demo.QlzSdkDemoActivity \
  --apks=app/build/outputs/apk/debug/app-debug.apk
```

联调前先从主应用登录；两个入口共享同一应用数据与登录会话。真机还需要支持 BLE 的俏郎中
检测设备。模拟器只能验证页面、接口与错误回调，不能完成真实蓝牙检测。

Debug Activity 也支持通过 Intent extras 注入短期联调 Token：
`qlz_check_token`、`qlz_token_type`、`qlz_expire_at`、`qlz_biz_type`。该入口只存在于
`src/debug`，用于服务端代理故障时隔离验证 SDK；不得作为生产 Token 获取方案。

## 安全与清单处理

- 覆盖 SDK 自带的全局明文网络配置，只对白名单中的俏郎中测试/报告域名允许 HTTP。
- 将 SDK 自带的外部 deep link Activity 改为 `exported=false`；当前接入只使用显式 SDK 调用。
- 旧版 `BLUETOOTH`、`BLUETOOTH_ADMIN` 权限限制到 API 30。
- SDK 仅在联调页或未来业务入口按需初始化，不在 Application 启动阶段读取设备标识。
