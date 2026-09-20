# LongCare

LongCare 是面向长期护理服务执行和客户评估场景的 Android 客户端。

- 护理端：服务单、身份核验、NFC/外接读卡、服务中定位、拍照上传、倒计时和服务完成。
- 销售端：潜在客户登记、待办、表单评估、QLZ 蓝牙设备评估和报告查看。

工程仅构建主应用（`com.ytone.longcare`）。隐私同意后，长按登录页中央大 Logo 并点击确认，可进入 NFC/R65C 本地读卡检测；不震动、不登录、不上传、不触发业务签到。

当前主业务链路已实现，工程处于模块化收敛阶段。当前 QLZ 测试配置及 QLZ/腾讯 SDK 已知风险已获明确接受，Release 会报告警告，仍须通过正式签名和其他质量/业务验收。风险接受不等于厂商问题修复。

## 环境

- macOS/Linux/Windows + Android SDK
- JDK 21
- Android SDK Platform 37
- Gradle 使用仓库自带 Wrapper（9.7.1）
- 推荐安装 Android CLI，用于项目描述、官方文档检索、设备和模拟器操作

SDK 路径写入未跟踪的 `local.properties`。Release 签名、私有 Maven 凭据和其他 secret 不得提交到仓库。

## 快速开始

```bash
# 查看模块、变体和已有构建产物
android describe --project_dir=.

# 构建主应用 Debug APK
./gradlew :app:assembleDebug

# 正式包，需要已配置正式签名
# ./gradlew :app:assembleRelease :app:bundleRelease

# 安装并启动已构建 APK（需要连接设备或模拟器）
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

未来 Release 仅提供主应用 APK/AAB、校验和与映射等辅助产物；历史助手附件和已安装旧助手保持不变。

仓库默认 `debug.useMockData=false`，Debug 会访问真实配置的后端。需要本地 mock 时显式构建：

```bash
./gradlew :app:assembleDebug -Pdebug.useMockData=true
```

## 验证

```bash
# 快速架构/模块检查
bash scripts/quality/preflight_local.sh --local-fast

# 快速检查 + 主应用、读卡 Feature 及共享模块编译/单测
bash scripts/quality/preflight_local.sh --full

# 与普通 Android CI 的主要构建任务对齐
./gradlew --no-daemon :app:lintDebug :app:assembleDebug :feature:carddiagnostics:lintDebug :feature:carddiagnostics:testDebugUnitTest
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt
```

正式版统一使用标准 Release，不需要额外模式参数：`./gradlew :app:assembleRelease :app:bundleRelease`。`preflight_local.sh --release` 执行发布质量检查；已明确接受的厂商风险告警，其余问题仍阻断。Android Release 工作流发布主应用 APK/AAB，并设为正式 Release 和 Latest。流程与门禁见 [CI、质量门禁与发布](docs/architecture/ci-quality-gates.md)。

## 项目结构

```text
app/                 应用壳、导航、Manifest、平台适配及尚未迁出的业务 UI
integration/txface/  腾讯人脸 SDK adapter、依赖来源与 consumer rules
baselineprofile/     Baseline Profile / Macrobenchmark
core/
  model/             Kotlin/JVM 共享模型
  domain/            Kotlin/JVM 领域契约
  data/              网络、数据库、COS 和 Repository 实现
  common/            日志、配置、图片与通用 Android 基础能力
  ui/                通用 Compose/UI 支撑
feature/
  carddiagnostics/   NFC/R65C 本地读卡 UI
  login/
  home/
  identification/
  location/
  photoupload/
  servicecountdown/
build-logic/         Gradle 约定插件
scripts/quality/     本地、CI 与发布门禁
docs/                当前产品、架构、集成和合规说明
```

完整模块职责和现实迁移状态见[系统架构概览](docs/architecture/system-overview.md)。

## 文档

统一阅读入口和维护规则见[文档索引](docs/README.md)，需求、技术评估、风险与优化顺序见[项目整体分析报告](docs/analysis/project-review.md)。代码协作先读 [AGENT.md](AGENT.md)。

机器输出保存在构建目录或 CI artifact；历史决策通过 Git、PR、Issue 和 OpenSpec 归档追溯。
