## 1. 固化迁移前契约

- [x] 1.1 扩展 `IdentificationUiActionQueueTest`，覆盖停止收集期间排队、严格 FIFO、重复 consume 幂等及已消费动作不重放，并运行 `./gradlew --no-daemon :feature:identification:testDebugUnitTest --tests '*IdentificationUiActionQueueTest'` 验证通过。
- [x] 1.2 扩展 `IdentificationFaceSdkCoordinatorTest`，覆盖同一请求只发布一次、消费后清除、过期 request id 被忽略以及成功/失败/取消事件归属，并运行 `./gradlew --no-daemon :feature:identification:testDebugUnitTest --tests '*IdentificationFaceSdkCoordinatorTest'` 验证通过。
- [x] 1.3 为 `IdentificationRoute` 的 `OrderNavParams` 往返、返回栈入口以及 `CAPTURED_IMAGE_URI_KEY`、`FACE_IMAGE_PATH_KEY`、`DEFAULT_FACE_VERIFICATION_RESULT_KEY` 增加 app 侧契约测试，并运行对应 `:app:testDebugUnitTest --tests` 用例确认迁移前基线为绿色。

## 2. 建立 Feature 屏幕状态与公开边界

- [x] 2.1 在 `features.identification.api` 增加唯一公开的 `IdentificationFeatureScreen` 入口和窄的 suspending 厂商人脸启动合约，将带 id 的启动请求类型移到公开 API 边界，并以 `:feature:identification:compileDebugKotlin` 与现有 coordinator tests 验证 API 不依赖 `:app`。
- [x] 2.2 新增不可变 `IdentificationScreenUiState`，在 ViewModel 中聚合验证类型、验证/上传/人脸设置状态、待消费动作和厂商启动请求，保留现有领域状态写入顺序；以 coroutine unit tests 验证初始值、组合更新和 collector 重启后的最新值。
- [x] 2.3 增加纯 render-state mapper 与内部屏幕事件模型，覆盖两类人员卡片、验证中/成功/失败/重试和“下一步”启用条件，并运行 focused mapper tests 验证每个状态组合及事件映射。
- [x] 2.4 将相机照片、人脸照片、默认验证结果、动作队列、上传完成及厂商启动处理收敛到 feature 的 screen-effect 层，使用稳定结果值/request id 并在提交后 acknowledgment；以 fake actions/launcher tests 验证每个输入至多处理一次、空结果无副作用和迟到 SDK 事件无效。

## 3. 在 Feature 内实现无状态身份识别 UI

- [x] 3.1 在 `:feature:identification` 内实现 module-internal 的卡片、状态区、Scaffold、顶部/底部栏和 body，使 `IdentificationScreenContent` 只接收 render state 与回调且任何子组件都不 import ViewModel；以 `rg` 断言和 `:feature:identification:compileDebugKotlin` 验证。
- [x] 3.2 在公开入口中使用 lifecycle-aware collection 连接 Hilt ViewModel、共享订单 ViewModel、返回键、老人照片水印、权限用途弹窗和 screen effects，并运行 feature 单元测试与 `lintDebug` 确认没有 Activity/NavController/厂商 UI 进入 ViewModel。
- [x] 3.3 将 `ic_service_person.webp`、`ic_elder_person.webp` 与身份识别独占字符串迁入 feature，为仍由 app 使用的通用/人脸引导文案增加 feature-local 等价值；通过全仓 `rg` 引用盘点、Android resource merge 和 `:feature:identification:lintDebug` 验证无 app `R` 引用、缺图或重复删除。
- [x] 3.4 为无状态 Compose 内容增加 UI tests，覆盖服务人员/老人卡片、返回、验证、重试、进度/错误展示及老人验证后下一步启用，并至少运行 `:feature:identification:compileDebugAndroidTestKotlin` 确认测试制品可构建。
- [x] 3.5 将无业务依赖的 `BottomSafeActionContainer` 以原包名/API 从 `:app` 提升到 `:core:ui` 并核对 feature 直接依赖；若编译确实缺少测试或 AndroidX API，仅通过 version catalog 增加最小依赖并同步 `module_dependency_allowlist.txt`，以依赖图确认没有 `:app`、Data 实现、其他 Feature internal 或版本升级。

## 4. 切换 App 路由与厂商适配

- [x] 4.1 将 `registerIdentificationRoute` 切换为调用 `IdentificationFeatureScreen`，由 route lambda 使用当前 UI Context 和既有 `rememberFaceSdkUiController()` 实现启动合约并把 `FaceSdkEvent` 回传；以 fake launcher 集成测试和 `:app:compileDebugKotlin` 验证同一 id 只启动一次。
- [x] 4.2 保持 `IdentificationActions` 的五个导航回调、三个结果 `StateFlow` 及 clear acknowledgment 原样接线，并扩展 app 路由测试验证系统返回/页面返回一致、结果消费后 key 清除且成功后只导航一次。
- [x] 4.3 删除 `:app` 中 9 个旧身份识别 UI 文件和已迁移的独占头像/字符串，保留手动采集兼容页、共享引导文案及 app-owned 平台控制器；以 `rg --files app/src/main/kotlin/com/ytone/longcare/features/identification/ui` 无结果和 `:app:assembleDebug` 成功验证没有双份实现。

## 5. 收紧架构治理并同步长期文档

- [x] 5.1 新增可独立运行的身份识别 feature 边界守卫，阻止 app legacy UI 回流、feature import app `navigation`/`platform`/app `R` 以及 app import feature 内部 UI/VM，并在临时正向 fixture 上验证返回零。
- [x] 5.2 为边界守卫增加至少三组负向 fixture：app 重建身份识别 UI、feature import app 平台实现、app 绕过公开入口；逐组验证守卫返回非零且输出违规文件、规则和修复方向。
- [x] 5.3 从 `legacy_feature_files_allowlist.txt` 精确删除 9 条已迁出路径，把 `verify_architecture_boundaries.sh` 的身份识别行数阈值迁到 feature 且不放宽，并接入 focused 守卫、preflight changed-path 路由及质量门禁登记；运行 legacy allowlist fixture 和架构守卫验证通过。
- [x] 5.4 更新 `docs/architecture/system-overview.md`、`dependency-rules.md`、`ui-and-screen-map.md`、`roadmap-and-open-gaps.md` 与受影响的 `ci-quality-gates.md`，并通过文档链接检查与 `preflight_local.sh --local-fast` 验证身份识别所有权、Navigation 2 非目标及厂商边界描述一致。

## 6. 集成与设备验证

- [x] 6.1 运行 focused 治理集合：身份识别边界 fixture、legacy allowlist fixture、`verify_architecture_boundaries.sh .`、`verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare .`，确认所有正向检查通过且负向 fixture 稳定失败。
- [x] 6.2 运行 `./gradlew --no-daemon :feature:identification:testDebugUnitTest :feature:identification:lintDebug :app:testDebugUnitTest`，确认状态、动作、SDK 协调、路由结果与资源检查全部通过。
- [x] 6.3 运行 `bash scripts/quality/verify_release_validation_entry.sh .`、`./gradlew --no-daemon :app:lintDebug :app:assembleDebug` 和 `bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt`，确认普通 Android CI 主路径无新增问题。
- [x] 6.4 运行 `bash scripts/quality/preflight_local.sh --full`，确认 Kotlin、模块边界、单测与构建综合门禁通过且未靠新增 ignore、豁免或签名 fallback 放宽规则。
- [x] 6.5 使用 Android CLI 在 API 36 模拟器部署 debug APK，并结合 instrumentation、`android layout` 和页面操作验证进入/返回、相机权限拒绝与恢复、三类子页面结果一次消费、前后台/重建以及成功后单次导航；厂商 UI 使用 fake 自动化，连接兼容真机时补充腾讯人脸成功/失败/取消 smoke test。
- [x] 6.6 运行 `bash scripts/quality/preflight_local.sh --release` 检查 release-oriented 快照，确认本 change 未新增生产阻断，同时 QLZ 配置/弱 TLS、腾讯人脸 16 KB 对齐与 consumer rules 等既有厂商问题仍按预期 fail-closed，且未被修改或绕过。
