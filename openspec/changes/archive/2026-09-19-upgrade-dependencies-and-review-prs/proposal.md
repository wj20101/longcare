## Why

2026-09-19 对版本目录与 GitHub PR 的核查发现多项稳定版更新、冲突或过时的依赖 PR，以及尚未落地的模拟器 CI 修复。需要以当前代码为基准分批升级并重新验证，避免把旧 CI、旧性能配置或厂商依赖的未验证兼容性当作可合入依据。

## What Changes

- 在 `complete-device-h5-evaluation-flow` 的 H5 无原生标题栏增量完成后执行本计划；不混入网页交互和业务结果协议改动。
- 先排查主分支 CI 的 Maven Central 429 下载失败，再重整 #115：KVM 可用性检查、按 PR 稳定分组取消旧运行、CI 相关文件变化触发 instrumentation smoke。
- 分批升级 Coil 3.6.3、Okio 3.18.2、Room 2.8.5、Kotlin 2.4.20、KSP 2.3.12、Compose BOM 2026.09.00、Robolectric 4.17，以及 Baseline Profile/Benchmark 1.5.0 稳定版；实施前复核发布信息和实际依赖解析。
- 按用户追加确认，联动核对 AGP 与 Gradle：保留最新稳定版 AGP 9.4.1 + Gradle 9.7.1，补齐尚未同步的 Wrapper JAR/启动脚本及校验信息，通过独立 PR 验证后合入，不采用预览版。
- 逐项处理 #120、#121、#122、#123、#124、#126、#127、#128；Coil PR 更新目标，冲突 PR 基于最新主分支重整，Compose BOM 新建独立 PR，避免重复 PR。
- #117 在最终代码和依赖稳定后重新生成 profile 再评审；#125 Protobuf 暂缓，保留 QLZ 推荐的 4.28.3，未经独立兼容性验证不升级；已关闭且被 AGP 9.4.1 替代的 #119 不重新合入。
- 每项以当前候选提交的检查结果验收，保持双 APK 隔离、业务回归和生产 fail-closed，不增加 Lint 豁免或降低测试要求。

## Capabilities

### New Capabilities

- `ci-upgrade-validation`: 本次 #115 带来的 CI 行为变化：模拟器 KVM 前置校验、稳定 PR 并发取消与 CI 路径变化触发 smoke。仅规范真实 CI 增量，不为纯版本号修改虚构业务能力。

### Modified Capabilities

无。双 APK、助手业务、接口和数据库契约不改变。

## Impact

- 影响 `gradle/libs.versions.toml`、共享构建插件、正式应用/助手及依赖它们的模块、CI workflow/守卫、性能 profile 和版本/质量文档；不新增模块。
- 保留 AGP 9.4.1、Gradle 9.7.1、JDK 21、minSdk 24、targetSdk 36、compileSdk 37；保留 kotlinx-datetime 的兼容变体，不引入预发布版本。
- 厂商依赖风险：Protobuf 升级是否兼容 QLZ 尚未验证；现有 QLZ 与腾讯人脸生产发布阻塞不属于本计划的绕过或修复范围。Room 补丁不主动修改 schema 或启用破坏性迁移。
- 计划已获用户确认实施；允许更新/推送现有 PR、为 Compose BOM 和 Wrapper 同步分别新建 PR，并在专项测试及最新 CI 通过后逐项合入。#125 继续暂缓；合入仍受最新差异和检查结果约束。
