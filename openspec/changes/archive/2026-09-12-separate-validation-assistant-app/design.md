## Context

动机见 [proposal.md](proposal.md)。当前 Android CLI 只识别 `:app` 为正式 application 目标；登录页 `LoginScreen` 无条件向主 Logo 注入长按回调，随后由 `LoginValidationEntrySheet` 和 `LoginValidationEntryNavigationActions` 打开五项验证能力。`FaceVerificationValidationActivity`、`NfcValidationActivity`、R65C 输入页、`NfcTestHelper` 与测试会话均位于 `app/src/main`，Debug Manifest 还把人脸验证 Activity 叠加为独立 Launcher。现有 `verify_release_validation_entry.sh` 则反向要求这些入口必须出现在 Release。

现有可复用能力分布不均：默认 CameraX/ML Kit 人脸验证已经位于 `:feature:identification`，登录状态逻辑位于 `:feature:login`；拍照 UI/ViewModel、手动人脸采集、腾讯兼容人脸 UI/状态和腾讯 SDK 实现仍位于 `:app`。由于 Android application 模块不能作为另一个 application 模块的公共实现边界，`:assistant` 不能直接依赖 `:app`。

已通过 Android CLI 核对官方文档：`kb://android/build/configure-app-module` 要求每个可独立安装应用使用唯一且显式的 applicationId，并建议 namespace 与代码基础包保持一致；`kb://android/studio/projects/add-app-module` 确认应用模块可以独立构建、测试和调试，两个应用都需要的代码应迁入 library module。设计据此采用第二个 application module，而不是共享 UID 或调用正式应用导出组件。

## Goals / Non-Goals

**Goals:**

- 正式 APK 和助手 APK 拥有清晰的编译边界、应用身份、会话沙箱和发布用途。
- 五项验证调用同一份生产业务/设备实现；助手只拥有验证编排和展示，不复制核心算法或网络协议。
- 将目前为了隐藏入口而留在 `:app` 的验证专用代码完全移走，同时借机把两端都需要的生产 UI 下沉到合理模块。
- 保持 `:app` 的 applicationId、业务路由参数、网络契约、数据库和正式发布门禁不变。

**Non-Goals:**

- 不把全部 `:app/features/**` 一次性模块化，只迁移助手实际需要共享的相机与人脸能力。
- 不新增 QLZ、定位持续跟踪、服务倒计时、应用更新或销售功能到助手。
- 不升级腾讯人脸二进制、不消除既有 16 KB/consumer rules blocker，也不改变生产 Release 的 fail-closed 结论。
- 不跨应用共享账号文件、DataStore、数据库、ContentProvider 或进程。

## Decisions

### 1. 使用独立 `:assistant` application module

新增 `:assistant`，暂定 namespace 与 applicationId 均为 `com.ytone.longcare.assistant`，Launcher 名称为“长护验证助手”，图标在现有品牌元素上增加明显的“验证/工具”区分。`:app` 继续使用 `com.ytone.longcare`。两个模块共享 compileSdk、minSdk、targetSdk、JDK 与版本常量；助手 versionName 增加可识别后缀，但 versionCode 与同次正式构建对齐，便于追踪。

`:assistant` 具有自己的 `@HiltAndroidApp` Application 和单 Activity Compose 壳层。它不初始化 Bugly、WorkManager 启动更新、QLZ 或持续定位生命周期观察器。除 Launcher Activity 外不新增导出组件。

**未采用的方案：**

- 不使用 `:app` 的 product flavor 生成助手。Flavor 虽能较快产出不同 applicationId，但助手仍会编译正式主导航、服务和销售能力，测试边界继续与正式壳层耦合，也无法通过模块依赖守卫证明隔离。
- 不让助手通过 Intent/深链调用正式应用内部 Activity。该方案要求正式应用安装在场、扩大 exported surface，并与“正式包不含验证入口”冲突。
- 不复制五套生产实现到助手。复制会造成测试工具与真实业务行为逐步漂移。

### 2. 明确助手专属代码与共享生产代码的归属

模块依赖保持以下方向：`:app` 与 `:assistant` 都只向下依赖 Feature/Core/厂商集成模块，两个 application module 之间没有依赖边。

| 能力 | 目标归属 | 设计边界 |
|---|---|---|
| 助手首页、鉴权跳转、订单 ID 输入、结果摘要、NFC/R65C 验证 UI | `:assistant` | 仅内部验证编排，不向正式 App 暴露 API |
| 登录业务状态 | `:feature:login` | 复用现有 `LoginViewModel`/Repository；助手自有精简登录 UI，正式登录 UI 不因助手扩张 |
| 默认人脸验证 | `:feature:identification` | 直接复用现有 `DefaultFaceVerificationScreen` 与用例，助手只提供 `OrderKey`、返回和照片指标回调 |
| 手动人脸采集 | `:feature:identification` | 将当前 `:app/features/face/**` 的生产采集 UI、状态与处理迁入 Feature，正式路由与助手路由共同调用公共入口 |
| 备用腾讯人脸验证 | `:feature:identification` + `:integration:txface` | Feature 持有 UI/状态和 `FaceVerifier` 契约调用；新集成模块持有腾讯 SDK 类型、AAR/Maven 选择、Hilt 实现与 consumer rules |
| 标准拍照/水印/压缩 | `:feature:photoupload` | 将当前 `:app/features/photoupload/ui` 与 `CameraViewModel` 迁入 Feature；缺少的系统 Logo 配置以最小 Domain provider 补齐，不让 Feature 依赖 `:core:data` |
| 手机 NFC 与卡号规范化 | `:core:common` 既有能力 | `NfcManager`、`NfcUtils`、`ExternalRfidTagParser` 继续作为共享平台能力 |
| NFC 测试会话、对话框与 R65C HID 捕获 | `:assistant` | 从正式 App 删除；页面生命周期本身决定是否监听，不再需要全局 `NfcTestEntrySession` 开关 |

`LoginFeatureActions` 删除 `validationEntryActions`，正式登录 UI 删除 `showValidationEntrySheet` 和 Logo 的 `combinedClickable`。验证字符串、test tag 和 Activity 资源迁入助手；仍被正式业务使用的通用字符串、主题 token 和图片处理能力放入对应 Feature/Core，而不是助手反向提供。

腾讯 SDK 当前由名称同时承担“签名 + SDK 依赖”的 application convention 注入。实现时拆分职责：应用签名约定可供两个 application module 复用；腾讯 AAR/Maven 来源及 SDK adapter 由 `:integration:txface` 单点拥有。正式 App 和助手都依赖该集成模块，但 Feature 只面向 `FaceVerifier` 等现有抽象，厂商类型不得泄漏到 Feature API。QLZ AAR 仍只属于正式 `:app`。

### 3. 助手使用独立、最小的类型安全导航图

助手只注册以下内部目的地：验证首页、助手登录、订单 ID 输入、默认人脸、NFC/R65C、标准相机、备用腾讯人脸、手动人脸。它不调用 `registerAppNavGraphs`，也不包含 Home、订单执行、销售或服务流程 route。

入口按是否需要服务端会话分类：NFC/R65C、拍照和手动采集可直接进入；默认人脸和备用腾讯人脸在无有效助手会话时先进入登录。导航状态保存待继续的目的地，登录成功后只恢复一次；取消登录返回验证首页。会话失效由助手壳层观察并清除待处理的敏感结果，然后回到登录页。

标准人脸流程采用“订单 ID 输入页 → `DefaultFaceVerificationScreen`”的最短公共路径，不复用正式 App 的整张 `IdentificationScreen`/订单执行导航图。成功、失败或取消都回到助手内，并由回调展示照片尺寸和压缩字节数。

### 4. 助手拥有独立运行配置、隐私同意和会话

`:assistant` 提供自己的 `RuntimeConfigProvider` 与 `FlavorInterceptorApplier` 绑定。所有助手变体默认连接真实配置的服务地址且 `useMockData=false`，不打包 `app/src/debug/assets/mock` 或正式 App 的 debug interceptor。公共 key 等非敏感构建常量从根构建配置统一读取，禁止复制 secret 到源码或资源。

助手首次执行登录、远端人脸或腾讯 SDK 前先取得自己的隐私同意；短信登录复用 `:feature:login` 的状态与数据契约。Core Data 的 DataStore/Room 使用助手 `ApplicationContext`，因此自然落入独立沙箱。即使两个 Release APK 使用同一发布证书，也不声明 sharedUserId、不共享 provider authority，并分别使用 `${applicationId}` 派生的文件 provider authority。

助手不假定同一账号可以在两个应用维持并发服务端会话；后续另行开展真实登录或人脸验收时优先使用专用测试账号，本次 NFC 验收不要求真实登录。如果后端采用单会话策略，助手登录可能令正式应用 token 失效，这属于服务端账号策略而非本地数据泄漏，需在助手登录页明确提示。

### 5. Manifest 与权限采用最小面

助手 Manifest 只声明 Internet、相机、前台 NFC 与所复用 SDK 实际要求的权限/feature。相机、NFC 和厂商 SDK 均按用户选择后再请求或启动；NFC 前台分发仅在 NFC 验证页面处于 resumed 状态时启用，离开页面立即关闭。R65C HID 输入只在对应页面持有焦点并过滤系统按键。

正式 `app/src/main/AndroidManifest.xml` 删除两个 validation Activity；`app/src/debug/AndroidManifest.xml` 删除验证 Launcher 叠加。助手若采用单 Activity，只有该 Activity `exported=true` 且仅带 MAIN/LAUNCHER；内部路由不可从外部 Intent 直接附带订单或凭据打开。腾讯 AAR 合并出的组件与权限需要分别审计两个应用的 merged manifest。

### 6. 双 APK 构建采用显式聚合入口，生产发布仍只发布正式 App

新增文档化脚本 `scripts/release/build-dual-apks.sh`：

- `--debug` 执行 `:app:assembleDebug` 与 `:assistant:assembleDebug`。
- `--acceptance` 要求现有签名输入并显式传入 `release.production=false`、`release.acceptance=true`，执行两个模块的 Release 构建。
- 构建成功后验证恰好存在两个目标 APK，并复制/重命名到忽略提交的 `build/outputs/dual-apk/<mode>/`，名称中包含 `longcare` / `longcare-assistant`、版本和模式；生成校验和作为 CI artifact，不写入长期文档。

单独的 `:app:assemble*` 行为保持不变。正式 production workflow 继续只构建/发布 `:app` APK/AAB，绝不隐式发布助手。普通 Android CI 增加 `:assistant:lintDebug`、`:assistant:testDebugUnitTest`、`:assistant:assembleDebug`，并将两个 Debug APK作为名称不同的 artifacts 上传；Acceptance workflow 可显式调用双 APK 脚本。

现有 application convention 需要支持第二个模块，但生产配置门禁仍只挂在 `:app`。助手 Release 只定义为内部验收构建，不能借其成功推导正式生产可发布。

### 7. 用反向洁净守卫替换隐藏入口守卫

将 `verify_release_validation_entry.sh` 替换为职责匹配的新守卫，例如 `verify_validation_assistant_isolation.sh`。守卫至少校验：

- `:assistant` 已注册且具有独立 applicationId；两个 app 模块互不依赖。
- 正式 Login 源码不含验证 sheet/动作或 Logo 长按入口，正式 main/debug/release Manifest 不含 validation Activity/Launcher。
- 验证专属类、资源和 DI 不再位于 `app/src/**`，正式 APK/merged manifest 不含助手 applicationId 或验证组件。
- 助手 Manifest 只有允许的导出组件，provider authority 使用自身 applicationId。
- 双构建产物的 applicationId、label、版本和签名符合预期，Android CLI 能描述两个 application 目标。

测试分层如下：

- 单元测试：待继续目的地/鉴权 gate、订单 ID 策略、R65C 归一化与超时、NFC 生命周期、照片指标与返回状态；迁移现有相关测试并保持覆盖。
- Compose/instrumentation：助手首页五个入口、未登录重定向与恢复、正式 Logo 长按无动作、权限拒绝与返回恢复。
- 构建/静态门禁：两个模块的 compile/test/lint/assemble、依赖 allowlist、导出组件、APK identity 与验证代码隔离。
- 设备验收：保留既有双应用共存和入口隔离证据，本次助手真机完成门槛仅为手机 NFC。覆盖实际贴卡、重复读取一致性、结果复制、NFC 关闭与设置返回恢复、前后台/返回释放及 Activity 重建后的监听恢复，并检查畸形外部 Intent 不触发内部路由。模拟器结果不得替代实际 NFC 标签读取证据。
- 验收排除项：R65C、拍照、默认人脸、备用腾讯人脸、手动人脸和真实登录不作为本次完成门槛，未验收项目不得记为通过。已有入口、实现及自动化测试保留；此范围调整不删除功能，不放宽正式业务回归、双包隔离、隐私/会话安全或生产发布门禁。后续如需保证这些真实链路，须另行安排对应设备、账号与参与人员验收。

### 8. 构建基线补充

AGP 升级至 9.4.0；官方兼容表要求 Gradle 至少 9.6.0、JDK 至少 17，支持最高 API 37，现有 Gradle 9.7.1、JDK 21 与 compileSdk 37 满足要求。保留 Kotlin、targetSdk、厂商 SDK 与签名策略，不以新增 Lint ignore 或版本警告例外替代升级。执行 build-logic 测试、完整 preflight、双应用 Debug lint/assemble、双 APK Debug/Acceptance 打包与隔离校验；新基线若揭示不兼容，先定位再修复，不放宽生产 fail-closed 门禁。

## Risks / Trade-offs

- [相机、手动人脸和腾讯兼容 UI 从 `:app` 下沉的改动面较大，可能影响正式业务] → 按能力逐个迁移，先让正式路由通过 Feature 公共入口保持等价并运行 focused tests，再接入助手，最后删除旧文件。
- [助手独立沙箱意味着验证人员必须再次登录] → 提供助手内短信登录与待继续流程；不以共享 UID、导出 provider 或读取正式存储换取便利。
- [后端单会话策略可能使两个 App 的同一测试账号互相顶下线] → UI 明示风险，后续真实登录验收使用专用账号并覆盖双方会话失效恢复；本次 NFC 验收不覆盖该风险，也不把它误判为本地隔离失败。
- [腾讯 SDK 可能依赖宿主包名、资源、Manifest 合并或混淆规则] → 将 SDK adapter 与规则集中在集成模块，对两个宿主分别跑 merged-manifest 和 Release shrink；真实人脸回归排除在本次验收范围之外，不宣称已通过，保留既有生产 blocker。
- [新增应用模块增加构建时间和 APK 存储] → affected-scope CI 只在相关路径变化时强制助手任务，产物独立上传并采用现有 retention 策略。
- [助手默认真实网络可能误操作生产数据] → 只暴露既有验证动作、明确显示环境与账号、禁用自动执行；不新增修改订单状态的入口。
- [双 APK 脚本可能在其中一个构建失败后留下旧产物] → 每次构建先清理脚本自己的精确输出目录，完成后校验两个 APK 均来自当前版本/模式，再生成校验和；不删除模块的其他构建目录。

## Migration Plan

1. 先建立应用签名与腾讯 SDK 集成的可复用构建边界，并让现有 `:app` 在新边界下保持编译、Manifest 和人脸行为等价。
2. 依次将标准相机、手动人脸和备用人脸公共实现迁入对应 Feature；每个切片先切换正式路由并完成 focused 验证。
3. 新增 `:assistant` 壳层、独立运行配置/隐私/会话、最小导航和五项入口，接入已稳定的公共能力；迁移 NFC/R65C 验证专属实现与测试。
4. 在助手入口和共享能力自动化回归通过、手机 NFC 真机链路可验证后，从正式 App 删除 Logo 长按、验证 Sheet/Activity/导航/DI/资源和 Debug Launcher；其余助手真实链路不作为本次迁移完成门槛。
5. 更新隔离守卫、模块 allowlist、affected-module 计算、CI artifacts、双 APK 脚本及架构/路由/技术栈/质量文档，然后执行双包构建与共存安装验收。

该变更没有数据库或服务端迁移。回滚时可以停发/卸载助手，并回退正式 App 的入口删除提交；不得通过重新导出正式验证 Activity 作为临时回滚手段。正式 APK 的发布应安排在助手 APK 已交付验证人员之后，避免验证入口空窗。

## Open Questions

- 助手图标最终视觉稿与对内分发渠道可以在实现/发布准备阶段确定；默认使用“长护验证助手”文字与明显工具角标，不影响模块、权限或行为契约。
