# CI、质量门禁与发布

最后核对：2026-08-28

本文描述当前脚本和 GitHub Actions 的实际行为。是否真正执行以 workflow、复用 action 和 runner 脚本为准。`scripts/quality/quality_gate_registry.json` 已同步 QLZ 制品与 production config 元数据，但仍只覆盖选定门禁；在全量 registry 对齐 change 完成前，它不能替代 workflow 中的实际执行清单。

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

质量快照包含 production-oriented 厂商 SDK readiness 和批准 QLZ AAR 完整性检查。当前批准 QLZ 的内部 TLS finding 只输出已接受外部风险信息；未知 QLZ 来源、腾讯人脸生产阻断项或其他新增根因仍会 fail closed。

共享 Release 隐藏验证入口的专用守卫目前由 Android CI/Release 直接执行；本地需要单独运行：

```bash
bash scripts/quality/verify_release_validation_entry.sh .
```

## Android CI

`.github/workflows/android-ci.yml` 的正常阻断策略是 build-only：

1. 计算 affected scope 和 Gradle tasks。
2. 执行 ci-required shell guards。
3. 执行 `:app:lintDebug :app:assembleDebug`；full scope 额外执行 `:app:bundleDebug`。
4. 对生成的 Lint 文本报告执行 warning allowlist。
5. 上传 Debug APK；full scope 上传 AAB 和可用的 Baseline Profile APK。
6. 总是上传报告，失败时上传额外诊断产物。

普通 Android CI **不执行业务单元测试、UI assertion 或完整用户旅程**。这是当前明确的流水线策略，不代表测试不重要：相关改动应在本地 `--full`、专项验证或发布验收中运行对应测试。

当前 ci-required guards：

| 守卫 | 保护内容 |
|---|---|
| `verify_gradle_stability.sh` | Wrapper/Gradle 基础稳定性；由共享 Android build environment 执行 |
| `verify_no_tracked_keystore_files.sh` | 禁止 keystore 进入 Git |
| `verify_qlz_sdk_artifact.sh` | 固定批准 QLZ AAR 文件名/SHA-256/唯一性/入口类；Release 后继续检查 APK/AAB |
| QLZ shell fixtures | 覆盖 AAR 篡改、正式/验收配置矩阵、vendor 风险分类和 Lint source scope 的负向路径 |
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

Android CI summary 和 registry 是导航信息，不是执行证据；新增或调整门禁时必须同步 workflow 自检，并以实际命令结果为准。

## Android Release

`.github/workflows/android-release.yml` 先检查触发时 `GITHUB_SHA` 的 Android CI，再执行发布校验。随后 workflow 会提交并推送 versionCode（可选同时更新 profile），并把这个新 commit 作为 GitHub Release 目标；当前没有再次要求新 commit 的 Android CI 成功，因此“触发 commit 已验证”不等于“实际发布 commit 已验证”。这是发布绑定的 P1 开放问题。手动触发必须选择模式：

### Acceptance

- 只允许 `workflow_dispatch`。
- 工作流传入 `release.production=false`、`release.acceptance=true`。
- 从独立 `QLZ_ACCEPTANCE_SDK_KEY` environment secret 取得验收 key，并显式设置测试模式；缺任一输入即失败。
- 当前批准 QLZ AAR 与 Production 共用且必须存在，但验收产物不能作为正式证据。
- APK、AAB 和 GitHub Release 名称必须标记为验收用途。

### Production

- workflow 虽声明 `v*` tag trigger，但 `Reject tag-triggered auto version bump` 会让所有 tag push 在构建前失败；当前真正可产生产流程的入口是 `workflow_dispatch` 显式选择 production。触发契约与文档/守卫仍需统一。
- 从独立 `QLZ_PRODUCTION_SDK_KEY` environment secret 取得正式 key，强制 QLZ 测试模式关闭；工作流不打印配置值。
- 执行 `verify_vendor_sdk_release_readiness.sh`。
- `assembleRelease` / `bundleRelease` 依赖 `verifyProductionReleaseConfiguration`。
- 构建前验证批准 AAR；重命名产物后同时检查 APK/AAB 内的 QLZ 入口类。
- 要求真实 Release keystore、密码和 alias；禁止 debug keystore fallback。
- 生成压缩 Release APK/AAB，并执行产物、签名、Manifest 和发布元数据检查。

QLZ 专项 production readiness 在正式 key、测试模式关闭、批准 AAR/产物检查和项目业务回归满足后可以通过。QLZ 1.3.0.2 内部 TLS finding 仍保留为厂商负责的非阻断接受风险，不代表已经修复。整体 production 当前仍可能因以下独立条件失败：

- 正式 QLZ key、Release 签名或受控发布环境输入缺失。
- 当前腾讯人脸 ARM64 native library 不满足 16 KB 对齐。
- 人脸 AAR 的 consumer rules 含生产阻断的全局选项。
- R8、Manifest、签名、CI commit 绑定或其他 release-required 检查失败。

详见 [QLZ SDK 接入](../integrations/qlz-sdk.md)和[路线图](roadmap-and-open-gaps.md)。

## 其他 workflows

| Workflow | 作用 |
|---|---|
| `Baseline Profile` | 手动/定时生成并校验 Baseline Profile，清理缓存 |
| `Face SDK Migration Check` | 验证本地 AAR 与私有 Maven 来源切换后的 compile/lint/manifest/assemble |
| `CI Health Monitor` | 收集运行健康指标并按阈值报告 |
| `Actions Runs Cleanup` | 定时/手动清理旧 Actions run |

`Face SDK Migration Check` 同样采用 build-only 策略，不把业务测试作为切源阻断项。

`CI Health Monitor` 的定时运行默认是观察性：阈值违约会更新 Issue，但除非手动输入 `fail_on_breach=true`，workflow 仍保持绿色。`Actions Runs Cleanup` 的定时运行默认不是 dry-run，会按配置删除旧 run/artifact/cache。

## Workflow 供应链与权限现实

- 六个 workflows 与共享 action 当前共有 32 处外部 action 引用，全部使用 `@vN`/`@vN.N.N` tag，0 处固定到完整 commit SHA。现有质量守卫把版本 tag 称为“pinned”，但只拒绝 `main/master/HEAD`；tag 仍可移动。GitHub 的 [Secure use reference](https://docs.github.com/en/actions/reference/security/secure-use) 明确说明只有完整 commit SHA 是不可变引用。
- `verify_ci_workflow_quality.sh` 的统一 workflow 列表只覆盖 Android CI、Android Release、Baseline Profile 和 Face SDK Migration；CI Health Monitor 未纳入，Actions cleanup 只检查少数固定模式。两个带写权限的维护 workflow 因此没有同等级的 timeout、permission、action SHA 与 artifact policy 守卫。
- Android CI 顶层授予 `actions: write`，导致 detect/verify jobs 也继承写权限；只有 cleanup job 实际需要该能力且已单独声明。应把普通 PR/Push 的构建 job 收敛到最小权限。

## Release-only 关键门禁

| 门禁 | 事实来源 | 常见修复方向 |
|---|---|---|
| Release exported components | `verify_release_exported_components.sh` | 收紧 Manifest 或有依据地更新 allowlist |
| Approved QLZ artifact | `verify_qlz_sdk_artifact.sh` | 恢复批准 AAR 原字节；不得修改、重打包、重命名或加入第二份实现 |
| Vendor SDK readiness | `verify_vendor_sdk_release_readiness.sh` | 解决腾讯人脸或未知厂商来源的阻断项；批准 QLZ finding 只报告接受风险 |
| Production config | `verify_production_release_config.sh` | 提供正式 QLZ key、关闭测试模式并恢复批准 AAR；不打印值 |
| Signing safety | build-logic + Release workflow | 配置真实 keystore，不使用 debug 签名 |
| Baseline profile source | Release workflow | 生成/提交受支持的 profile 或明确 warning |

## Lint waiver 规则

`verify_lint_warning_allowlist.sh` 默认 `LINT_ENFORCE_UNUSED_WAIVERS=auto`：

- 本地：未使用 waiver 作为阻断，推动及时清理。
- GitHub Actions：未使用 waiver 默认仅提示，降低环境差异导致的 post-merge 噪声。
- CI 仍可通过 `LINT_ENFORCE_UNUSED_WAIVERS=true` 强制严格模式。

新增 warning 应优先修复根因。只有有 Owner、精确来源、接受原因、复核日期和退出/触发条件的已知厂商问题才可进入 waiver。当前 QLZ waiver 只覆盖批准文件/版本，表示业务负责人接受厂商内部风险而非项目已经修复；应用源码、未知 QLZ 文件和其他依赖中的同类 finding 仍失败。腾讯人脸等独立 production blocker 不因该分类而放宽。

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

# 与普通 Android CI 对齐
bash scripts/quality/verify_release_validation_entry.sh .
./gradlew --no-daemon :app:lintDebug :app:assembleDebug
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt

# 查看完整 release-oriented 快照；独立厂商 blocker 仍会使其 fail closed
bash scripts/quality/preflight_local.sh --release
```

只运行与改动风险相称的最小集合，但不能用“普通 CI 不跑测试”作为跳过相关单元测试或真机回归的理由。
