# 持续现代化优化清单（2026-02-17）

## 扫描结论

- 历史台账中的阶段性优化项已收口（无“未完成项”）。
- 按“现代化架构 + Kotlin/Jetpack API + CI/CD/测试自动化健壮性”进行全项目扫描后，识别出一批新的持续优化项。

## 优化任务清单

| ID | 优化项 | 对应目标 | 状态 |
|---|---|---|---|
| N1 | COS 凭证同步刷新增加超时与主线程防阻塞回退 | Kotlin 并发现代化、健壮性 | DONE |
| N2 | 倒计时闹钟 WakeLock 延迟释放从 `Handler` 迁移为协程 | Kotlin 现代化 API、健壮性 | DONE |
| N3 | Baseline Profile 启动/生成脚本补真实交互（等待、滑动、返回） | 测试自动化、性能基线有效性 | DONE |
| N4 | 新增 `verify_baselineprofile_journeys.sh` 守卫并接入共享 CI action | CI/CD 自动化与回归防护 | DONE |
| N5 | 质量快照脚本纳入 Jetpack/Lint/Baseline/CI 守卫 | 自动化可观测性与健壮性 | DONE |
| N6 | 统一替换剩余 `Handler(Looper.getMainLooper())` 工具类调用（`ToastHelper`、`ContextExtensions`） | Kotlin/Jetpack 兼容 API 收敛 | DONE |
| N7 | 为 COS 同步凭证刷新新增超时与回退路径单测 | 测试健壮性 | DONE |
| N8 | 构建基线采集脚本默认禁用缓存并强制重跑任务，消除耗时统计失真 | CI/CD 指标可信度、性能治理可重复性 | DONE |
| N9 | CI 健康监控将“非取消运行成功率”作为主稳定性指标，并将取消率超阈值默认降级为告警 | CI/CD 稳定性评估去噪、自动化可靠性 | DONE |
| N10 | 质量快照脚本在缺失 lint 报告时自动 bootstrap `:app:lintDebug` | 自动化健壮性（clean 环境可直接执行） | DONE |

## 下一批剩余优化项（持续推进）

| ID | 优化项 | 对应目标 | 状态 |
|---|---|---|---|
| R1 | 继续将主体业务从 `app/features/*` 迁移到 `feature/*` 模块（优先：`photoupload`、`identification`、`servicecountdown`） | 现代化模块架构、边界收敛 | TODO |
| R2 | 对 `app/features/*` 超大目录做二次拆分（按 `ui/vm/domain/data` 纵向分层） | 主体代码质量、可维护性 | TODO |
| R3 | CI 健康监控告警项治理：将 Android CI 取消率从 50% 降到阈值内（触发策略与提交流水优化） | CI/CD 资源效率、稳定性 | TODO |
| R4 | 在“无缓存 + rerun”基线口径下继续压降 `:app:assembleDebug` 冷构建耗时（当前 79s） | 构建性能目标收敛 | TODO |

## 本轮已落地文件

- `app/src/main/kotlin/com/ytone/longcare/data/cos/repository/CosRepositoryImpl.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/countdown/receiver/CountdownAlarmReceiver.kt`
- `baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/StartupBenchmarks.kt`
- `baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/BaselineProfileGenerator.kt`
- `scripts/quality/verify_baselineprofile_journeys.sh`
- `core/common/src/main/kotlin/com/ytone/longcare/common/utils/ToastHelper.kt`
- `app/src/main/kotlin/com/ytone/longcare/common/utils/ContextExtensions.kt`
- `.github/actions/android-build-env/action.yml`
- `.github/workflows/face-sdk-migration-check.yml`
- `scripts/quality/verify_ci_workflow_quality.sh`
- `scripts/quality/collect_quality_snapshot.sh`
- `scripts/quality/collect_build_baseline.sh`
- `scripts/quality/monitor_ci_health.sh`
- `app/src/test/kotlin/com/ytone/longcare/data/cos/repository/CosRepositoryImplTest.kt`

## 本轮验收命令

- `./gradlew --no-daemon :app:compileDebugKotlin :app:testDebugUnitTest`
- `./gradlew --no-daemon :baselineprofile:assemble`
- `bash scripts/quality/verify_jetpack_compat_apis.sh`
- `bash scripts/quality/verify_architecture_boundaries.sh .`
- `bash scripts/quality/verify_baselineprofile_journeys.sh`
- `bash scripts/quality/verify_ci_workflow_quality.sh`
- `bash scripts/quality/collect_quality_snapshot.sh`
- `BASELINE_CLEAN_BEFORE_RUN=true bash scripts/quality/collect_build_baseline.sh`
- `bash scripts/quality/monitor_ci_health.sh yyg20101/longcare 50 scripts/quality/ci_health_thresholds.json build/ci-health`
- `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.ytone.longcare.data.cos.repository.CosRepositoryImplTest"`
