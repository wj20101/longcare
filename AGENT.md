# LongCare 协作入口

`AGENT.md` 是新会话的默认单入口。先用本文件建立最小上下文，再按任务阅读 [docs/README.md](docs/README.md) 中的专项文档。

## 项目一句话

LongCare 是单 APK、多模块的 Android 客户端，服务两类主要流程：

- 护理执行：登录 → 服务单 → NFC/读卡与身份核验 → 服务项目 → 定位/照片/倒计时 → 签退与完成。
- 销售评估：客户/待办 → 登记照片 → 表单或 QLZ 蓝牙设备评估 → 应用内报告。

主链路可运行。当前核心风险是生产厂商 SDK readiness、`:app` 壳层过重、复杂平台生命周期的回归深度，以及 targetSdk 37 前的大屏适配。

## 先看什么

| 任务 | 文档 |
|---|---|
| 产品行为、角色、业务规则 | `docs/product/overview.md` |
| 模块、运行时、平台边界 | `docs/architecture/system-overview.md` |
| 依赖/版本/构建变体 | `docs/architecture/tech-stack.md` |
| 页面、路由、返回结果 | `docs/architecture/ui-and-screen-map.md` |
| 分层或模块变更 | `docs/architecture/dependency-rules.md` |
| CI、Lint、发布 | `docs/architecture/ci-quality-gates.md` |
| 优先级和已知 blocker | `docs/architecture/roadmap-and-open-gaps.md` |
| QLZ / 销售评估 | `docs/integrations/qlz-sdk.md` |
| 定位生命周期 | `feature/location/src/main/kotlin/com/ytone/longcare/features/location/README.md` |

冲突时相信当前代码、Gradle/Manifest/workflow 和测试，再相信对应当前文档。不要从旧提交中的计划或日志推导现状。

## 当前架构

- `:app`
  - `MainApplication`、`MainActivity`、隐私/会话入口和 App 更新弹窗。
  - Navigation Compose 2 类型安全路由和根 NavHost。
  - Android 组件、Service/闹钟/安装器，以及 NFC、腾讯人脸、QLZ 等 app-owned controller。
  - 仍持有大多数 route-bound UI；legacy feature 目录冻结新增。
- `:core:model` / `:core:domain`
  - Kotlin/JVM 模块，保持 Android-free。
- `:core:data`
  - Retrofit、Room、DataStore/COS 数据实现和绑定。
- `:core:common` / `:core:ui`
  - 日志/诊断/图片/通用 Android 能力，以及共享 Compose/UI 支撑。
- `:feature:*`
  - 已承接部分动作、ViewModel、用例、Service 或页面。
  - `:feature:identification` 已拥有默认 CameraX/ML Kit 眨眼核验页面。
  - `:feature:location` 已拥有持续定位 Service 和上报链路。

## 开发守则

- 保持现有行为和 route contract，使用小而可验证的切片推进模块迁移。
- Feature/UI 不直接依赖 Data 实现；Domain 不引入 Android 类型。
- ViewModel 不持有 Activity，不直接启动 Service/闹钟/安装器/厂商 SDK UI。
- 持久 UI 状态使用 `StateFlow`；不可丢失动作保持到 UI 确认消费。
- 协程捕获异常时继续抛出 `CancellationException`。
- Room 升级提交 schema、显式 Migration 和迁移测试，禁止破坏性兜底。
- Retrofit 方法、路径、注解或 JSON key 变化同步契约测试。
- 标准持久图片统一走 `UnifiedImagePipeline`、`ImageProcessingPolicies` 和受管文件生命周期。
- 权限/NFC/相机/定位/Service 改动必须覆盖拒绝、恢复、前后台、退出/换号和资源释放。
- 新导出组件、新 secret、Lint ignore、全局 ProGuard ignore 或 debug 签名 fallback 都需要停下来做安全审查。
- 产品、模块、路由、版本、门禁或 SDK 行为变化时，同步 [文档维护矩阵](docs/README.md#文档维护规则)。
- 不新建 task plan、progress、findings 或执行日志文档；过程留在 PR/Issue，长期决策写 ADR。

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

## 最小验证

```bash
# 文档、边界或小改动
bash scripts/quality/preflight_local.sh --local-fast

# Kotlin/业务逻辑
bash scripts/quality/preflight_local.sh --full

# 普通 Android CI 主路径
bash scripts/quality/verify_release_validation_entry.sh .
./gradlew --no-daemon :app:lintDebug :app:assembleDebug
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt
```

按风险补充 focused test、instrumentation、模拟器或真机验证。普通 Android CI 当前不跑业务单测；不要把“CI 绿色”误解为业务流程已完整回归。

## 发布现实

- app 版本、SDK 和依赖以 `constants.gradle.kts`、version catalog 和 Wrapper 为准。
- 验收 Release 必须显式声明 acceptance。
- 生产 Release 当前 fail-closed：固定 QLZ 测试配置、QLZ 弱 TLS finding、腾讯人脸 16 KB 对齐和 consumer rule 问题未解决前不得绕过门禁。
- targetSdk 36 的大屏竖屏 opt-out 在 API 37 被移除；升级前必须完成自适应与相机方向回归。

## 结束任务前

1. 检查 `git diff`，不覆盖用户已有改动。
2. 跑与风险相称的最小验证，并如实记录未执行项。
3. 检查相对链接和文档事实是否同步。
4. 不提交 `build/`、本机路径、凭据或临时报告。
