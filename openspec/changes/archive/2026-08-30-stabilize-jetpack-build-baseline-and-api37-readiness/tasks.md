## 1. 建立可隔离验证的工程守卫

- [x] 1.1 新增 Android build baseline focused 守卫及临时 fixture，解析 settings 中的 `minSdk`/`compileSdk`/`targetSdk`、常量中的 JDK/应用版本和 AGP/settings plugin 版本，并验证统一配置成功、字段缺失、SDK 顺序非法、插件版本不一致及模块级覆盖均按预期通过或 fail-closed。
- [x] 1.2 新增依赖稳定性 focused 守卫、精确预览版 allowlist 格式和临时 fixture，覆盖未豁免 alpha/beta/RC/snapshot/dev/compat、缺失负责人/原因/验证范围/退出条件、泛化豁免和合法精确豁免，并通过 fixture 的正负断言验证错误输出包含版本别名。
- [x] 1.3 新增 instrumentation smoke class 完整性守卫及临时 fixture，校验默认 smoke 与 `affected-modules.sh` 选择的每个全限定类名在 `app/src/androidTest` 中真实存在，并验证不存在的类会返回非零且打印类名和来源文件。
- [x] 1.4 为上述 focused 守卫增加 `bash -n` 和 fixture 聚合入口，先证明其能识别当前重复 SDK、技术栈漂移和失效 smoke 类，再确认 fixture 不修改真实 Gradle、测试或 allowlist 文件。

## 2. 收敛 SDK/JDK 构建基线

- [x] 2.1 在 `settings.gradle.kts` 应用与 AGP `9.3.2` 同版本的 `com.android.settings`，声明 `minSdk 24`、`compileSdk 37`、`targetSdk 36`，并通过 Gradle 配置和 baseline focused 守卫验证 settings 是三个 SDK 值的唯一权威来源。
- [x] 2.2 从 `constants.gradle.kts`、`:app`、`:baselineprofile` 和 Android library convention 删除 SDK 数值与模块级赋值，只保留 JDK 21 和应用 versionCode/versionName 的既有唯一来源，并通过全仓库扫描、build-logic tests 及各模块 Gradle 配置验证没有 SDK override 或 JDK 漂移。
- [x] 2.3 将 `verify_target_sdk_upgrade.sh`、`run_target_sdk_local_smoke.sh`、quality snapshot、affected-files 规则与 Android CI 调用迁移为解析 settings DSL，不保留旧 SDK extra 的兼容分支，并通过旧常量缺失、合法 settings、非法 SDK 顺序和 CI API 低于 target 的 fixture 验证。
- [x] 2.4 将 build baseline、target 升级和 smoke class 守卫接入现有 local-fast 与 CI required gates，更新 workflow summary 名称，并验证 `verify_ci_workflow_quality.sh`、`preflight_local.sh --local-fast`、`:app:lintDebug` 和 `:app:assembleDebug` 均能使用新基线。

## 3. 固化依赖稳定性与厂商边界

- [x] 3.1 为 `androidxBaselineProfile` 与 `androidxBenchmark` 的 `1.5.0-rc02` 分别填写精确豁免，记录负责人、原因、验证范围和“稳定且经 AGP 9.3 验证的 1.5.x 可用后退出”条件，并验证其他预览别名、缺失字段或复用豁免均被阻断。
- [x] 3.2 将 `maxAgpVersion=false` 与上述 Baseline Profile 豁免关联，并在依赖守卫中加入 Jetifier/厂商约束：`android.enableJetifier=true` 存在时 AGP 10+ 候选必须失败；通过 fixture 验证 AGP 9.3.2 当前基线允许、AGP 10 候选被阻断且不修改任何厂商 AAR。
- [x] 3.3 将 Coil 从 `3.5.0` 独立升级到 `3.6.0`，仅进行必要的编译兼容调整，并通过 `dependencyInsight`、统一图片管线/缓存 focused tests、相关 Compose instrumentation、lint 和 assemble 确认无混合版本及图片行为回归。
- [x] 3.4 在 Coil 批次通过后，将 kotlinx-datetime 从 `0.8.0-0.6.x-compat` 独立切换到稳定 `0.8.0`，仅进行必要的 API 兼容调整，并通过 `dependencyInsight`、序列化、时区边界、日期判断和倒计时 focused tests 确认无 compat 残留或业务时间语义变化。
- [x] 3.5 重新运行 Android CLI 官方版本查询和应用运行时依赖解析，确认 AGP、Gradle、Kotlin、Compose BOM、Navigation Compose 2 及其余 Jetpack/基础库仍在批准的稳定基线，验证本 change 没有引入无计划升级、动态版本或 Nav3 制品。

## 4. 建立 targetSdk 37 准入而不提升正式 target

- [x] 4.1 新增严格可解析的 target readiness policy，初始写入 approved target 36、candidate target 37、Android 17 Beta、promotion blocked，以及平台行为/厂商/大屏/测试矩阵未验证状态，并通过字段缺失、非法状态和伪造 verified 组合的 fixture 验证 fail-closed。
- [x] 4.2 扩展 target 升级守卫：target 等于 36 时验证当前基线，高于 36 时要求平台 stable、四类状态 verified、候选值一致及独立 OpenSpec change 标识；通过临时将 target 设为 37 的负向 fixture 确认当前一定失败并列出所有未满足项。
- [x] 4.3 修复 `run_target_sdk_local_smoke.sh` 与 `affected-modules.sh` 中不存在的 `ServiceTimeNotificationIntegrationTest`，选择或补充能验证倒计时/通知/精确闹钟拒绝恢复的真实 instrumentation test，并通过 smoke class 完整性守卫及目标 class 单独运行验证。
- [x] 4.4 建立 API 36 阻断式当前-target smoke 集合，覆盖 MainActivity/隐私与登录入口、服务/图片、WebView、通知/精确闹钟及已有自适应 UI tests，并为 NFC、定位、相机、销售评估和厂商能力接入可自动化契约测试与 Release 真机证据要求；在 API 36 emulator 运行该集合并确认失败会阻断对应验证入口。
- [x] 4.5 建立与 API 36 分离的 API 37 Beta readiness 入口，覆盖大屏窗口变化、MessageQueue/反射/native 清单、本地网络、CT/网络栈、后台闹钟音频和厂商 SDK 启动；验证该入口的失败保持 candidate blocked、产生 CI artifact/summary，但不会把 target 36 的正常构建误判为失败。
- [x] 4.6 增加 Manifest/readiness 一致性校验：只要任一正式 Activity 仍依赖固定方向或 `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`，adaptive 状态不得标为 verified；用当前 Manifest 验证 API 37 readiness 保持 blocked，且本 change 不删除这些生产声明。
- [x] 4.7 保留 API 33 Baseline Profile 生成设备并把其结果与 API 36/37 平台验证分别命名和汇总，运行现有 journey guard 及 Profile 生成/配置检查，确认 API 33 成功不能覆盖任一 target smoke/readiness 失败。

## 5. 同步长期事实与迁移决策

- [x] 5.1 修正 `docs/architecture/tech-stack.md` 中应用 versionCode、SDK/JDK、Navigation、CameraX、Coil、kotlinx-datetime 和 Baseline Profile/Benchmark 版本，并新增 focused 文档漂移检查，验证任一受管字段改回旧值都会失败且无需提交完整依赖树。
- [x] 5.2 更新 `docs/architecture/dependency-rules.md`，记录稳定版优先、预览版精确豁免、Baseline Profile 退出条件和 Jetifier 导致的 AGP 10 厂商阻断，并验证引用的守卫、allowlist、catalog alias 和 Gradle 属性都真实存在。
- [x] 5.3 更新 `docs/architecture/roadmap-and-open-gaps.md` 的 API 37 状态，列明 Android 17 stable、平台行为、厂商、大屏和测试矩阵准入项，验证文档与 readiness policy 一致且没有把 Beta readiness 写成已完成。
- [x] 5.4 在现有架构路线图中记录 Navigation 3 评估：当前保持 Navigation Compose `2.10.0`，后续迁移必须先覆盖动态起点、清栈、结果返回、共享 ViewModel scope、配置变化/进程恢复并收敛大对象 route 参数，再由独立原子 change 使用稳定 Nav3；验证 version catalog 未新增 Navigation 3 依赖。

## 6. 综合验证与范围确认

- [x] 6.1 运行全部新增 shell fixture、build baseline、dependency policy、target readiness、smoke class、architecture、Jetpack compat、release validation entry 和 workflow quality 守卫，确认正向基线通过且每个负向 fixture 都返回预期路径/字段。
- [x] 6.2 运行受影响模块的单元测试、Coil/日期时间 focused tests、build-logic tests、`:app:lintDebug`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest` 和依赖解析检查，确认正式产物仍为 `targetSdk 36`、无版本混用且不修改业务/数据契约。
- [x] 6.3 在可用 API 36 emulator 运行当前-target instrumentation smoke，并运行 API 33 Baseline Profile 验证；API 37 Beta 环境可用时运行 readiness lane，否则保持 policy blocked 并验证任何缺失证据都不能授权 target 37。
- [x] 6.4 运行 `preflight_local.sh --local-fast` 和风险相称的 `--full`，如实保留既有 production Release fail-closed 结果，不通过放宽签名、TLS、Lint、R8、厂商或发布守卫制造绿色。
- [x] 6.5 运行 `openspec validate --all --strict --no-interactive`，检查 git diff 只包含本 change 规划的构建基线、依赖、质量守卫、测试入口、长期文档和 OpenSpec 产物，并确认 Room/用户存储、WebView 行为、Manifest 组件行为、Navigation 生产代码及厂商 AAR 均未变化。
