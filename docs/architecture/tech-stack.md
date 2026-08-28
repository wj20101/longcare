# 技术栈与构建基线

最后核对：2026-08-28

本文是便于阅读的快照。版本发生冲突时，以 `constants.gradle.kts`、`gradle/libs.versions.toml`、`gradle-wrapper.properties` 和各模块 `build.gradle.kts` 为准。

## Android 与工具链

| 项目 | 当前值 | 事实来源 |
|---|---:|---|
| Application ID | `com.ytone.longcare` | `app/build.gradle.kts` |
| 版本 | `1.0.6 (58)` | `constants.gradle.kts` |
| `compileSdk` | 37 | `constants.gradle.kts` |
| `targetSdk` | 36 | `constants.gradle.kts` |
| `minSdk` | 24 | `constants.gradle.kts` |
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
| Navigation | Navigation Compose | 2.9.8 |
| Lifecycle | AndroidX Lifecycle | 2.11.0 |
| DI | Dagger Hilt / AndroidX Hilt | 2.60.1 / 1.4.0 |
| Persistence | Room | 2.8.4 |
| Preferences | DataStore | 1.2.1 |
| Background | WorkManager | 2.11.2 |
| Camera | CameraX | 1.6.1 |
| Face detection | ML Kit Face Detection | 16.1.7 |
| Network | Retrofit / OkHttp | 3.0.0 / 5.5.0 |
| Serialization | Moshi / kotlinx.serialization | 1.15.2 / 1.11.0 |
| Images | Coil | 3.5.0 |
| Async | kotlinx.coroutines | 1.11.0 |
| Location | AMap Location | 11.2.100 |
| Object storage | Tencent COS Android | 5.9.52 |
| Diagnostics | Tencent Bugly CrashReport | 4.1.9.3 |
| Performance | Baseline Profile / Macrobenchmark | 1.5.0-rc01 |

## 本地 AAR 与兼容配置

| 组件 | 当前来源 | 说明 |
|---|---|---|
| QLZ | `app/libs/qlzsdk-1.3.0.2-protobufLiteRelease-ui.aar` | 正式销售评估必需；文件名/SHA-256 受门禁保护，依赖旧版 `protobuf-lite:3.0.1`；内部 TLS finding 为已登记厂商风险而非 QLZ production blocker |
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

## App 构建变体

Android CLI 当前识别以下 app 变体：

| 变体 | 用途 | 关键差异 |
|---|---|---|
| `debug` | 日常开发与联调 | 可用 `debug.useMockData` 切换本地 mock；默认仓库配置为 `false` |
| `release` | 签名、压缩和资源收缩的发布包 | 默认按生产模式校验；QLZ 检查项目可控配置/批准 AAR，其他独立厂商问题仍可阻断 |
| `nonMinifiedRelease` | Baseline Profile 目标变体 | 由 Baseline Profile 插件创建 |
| `benchmarkRelease` | Macrobenchmark/Profile 验证 | 由性能插件创建 |

默认仅打包 `arm64-v8a`。运行 Baseline Profile 的 x86_64 环境可显式传入 `-Pbaseline.enableX86_64=true`。

## 重要构建开关

| 配置 | 默认值/行为 |
|---|---|
| `debug.useMockData` | 仓库中为 `false`；传 `true` 使用 `app/src/debug/assets/mock` |
| `release.production` | 默认为 `true` |
| `release.acceptance` | 默认为 `false`；非生产 Release 必须显式设为 `true` |
| `QLZ_SDK_KEY` | Gradle property 优先、同名环境变量回退；Debug 可为空，Acceptance/Production 必须非空 |
| `QLZ_TEST_MODE` | 默认为 `false`；Acceptance 必须显式为 `true`，Production 产物强制为 `false`且拒绝 true 请求 |
| `baseline.enableX86_64` | 默认为 `false` |

生产 Release 需要 LongCare Release 签名配置。缺少签名时不会静默使用 debug keystore；只有明确的受控环境可以通过专用开关允许 unsigned/debug fallback。

## 常用命令

```bash
android describe --project_dir=.
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
bash scripts/quality/preflight_local.sh --full
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

生产/验收包的具体门禁和已知阻断见 [CI 与质量门禁](ci-quality-gates.md)；QLZ 专项配置见 [QLZ SDK 接入](../integrations/qlz-sdk.md)。
