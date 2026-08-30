# CI、质量门禁与发布

最后核对：2026-08-31

本文描述当前脚本和 GitHub Actions 的实际行为。门禁名称/Owner 元数据以 `scripts/quality/quality_gate_registry.json` 为准；是否真正执行则以对应 workflow 和 runner 脚本为准。

## 分层

| 层级 | 目的 | 是否阻断 |
|---|---|---|
| `local-fast` | 提交前快速发现构建/依赖/文档漂移、失效 smoke 类、新增 legacy 文件和架构/模块边界回退 | 本地命令失败 |
| `ci-required` | 普通 PR/Push 的构建、Lint、架构和 workflow 治理 | Android CI 阻断 |
| `release-required` | 导出组件、厂商 SDK、签名、生产配置和发布产物安全 | Release 阻断 |
| `observability-only` | 质量快照、CI 健康和 Startup/Profile 趋势 | 报告本身不直接定义合并策略 |

## 本地入口

`scripts/quality/preflight_local.sh` 是开发者入口：

| 命令 | 实际执行 |
|---|---|
| `bash scripts/quality/preflight_local.sh` | `local-fast` |
| `... --changed-only` | 使用可靠 base ref 缩小检查；无法解析时安全回退到完整 `local-fast` |
| `... --full` | `local-fast` + `:app:compileDebugKotlin` + `:app:testDebugUnitTest` + Debug Mock 聚焦契约 |
| `... --release` | `--full` + `run_quality_gate.sh` 质量快照 |

`local-fast` 当前包含：

- `check_new_files_guard.sh`
- `test_android_build_governance.sh`、`test_ci_workflow_quality.sh`、项目 R8 规则 verifier/fixtures，以及 build baseline、dependency policy、target readiness/matrix、smoke class、workflow、tech-stack 正向守卫；affected changed-path fixture 只由 Android build governance 入口执行一次
- `test_legacy_feature_file_allowlist.sh`、`test_user_storage_boundaries.sh`、`test_identification_feature_boundary.sh`、`test_login_feature_boundary.sh`、`test_entry_navigation_contracts.sh` 与 `test_instrumentation_test_ownership.sh` 的正反 fixture
- `verify_entry_navigation_contracts.sh`（Navigation Testing 仅限 androidTest、入口 renderer/host 保持 `internal`、focused 测试类完整）
- `verify_architecture_boundaries.sh`（内部执行 legacy 精确快照、身份/登录 feature 所有权、用户存储/后台身份、WebView 原生 bridge 和 instrumentation test APK 所有权守卫）
- `verify_module_dependency_whitelist.sh`
- `verify_module_api_visibility.sh`
- Startup/Baseline Profile 场景配置、确定性旅程、fully-drawn、AndroidX benchmark JSON 归一化、文本规则和 Release artifact 的正反 fixture；这些检查不启动模拟器
- `test_debug_mock_network.sh` / `verify_debug_mock_network.py`：默认关闭、Debug-only source set、Release 真实上传绑定、测试身份隔离和长期文档一致性；`--full` 另执行 `run_debug_mock_network_contracts.sh` 的路由、fixture、上传、更新、第三方与 performance/offline focused tests

`--changed-only` 按 `BASE_REF`、`origin/$GITHUB_BASE_REF`、`origin/master`、`origin/main` 的顺序寻找强基线。找不到时不会相信局部 diff，而是扩大扫描，避免 false green。

### 质量快照

`run_quality_gate.sh` 调用 `collect_quality_snapshot.sh`，需要 `jq`。Lint 报告缺失时默认先运行 `:app:lintDebug`，结果写入 `build/quality-snapshot/`。

质量快照包含 production-oriented 厂商 SDK readiness 检查。当前已知 QLZ/腾讯人脸问题仍存在时，该命令失败是预期的 fail-closed 结果，不应通过放宽规则让它变绿。

共享 Release 隐藏验证入口的专用守卫目前由 Android CI/Release 直接执行；本地需要单独运行：

```bash
bash scripts/quality/verify_release_validation_entry.sh .
bash scripts/quality/test_release_validation_entry.sh
```

### Connected instrumentation 专项入口

完整连接设备回归使用受支持入口：

```bash
ANDROID_SERIAL=<device-serial> bash scripts/quality/run_connected_instrumentation_suite.sh
```

`scripts/quality/instrumentation_test_modules.txt` 当前只登记四个非空 test APK owner：`:app`、`:core:data`、`:feature:identification`、`:feature:login`。入口据此生成四个模块限定的 `connectedDebugAndroidTest` task，并分别保留模块报告；它不会调用根级 task，也不会为无测试 Library 增加 runner。Managed Device 名称和类选择器仍只由 `target_platform_test_matrix.properties` 管理。

## Android CI

`.github/workflows/android-ci.yml` 以构建门禁为基础，并按 affected scope 选择性追加 API 36 smoke：

摘要明确区分 `build-only`、`app-focused` 和 `login-feature-focused`；两类 focused 同时受影响时组合显示。构建成功但设备测试未请求时只表示 build-only，不能作为业务 instrumentation 通过证据。

1. 计算 affected scope 和 Gradle tasks。
2. 执行 ci-required shell guards。
3. 执行 `:app:lintDebug :app:assembleDebug`；full scope 额外执行 `:app:bundleDebug`。当 `:feature:login` 受影响时追加 feature compile/unit/lint/androidTest compile。
4. 对生成的 Lint 文本报告执行 warning allowlist。
5. 上传 Debug APK；full scope 上传 AAB 和可用的 Baseline Profile APK。
6. 当 app 或 login feature 的 instrumentation 标志为 true 时，在各自 `pixel6Api36` test APK 上运行分离的 blocking smoke 并上传报告；feature-owned 登录 UI class 不得进入 app test APK。
7. 总是上传报告，失败时上传额外诊断产物。

普通 Android CI **不执行全部业务单元测试或完整用户旅程**；受影响变更会执行受管的 API 36 UI/平台 smoke。相关改动仍应在本地 `--full`、专项验证或发布验收中运行更完整的 focused tests 和真机链路。

当前 ci-required guards：

| 守卫 | 保护内容 |
|---|---|
| `verify_no_tracked_keystore_files.sh` | 禁止 keystore 进入 Git |
| `verify_ci_workflow_quality.sh` | workflow action 版本、timeout、retention、触发和治理约束 |
| `verify_release_validation_entry.sh` / `test_release_validation_entry.sh` | Debug/Release 共享隐藏验证入口、五动作 app 适配、不可导出契约及守卫自验证 |
| `verify_lint_ignore_policy.sh` | 禁止不受控 Lint ignore |
| `verify_jetpack_compat_apis.sh` | 受保护 Jetpack API 使用 |
| `verify_baselineprofile_journeys.sh` | 六个确定场景、准确节点、四 Startup/两 Baseline-only 分层、双 compilation mode 对称、fully-drawn 与性能 source-set 隔离 |
| `verify_cancellation_guards.sh` | 敏感协程取消处理 |
| `verify_no_empty_catch_blocks.sh` | 禁止空 catch |
| `verify_target_sdk_upgrade.sh` | targetSdk 与 workflow smoke 约束同步 |
| `verify_android_build_baseline.sh` | Settings Plugin SDK 唯一来源、JDK/应用版本和 AGP/plugin 一致性，以及定位/上传/倒计时/身份 Feature 的最小构建能力与直接依赖边界 |
| `verify_project_r8_rules.py` / `test_project_r8_rules.sh` | Release 优化默认文件/项目规则/腾讯规则接线、危险或已删除规则、package-wide allowlist，以及 Retrofit `ApiResult<T>` 精确泛型签名约束 |
| `verify_dependency_policy.sh` | 稳定依赖、精确预览豁免与 Jetifier/AGP 10 边界 |
| `verify_target_sdk_readiness.sh` | 正式 target 与候选 readiness 状态组合、Manifest adaptive 一致性 |
| `verify_target_platform_test_matrix.sh` | API 33/36/37 验证目标和设备严格分离，app/login feature class 归属正确 test APK |
| `verify_instrumentation_smoke_classes.sh` | workflow/脚本/matrix 引用的 instrumentation 类真实存在；matrix 的 app/login 字段只能解析到各自 test APK |
| `verify_instrumentation_test_ownership.sh` / `test_instrumentation_test_ownership.sh` | 四模块清单与非空 `src/androidTest` 双向一致，runner/依赖/聚合 task 完整，遗漏、陈旧、未知和根级聚合稳定失败 |
| `verify_entry_navigation_contracts.sh` | Navigation Testing 不进入生产依赖、入口 renderer/host 保持 `internal`，且入口/Home/Sales focused 测试契约完整；正反 fixture 验证守卫本身 |
| `verify_debug_mock_network.py` / `test_debug_mock_network.sh` | Debug Mock 默认关闭、第一方 fail-closed 路由、Debug-only fixture/上传 fake、Release/测试身份隔离与文档一致性；负例证明默认开启和 Release 泄漏会失败 |
| `run_debug_mock_network_contracts.sh` | 第一方路由/fixture、上传选择器、更新 Worker、第三方客户端与 performance/offline 优先级的 focused JVM 契约 |
| `verify_tech_stack_baseline.sh` | 技术栈长期字段与 Settings/constants/catalog/wrapper 一致 |
| `verify_exact_alarm_permission_config.sh` | 精确闹钟 Manifest 策略 |
| `verify_architecture_boundaries.sh` | 退役冗余指纹、分层、legacy freeze、身份/登录 feature 所有权、用户存储/任务身份、WebView bridge、ViewModel 和代码规模规则 |
| `verify_identification_feature_boundary.sh` | 禁止身份 UI 回流 app、feature 反向引用 app 壳层及 app 绕过公开 API；由架构总守卫调用 |
| `verify_login_feature_boundary.sh` | 禁止登录 UI/校验面板回流 app、feature 反向引用 app 壳层/Activity 及 app 绕过公开 API；由架构总守卫调用 |
| `verify_module_dependency_whitelist.sh` | Gradle 项目模块依赖边 |
| `verify_module_api_visibility.sh` | 跨模块公共 API 边界 |
| `verify_lint_warning_allowlist.sh` | Lint 报告新增 warning 和 waiver 漂移 |

## Android Release

`.github/workflows/android-release.yml` 先要求目标 commit 的 Android CI 成功，再执行发布校验。手动触发必须选择模式：

### Acceptance

- 只允许 `workflow_dispatch`。
- 工作流传入 `release.production=false`、`release.acceptance=true`。
- 临时 QLZ key/test mode 和已知厂商包只在明确验收模式下允许。
- APK、AAB 和 GitHub Release 名称必须标记为验收用途。
- Release 先完成 minify/资源收缩，再把本次构建后重命名的显式 APK/AAB 路径交给 artifact verifier；不得用工作区旧 Profile 或只统计 `app/src` 文件替代产物证据。

### Production

- tag 触发和显式 production 模式均按生产要求处理。
- 执行 `verify_vendor_sdk_release_readiness.sh`。
- `assembleRelease` / `bundleRelease` 依赖 `verifyProductionReleaseConfiguration`。
- 要求真实 Release keystore、密码和 alias；禁止 debug keystore fallback。
- 生成压缩 Release APK/AAB，并执行产物、签名、Manifest 和发布元数据检查。

当前 production 必须失败，直到以下问题全部消失：

- Android 内仍有固定 QLZ 测试 key 和 `QLZ_TEST_MODE=true`。
- QLZ 1.3.0.2 可达代码存在弱 TLS trust manager。
- 当前腾讯人脸 ARM64 native library 不满足 16 KB 对齐。
- 人脸 AAR 的 consumer rules 含生产阻断的全局选项。

详见 [QLZ SDK 接入](../integrations/qlz-sdk.md)和[路线图](roadmap-and-open-gaps.md)。

## 其他 workflows

| Workflow | 作用 |
|---|---|
| `Baseline Profile` | 手动/定时在 API 33 生成六场景 Baseline/Startup Profile，严格校验规则分层并仅为真实规则变化创建 PR；模拟器证据不声明性能收益 |
| `Face SDK Migration Check` | 验证本地 AAR 与私有 Maven 来源切换后的 compile/lint/manifest/assemble |
| `CI Health Monitor` | 收集运行健康指标并按阈值报告 |
| `Actions Runs Cleanup` | 定时/手动清理旧 Actions run |

Android CI 还提供显式 `run_api37_readiness` 手动输入：在 app 与 login feature 各自的 `pixelTabletApi37` 16 KB image/test APK 上运行 Android 17 Beta readiness，并总是上传 policy 与测试报告。该 job 的 policy/emulator 步骤可容错，失败只保持 candidate blocked，不会把正式 target 36 的构建误判为失败。Baseline Profile workflow 则继续使用 API 33 生成规则和验证报告格式；三类结果在名称、summary 和 artifact 中互不替代。

`Face SDK Migration Check` 同样采用 build-only 策略，不把业务测试作为切源阻断项。

## Release-only 关键门禁

| 门禁 | 事实来源 | 常见修复方向 |
|---|---|---|
| Release exported components | `verify_release_exported_components.sh` | 收紧 Manifest 或有依据地更新 allowlist |
| Vendor SDK readiness | `verify_vendor_sdk_release_readiness.sh` | 替换厂商二进制并回归，不加 ignore |
| Production config | `verify_production_release_config.sh` | 删除临时 QLZ 配置、升级厂商 SDK |
| Signing safety | build-logic + Release workflow | 配置真实 keystore，不使用 debug 签名 |
| Profile 文本与发布产物 | Release workflow + `verify_text_profiles.py` + `verify_release_profile_artifacts.py` | 重新生成严格分层文本；使用本次 minified APK/AAB 修复 ART Profile、R8/DEX 或测试能力泄漏，缺失时直接失败 |
| Debug Mock / test capability isolation | `verify_debug_mock_network.py` + `verify_release_profile_artifacts.py` | 保持 Release `USE_MOCK_DATA=false`、真实上传绑定，并从本次 APK/AAB 移除 Debug route/assets/fake 与测试身份 marker |

## 项目 R8 三层证据

| 层级 | 职责 | 不替代 |
|---|---|---|
| 静态项目守卫 | `verify_project_r8_rules.py` 检查 Release 三类规则输入、全局 `-dont*`、已删除指纹、12 条 package-wide allowlist 和 `ApiResult<T>` 单类运行时规则；fixtures 证明缺项或回退会 fail-closed | 不模拟 R8 全程序分析或厂商反射行为 |
| Configuration Analyzer | `:app:analyzeReleaseR8Config` 在相同依赖与 Release 输入下比较项目规则 origin、unused/subsumed 和 Optimization/Shrinking/Obfuscation；当前清理前后为 `65.7/72.7/65.8%` → `73.0/73.2/73.2%` | Analyzer 分数不证明应用可运行，也不证明厂商生产条件已解决 |
| acceptance 产物与设备 | 使用本次 minified APK/AAB 检查 mapping、资源收缩、ART Profile、R8/DEX、签名、Manifest，再在 API 36 覆盖类型安全参数/进程恢复及相关反射/JNI 路径 | 无账号、订单、蓝牙/NFC 外设时不得把未执行的完整业务链路标记为通过 |

`ApiResult<T>` 是项目自定义 Retrofit suspend call adapter 的反射契约。R8 full mode 若只保留外层 `Continuation`，可能把 `Continuation<? super ApiResult<T>>` 退化为原始 `ApiResult` 并在 Release 启动时报错；项目因此只保留 `com.ytone.longcare.model.result.ApiResult` 的允许优化/收缩/混淆约束，不恢复 Kotlinx 全局规则或整包 keep。Analyzer 报告继续只保存在 `app/build/outputs/mapping/release/` 或 CI artifact，不作为稳定源码接口提交。

## Startup/Baseline Profile 四层证据

| 层级 | 证明内容 | 明确不证明 |
|---|---|---|
| 源码与正反 fixture | 六场景目录、正式存储 API 预置、准确 tag、Startup/Baseline-only 边界、fully-drawn、双模式对称及 production 隔离 | Profile 已被打包或带来收益 |
| API 33 生成文本 | `startup-prof.txt` 非空且是 `baseline-prof.txt` 的严格规则子集，差集来自两条声明业务旅程 | 模拟器绝对耗时或真实设备收益 |
| acceptance APK/AAB | 显式本次产物含可解析 `baseline.prof`/`baseline.profm`；R8 布局/profile-guided 开启、Startup DEX checksum 对齐，且无性能测试能力泄漏 | 厂商生产条件已经解决 |
| 设备 benchmark | 四 Startup 场景的 None/Profile 各 10 次均有 TTID/TTFD、设备/API/ABI/构建 SHA 完整 | 单轮模拟器结果不能证明收益；收益需同一 ARM64 真机至少两轮满足预算 |

TTID 由系统记录；TTFD 由隐私、Login、护理 Home 或销售 Home 的互斥可交互根页面释放。API 33 managed device 的文本生成工作流报告标记为 `journey-and-dependency`，归一化 benchmark 报告标记为 `journey-and-report-format-only`；两者的收益都必须是 `unverified`。当前仍未完成受控 ARM64 真机两轮验收，因此不能宣称 Profile 性能收益已验证。

## Lint waiver 规则

`verify_lint_warning_allowlist.sh` 默认 `LINT_ENFORCE_UNUSED_WAIVERS=auto`：

- 本地：未使用 waiver 作为阻断，推动及时清理。
- GitHub Actions：未使用 waiver 默认仅提示，降低环境差异导致的 post-merge 噪声。
- CI 仍可通过 `LINT_ENFORCE_UNUSED_WAIVERS=true` 强制严格模式。

新增 warning 应优先修复根因。只有有 Owner、范围和退出条件的已知厂商问题才可进入 waiver；production-blocking finding 不能靠 waiver 解除。

## 生成报告的位置

- 质量快照：`build/quality-snapshot/`
- Lint：`app/build/reports/` 与受影响 feature 的 `build/reports/`
- 单测：各模块 `build/reports/tests/` 和 `build/test-results/`
- CI 健康指标：`build/ci-health/` 或 CI artifact
- Startup/Profile 一次性报告：`build/reports/startup-profile/`、`:baselineprofile` 的 managed-device additional output 或 CI artifact

报告是一次性证据，不提交到 `docs/`。

## 推荐命令

```bash
# 文档/轻量架构改动
bash scripts/quality/preflight_local.sh --local-fast

# Kotlin/业务改动
bash scripts/quality/preflight_local.sh --full

# 入口认证、Home owner/角色或 Sales 内部导航改动；设备必须为 API 36
bash scripts/quality/run_entry_navigation_focused.sh --device <api-36-serial>

# 与普通 Android CI 对齐
bash scripts/quality/verify_release_validation_entry.sh .
./gradlew --no-daemon :app:lintDebug :app:assembleDebug
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt

# 查看完整 release-oriented 快照；当前厂商 blocker 会使其 fail closed
bash scripts/quality/preflight_local.sh --release
```

只运行与改动风险相称的最小集合，但不能用“普通 CI 不跑测试”作为跳过相关单元测试或真机回归的理由。
