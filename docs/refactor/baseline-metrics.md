# LongCare Build Baseline Metrics

本文件用于记录优化重构前后的构建基线数据。

## Usage

```bash
./scripts/quality/collect_build_baseline.sh
```

可选参数：

```bash
./scripts/quality/collect_build_baseline.sh /absolute/path/to/output.md /tmp/longcare_baseline_logs
```

## 指标说明

| 指标 | 含义 | 目标趋势 |
|---|---|---|
| `:app:compileDebugKotlin` Duration | Kotlin 编译主耗时 | 下降 |
| `:app:testDebugUnitTest` Duration | 单测执行主耗时 | 下降或持平 |
| `:app:assembleDebug` Duration | 端到端构建耗时 | 下降 |
| APK Size | Debug APK 体积 | 可控（功能不变时不显著增长） |
| Dex File Count | DEX 分包数量 | 可控 |
| Module Count | `settings.gradle.kts` 中 include 数量 | 随模块化阶段上升 |

## 数据解释规则

1. 同一台机器、尽量一致的系统负载下对比。  
2. 重点观察 `compileDebugKotlin` 和 `assembleDebug` 两条曲线。  
3. 每次关键结构调整后都追加一段新 Baseline Run，不覆盖历史。  
4. 若耗时上升超过 15%，必须在主计划文档“偏差说明”记录原因。  
5. `Method Count` 暂未接入自动统计，后续在 D4 引入 `apkanalyzer` 或 dexcount。  

## Baseline Run - 2026-02-13 01:07:29 +0800

### Environment

- Host: `Darwin wajiedeMac-mini.local 25.3.0 Darwin Kernel Version 25.3.0: Wed Jan 28 20:49:24 PST 2026; root:xnu-12377.81.4~5/RELEASE_ARM64_T8132 arm64`
- Java: `openjdk version "21.0.9" 2025-10-21`
- Gradle: `9.3.1`
- Module Count (settings include): `2`

### Build Task Metrics

| Task | Status | Duration | Log |
|---|---|---:|---|
| `:app:compileDebugKotlin` | PASS | 3s | `/tmp/longcare_baseline_logs/_app_compileDebugKotlin.log` |
| `:app:testDebugUnitTest` | PASS | 16s | `/tmp/longcare_baseline_logs/_app_testDebugUnitTest.log` |
| `:app:assembleDebug` | PASS | 50s | `/tmp/longcare_baseline_logs/_app_assembleDebug.log` |

### Artifact Metrics

| Metric | Value |
|---|---|
| APK Path | `/Users/wajie/StudioProjects/longcare/app/build/outputs/apk/debug/app-debug.apk` |
| APK Size | 46M (48141965 bytes) |
| Dex File Count | 22 |
| Method Count | N/A（需要 apkanalyzer 或 dexcount，当前基线先不统计） |

### Commands

- `./gradlew :app:compileDebugKotlin --no-daemon`
- `./gradlew :app:testDebugUnitTest --no-daemon`
- `./gradlew :app:assembleDebug --no-daemon`

## Baseline Run - 2026-02-13 07:53:52 +0800

### Environment

- Host: `Darwin wajiedeMac-mini.local 25.3.0 Darwin Kernel Version 25.3.0: Wed Jan 28 20:49:24 PST 2026; root:xnu-12377.81.4~5/RELEASE_ARM64_T8132 arm64`
- Java: `openjdk version "21.0.9" 2025-10-21`
- Gradle: `9.3.1`
- Module Count (settings include): `10`

### Build Task Metrics

| Task | Status | Duration | Log |
|---|---|---:|---|
| `:app:compileDebugKotlin` | PASS | 16s | `/tmp/longcare_d26_logs/_app_compileDebugKotlin.log` |
| `:app:testDebugUnitTest` | PASS | 41s | `/tmp/longcare_d26_logs/_app_testDebugUnitTest.log` |
| `:app:assembleDebug` | PASS | 18s | `/tmp/longcare_d26_logs/_app_assembleDebug.log` |

### Artifact Metrics

| Metric | Value |
|---|---|
| APK Path | `/Users/wajie/StudioProjects/longcare/app/build/outputs/apk/debug/app-debug.apk` |
| APK Size | 46M (48158644 bytes) |
| Dex File Count | 27 |
| Method Count | N/A（需要 apkanalyzer 或 dexcount，当前基线先不统计） |

### Commands

- `BASELINE_CLEAN_BEFORE_RUN=true ./scripts/quality/collect_build_baseline.sh`
- `./gradlew clean --no-daemon`
- `./gradlew :app:compileDebugKotlin --no-daemon`
- `./gradlew :app:testDebugUnitTest --no-daemon`
- `./gradlew :app:assembleDebug --no-daemon`
