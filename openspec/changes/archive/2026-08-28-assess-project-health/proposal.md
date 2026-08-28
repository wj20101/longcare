## Why

LongCare 已具备多层质量门禁和较完整的架构文档，但现有证据分散在 Gradle、脚本、测试、构建产物与专项说明中，普通 CI 也没有覆盖业务单测和完整用户旅程。现在需要建立一次覆盖全工程、可复现且能区分已知阻断与新增回退的健康基线，为后续修复排序提供可信依据。

## What Changes

- 建立一套全项目健康评估能力，以 Android 官方的核心价值、用户体验、技术质量、隐私与安全四个质量支柱为外部基线，并结合 LongCare 的模块化、厂商 SDK 和发布现实细化检查域。
- 对全部 Gradle 模块以及 Debug、验收 Release、生产 Release、Baseline/Startup Profile 路径建立“检查项—证据来源—覆盖状态—结论”的可追溯矩阵。
- 分阶段检查构建与依赖、架构边界、代码与协程质量、测试可信度、核心业务契约、Android 组件/权限/隐私安全、数据与文件生命周期、性能/资源使用、大屏适配、CI/发布和文档一致性。
- 对发现项统一记录复现证据、影响范围、置信度、严重度、责任边界和建议验证方式，并区分新增回退、已知且仍成立的问题、环境限制和预期 fail-closed。
- 将长期仍成立的结论同步到现有真相文档或路线图；一次性日志、原始报告和本机路径仅保存在忽略的构建目录或 CI artifact。
- 形成按 P0/P1/P2 排序、可拆分为后续独立 OpenSpec change 的整改队列；本 change 只执行体检和事实同步，不直接修改业务行为或批量修复发现项。

## Capabilities

### New Capabilities

- `engineering/project-health-assessment`: 定义 LongCare 全项目健康体检的覆盖范围、证据标准、结论分级、已知阻断识别、输出约束和完成条件。

### Modified Capabilities

无。当前尚未建立主规格，本 change 不修改用户可见业务契约。

## Impact

- **覆盖代码与配置**：`:app`、`:baselineprofile`、全部 `:core:*`、`:feature:*`、`build-logic`、Gradle/version catalog、Manifest、R8/consumer rules、脚本、测试和 GitHub Actions。
- **验证环境**：本地 JVM/Gradle 检查、Android Lint、Debug/验收构建、可用的模拟器或真机旅程、Macrobenchmark/Baseline Profile；依赖外部凭据或 Play Console/厂商数据的项目必须明确标为外部证据缺口。
- **文档**：只更新 `docs/README.md` 维护矩阵指定的现有真相文档、必要 ADR 和 `roadmap-and-open-gaps.md`，不新增平行审计报告。
- **兼容性**：不改变用户行为、route/network/data contract、Room schema、依赖版本或发布守卫；生产 Release 的既有厂商 blocker 保持 fail-closed。
- **外部依赖与风险**：QLZ/腾讯人脸等闭源 AAR、服务端配置、真实设备矩阵及线上 Android Vitals 可能限制结论完备性；所有此类限制必须显式记录，不能以推测补齐。
