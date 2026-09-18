# CI、质量门禁与发布

最后核对：2026-09-19

本文描述当前脚本和 GitHub Actions 的实际行为。门禁名称/Owner 元数据以 `scripts/quality/quality_gate_registry.json` 为准；是否真正执行则以对应 workflow 和 runner 脚本为准。

## 分层

| 层级 | 目的 | 是否阻断 |
|---|---|---|
| `local-fast` | 提交前快速发现新增 legacy 文件和架构/模块边界回退 | 本地命令失败 |
| `ci-required` | 普通 PR/Push 的构建、Lint、架构和 workflow 治理 | Android CI 阻断 |
| `release-required` | 导出组件、厂商 SDK、签名、生产配置和发布产物安全 | Release 阻断 |
| `observability-only` | 构建基线、质量快照、CI 健康趋势 | 报告本身不直接定义合并策略 |

## 本地入口

`scripts/quality/preflight_local.sh` 是开发者入口：

| 命令 | 实际执行 |
|---|---|
| `bash scripts/quality/preflight_local.sh` | `local-fast` |
| `... --changed-only` | 使用可靠 base ref 缩小检查；无法解析时安全回退到完整 `local-fast` |
| `... --full` | `local-fast` + 双应用 Kotlin 编译 + App、助手、腾讯集成、Common/Data/UI、Identification/PhotoUpload 单测 |
| `... --release` | `--full` + `run_quality_gate.sh` 质量快照 |

`local-fast` 当前包含：

- `check_new_files_guard.sh`
- `verify_architecture_boundaries.sh`
- `verify_module_dependency_whitelist.sh`
- `verify_module_api_visibility.sh`

`--changed-only` 按 `BASE_REF`、`origin/$GITHUB_BASE_REF`、`origin/master`、`origin/main` 的顺序寻找强基线。找不到时不会相信局部 diff，而是扩大扫描，避免 false green。

### 质量快照

`run_quality_gate.sh` 调用 `collect_quality_snapshot.sh`，需要 `jq`。Lint 报告缺失时默认先运行 `:app:lintDebug`，结果写入 `build/quality-snapshot/`。

质量快照包含 production-oriented 厂商 SDK readiness 检查。当前已知 QLZ/腾讯人脸问题仍存在时，该命令失败是预期的 fail-closed 结果，不应通过放宽规则让它变绿。

正式/助手隔离守卫由本地 preflight 与 Android CI/Release 执行，也可单独运行：

```bash
bash scripts/quality/verify_validation_app_isolation.sh .
```

## Android CI

`.github/workflows/android-ci.yml` 保留普通 PR/Push 的 build-only 主阻断路径，并在 affected scope 明确要求时追加独立 instrumentation smoke job：

1. `detect-affected` 计算 Gradle tasks、`run_instrumentation` 和 smoke test classes；Android CI 工作流、smoke runner 或影响分析器本身发生变更时强制执行 instrumentation，避免 CI 控制面改动产生假绿。
2. `verify-build` 执行 ci-required guards、Lint 和 Debug 构建，不启动模拟器；full scope 额外构建 Debug AAB。
3. 仅当 `run_instrumentation=true` 时，`instrumentation-smoke` 先启用并验证 `/dev/kvm` 硬件加速，再在 API 36 x86_64 emulator 上构建 App/androidTest APK，并通过 `.github/scripts/run-instrumentation-smoke.sh` 逐个执行选中的 App test class；KVM 不可用时快速失败，不允许退化为不稳定的软件模拟。
4. Debug APK、构建报告和诊断产物按既有策略上传；smoke 报告和失败 logcat 作为 7 天 artifact 上传，未受影响的改动不承担 emulator 成本。

PR 并发组以 PR 编号保持稳定，不包含随提交变化的 head SHA；同一 PR 推送新提交时会取消旧流水线，避免过期 build 与 emulator job 继续占用资源。主分支 push 不自动取消，确保每个已合入提交仍有独立结果。

该条件 job 仍不是完整业务回归矩阵。普通主阻断路径执行助手单测，但不覆盖正式应用完整业务单测或完整用户旅程，条件 smoke 也只执行 affected scope 选中的 App instrumentation class。完整 `:app` 与 `:core:data` connected tests 通过 `scripts/quality/run_connected_android_tests.sh` 在本地或发布验收环境执行。

`test_ci_upgrade_validation.py` 由 workflow 守卫调用：执行工作流中的 KVM 脚本并注入缺失/权限拒绝场景，在临时 Git 仓库逐项验证三类 CI 路径触发 smoke，并验证 PR 并发分组跨提交稳定且彼此隔离。这些本地守卫不替代真实 Actions 模拟器运行。

当前 ci-required guards：

| 守卫 | 保护内容 |
|---|---|
| `verify_no_tracked_keystore_files.sh` | 禁止 keystore 进入 Git |
| `verify_ci_workflow_quality.sh` | workflow action 版本、timeout、retention、触发和治理约束 |
| `verify_validation_app_isolation.sh` | 正式 Debug/Release 无验证入口、双包依赖/包名、助手导出面 |
| `verify_lint_ignore_policy.sh` | 禁止不受控 Lint ignore |
| `verify_jetpack_compat_apis.sh` | 受保护 Jetpack API 使用 |
| `verify_baselineprofile_journeys.sh` | Baseline Profile 旅程存在且无 TODO |
| `verify_cancellation_guards.sh` | 敏感协程取消处理 |
| `verify_no_empty_catch_blocks.sh` | 禁止空 catch |
| `verify_target_sdk_upgrade.sh` | targetSdk 与 workflow smoke 约束同步 |
| `verify_exact_alarm_permission_config.sh` | 精确闹钟 Manifest 策略 |
| `verify_architecture_boundaries.sh` | 分层、legacy freeze、ViewModel 和代码规模规则 |
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

### Production

- tag 触发和显式 production 模式均按生产要求处理。
- 执行 `verify_vendor_sdk_release_readiness.sh`。
- `assembleRelease` / `bundleRelease` 依赖 `verifyProductionReleaseConfiguration`。
- 要求真实 Release keystore、密码和 alias；禁止 debug keystore fallback。
- 生成压缩 Release APK/AAB，并执行产物、签名、Manifest 和发布元数据检查。

当前 production 必须失败，直到以下问题全部消失：

- Android 内仍有固定 QLZ 测试 key 和 `QLZ_TEST_MODE=true`。
- QLZ 1.3.0.5 可达代码存在弱 TLS trust manager。
- 当前腾讯人脸 ARM64 native library 不满足 16 KB 对齐。
- 人脸 AAR 的 consumer rules 含生产阻断的全局选项。

详见 [QLZ SDK 接入](../integrations/qlz-sdk.md)和[路线图](roadmap-and-open-gaps.md)。

## 其他 workflows

| Workflow | 作用 |
|---|---|
| `Baseline Profile` | 手动/定时生成并校验 Baseline Profile，清理缓存 |
| `Face SDK Migration Check` | 验证本地 AAR 与私有 Maven 来源切换后的 compile/lint/manifest/assemble |
| `CI Health Monitor` | 收集运行健康指标并按阈值报告 |
| `Actions Runs Cleanup` | 定时/手动清理旧 Actions run |

`Face SDK Migration Check` 同样采用 build-only 策略，不把业务测试作为切源阻断项。

## Release-only 关键门禁

| 门禁 | 事实来源 | 常见修复方向 |
|---|---|---|
| Release exported components | `verify_release_exported_components.sh` | 收紧 Manifest 或有依据地更新 allowlist |
| Vendor SDK readiness | `verify_vendor_sdk_release_readiness.sh` | 替换厂商二进制并回归，不加 ignore |
| Production config | `verify_production_release_config.sh` | 删除临时 QLZ 配置、升级厂商 SDK |
| Signing safety | build-logic + Release workflow | 配置真实 keystore，不使用 debug 签名 |
| Baseline profile source | Release workflow | 生成/提交受支持的 profile 或明确 warning |

## Lint waiver 规则

`verify_lint_warning_allowlist.sh` 默认 `LINT_ENFORCE_UNUSED_WAIVERS=auto`：

- 本地：未使用 waiver 作为阻断，推动及时清理。
- GitHub Actions：未使用 waiver 默认仅提示，降低环境差异导致的 post-merge 噪声。
- CI 仍可通过 `LINT_ENFORCE_UNUSED_WAIVERS=true` 强制严格模式。
- 版本目录产生的 `GradleDependency` 与 `NewerVersionAvailable` 仅作为 advisory 输出，不阻断 CI；依赖升级由每周 Dependabot PR 承载，并单独执行兼容性回归。

新增 warning 应优先修复根因。只有有 Owner、范围和退出条件的已知厂商问题才可进入 waiver；production-blocking finding 不能靠 waiver 解除。

## 生成报告的位置

- 质量快照：`build/quality-snapshot/`
- 构建基线：`build/reports/baseline/build-baseline.md`
- Lint：`app/build/reports/`
- 单测：各模块 `build/reports/tests/` 和 `build/test-results/`
- CI 运行指标：调用脚本指定的 `build/` 输出目录或 CI artifact

报告是一次性证据，不提交到 `docs/`。

## 推荐命令

```bash
# 文档/轻量架构改动
bash scripts/quality/preflight_local.sh --local-fast

# Kotlin/业务改动
bash scripts/quality/preflight_local.sh --full

# 完整 App 与 core:data connected tests
ANDROID_SERIAL=emulator-5554 bash scripts/quality/run_connected_android_tests.sh --continue

# 与普通 Android CI 对齐
bash scripts/quality/verify_validation_app_isolation.sh .
./gradlew --no-daemon :app:lintDebug :app:assembleDebug
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt

# 查看完整 release-oriented 快照；当前厂商 blocker 会使其 fail closed
bash scripts/quality/preflight_local.sh --release
```

只运行与改动风险相称的最小集合，但不能用“普通 CI 不跑测试”作为跳过相关单元测试或真机回归的理由。

## 双 APK 验收交付

- `bash scripts/release/build-dual-apks.sh --debug`：两应用真实接口 Debug，输出 `build/outputs/dual-apk/debug/`。
- `bash scripts/release/build-dual-apks.sh --acceptance`：显式 acceptance、强制非生产且禁用签名 fallback，输出 `build/outputs/dual-apk/acceptance/`。
- 打包只选择 Gradle output-metadata.json 当前声明的 APK，校验独立包名、相同版本、模式和文件；失败不导出半套新包。每套包含 `SHA256SUMS`、`artifacts.json`。
- Android CI 始终编译/测试/检查助手并独立上传 `assistant-debug-apk`。Release workflow 仅 acceptance 分支生成 `dual-acceptance-apks`；生产发布仍只包含正式 `:app`，原 fail-closed 厂商门禁不变。
- 脚本回归：`python3 scripts/quality/test_validation_app_isolation.py`、`python3 scripts/release/test_package_dual_apks.py`。

助手设备回归应使用独占的 ARM64 测试模拟器，避免与其他项目同时运行 instrumentation。相机权限测试要求开始时助手未授予相机权限；使用空白测试环境，不对个人手机清数据。登录测试以测试内存会话和 Repository 替身覆盖状态，不发送真实短信或提交真实人脸。

```bash
# 将序列号替换为专用模拟器；不要默认选中连接的真机
ANDROID_SERIAL=emulator-5580 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.ytone.longcare.features.login.ui
ANDROID_SERIAL=emulator-5580 ./gradlew :assistant:connectedDebugAndroidTest
```

测试覆盖登录页 Logo 隔离、助手登录/导航/订单校验/结果展示、相机拒绝和设置授权恢复，以及页面重建。模拟器上的拍照与 R65C 页面测试不能替代 NFC、外接读卡器和服务端人脸真机验收。

NFC 真机专项使用 `AssistantNfcHardwareLifecycleTest`，须显式传入 `nfcHardwareTests=true`，否则跳过硬件用例。它临时切换 NFC 并恢复初始状态，使用真实系统服务的前台分发注册状态验证设置返回、Home/返回、离开页面及 Activity 重建；不模拟贴卡，也不运行相机、人脸或 R65C。执行期间保持设备解锁，不同时运行 Android CLI layout 或其他 instrumentation，并先移开 NFC 标签。`AssistantNfcIntentTest` 单独检查畸形外部 Intent 不触发助手路由。

```bash
# 替换为专用 NFC 真机序列号；只运行 NFC 专项。
ANDROID_SERIAL="NFC_DEVICE_SERIAL" ./gradlew :assistant:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.assistant.AssistantNfcHardwareLifecycleTest,com.ytone.longcare.assistant.AssistantNfcIntentTest \
  -Pandroid.testInstrumentationRunnerArguments.nfcHardwareTests=true
```

自动测试之后仍须实际贴卡，检查卡号展示/复制和重复读取；这些结果才是 NFC 标签读取的真机证据。其他硬件、真实登录和人脸验收需单独安排，不以 NFC 专项通过替代。
