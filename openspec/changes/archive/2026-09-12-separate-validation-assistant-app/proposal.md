## Why

登录页通过长按 Logo 暴露的内部验证入口同时存在于 Debug 与 Release，导致正式应用包含面向研发/验收的 UI、Activity、导航契约和 NFC 测试辅助代码。将这些能力迁入同一项目内可独立安装的助手应用，可以保持正式 APK 的产品边界整洁，同时保留真实设备和真实服务链路的验证能力。

## What Changes

- 新增独立 Android 应用模块 `:assistant`，使用独立 applicationId，可与正式 `:app` 同时安装，并从 Launcher 直接进入功能验证首页。
- 将原登录页底部“功能验证”面板中的五项能力迁入助手应用：正式服务人脸验证、手机 NFC / R65C 读卡验证、拍照验证、备用腾讯人脸验证和手动人脸采集验证。
- 助手应用独立管理登录与会话；需要真实服务鉴权的验证流程在助手内完成登录，不读取或修改正式应用的私有数据。
- 从正式应用删除 Logo 长按手势、验证面板、验证专用 Activity、验证导航动作、NFC 测试会话/辅助实现及其专用资源和 DI；正式业务仍使用的相机、人脸、NFC 能力保持不变。
- 提取助手和正式应用都需要的生产能力到合适的 Core/Feature 公共边界；`:assistant` 不依赖 `:app`，`:app` 也不依赖助手或验证专用模块。
- 增加明确的双应用构建入口，使同一变体的一次构建输出正式 APK 与助手 APK，并在 CI/产物检查中分别命名、校验和上传。
- 更新模块依赖、Manifest/导出组件、安全守卫、测试与长期架构/路由/构建文档。
- 将 AGP 从 9.3.2 升级至 9.4.0，修复构建基线版本的 Lint 阻断，并执行约定插件、双应用与共享 Feature 回归；不新增 Lint 忽略项。
- 本次助手真机验收范围仅为手机 NFC：实际读卡、重复读取、结果复制、NFC 开关与设置返回恢复、前后台/返回及 Activity 重建后的监听管理。R65C、拍照、默认/备用/手动人脸及真实登录不作为本次完成门槛；保留已有入口和实现，不将未验收项目记为通过。双包隔离、构建回归与生产安全门禁不变。
- **BREAKING**：正式应用不再提供登录页长按 Logo 的隐藏验证入口；验证人员改用独立助手应用。

## Capabilities

### New Capabilities

- `validation-assistant-app`: 定义独立助手应用的启动、鉴权隔离、五项验证流程、权限与返回行为。
- `dual-apk-packaging`: 定义正式应用与助手应用的包隔离、双 APK 构建产物以及正式 APK 的验证代码洁净约束。

### Modified Capabilities

无。

## Impact

- 受影响模块与代码：`settings.gradle.kts`、新 `:assistant` 应用模块、新的腾讯人脸集成边界、`:app` 登录/导航/Manifest/DI/验证源码、`:feature:login` 登录动作契约，以及为双端复用而调整的 `:feature:identification`、`:feature:photoupload`、`:core:ui` / `:core:common` 边界。
- 受影响工程系统：模块依赖 allowlist、Release 导出组件检查、原隐藏入口守卫、Android CI/Release 构建与 APK artifact、Android CLI 项目描述、架构/路由/技术栈/质量门禁文档。
- 兼容性：正式应用 applicationId、登录和业务路由保持不变；助手使用独立 applicationId 和独立沙箱，两个应用可共存。现有正式应用会话不会自动迁移或共享给助手。
- 权限与安全：助手只声明五项验证流程实际需要的相机/NFC等权限；除 Launcher Activity 外组件默认不导出。不得通过共享 UID、导出正式应用内部组件或跨应用读取私有存储来共享会话。
- 厂商与发布风险：备用人脸验证仍依赖腾讯人脸 SDK 及其现有 Release blocker；助手 Release 仅作为内部验收产物，不改变正式生产发布的 fail-closed 规则，也不在本变更中升级厂商 SDK。
- 非目标：不改变正式业务的网络、Room、SavedStateHandle 或服务流程契约；不新增 QLZ 验证；不把助手作为面向终端用户的商店产品发布。
