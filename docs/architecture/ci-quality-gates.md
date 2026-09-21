# CI、质量门禁与发布

最后核对：2026-09-21（CI/CD 精简；本地构建与测试已核对，线上结果另行验收）

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
| `... --full` | `local-fast` + 主应用/读卡 Feature Kotlin 编译 + 完整 Android 模块 Debug 单测及 Model/Domain JVM 单测 |
| `... --release` | `--full` + `run_quality_gate.sh` 质量快照 |

`local-fast` 当前包含：

- `verify_validation_app_isolation.sh`
- `check_new_files_guard.sh`
- `verify_architecture_boundaries.sh`
- `verify_module_dependency_whitelist.sh`
- `verify_module_api_visibility.sh`

`--full` 与 CI 共用 `run_jvm_tests.sh`：执行无模块前缀的 `testDebugUnitTest` 及 `:core:model:test :core:domain:test`。没有测试源码的模块可能显示 NO-SOURCE；Gradle 缓存命中不等于重新执行测试。

`--changed-only` 按 `BASE_REF`、`origin/$GITHUB_BASE_REF`、`origin/master`、`origin/main` 的顺序寻找强基线。找不到时不会相信局部 diff，而是扩大扫描，避免 false green。

### 质量快照

`run_quality_gate.sh` 调用 `collect_quality_snapshot.sh`，需要 `jq`。Lint 报告缺失时默认先运行 `:app:lintDebug`，结果写入 `build/quality-snapshot/`。

质量快照包含厂商 SDK 风险检查。经用户于 2026-09-19 明确确认，当前 QLZ 1.3.0.5 和腾讯人脸 6.6.2 已知事项改为警告；缺失报告或未接受的目标厂商问题仍失败，其他 Lint/签名检查不变。

本地读卡业务隔离守卫由本地 preflight 与 Android CI/Release 执行，也可单独运行：

```bash
bash scripts/quality/verify_validation_app_isolation.sh .
```

## Android CI

代码/构建改动执行完整 JVM 业务测试、App Debug 构建、App/读卡 Lint 和质量守卫；不再使用固定 App 测试类过滤、伪模块影响列表或 full/partial 构建分支。

1. `select-checks` 始终验证文档，并由 `select_ci_smoke.sh` 选择是否需要构建和设备冒烟；仅文档变更不启动 Gradle/模拟器。
2. `verify-build` 调用 `run_ci_checks.sh` 和 `run_jvm_tests.sh`，上传主应用 Debug APK 和所有模块测试报告，不常规构建 Debug AAB。
3. `instrumentation-smoke` 在前置构建成功且所选路径需要时执行。API 36 模拟器以真实启动用例为基础，按导航、WebView、服务、销售 UI 等路径追加离线用例；不自动运行需账号/硬件的 opt-in 测试。
4. 手动 CI 或差异基线不可用时执行完整基础验证及固定离线冒烟集合，不将 HEAD 自身比较为空视为无需验证。
5. PR 并发组跨提交稳定，新提交只取消同 PR 旧运行；主分支 Push 不自动取消。CI 本身仅有读取权限，无缓存删除 job。

`enable_kvm.sh` 被 CI 和独立 Baseline 工作流共用：等待 udev 权限事件完成，再确认 KVM 存在且可读写，缺失/超时/权限不足均阻断。Mac 本地模拟器不使用 Linux KVM；本地成功不替代 Actions 的 Linux 验收。

完整 JVM 集合仍不等于全部硬件或真实业务旅程验收。完整 connected tests 可由 `run_connected_android_tests.sh` 在指定设备执行。测试报告区分执行、缓存、跳过和未执行。

工作流守卫执行 `test_*.py`：覆盖选择器、KVM、PR 并发、门禁调用、签名/产物、版本推送时机及存储保护。测试通过本地 Git 与工具替身执行，不发布版本或删除远端数据；步骤标题和固定时长不再作为主要约束。

当前 ci-required guards：

| 守卫 | 保护内容 |
|---|---|
| `verify_no_tracked_keystore_files.sh` | 禁止 keystore 进入 Git |
| `verify_ci_workflow_quality.sh` | 必要权限、版本完整性、触发、验证和发布行为契约 |
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

唯一入口为分支上的 `workflow_dispatch`，无发布模式、Tag Push、临时依赖切换或生成 Baseline 选项。依赖来源由版本化构建配置管理；Baseline 使用独立工作流。

- 按准确源 SHA/分支确认 Android CI 成功且 `verify-build` 真正通过；文档专用绿色结果不能充当构建证据，需手动 CI。
- 不重建无消费的 Debug APK；保留必要的 Lint 与厂商风险检查，常规静态门禁由同源提交 CI 提供。
- 要求真实 Release 签名；`assembleRelease / bundleRelease` 仍依赖 `verifyReleaseConfiguration`，R8/资源压缩及导出组件检查不变。
- 本地递增版本并同步技术栈文档，正式 APK/AAB、签名、身份、版本、不可调试属性、mapping 及校验和全部通过后，才提交并推送版本。
- 发布使用稳定并发组且不自动取消进行中的发布；分支已前进或标签存在时拒绝强推/覆盖，发布失败保留诊断，不自动改写远端历史。
- 标签为 `v<versionName>-<versionCode>`，正式、非草稿、非预发布，设为 Latest；APK/AAB 保留版本、日期、版本号和 release 标识。
- 主应用 APK/AAB、SHA-256 校验和及 mapping ZIP 进入 GitHub Release；Actions 同时留存验证产物。历史版本不修改，不构建独立助手。

`test_release_workflow.py` 和 `test_release_sequence.py` 覆盖产物命名、签名/身份失败、源 CI、版本只在本地准备、远端分支前进、已存在标签和推送时序。`test_release_policy.py` 保护既有厂商风险接受范围；不使用签名/Lint 豁免制造绿色。

用户已明确接受以下当前风险，Release 输出警告而不因此单独失败；这不是问题已修复或全设备兼容的保证：

- Android 内仍有固定 QLZ 测试 key 和 `QLZ_TEST_MODE=true`。
- QLZ 1.3.0.5 可达代码存在弱 TLS trust manager。
- 当前腾讯人脸 ARM64 native library 不满足 16 KB 对齐。
- 人脸 AAR 的 consumer rules 含已知全局选项。


详见 [QLZ SDK 接入](../integrations/qlz-sdk.md)和[路线图](../analysis/project-review.md#17-分阶段技术方案与实施顺序)。

## 其他 workflows

| Workflow | 作用 |
|---|---|
| `Baseline Profile` | 独立手动/定时生成并校验 Baseline Profile |
| `Face SDK Migration Check` | 验证本地 AAR 与私有 Maven 来源切换后的 compile/lint/manifest/assemble |
| `CI Health Monitor` | 收集运行健康指标并按阈值报告 |
| `Actions Runs Cleanup` | 唯一存储维护入口，清理过期 run/artifact/cache |

`Face SDK Migration Check` 仅相关接入文件变化或手动运行时执行切源构建检查；普通业务单测由 Android CI 负责。

存储维护不影响已完成构建的结果。近期创建或访问的缓存受到保护；报告和运行记录至少保留 7 天，运行保留期不能短于 artifact 保留期。容量超标优先删除到期对象，仍超限只告警，不提前删除保护期内对象；GitHub Release 不在清理范围内。

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

# 与 CI 相同的 JVM、Lint、Debug 构建入口
bash scripts/quality/verify_validation_app_isolation.sh .
bash scripts/quality/run_jvm_tests.sh :app:lintDebug :feature:carddiagnostics:lintDebug :app:assembleDebug
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt

# 查看完整 release-oriented 快照，已接受厂商风险仍输出警告
bash scripts/quality/preflight_local.sh --release
```

只运行与改动风险相称的最小集合，但不能用“普通 CI 不跑测试”作为跳过相关单元测试或真机回归的理由。

## 读卡检测验收

自动化覆盖读卡解析、大 Logo 长按/短按与无震动、取消/生命周期与 Navigation 3 返回，正式包构建与 R8 检查不替代真机流程。

真机需验证：登录页中央大 Logo 长按先确认且无震动，普通点击及非入口区域不触发；取消、后台、重复长按和返回不会误跳，无摇动监听。实际 NFC 标签及 R65C 分别验收读取、复制、清空、模式切换与退出释放。检测无须登录，不提交业务数据。用户选择稍后进行时保留未完成验收项。
