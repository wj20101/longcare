# Tech Stack

## Languages & Frameworks

| Technology | Version | Purpose |
|---|---|---|
| Kotlin | 2.3.20 | Android 主语言 |
| JDK Toolchain | 21 | Gradle / Kotlin 编译工具链 |
| Android Gradle Plugin | 9.1.0 | Android 构建系统 |
| Jetpack Compose | BOM 2026.03.00 | 主要 UI 技术栈 |
| Hilt | Dagger 2.59.2 / AndroidX Hilt 1.3.0 | 依赖注入 |
| Room | 2.8.4 | 本地数据库 |
| WorkManager | 2.11.1 | 后台任务与启动任务 |

## Android Baseline

| Item | Value | Notes |
|---|---|---|
| `compileSdk` | 37 | 来自 [constants.gradle.kts](/Users/wajie/StudioProjects/longcare/constants.gradle.kts) |
| `targetSdk` | 36 | 当前目标 SDK |
| `minSdk` | 24 | 最低支持版本 |
| Application ID | `com.ytone.longcare` | 来自 [app/build.gradle.kts](/Users/wajie/StudioProjects/longcare/app/build.gradle.kts) |
| Current version | `1.0.2 (24)` | 来自 [constants.gradle.kts](/Users/wajie/StudioProjects/longcare/constants.gradle.kts) |

## Project Modules

| Module | Responsibility |
|---|---|
| `:app` | 应用壳层、导航组装、Android 组件声明、部分历史业务实现 |
| `:baselineprofile` | 启动与性能基线生成 |
| `:core:model` | 通用模型、值对象 |
| `:core:domain` | Repository 接口、领域契约、规则 |
| `:core:data` | 数据实现、网络、数据库、DI 绑定 |
| `:core:ui` | 通用 UI、共享 ViewModel、权限工具等 |
| `:core:common` | 日志、安全、工具、错误模型等基础能力 |
| `:feature:login` | 登录能力 |
| `:feature:home` | 首页入口能力 |
| `:feature:identification` | 身份识别相关能力 |
| `:feature:location` | 定位能力 |
| `:feature:photoupload` | 拍照上传能力 |
| `:feature:servicecountdown` | 服务倒计时能力 |

## Key Dependencies

| Package | Version | Rationale |
|---|---|---|
| Retrofit | 3.0.0 | HTTP API 接入 |
| OkHttp | 5.3.2 | 网络请求与拦截器 |
| Moshi | 1.15.2 | JSON 编解码 |
| kotlinx-serialization-json | 1.10.0 | 部分序列化链路 |
| DataStore Preferences | 1.2.1 | 本地轻量配置存储 |
| CameraX | 1.5.3 | 相机采集与拍照能力 |
| Coil 3 | 3.4.0 | 图片加载 |
| AMap Location | 11.1.000 | 定位服务 |
| Tencent COS Android | 5.9.50 | 文件上传 |
| Bugly CrashReport | 4.1.9.3 | 崩溃上报 |

## Build Logic

| Component | Purpose | Notes |
|---|---|---|
| `build-logic` includeBuild | 约定插件 | 统一 application/library/Kotlin 编译设置 |
| `longcare.android.application` | app 插件封装 | 当前较轻，主要转发 AGP application |
| `longcare.android.library` | library 插件封装 | 统一 `compileSdk/minSdk/JDK` |
| `longcare.kotlin.common` | Kotlin 公共编译参数 | 统一 JVM target |

## Infrastructure

| Component | Choice | Notes |
|---|---|---|
| Source Control | GitHub | 当前分支观察为 `master`，远端同步 |
| CI | GitHub Actions | affected scope、质量门禁、产物上传 |
| Release | GitHub Actions + GitHub Release | 支持 tag/manual 触发 |
| Local Mocking | `app/src/debug/assets/mock` + debug interceptor | Debug 环境可切 mock 数据 |

## Dev Tools

| Tool | Purpose | Config / Entry |
|---|---|---|
| Gradle Wrapper | 构建入口 | [gradlew](/Users/wajie/StudioProjects/longcare/gradlew) |
| 质量门禁脚本 | 统一质量检查入口 | [run_quality_gate.sh](/Users/wajie/StudioProjects/longcare/scripts/quality/run_quality_gate.sh) |
| 架构边界守卫 | 防止跨层依赖回退 | `scripts/quality/verify_architecture_boundaries.sh` |
| CI Workflow 质量守卫 | 校验 workflow 关键约束 | `scripts/quality/verify_ci_workflow_quality.sh` |
| Baseline 采集 | 记录构建/性能基线 | `scripts/quality/collect_build_baseline.sh` |

## Observed Risks

- `:app` 仍有大量业务代码，说明“壳层收敛”尚未完全结束。
- README 的模块说明落后于真实模块清单，文档同步需要常态化。
- `core` 内存在历史包名与新模块边界并存的情况，迁移时要优先看依赖方向，不要只看包路径。
