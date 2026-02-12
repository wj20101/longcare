# D4 构建性能对比（A1 vs D26）

## 对比结论

- 模块化推进后，关键端到端链路 `:app:assembleDebug` 从 50s 降到 18s（-64%）。
- 由于 D26 使用了 `clean` 基线，`compileDebugKotlin` 与 `testDebugUnitTest` 时长高于 A1 历史数据，当前不宜直接判定回归。
- APK 体积与方法体量变化可控（体积 +0.03%，Dex +5）。

## 数据来源

- A1 基线：`docs/refactor/baseline-metrics.md`（2026-02-13 01:07:29 +0800）
- D26 基线：`docs/refactor/baseline-metrics.md`（2026-02-13 07:53:52 +0800，`BASELINE_CLEAN_BEFORE_RUN=true`）

## 指标对比

| 指标 | A1（历史） | D26（当前） | 变化 |
|---|---:|---:|---:|
| Module Count | 2 | 10 | +8 |
| `:app:compileDebugKotlin` | 3s | 16s | +13s |
| `:app:testDebugUnitTest` | 16s | 41s | +25s |
| `:app:assembleDebug` | 50s | 18s | -32s |
| APK Size | 48,141,965 bytes | 48,158,644 bytes | +16,679 bytes |
| Dex File Count | 22 | 27 | +5 |

## 说明与风险

1. A1 采集未显式 clean，D26 采集为 clean 基线，`compile/test` 对比存在方法学偏差。
2. 从结果看，构建主链路（assemble）收益明显，但编译与单测波动需要在同口径下复测确认。
3. 已在 `collect_build_baseline.sh` 增加 `BASELINE_CLEAN_BEFORE_RUN` 开关，后续可统一采样口径。

## 下一步建议（进入持续化）

1. 使用同口径（clean=false / clean=true）各采 3 次取中位数。
2. 在 CI 产物里追加 `build/reports/profile`，定位 compile/test 的热点任务。
3. 结合 `scripts/quality/affected-modules.sh` 观察 D3 分层后的实际时长下降幅度。
