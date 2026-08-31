# CI、质量门禁与发布

最后核对：2026-08-27

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
| `... --full` | `local-fast` + `:app:compileDebugKotlin` + `:app:testDebugUnitTest` |
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

共享 Release 隐藏验证入口的专用守卫目前由 Android CI/Release 直接执行；本地需要单独运行：

```bash
bash scripts/quality/verify_release_validation_entry.sh .
```

## Android CI

`.github/workflows/android-ci.yml` 保留普通 PR/Push 的 build-only 主阻断路径，并在 affected scope 明确要求时追加独立 instrumentation smoke job：

1. `detect-affected` 计算 Gradle tasks、`run_instrumentation` 和 smoke test classes。
2. `verify-build` 执行 ci-required guards、Lint 和 Debug 构建，不启动模拟器；full scope 额外构建 Debug AAB。
3. 仅当 `run_instrumentation=true` 时，`instrumentation-smoke` 在 API 36 x86_64 emulator 上构建 App/androidTest APK，并通过 `.github/scripts/run-instrumentation-smoke.sh` 逐个执行选中的 App test class。
4. Debug APK、构建报告和诊断产物按既有策略上传；smoke 报告和失败 logcat 作为 7 天 artifact 上传，未受影响的改动不承担 emulator 成本。

该条件 job 仍不是完整业务回归矩阵。普通主阻断路径本身不执行业务单元测试或完整用户旅程，条件 smoke 也只执行 affected scope 选中的 App instrumentation class。完整 `:app` 与 `:core:data` connected tests 通过 `scripts/quality/run_connected_android_tests.sh` 在本地或发布验收环境执行。

当前 ci-required guards：

| 守卫 | 保护内容 |
|---|---|
| `verify_no_tracked_keystore_files.sh` | 禁止 keystore 进入 Git |
| `verify_ci_workflow_quality.sh` | workflow action 版本、timeout、retention、触发和治理约束 |
| `verify_release_validation_entry.sh` | Debug/Release 共享隐藏验证入口与不可导出契约 |
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
- QLZ 1.3.0.2 可达代码存在弱 TLS trust manager。
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
bash scripts/quality/verify_release_validation_entry.sh .
./gradlew --no-daemon :app:lintDebug :app:assembleDebug
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt

# 查看完整 release-oriented 快照；当前厂商 blocker 会使其 fail closed
bash scripts/quality/preflight_local.sh --release
```

只运行与改动风险相称的最小集合，但不能用“普通 CI 不跑测试”作为跳过相关单元测试或真机回归的理由。
