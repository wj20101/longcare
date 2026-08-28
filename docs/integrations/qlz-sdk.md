# QLZ SDK 1.3.0.2 接入说明

最后核对：2026-08-28

> 当前状态：QLZ 1.3.0.2 AAR 是销售设备评估在 Debug、Acceptance 和 Production 中都必须保留的厂商依赖。项目侧 QLZ production readiness 只检查正式配置、批准制品、接入边界和业务回归；AAR 内部已登记的 TLS finding 属厂商责任和已接受外部风险，不伪称已修复，也不单独阻断 QLZ 正式打包。腾讯人脸、签名等独立发布门禁仍可阻断整体 Production。

## 接入范围

- 客户端 SDK：`app/libs/qlzsdk-1.3.0.2-protobufLiteRelease-ui.aar`
- SDK 封装：`QlzSdkClient`
- Sale 数据链路：`:core:model` → `:core:domain` → `:core:data`
- SDK AAR SHA-256：
  `572294bb71c513a685aa4a62aea6a2589a2da4979c80cf4b658a32d09467c688`

选用厂商建议的 protobuf Lite 包，并按接入文档使用
`com.google.protobuf:protobuf-lite:3.0.1`。工程已有的 OkHttp 和 AppCompat 版本继续统一
由版本目录管理，未额外引入厂商文档中的旧版本。

## 受控构建配置

QLZ SDK key 和测试模式只从同名 Gradle property 或 CI environment 读取；Gradle property
优先于环境变量，字符串会先 `trim`。仓库没有固定测试 key，也没有 production fallback：

| 用途 | `QLZ_SDK_KEY` | `QLZ_TEST_MODE` | Release 模式 |
|---|---|---|---|
| Debug | 可不提供；不影响编译，进入真实 QLZ 能力时返回配置错误 | 默认 `false`，联调时可显式设为 `true` | 不适用 |
| Acceptance | 必须提供独立验收 key | 必须显式为 `true` | `release.production=false`、`release.acceptance=true` |
| Production | 必须提供非空正式 key，已知测试值会失败 | 产物强制为 `false`，任何 `true` 请求也会被门禁拒绝 | `release.production=true`、`release.acceptance=false` |

本机联调推荐使用未纳入仓库的用户级 Gradle property；也可在当前命令的环境中传入。示例只使用变量名，不包含真实值：

```bash
# Debug：不配置也应能编译
./gradlew :app:assembleDebug

# 显式验收 Release
QLZ_SDK_KEY="$QLZ_ACCEPTANCE_SDK_KEY" QLZ_TEST_MODE=true \
  ./gradlew :app:assembleRelease \
  -Prelease.production=false \
  -Prelease.acceptance=true

# 正式 Release；其他独立发布门禁仍必须通过
QLZ_SDK_KEY="$QLZ_PRODUCTION_SDK_KEY" QLZ_TEST_MODE=false \
  ./gradlew :app:assembleRelease :app:bundleRelease \
  -Prelease.production=true \
  -Prelease.acceptance=false
```

GitHub `Android Release` 使用两个分离的 environment secrets：
`QLZ_ACCEPTANCE_SDK_KEY` 和 `QLZ_PRODUCTION_SDK_KEY`。工作流按明确模式选择其一，并只在
Release 构建步骤内映射为 `QLZ_SDK_KEY`；日志和门禁只报告是否缺失，不打印实际值。
Acceptance 产物的 APK、AAB 和 GitHub Release 名称必须保留验收标识，不得冒充生产包。

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
- QLZ 1.3.0.2 内置链路的弱 TLS finding 仍然存在，责任归厂商。当前 AAR 因正式销售业务
  必需而被明确接受为外部风险；`verify_vendor_sdk_release_readiness.sh` 会报告该事实但不把它
  加入 violation 集合，文档和门禁不得写成“已修复”。
- `verifyProductionReleaseConfiguration` 只阻断项目可控条件：缺正式 key、已知测试 key、
  production 测试模式、模式冲突、AAR 缺失或摘要变化，以及腾讯人脸等独立根因。
- `verify_qlz_sdk_artifact.sh` 固定当前文件名和 SHA-256，拒绝缺失、重命名、重新打包、摘要变化
  或第二份本地 QLZ AAR，并检查 APK/AAB 中仍保留业务入口类。
- 应用代码只通过 `integration/qlz` 内的 app-owned facade 调用厂商公开 API；不得用设备 ID
  注入、网络 Hook、反射、二进制修补或未公开 API 改变 AAR 行为。
- 厂商提供新 AAR/正式说明、法规要求变化或出现真实业务影响时，必须重新评估版本、摘要、
  权限、网络、Lint 和真机链路，再通过独立 change 决定是否调整。

## Android 平台约束与后续复核

以下约束已使用 Android CLI 核对当前官方文档：[Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)、
[Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)、
[Unsafe X509TrustManager](https://developer.android.com/privacy-and-security/risks/unsafe-trustmanager)、
[Unsafe HostnameVerifier](https://developer.android.com/privacy-and-security/risks/unsafe-hostname)和
[Cleartext communications](https://developer.android.com/privacy-and-security/risks/cleartext-communications)。

- Android 12（API 31）及以上扫描和连接 BLE 设备前分别请求
  `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT` 运行时权限；旧 `BLUETOOTH`、
  `BLUETOOTH_ADMIN` 只声明到 API 30。Android 11 及以下扫描继续按当前业务请求位置权限。
- 不在缺少厂商真机证据时为扫描权限增加 `neverForLocation`。官方文档明确说明该标志可能
  过滤部分 BLE beacon；项目本身也有独立定位业务，不能把权限变化只按 QLZ 推断。
- 当前 AAR 验收覆盖 API 30 及以下与 API 31 及以上的拒权、随后授权和重新进入；拒权时不得
  打开 SDK 页面。AAR 额外合并权限的最小化仍按独立 change 管理，不在本变更中顺带修改。
- production 的 Network Security Configuration 继续保持 base cleartext 关闭，只保留当前
  明确列出的 QLZ 域例外。域名或协议变化必须复核；这一项目侧配置不代表厂商内部 TLS 已修复。
- 项目不新增空实现 `X509TrustManager`、恒真 `HostnameVerifier`、跳过主机校验或自定义弱 TLS
  回退，也不通过网络 Hook 尝试修补闭源 AAR。未知 QLZ 文件或应用源码中的同类 finding 仍会
  被 Lint source-scoped waiver 负向检查阻断。
