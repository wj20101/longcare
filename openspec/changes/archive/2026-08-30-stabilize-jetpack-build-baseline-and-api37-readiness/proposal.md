## Why

LongCare 当前已使用最新稳定的 AGP、Gradle、Kotlin、Compose BOM 和绝大多数 Jetpack 依赖，但 SDK/JDK 基线在根常量、应用模块、基准性能模块和自定义构建逻辑中重复表达，版本说明也已出现漂移。与此同时，`compileSdk 37` 不等于已经具备 `targetSdk 37` 的生产条件；Android 17 仍处于 Beta，现有竖屏兼容策略、厂商 SDK 和平台行为必须在正式升级前经过可执行验证。

## What Changes

- 建立 Android SDK/JDK 的单一事实来源，使应用、库、基准性能模块、质量守卫和长期文档读取同一基线，并阻止模块静默覆盖全局配置。
- 正式环境继续使用 `minSdk 24`、`compileSdk 37`、`targetSdk 36` 和 JDK 21；本 change 不把生产 `targetSdk` 升至 37。
- 将 API 37 升级从路线图文字转换为可执行准入：要求 Android 17 稳定发布、厂商兼容确认、大屏/自适应验证、平台行为回归以及 API 37 测试矩阵全部满足后，才允许通过独立 change 原子升级。
- 升级已确认存在稳定新版本且范围可控的依赖：Coil `3.5.0` 升至 `3.6.0`，kotlinx-datetime 从 `0.8.0-0.6.x-compat` 切换到稳定 `0.8.0`，并为图片加载与日期时间行为补充 focused 验证。
- 保持 AGP `9.3.2`、Gradle `9.7.1`、Kotlin `2.4.10`、Compose BOM `2026.08.00` 及其余已处于最新稳定版的 Jetpack/基础依赖不变，避免无收益的版本扰动。
- Navigation Compose 保持当前稳定版 `2.10.0`。Navigation 3 虽已有稳定版 `1.1.7`，但其迁移会替换现有 back stack、结果返回和 ViewModel scope 契约，必须先补齐导航行为回归基线，再由独立且原子的 change 实施；本 change 不引入 Nav3 依赖。
- 为 Baseline Profile/Benchmark `1.5.0-rc02` 建立有负责人、原因和退出条件的预览版豁免；稳定且兼容的 `1.5.x` 发布后移除豁免和相关警告抑制，不盲目降级到较旧稳定版。
- 区分 API 33 Baseline Profile 生成设备与 API 36/37 平台兼容测试：前者继续服务性能配置生成，后者覆盖当前 target 和下一 target 的行为准入。
- 修正技术栈文档与真实 version catalog/构建基线的漂移，并增加可重复的版本、SDK 和文档一致性守卫。
- 厂商 AAR、厂商 Maven 制品及 `android.enableJetifier=true` 保持现状；在厂商提供纯 AndroidX 兼容制品前，不进入会移除 Jetifier 的 AGP 10 升级。
- 不修改业务流程、Room/用户存储、WebView 导航、Manifest 组件行为或生产发布的既有 fail-closed 条件，也不进行 Navigation 3 迁移、全量 Feature 拆分或厂商 AAR 替换。

## Capabilities

### New Capabilities

- `android-build-baseline`: 规定 SDK/JDK 单一事实来源、稳定依赖升级策略、预览版豁免以及版本/文档漂移门禁。
- `android-api-level-readiness`: 规定生产 targetSdk 的保持与升级准入、当前/下一 API 测试矩阵和厂商兼容边界。

### Modified Capabilities

无。当前 `openspec/specs/` 没有可修改的既有能力，本 change 仅新增上述工程契约。

## Impact

- **构建配置**：根 settings、version catalog、`build-logic`、`:app` 与 `:baselineprofile` 的 Android 配置来源。
- **依赖与代码**：Coil 和 kotlinx-datetime 的解析版本，以及受影响的图片加载、日期时间调用与 focused tests；不改变网络、数据库和用户数据契约。
- **质量与 CI**：target SDK 守卫、依赖/预览版治理守卫、API 36 当前行为验证和 API 37 readiness 验证。
- **性能基线**：保留 API 33 Profile 生成路径，并明确其不能替代 targetSdk 平台兼容验证。
- **长期文档**：技术栈、依赖规则和 API 37 路线图事实同步。
- **外部依赖与风险**：API 37 正式升级依赖 Android 17 稳定版及厂商确认；厂商 AAR 与 Jetifier 不由本项目单方面变更，AGP 10 因此保持阻断。
- **兼容性**：无用户可见 breaking change；正式 target、业务逻辑、持久化和厂商集成保持不变。
