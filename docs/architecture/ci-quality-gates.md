# CI、质量门禁与发布

最后核对：2026-09-20（代码与文档静态核对；非本轮全量运行验收）

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
| `... --full` | `local-fast` + 主应用/读卡 Feature Kotlin 编译 + App、读卡 Feature、腾讯集成、Common/Data/UI、Identification/PhotoUpload 单测 |
| `... --release` | `--full` + `run_quality_gate.sh` 质量快照 |

`local-fast` 当前包含：

- `verify_validation_app_isolation.sh`
- `check_new_files_guard.sh`
- `verify_architecture_boundaries.sh`
- `verify_module_dependency_whitelist.sh`
- `verify_module_api_visibility.sh`

`--full` 的显式测试列表目前不含 `:feature:home:testDebugUnitTest` 和 `:feature:location:testDebugUnitTest`，虽然这两个模块已有测试源码；涉及它们时需补跑，不将 `--full` 描述为全模块全量测试。

`--changed-only` 按 `BASE_REF`、`origin/$GITHUB_BASE_REF`、`origin/master`、`origin/main` 的顺序寻找强基线。找不到时不会相信局部 diff，而是扩大扫描，避免 false green。

### 质量快照

`run_quality_gate.sh` 调用 `collect_quality_snapshot.sh`，需要 `jq`。Lint 报告缺失时默认先运行 `:app:lintDebug`，结果写入 `build/quality-snapshot/`。

质量快照包含厂商 SDK 风险检查。经用户于 2026-09-19 明确确认，当前 QLZ 1.3.0.5 和腾讯人脸 6.6.2 已知事项改为警告；缺失报告或未接受的目标厂商问题仍失败，其他 Lint/签名检查不变。

本地读卡业务隔离守卫由本地 preflight 与 Android CI/Release 执行，也可单独运行：

```bash
bash scripts/quality/verify_validation_app_isolation.sh .
```

## Android CI

`.github/workflows/android-ci.yml` 使用普通 PR/Push 的无设备构建/专项单测主阻断路径，并在 affected scope 明确要求时追加独立 instrumentation smoke job：

1. `detect-affected` 计算 Gradle tasks、`run_instrumentation` 和 smoke test classes；Android CI 工作流、smoke runner 或影响分析器本身发生变更时强制执行 instrumentation，避免 CI 控制面改动产生假绿。
2. `verify-build` 执行 ci-required guards、Lint 和 Debug 构建，不启动模拟器；full scope 额外构建 Debug AAB。
3. 仅当 `run_instrumentation=true` 时，`instrumentation-smoke` 先启用并验证 `/dev/kvm` 硬件加速，再在 API 36 x86_64 emulator 上构建 App/androidTest APK，并通过 `.github/scripts/run-instrumentation-smoke.sh` 逐个执行选中的 App test class；KVM 不可用时快速失败，不允许退化为不稳定的软件模拟。
4. Debug APK、构建报告和诊断产物按既有策略上传；smoke 报告和失败 logcat 作为 7 天 artifact 上传，未受影响的改动不承担 emulator 成本。

PR 并发组以 PR 编号保持稳定，不包含随提交变化的 head SHA；同一 PR 推送新提交时会取消旧流水线，避免过期 build 与 emulator job 继续占用资源。主分支 push 不自动取消，确保每个已合入提交仍有独立结果。

该条件 job 仍不是完整业务回归矩阵。普通主阻断路径执行读卡 Feature 单测及主应用长按入口、NFC 平台、导航专项单测，但不覆盖正式应用完整业务单测或完整用户旅程，条件 smoke 也只执行 affected scope 选中的 App instrumentation class。完整 `:app` 与 `:core:data` connected tests 通过 `scripts/quality/run_connected_android_tests.sh` 在本地或发布验收环境执行。

`test_ci_upgrade_validation.py` 由 workflow 守卫调用：执行工作流中的 KVM 脚本并注入缺失/权限拒绝场景，在临时 Git 仓库逐项验证三类 CI 路径触发 smoke，并验证 PR 并发分组跨提交稳定且彼此隔离。这些本地守卫不替代真实 Actions 模拟器运行。

当前 ci-required guards：

| 守卫 | 保护内容 |
|---|---|
| `verify_no_tracked_keystore_files.sh` | 禁止 keystore 进入 Git |
| `verify_ci_workflow_quality.sh` | workflow action 版本、timeout、retention、触发和治理约束 |
| `verify_validation_app_isolation.sh` | 检测只读本地、无旧助手模块或外部检测组件、主应用身份与共享实现 |
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

`.github/workflows/android-release.yml` 只有一条正式 Release 流程，无额外模式选项。先要求目标 commit 的 Android CI 成功，再执行发布校验；文档提交若没有触发 CI，须先对该提交手动运行 Android CI。

- 通过 `workflow_dispatch` 从分支发布；现有 tag push 拒绝规则保持不变，避免自动递增版本号修改已打标签的提交。
- 执行 `verify_vendor_sdk_release_readiness.sh`。
- `assembleRelease` / `bundleRelease` 依赖 `verifyReleaseConfiguration`，不传额外模式参数。
- 要求真实 Release keystore、密码和 alias；禁止 debug keystore fallback。
- 生成主应用压缩 Release APK/AAB。发布前检查主 APK 签名、包名、版本、不可调试属性、R8 mapping 及导出组件；缺包或失败阻断。
- 自动递增 versionCode、推送版本提交，tag 为 `v<versionName>-<versionCode>`；名称为 `Release v<versionName> (<versionCode>)`，非草稿、非预发布，并设为 Latest。
- APK/AAB 命名为 `app-v<versionName>-<yyMMdd>-<versionCode>-release.apk/aab`，Actions artifact 名称为 `app-release-artifacts`；不改动历史 Release 的现有下载链接。
- `release-checksums.txt` 覆盖主 APK/AAB，主应用 mapping 随 Actions artifact 留存，不再构建或上传助手产物。

用户已明确接受以下当前风险，Release 输出警告而不因此单独失败；这不是问题已修复或全设备兼容的保证：

- Android 内仍有固定 QLZ 测试 key 和 `QLZ_TEST_MODE=true`。
- QLZ 1.3.0.5 可达代码存在弱 TLS trust manager。
- 当前腾讯人脸 ARM64 native library 不满足 16 KB 对齐。
- 人脸 AAR 的 consumer rules 含已知全局选项。

`test_release_policy.py` 由 workflow 守卫调用，覆盖风险告警、旧模式参数拒绝、错误参数、缺失报告和其他版本不自动放行。`test_release_workflow.py` 离线执行实际工作流的产物命名、校验和与元数据片段，以工具替身覆盖主 APK 签名/身份/调试属性失败，并断言单应用上传路径、发布前检查顺序、正式发布标记、目标提交 CI 守卫和读卡隔离。不得通过 `continue-on-error` 或关闭签名/Lint 来放行其他失败。

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
| Vendor SDK risk policy | `verify_vendor_sdk_release_readiness.sh` | 当前已接受事项告警；其他目标厂商问题仍需处理 |
| Release config | `verify_release_config.sh` | 错误参数阻断，当前已接受测试配置告警 |
| Signing safety | build-logic + Release workflow | 配置真实 keystore，不使用 debug 签名 |
| Baseline profile source | Release workflow | 生成/提交受支持的 profile 或明确 warning |

## Lint waiver 规则

`verify_lint_warning_allowlist.sh` 默认 `LINT_ENFORCE_UNUSED_WAIVERS=auto`：

- 本地：未使用 waiver 作为阻断，推动及时清理。
- GitHub Actions：未使用 waiver 默认仅提示，降低环境差异导致的 post-merge 噪声。
- CI 仍可通过 `LINT_ENFORCE_UNUSED_WAIVERS=true` 强制严格模式。
- 版本目录产生的 `GradleDependency` 与 `NewerVersionAvailable` 仅作为 advisory 输出，不阻断 CI；依赖升级由每周 Dependabot PR 承载，并单独执行兼容性回归。

新增 warning 应优先修复根因。只有有 Owner、范围和退出条件的已知厂商问题才可进入 waiver；未经明确接受的发布阻断问题不能靠新增 waiver 绕过。

## 生成报告的位置

- 质量快照：`build/quality-snapshot/`
- 构建基线：`build/reports/baseline/build-baseline.md`
- Lint：`app/build/reports/`
- 单测：各模块 `build/reports/tests/` 和 `build/test-results/`
- CI 运行指标：调用脚本指定的 `build/` 输出目录或 CI artifact

上述机器生成报告是一次性证据，不提交到 `docs/`。人工维护的[项目整体分析](../analysis/project-review.md)属于有来源和复核日期的优化基线，不保存构建输出或会话日志。

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

# 查看完整 release-oriented 快照，已接受厂商风险仍输出警告
bash scripts/quality/preflight_local.sh --release
```

只运行与改动风险相称的最小集合，但不能用“普通 CI 不跑测试”作为跳过相关单元测试或真机回归的理由。

## 读卡检测验收

自动化覆盖读卡解析、大 Logo 长按/短按与无震动、取消/生命周期与 Navigation 3 返回，正式包构建与 R8 检查不替代真机流程。

真机需验证：登录页中央大 Logo 长按先确认且无震动，普通点击及非入口区域不触发；取消、后台、重复长按和返回不会误跳，无摇动监听。实际 NFC 标签及 R65C 分别验收读取、复制、清空、模式切换与退出释放。检测无须登录，不提交业务数据。用户选择稍后进行时保留未完成验收项。
