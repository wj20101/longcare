# LongCare 协作入口

`AGENT.md` 是新会话的默认单入口。先用本文件建立最小上下文，再按任务阅读 [docs/README.md](docs/README.md) 中的专项文档。

## 项目一句话

LongCare 是单应用、多模块的 Android 客户端，服务两类主要流程：

- 护理执行：登录 → 服务单 → NFC/读卡与身份核验 → 服务项目 → 定位/照片/倒计时 → 签退与完成。
- 销售评估：客户/待办 → 登记照片 → 表单或 QLZ 蓝牙设备评估 → 应用内报告。

主链路可运行。当前核心风险是生产厂商 SDK readiness、`:app` 壳层过重、复杂平台生命周期的回归深度，以及 targetSdk 37 前的大屏适配。

## 阅读与架构入口

所有文档职责和更新矩阵统一见[文档索引](docs/README.md)。需求、风险和优化顺序见[整体分析](docs/analysis/project-review.md)；模块、强制依赖规则、定位生命周期及 ADR 见[系统概览](docs/architecture/system-overview.md)。

App 保留启动、导航、平台组装和多数 route UI；Core/Feature 渐进承接业务。登录页中央大 Logo 长按确认进入本地 NFC/R65C，不上传或签到；平台监听随页面生命周期释放。独立助手已退役。

判断现状时核对代码、Gradle/Manifest/workflow 和测试；与已接受规格冲突时记录偏差，不以实现自动覆盖需求，不从历史计划推导现状。

## 开发守则

- 保持现有行为和 route contract，使用小而可验证的切片推进模块迁移。
- 严格区分返回与跳转：顶部返回、系统返回及普通关闭必须弹出当前页面，禁止用 navigate、固定跳首页或重建目标页替代返回。仅登录态切换、业务已完成页面的替换/移除等明确特殊场景可以调整栈，须说明关闭范围并测试实际 entry 序列；不得清除仍有效的来源页。
- 倒计时页退出回首页是已确认的业务例外，禁止重新暴露前序核验、项目选择等服务步骤；完成页则普通出栈，复用保留的原首页。
- Feature/UI 不直接依赖 Data 实现；Domain 不引入 Android 类型。
- ViewModel 不持有 Activity，不直接启动 Service/闹钟/安装器/厂商 SDK UI。
- 持久 UI 状态使用 `StateFlow`；不可丢失动作保持到 UI 确认消费。
- 协程捕获异常时继续抛出 `CancellationException`。
- Room 当前缺少迁移路径时重建表（见 `DatabaseModule`）；升级须先明确保留需求，提交 schema 和对应升级测试。需要保留的数据必须有迁移路径，不得扩大存量重建策略或把重建测试写成保留数据证明。
- Retrofit 方法、路径、注解或 JSON key 变化同步契约测试。
- 标准持久图片统一走 `UnifiedImagePipeline`、`ImageProcessingPolicies` 和受管文件生命周期。
- 权限/NFC/相机/定位/Service 改动必须覆盖拒绝、恢复、前后台、退出/换号和资源释放。
- 新导出组件、新 secret、Lint ignore、全局 ProGuard ignore 或 debug 签名 fallback 都需要停下来做安全审查。
- 产品、模块、路由、版本、门禁或 SDK 行为变化时，同步 [文档维护矩阵](docs/README.md#文档维护规则)。
- 不新建 task plan、progress、findings 或执行日志文档；过程留在 PR/Issue，长期决策写 ADR。人工维护的整体分析可放在 docs/analysis，必须标明基线与验证边界，机器报告仍放 build/CI artifact。

## Android CLI

Android 平台行为容易随版本变化。涉及 targetSdk、权限、前台服务、大屏、SDK 或推荐 API 时，先使用 Android CLI 官方知识库核对：

```bash
android docs search "关键词"
android docs fetch kb://...
```

项目/设备常用入口：

```bash
android describe --project_dir=.
android info
android emulator list
android run --apks=app/build/outputs/apk/debug/app-debug.apk
android layout --pretty
```

## OpenSpec 维护流程

本项目使用 OpenSpec 管理需要先对齐行为与方案的改动。OpenSpec 采用存量项目的 delta-first 方式：只为当前真实改动描述增量，不预先回填整个代码库。

以下改动在写业务代码前先建立 OpenSpec change：

- 新增或修改用户可见行为、业务规则、route/network/data contract。
- 跨模块重构、模块迁移、Room schema 或构建/依赖基线变化。
- 权限、组件导出、前台服务、厂商 SDK、隐私、安全或生产发布相关变化。
- 范围较大、验收标准不明确，或需要先比较多种方案的缺陷修复。

纯拼写/格式修正等无行为影响的小改动可以直接处理。不要为了“补全规格”给未触及的旧代码批量建 spec。

在 Codex 对话中使用项目生成的技能：

```text
$openspec-explore          调研代码与方案，不创建或修改实现
$openspec-propose          创建 proposal/specs/design/tasks，完成后等待评审
$openspec-apply-change     用户确认后按 tasks 实现并验证
$openspec-update-change    实现中发现新事实时更新 change 产物
$openspec-sync-specs       需要时提前把 delta 同步到主 specs
$openspec-archive-change   实现与验证完成后归档并更新主 specs
```

OpenSpec 产物统一使用简体中文并提交到 `openspec/`；结构关键字保留英文。归档前运行 `openspec validate --all --strict --no-interactive`。不要在 `openspec/` 之外再创建平行的 task plan、progress、findings 或执行日志文档。

## 最小验证

完整命令、CI 选择范围与发布检查见[CI 与门禁](docs/architecture/ci-quality-gates.md)。

```bash
# 文档一致性（链接、清单、指定版本；不替代业务核对）
python3 scripts/quality/verify_documentation.py

# 文档、边界或小改动
bash scripts/quality/preflight_local.sh --local-fast

# Kotlin/业务逻辑
bash scripts/quality/preflight_local.sh --full

# App/读卡模块基础编译与测试（不等于全部 CI 专项）
bash scripts/quality/verify_validation_app_isolation.sh .
./gradlew --no-daemon :app:lintDebug :app:assembleDebug :feature:carddiagnostics:lintDebug :feature:carddiagnostics:testDebugUnitTest
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt
```

按风险补充 focused test、instrumentation、模拟器或真机验证。普通 Android CI 执行完整 JVM 业务单测、构建/Lint 和按需离线设备冒烟；真机、在线接口和厂商硬件仍需专项验收，不把“CI 绿色”当作全业务全硬件已验收。

## 发布现实

- app 版本、SDK 和依赖以 `constants.gradle.kts`、version catalog 和 Wrapper 为准。
- 正式版统一使用标准 Release，不设置额外发布模式；仅主应用 APK/AAB 发布到 GitHub Release，不再生成独立助手，历史产物不变。
- 2026-09-19 用户接受当时 QLZ 测试配置及厂商风险；2026-09-21 已按确认删除测试模式，保留现有正式 appKey。QLZ 1.3.0.5 弱 TLS 和腾讯人脸 6.6.2 的 16 KB/consumer rule 风险仍由正式构建告警，不代表已修复。其余签名、Lint、产物和业务验收仍阻断；不得扩展为任意错误放行。
- targetSdk 36 的大屏竖屏 opt-out 在 API 37 被移除；升级前必须完成自适应与相机方向回归。

## 结束任务前

1. 检查 `git diff`，不覆盖用户已有改动。
2. 跑与风险相称的最小验证，并如实记录未执行项。
3. 检查相对链接和文档事实是否同步。
4. 不提交 `build/`、本机路径、凭据或临时报告。
