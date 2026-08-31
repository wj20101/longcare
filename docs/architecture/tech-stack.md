# 技术栈与构建基线

最后核对：2026-08-31

本文是便于阅读的快照。版本发生冲突时，SDK 以 `settings.gradle.kts` 为准，JDK/应用版本以 `constants.gradle.kts` 为准，依赖与插件以 `gradle/libs.versions.toml` 为准，Gradle 以 `gradle-wrapper.properties` 为准。

## Android 与工具链

| 项目 | 当前值 | 事实来源 |
|---|---:|---|
| Application ID | `com.ytone.longcare` | `app/build.gradle.kts` |
| 版本 | `1.0.6 (58)` | `constants.gradle.kts` |
| `compileSdk` | 37 | `settings.gradle.kts` |
| `targetSdk` | 36 | `settings.gradle.kts` |
| `minSdk` | 24 | `settings.gradle.kts` |
| JDK / JVM toolchain | 21 | `constants.gradle.kts`、约定插件 |
| Gradle Wrapper | 9.7.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.3.2 | `gradle/libs.versions.toml` |
| Kotlin | 2.4.10 | `gradle/libs.versions.toml` |
| KSP | 2.3.11 | `gradle/libs.versions.toml` |

## 主要库

| 领域 | 组件 | 版本 |
|---|---|---:|
| UI | Jetpack Compose BOM | 2026.08.00 |
| UI | Material 3 / Adaptive Navigation Suite | 由 Compose BOM 管理 |
| Navigation | Navigation Compose | 2.10.0 |
| Lifecycle | AndroidX Lifecycle | 2.11.0 |
| DI | Dagger Hilt / AndroidX Hilt | 2.60.1 / 1.4.0 |
| Persistence | Room | 2.8.4 |
| Preferences | DataStore | 1.2.1 |
| Background | WorkManager | 2.11.2 |
| Camera | CameraX | 1.6.2 |
| Face detection | ML Kit Face Detection | 16.1.7 |
| Network | Retrofit / OkHttp | 3.0.0 / 5.5.0 |
| Serialization | Moshi / kotlinx.serialization | 1.15.2 / 1.11.0 |
| Images | Coil | 3.6.0 |
| Date/time | kotlinx-datetime | 0.8.0 |
| Async | kotlinx.coroutines | 1.11.0 |
| Location | AMap Location | 11.2.100 |
| Object storage | Tencent COS Android | 5.9.52 |
| Diagnostics | Tencent Bugly CrashReport | 4.1.9.3 |
| Performance | Baseline Profile / Macrobenchmark | 1.5.0-rc02 / 1.5.0-rc02 |

## 本地 AAR 与兼容配置

| 组件 | 当前来源 | 说明 |
|---|---|---|
| QLZ | `app/libs/qlzsdk-1.3.0.2-protobufLiteRelease-ui.aar` | 依赖旧版 `protobuf-lite:3.0.1`；当前只允许 Debug/验收用途 |
| 腾讯人脸 Live | `WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc.aar` | 默认本地 AAR，可通过 Gradle 属性切到私有 Maven |
| 腾讯人脸 Normal | `WbCloudNormal-v5.1.10-4e3e198.aar` | 与 Live SDK 一起由约定插件装配 |

QLZ、腾讯人脸和腾讯 COS 仍引用旧 support library 类，因此 `android.enableJetifier=true` 暂时不能删除。切换到 AndroidX-only 厂商包后应重新跑 Lint、SDK 回归和生产发布门禁，再移除 Jetifier。

腾讯人脸依赖来源由以下配置控制：

- `TX_FACE_SDK_SOURCE=local|maven`
- `TX_FACE_LIVE_COORD`、`TX_FACE_NORMAL_COORD`
- `TX_FACE_MAVEN_REPO_URL` 及可选仓库凭据
- `TX_FACE_INCLUDE_MAVEN_LOCAL=true` 仅用于明确的本地发布验证

## 模块与构建逻辑

项目包含 13 个 Gradle 模块：

- 应用/测试：`:app`、`:baselineprofile`
- Core：`:core:model`、`:core:domain`、`:core:data`、`:core:ui`、`:core:common`
- Feature：`:feature:login`、`:feature:home`、`:feature:identification`、`:feature:location`、`:feature:photoupload`、`:feature:servicecountdown`

`build-logic` 是 included build，提供 application、library、Kotlin 公共配置，以及 Release 签名和腾讯人脸依赖来源约定。版本目录统一管理 Maven 依赖；业务模块不应自行声明版本号。

三个 Android SDK 值由 `com.android.settings` 统一下发，插件版本必须与 AGP `9.3.2` 相同；模块和 convention plugin 不得再次覆盖 SDK。JDK 21 与应用版本继续由 `constants.gradle.kts` 单独管理。

平台验证按目的分离：`pixel6Api33` 生成 Baseline/Startup Profile，并验证六场景旅程、依赖链和 Macrobenchmark 报告格式；其模拟器耗时不得用于收益结论。`pixel6Api36` 执行正式 target 的阻断式 smoke，`pixelTabletApi37` 只用于 Android 17 Beta readiness。API 33 或 API 36 成功不能授权 target 37。

## App 构建变体

Android CLI 当前识别以下 app 变体：

| 变体 | 用途 | 关键差异 |
|---|---|---|
| `debug` | 日常开发与联调 | 可用 `debug.useMockData` 显式切换第一方本地 mock；默认仓库配置与代码 fallback 均为 `false` |
| `release` | 签名、压缩和资源收缩的发布包 | 默认按生产模式校验；当前已知厂商问题会阻断生产构建 |
| `nonMinifiedRelease` | Baseline/Startup Profile 生成目标 | 由 Baseline Profile 插件创建；绑定仅性能变体可见的确定性状态控制器 |
| `benchmarkRelease` | Macrobenchmark/Profile 验证 | 由性能插件创建；与 `nonMinifiedRelease` 共用 `src/profile` 状态边界和本地性能签名 |

默认仅打包 `arm64-v8a`。运行 Baseline Profile 的 x86_64 环境可显式传入 `-Pbaseline.enableX86_64=true`。

## 重要构建开关

| 配置 | 默认值/行为 |
|---|---|
| `debug.useMockData` | 仓库与代码 fallback 均为 `false`；传 `true` 使用 `app/src/debug/assets/mock`，未知第一方路由抛出 `MissingMockRouteException` 而不访问网络 |
| `release.production` | 默认为 `true` |
| `release.acceptance` | 默认为 `false`；非生产 Release 必须显式设为 `true` |
| `baseline.enableX86_64` | 默认为 `false` |

生产 Release 需要 LongCare Release 签名配置。缺少签名时不会静默使用 debug keystore；只有明确的受控环境可以通过专用开关允许 unsigned/debug fallback。

Debug Mock 的最终值由一个 Gradle Provider 解析并生成 `BuildConfig.USE_MOCK_DATA`。`:app:reportDebugMockMode` 输出最终布尔值及属性来源，`:app:verifyDebugMockMode` 可配合 `-Pdebug.expectedUseMockData=<true|false>` 阻止命令行意图与产物不一致。Mock 路由表、fixture、场景 provider 与上传 fake 只存在于 Debug source set；Release 将该字段硬编码为 `false` 并绑定真实上传器。

## Profile 与启动指标基线

- 四个 Startup 场景：首次隐私协议、已同意隐私且未登录、护理 Home、销售 Home。
- 两个 Baseline-only 场景：护理服务记录往返、销售客户列表往返。
- `startup-prof.txt` 必须是 `baseline-prof.txt` 的严格规则子集；规则身份比较忽略 ART 的 `H/S/P` 使用标志差异，但不忽略类/方法身份。
- Macrobenchmark 对每个 Startup 场景以相同 helper 对称运行 `CompilationMode.None` 和 `Partial(BaselineProfileMode.Require)`，固定 `StartupMode.COLD`、每模式 10 次，并要求 `timeToInitialDisplayMs` 与 `timeToFullDisplayMs` 都存在。
- 性能状态控制器和离线保护只编译进 `nonMinifiedRelease`、`benchmarkRelease`，正式 Release 产物守卫检查组件、permission、fixture token 和离线标记均不存在。
- API 33 managed device 是旅程/依赖/报告格式证据；真实收益必须来自同一台受控多核 `arm64-v8a` 真机的至少两轮完整比较，当前收益状态为 `unverified`。
- 统一验收配置为 `scripts/quality/real_device_acceptance.json`：真实结论只接受 API 36 physical、`arm64-v8a`、至少 2 核、电量至少 50%、未充电且热状态不高于 light 的显式设备；API 28 与模拟器只允许诊断。
- `run_real_device_acceptance.sh` 绑定一次 execution 的 minified acceptance APK/AAB、R8 mapping、benchmark 目标/测试 APK 与 Profile 文本哈希。`r8RuntimeAcceptance` 和 `startupProfileBenefit` 独立聚合，前者不代表 Profile 收益，后者不代表 production readiness。
- 报告 schema、脱敏日志、每轮原始/归一化 JSON、中位数和 comparator 结果只写入 `build/reports/real-device-acceptance/`；账号、验证码、Token、手机号、身份证、照片、原始 serial、本机绝对路径和完整 URL query 均禁止进入报告。

## 常用命令

```bash
android describe --project_dir=.
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
bash scripts/quality/preflight_local.sh --full
android run --apks=app/build/outputs/apk/debug/app-debug.apk
# 真机长任务的子命令和必要显式参数
bash scripts/quality/run_real_device_acceptance.sh benchmark-round --help
```

生产/验收包的具体门禁和已知阻断见 [CI 与质量门禁](ci-quality-gates.md)；QLZ 专项配置见 [QLZ SDK 接入](../integrations/qlz-sdk.md)。
