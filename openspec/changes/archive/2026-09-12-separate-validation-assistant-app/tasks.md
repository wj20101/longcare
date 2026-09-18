## 1. 建立双应用与厂商集成构建边界

- [x] 1.1 将现有 application convention 的 Release 签名与腾讯人脸依赖职责拆开，保留原安全校验并补齐 build-logic 单测；运行 `./gradlew -p build-logic test` 验证约定插件。
- [x] 1.2 新增 `:integration:txface` Android library，将腾讯 SDK AAR/Maven 来源、consumer rules、`FaceVerifier` 实现和 Hilt 绑定迁入单一边界；运行 `./gradlew :integration:txface:assembleDebug :app:compileDebugKotlin` 验证正式 App 仍可编译且厂商类型未泄漏到 Feature API。
- [x] 1.3 更新 `:app` 对腾讯集成模块的依赖并删除旧的 App 内 SDK 实现/重复依赖，运行既有人脸边界单测、`:app:processDebugMainManifest` 和 `:app:assembleDebug` 验证行为及 Manifest 合并等价。
- [x] 1.4 在 `settings.gradle.kts` 注册 `:assistant` application module，配置 `com.ytone.longcare.assistant`、共享 SDK/JDK/版本基线、独立名称/图标与最小 Launcher Manifest；运行 `./gradlew :assistant:processDebugMainManifest :assistant:assembleDebug` 并检查 badging 中的 applicationId、label 与唯一导出 Launcher。
- [x] 1.5 更新模块依赖 allowlist 和架构守卫以容纳 `:assistant`/`:integration:txface` 且禁止两个 application module 互相依赖；运行 `bash scripts/quality/verify_module_dependency_whitelist.sh .` 和 `bash scripts/quality/verify_architecture_boundaries.sh .`。

## 2. 下沉共享拍照能力

- [x] 2.1 在 `:core:domain` 增加只暴露水印 Logo 配置的最小 provider，并由 `:core:data` 的现有系统配置实现/绑定；运行两个模块的 focused unit tests 验证正常值、空值和异常/取消传播。
- [x] 2.2 将标准拍照 UI、`CameraViewModel`、水印/预览资源及其内部实现从 `:app` 迁入 `:feature:photoupload`，只依赖 Core 契约并补齐 Compose/协程依赖；运行 `./gradlew :feature:photoupload:testDebugUnitTest :feature:photoupload:lintDebug`。
- [x] 2.3 将正式 App 的 `CameraRoute` 切换到 Feature 公共入口并保持 `WatermarkData` 与 `CAPTURED_IMAGE_URI_KEY` 契约不变；运行相机/照片上传 focused tests 和 `./gradlew :app:compileDebugKotlin :app:assembleDebug`。

## 3. 下沉共享人脸能力

- [x] 3.1 将手动人脸采集 UI、ViewModel、检测/存储 delegate 与资源从 `:app/features/face/**` 迁入 `:feature:identification`，修正资源/主题边界并保留取消和文件返回语义；运行现有手动采集 focused tests 与 `./gradlew :feature:identification:testDebugUnitTest :feature:identification:lintDebug`。
- [x] 3.2 将备用腾讯人脸 UI、照片处理和 ViewModel 迁入 `:feature:identification`，改用现有 `FaceVerificationConfigProvider` 和显式 `currentUserId` 输入，避免依赖 App 的 Home ViewModel 或 Data 实现；补充配置缺失、照片处理取消、SDK 成功/失败/取消单测并运行该模块测试。
- [x] 3.3 把通用 `FaceSdkUiController` 放入不暴露厂商类型的共享边界，让正式 App 与助手通过 `FaceVerifier` 使用同一 SDK adapter；运行 controller 生命周期/回调单测并用依赖检查确认 Feature 源码不导入腾讯 SDK 包。
- [x] 3.4 将正式 App 的手动人脸与备用人脸 routes 切换到 Feature 公共入口，保持 `FACE_IMAGE_PATH_KEY`、成功回调和系统返回行为不变；运行相关导航/状态测试及 `./gradlew :app:compileDebugKotlin :app:assembleDebug`。

## 4. 实现助手壳层、隐私与鉴权

- [x] 4.1 实现最小 `AssistantApplication`、Hilt 组装、真实网络 `RuntimeConfigProvider` 与无 mock 的 interceptor 绑定，确保不初始化 Bugly、启动 Worker、QLZ 或持续定位；运行 Hilt 编译测试并检查助手依赖图/merged manifest 不含这些组件。
- [x] 4.2 实现助手独立隐私同意与精简短信登录页，复用 `:feature:login` 的 ViewModel/Repository，并显示同账号可能受后端单会话策略影响的提示；运行登录输入、同意 gate、成功/失败与倒计时 focused tests。
- [x] 4.3 实现助手会话观察、鉴权需求分类与可保存的待继续目的地，保证登录成功只恢复一次、取消登录返回首页、会话失效只清理助手会话；运行 `:assistant:testDebugUnitTest` 覆盖未登录重定向、恢复、失效和进程状态重建。
- [x] 4.4 实现助手专属类型安全导航图和验证首页，展示五个明确入口且不注册正式 App 导航图；运行 Compose 导航测试验证每个入口、系统返回和助手内闭环。

## 5. 接入五项助手验证流程

- [x] 5.1 实现订单 ID 输入与默认人脸 route，直接调用 `DefaultFaceVerificationScreen`，回传成功/失败/取消及 JPEG 指标；运行无效 ID、会话 gate、指标展示和返回行为测试。
- [x] 5.2 接入标准拍照 route，使用 Feature 公共入口完成权限、拍照、水印、压缩和预览/结果返回；运行相机权限拒绝/恢复 Compose 测试与助手编译测试。
- [x] 5.3 接入备用腾讯人脸和手动人脸 route，分别从助手会话提供 userId 并处理成功/失败/取消；运行 Feature/助手 focused tests，确认路由不会进入正式 Home 或订单流程。
- [x] 5.4 将 `NfcValidationScreen`、R65C HID 捕获/状态与 `NfcTestHelper` 的验证专属部分迁入 `:assistant`，以页面生命周期替代 `NfcTestEntrySession`；迁移并运行 UID 规范化、输入超时、系统键过滤、复制、resume/pause/destroy 单测。
- [x] 5.5 完成助手 NFC Intent 分发、NFC 开关设置返回刷新、相机/NFC 权限与资源释放；运行 instrumentation 测试验证权限拒绝恢复、离开页面停用监听以及 Activity 重建。
- [x] 5.6 审计助手 Debug/Release merged manifest 与依赖报告，确认仅声明五项能力所需权限/feature、仅 Launcher 导出、provider authority 基于助手 applicationId；运行 Release exported-components 守卫的助手用例。

## 6. 清理正式 App 验证入口

- [x] 6.1 从 `LoginScreen`/`LoginBrandingHeader` 删除验证状态、Sheet 调用和 Logo `combinedClickable`/长按触觉反馈，并从 `LoginFeatureActions` 删除验证动作；运行正式登录页 Compose 测试，证明点击和长按 Logo 均无验证动作且登录行为不变。
- [x] 6.2 删除正式 App 内 `LoginValidationEntrySheet`、验证导航 actions/Activity、NFC/R65C 验证代码、`NfcTestEntrySession`/配置/辅助 DI 与助手专属字符串资源，并迁移或删除对应旧测试；运行 `rg` 洁净检查及 `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`。
- [x] 6.3 从正式 main Manifest 删除 validation Activity，并从 Debug Manifest 删除验证 Launcher 叠加；运行 `:app:processDebugMainManifest`、`:app:processReleaseMainManifest` 和导出组件检查，确认正式 applicationId 与业务组件不变。
- [x] 6.4 用新的 `verify_validation_app_isolation.sh` 替换反向要求 Release 隐藏入口的旧守卫，覆盖模块注册/依赖、正式源码与 Manifest 洁净、助手导出面和 applicationId；为脚本增加正反 fixture 测试并运行守卫。
- [x] 6.5 运行正式 App 的相机、默认/备用/手动人脸与 NFC focused 回归测试以及 `./gradlew :app:lintDebug :app:assembleDebug`，确认清理验证代码未破坏生产能力。

## 7. 双 APK 打包、CI 与文档

- [x] 7.1 新增 `scripts/release/build-dual-apks.sh --debug|--acceptance`，精确清理自身输出目录、构建两个模块、验证当前版本/模式并输出两个确定名称的 APK 与校验和；用脚本测试覆盖缺包、旧包和任一 Gradle 构建失败场景。
- [x] 7.2 更新 affected-module 计算、本地 preflight 与 ci-required registry，使相关改动运行助手 test/lint/assemble 和隔离守卫；执行脚本自身测试与 `bash scripts/quality/preflight_local.sh --local-fast`。
- [x] 7.3 更新 Android CI，分别上传正式与助手 Debug APK；更新 Acceptance 流程以显式生成内部助手 artifact，同时保留 Production 只发布 `:app`，并运行 `bash scripts/quality/verify_ci_workflow_quality.sh .`。
- [x] 7.4 更新 `README.md`、`AGENT.md`、系统概览、依赖规则、页面/路由地图、技术栈、CI/发布说明及相关路线图，删除“单 APK”和 Release 隐藏入口的过时事实，并校验相对链接与文档命令。
- [x] 7.5 将 AGP 从 9.3.2 升级至 9.4.0 并同步基线文档；运行 `./gradlew -p build-logic test`、`openspec validate --all --strict --no-interactive`、两 Feature focused tests、`:assistant:testDebugUnitTest`、`bash scripts/quality/preflight_local.sh --full`、两个模块的 Debug lint/assemble 与 lint allowlist、双 APK Debug/Acceptance 打包和隔离回归，检查 `git diff` 不包含 build 产物、凭据或用户已有定位/NFC改动；不得新增 Lint 忽略项。
- [x] 7.6 执行双 Debug 构建并用 `android describe --project_dir=.` 确认两个 application 目标和 APK 路径；通过 Android CLI 把两个 APK 同时安装到同一设备，使用 `android layout --pretty` 验证助手五项入口和正式登录页无入口。
- [x] 7.7 在支持设备上完成手机 NFC 真机验收，覆盖实际标签读取、重复读取一致性、结果复制、NFC 开关与设置返回恢复、后台/返回释放、Activity 重建恢复及畸形外部 Intent 防护；沿用 7.6 的双应用共存与入口隔离证据，结果保留在构建报告/CI artifact/PR 证据而不新增仓库报告文档。
  - Pixel 10 已完成两次实际贴卡与复制验证，NFC 生命周期及异常 Intent 专项 3 项测试通过；本项依据既有证据完成，不代表本次文档修订重新执行了设备测试。
  - 按确认，本次助手真机验收仅要求 NFC；R65C、拍照、默认/备用/手动人脸及真实登录排除在本次完成门槛之外，未验收不记为通过。保留这些功能及既有自动化回归，双包隔离与生产安全门禁不变。
