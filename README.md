# long care

## 本地常用命令

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
./gradlew :app:generateBaselineProfile
```

## 重构基线与执行计划

- 主计划文档：`docs/architecture/project-optimization-refactor-master-plan.md`
- CI/CD 优化计划：`docs/architecture/ci-cd-automation-optimization-plan.md`
- 构建基线文档：`docs/refactor/baseline-metrics.md`
- 构建性能对比：`docs/refactor/build-performance-comparison.md`
- 回归清单：`docs/qa/refactor-regression-checklist.md`
- 最终重构报告：`docs/refactor/final-refactor-report.md`

采集当前基线：

```bash
./scripts/quality/collect_build_baseline.sh
```

## 重构后模块结构（当前）

- `:app`：应用壳层、启动与导航组装
- `:core:model`：通用模型
- `:core:domain`：领域接口与规则
- `:core:data`：数据层实现与 DI 入口
- `:core:ui`：通用 UI 能力
- `:core:common`：通用基础能力
- `:feature:login`：登录特性
- `:feature:home`：首页特性
- `:feature:identification`：身份识别特性

## 架构守卫与稳定性检查

```bash
bash scripts/quality/verify_gradle_stability.sh
bash scripts/quality/verify_ci_workflow_quality.sh
bash scripts/quality/verify_architecture_boundaries.sh .
bash scripts/quality/verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare
bash scripts/quality/free_runner_disk_space.sh --dry-run --min-free-mb 1024
```

## CI/CD（GitHub Actions）

- `Android CI`：PR/Push 先计算 affected scope，按 `partial/full` 执行差异化任务；`full` 路径执行 `bundleDebug + baselineprofile:assemble + bundleRelease`，并按需触发 instrumentation smoke。
- `Baseline Profile`：在模拟器上生成 Baseline Profile，执行产物存在性校验并上传 baseline 相关文件。
- `Android Release`：Tag（`v*`）或手动触发，执行质量校验并产出 release APK/AAB，同时生成 SHA256 校验文件并自动创建 GitHub Release。

## Gradle / AGP 兼容状态

- `compileSdk/targetSdk = 36`
- `AGP = 9.0.0`
- 已启用 AGP 9 默认链路（Built-in Kotlin + New DSL），不再依赖 `android.builtInKotlin` / `android.newDsl` 兼容参数。
- 已移除 Wire Gradle 插件，`User` 会话模型改为本地 JSON 编解码实现。

## Release 签名（GitHub）

- `Android Release` workflow 支持自动读取以下 Secrets：
- `ANDROID_KEYSTORE_BASE64`：keystore 的 base64 字符串
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`
- 若 secrets 未配置，构建会自动回退为 debug 签名，保证 CI 打包流程可用。

### 一键同步本地 + GitHub Environment Secrets

推荐使用同一套 release `jks`（同一包名升级需要签名一致）：

```bash
chmod +x ./scripts/release/setup-signing-secrets.sh
./scripts/release/setup-signing-secrets.sh \
  --jks /absolute/path/to/release.jks \
  --env release \
  --copy-base64
```

执行后会完成：

- 写入本地 `~/.gradle/gradle.properties`（`LONGCARE_RELEASE_*` 配置，避免多项目冲突）
- 同步 `release` Environment Secrets（`ANDROID_KEYSTORE_BASE64` + `RELEASE_*`）

可选：需要手工粘贴时输出明文文件（使用后请删除）：

```bash
./scripts/release/setup-signing-secrets.sh \
  --jks /absolute/path/to/release.jks \
  --manual-file /tmp/longcare-release-secrets.env
```

仅更新本地配置：

```bash
./scripts/release/setup-signing-secrets.sh --jks /absolute/path/to/release.jks --local-only
```

仅同步 GitHub Secrets：

```bash
GH_TOKEN=你的PAT ./scripts/release/setup-signing-secrets.sh --github-only --env release
```

说明：

- `--github-only` 会优先从 `~/.gradle/gradle.properties` 读取 `LONGCARE_RELEASE_*`，不需要重复输入密码
- 若未执行 `gh auth login`，可通过 `GH_TOKEN`（或 `GITHUB_TOKEN`）临时注入认证

注意：

- 本地敏感信息请放 `~/.gradle/gradle.properties`，不要放到 `local.properties`
- `app/build.gradle.kts` 会优先读取 `LONGCARE_RELEASE_*`，并兼容旧 `RELEASE_*`
- `jks` 文件不要进入仓库
- 若使用 `--manual-file` 生成明文 secrets 文件，使用后请立即删除

## 更新 Gradle Wrapper

```bash
./gradlew wrapper --gradle-version latest --distribution-type all
```
