# Gradle 构建稳定性治理手册

本手册用于 D2 阶段统一本地与 CI 的 Gradle 稳定性策略，减少配置缓存失效、Kotlin daemon 抖动与构建并发冲突。

## 1. 基线配置

以下参数必须在 `gradle.properties` 中保持开启：

- `org.gradle.configuration-cache=true`
- `org.gradle.configuration-cache.problems=warn`
- `org.gradle.caching=true`
- `org.gradle.parallel=true`
- `org.gradle.vfs.watch=true`
- `kotlin.incremental=true`
- `kotlin.compiler.execution.strategy=daemon`
- `kotlin.daemon.jvmargs`（非空）
- `org.gradle.jvmargs`（非空）

并保证 `constants.gradle.kts` 中的 `appJdkVersion` 与 `gradle/gradle-daemon-jvm.properties` 的 `toolchainVersion` 一致。

## 2. 自动校验

使用脚本：

```bash
bash scripts/quality/verify_gradle_stability.sh
```

可选参数：

```bash
bash scripts/quality/verify_gradle_stability.sh \
  gradle.properties \
  gradle/gradle-daemon-jvm.properties \
  constants.gradle.kts
```

## 3. CI 接入建议

在 `android-ci.yml`、`android-release.yml`、`baseline-profile.yml` 中均加入稳定性校验步骤，放在 Gradle 任务前执行：

```bash
bash scripts/quality/verify_gradle_stability.sh
```

## 4. 故障处置

当出现构建波动时按顺序执行：

1. 校验稳定性脚本，修复参数缺失或版本不一致。
2. 清理并重建一次：

```bash
./gradlew --stop
./gradlew clean :app:compileDebugKotlin --no-daemon
```

3. 复测关键链路：

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

4. 若耗时或失败率异常，补充至 `docs/refactor/baseline-metrics.md` 与主计划偏差说明。
