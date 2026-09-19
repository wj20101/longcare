# 技术栈与构建基线

最后核对：2026-09-19

本文是便于阅读的快照。版本发生冲突时，以 `constants.gradle.kts`、`gradle/libs.versions.toml`、`gradle-wrapper.properties` 和各模块 `build.gradle.kts` 为准。

## Android 与工具链

| 项目 | 当前值 | 事实来源 |
|---|---:|---|
| Application ID | 正式 `com.ytone.longcare`；助手 `com.ytone.longcare.assistant` | 两个 application module 的 build.gradle.kts |
| 版本 | `1.0.6 (59)` | `constants.gradle.kts` |
| `compileSdk` | 37 | `constants.gradle.kts` |
| `targetSdk` | 36 | `constants.gradle.kts` |
| `minSdk` | 24 | `constants.gradle.kts` |
| JDK / JVM toolchain | 21 | `constants.gradle.kts`、约定插件 |
| Gradle Wrapper | 9.7.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.4.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.4.10 | `gradle/libs.versions.toml` |
| KSP | 2.3.11 | `gradle/libs.versions.toml` |

## 主要库

| 领域 | 组件 | 版本 |
|---|---|---:|
| UI | Jetpack Compose BOM | 2026.08.00 |
| UI | Material 3 / Adaptive Navigation Suite | 由 Compose BOM 管理 |
| Navigation | Navigation 3 runtime / ui | 1.1.7 |
| Navigation state | Lifecycle ViewModel Navigation 3 decorator | 2.11.0 |
| Compose DI | Hilt lifecycle-viewmodel-compose（无 Navigation 2 依赖） | 1.4.0 |
| Lifecycle | AndroidX Lifecycle | 2.11.0 |
| DI | Dagger Hilt / AndroidX Hilt | 2.60.1 / 1.4.0 |
| Persistence | Room | 2.8.4 |
| Preferences | DataStore | 1.2.1 |
| Background | WorkManager | 2.11.2 |
| Camera | CameraX | 1.6.1 |
| Face detection | ML Kit Face Detection | 16.1.7 |
| Network | Retrofit / OkHttp | 3.0.0 / 5.5.0 |
| Network I/O | Okio | 3.18.2 |
| WebView | AndroidX WebKit（渲染进程异常保护；关闭接口直接注册 NativeBridge） | 1.17.0 |
| Serialization | Moshi / kotlinx.serialization | 1.15.2 / 1.11.0 |
| Images | Coil | 3.6.3 |
| Async | kotlinx.coroutines | 1.11.0 |
| Location | AMap Location | 11.2.100 |
| Object storage | Tencent COS Android | 5.9.52 |
| Diagnostics | Tencent Bugly CrashReport | 4.1.9.3 |
| Performance | Baseline Profile / Macrobenchmark | 1.5.0-rc01 |

## 本地 AAR 与兼容配置

| 组件 | 当前来源 | 说明 |
|---|---|---|
| QLZ | `app/libs/qlzsdk-1.3.0.5-protobufLiteRelease-ui.aar` | 依赖 `protobuf-javalite:4.28.3`；当前只允许 Debug/验收用途 |
| 腾讯人脸 Live | `WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc.aar` | 默认本地 AAR，可通过 Gradle 属性切到私有 Maven |
| 腾讯人脸 Normal | `WbCloudNormal-v5.1.10-4e3e198.aar` | 与 Live SDK 一起由约定插件装配 |

QLZ、腾讯人脸和腾讯 COS 仍引用旧 support library 类，因此 `android.enableJetifier=true` 暂时不能删除。切换到 AndroidX-only 厂商包后应重新跑 Lint、SDK 回归和生产发布门禁，再移除 Jetifier。

WebKit 1.17.0 的渲染退出检查器会误报已实现回调的父类构造调用；经审查及确认，仅
`ManagedWebViewClient` 使用带原因说明的局部 `MissingOnRenderProcessGone` 豁免。
两个网页容器的异常释放与原生返回已有测试；全局 Lint allowlist 不变，检查器修复后删除该注解。

腾讯人脸依赖来源由以下配置控制：

- `TX_FACE_SDK_SOURCE=local|maven`
- `TX_FACE_LIVE_COORD`、`TX_FACE_NORMAL_COORD`
- `TX_FACE_MAVEN_REPO_URL` 及可选仓库凭据
- `TX_FACE_INCLUDE_MAVEN_LOCAL=true` 仅用于明确的本地发布验证

## 模块与构建逻辑

项目包含 17 个 Gradle 模块：

- 应用/测试：`:app`、`:assistant`、`:baselineprofile`
- Core：`:core:model`、`:core:domain`、`:core:data`、`:core:ui`、`:core:common`
- Feature：`:feature:login`、`:feature:home`、`:feature:identification`、`:feature:location`、`:feature:photoupload`、`:feature:servicecountdown`

- Integration：`:integration:txface`；`:integration:txface-live` / `:integration:txface-normal` 为本地 AAR 的纯 artifact wrapper（不产 APK）。AAR 二进制仍在 `app/libs`，依赖只由集成模块拥有，避免 AGP 禁止 Android library 直接打包本地 AAR 的限制。

`build-logic` 是 included build，提供 application、library、Kotlin 公共配置，以及 Release 签名和腾讯人脸依赖来源约定。签名兼容插件与 `longcare.tencent-face` 职责分离，后者只用于集成 library。版本目录统一管理 Maven 依赖；业务模块不应自行声明版本号。

约定插件自身也固定使用 JDK 21 toolchain，确保单独执行 `./gradlew -p build-logic test` 时不会因 Android Studio 的更高版本 JDK 生成主构建无法加载的字节码。

## App 构建变体

Android CLI 当前识别以下 app 变体：

| 变体 | 用途 | 关键差异 |
|---|---|---|
| `debug` | 日常开发与联调 | 可用 `debug.useMockData` 切换本地 mock；默认仓库配置为 `false` |
| `release` | 签名、压缩和资源收缩的发布包 | 默认按生产模式校验；当前已知厂商问题会阻断生产构建 |
| `nonMinifiedRelease` | Baseline Profile 目标变体 | 由 Baseline Profile 插件创建 |
| `benchmarkRelease` | Macrobenchmark/Profile 验证 | 由性能插件创建 |

默认仅打包 `arm64-v8a`。运行 Baseline Profile 的 x86_64 环境可显式传入 `-Pbaseline.enableX86_64=true`。

助手只提供 Debug/Release，始终关闭 mock，与正式 App 共享基础版本、versionCode 及签名校验，助手 versionName 追加 `-assistant`；不应用 Baseline Profile 插件。统一命令为 `bash scripts/release/build-dual-apks.sh --debug|--acceptance`（选择其一），输出 `build/outputs/dual-apk/<mode>/` 中两份版本化 APK、校验和和元数据。

## 重要构建开关

| 配置 | 默认值/行为 |
|---|---|
| `debug.useMockData` | 仓库中为 `false`；传 `true` 使用 `app/src/debug/assets/mock` |
| `release.production` | 默认为 `true` |
| `release.acceptance` | 默认为 `false`；非生产 Release 必须显式设为 `true` |
| `baseline.enableX86_64` | 默认为 `false` |

生产 Release 需要 LongCare Release 签名配置。缺少签名时不会静默使用 debug keystore；双 APK 验收脚本还显式禁用 unsigned/debug fallback。

## 常用命令

```bash
android describe --project_dir=.
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
bash scripts/quality/preflight_local.sh --full
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

生产/验收包的具体门禁和已知阻断见 [CI 与质量门禁](ci-quality-gates.md)；QLZ 专项配置见 [QLZ SDK 接入](../integrations/qlz-sdk.md)。
