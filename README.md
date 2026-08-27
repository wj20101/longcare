# LongCare

LongCare 是面向长期护理服务执行和客户评估场景的 Android 客户端。

- 护理端：服务单、身份核验、NFC/外接读卡、服务中定位、拍照上传、倒计时和服务完成。
- 销售端：潜在客户登记、待办、表单评估、QLZ 蓝牙设备评估和报告查看。

当前主业务链路已实现，工程处于模块化收敛阶段。Debug 和显式验收构建可用；生产 Release 会主动阻断临时 QLZ 配置及已知厂商 SDK 安全/兼容问题，不能把验收包作为生产包。

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

# 构建 Debug
./gradlew :app:assembleDebug

# 安装并启动已构建 APK（需要连接设备或模拟器）
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

仓库默认 `debug.useMockData=false`，Debug 会访问真实配置的后端。需要本地 mock 时显式构建：

```bash
./gradlew :app:assembleDebug -Pdebug.useMockData=true
```

## 验证

```bash
# 快速架构/模块检查
bash scripts/quality/preflight_local.sh --local-fast

# 快速检查 + Kotlin 编译 + app 单测
bash scripts/quality/preflight_local.sh --full

# 与普通 Android CI 的主要构建任务对齐
./gradlew --no-daemon :app:lintDebug :app:assembleDebug
bash scripts/lint/verify_lint_warning_allowlist.sh app/build/reports/lint-results-debug.txt
```

`preflight_local.sh --release` 会进入 production-oriented 厂商 SDK readiness 检查；在当前 QLZ/腾讯人脸 blocker 未解决前，失败是预期的安全结果。发布模式和门禁见 [CI、质量门禁与发布](docs/architecture/ci-quality-gates.md)。

## 项目结构

```text
app/                 应用壳、导航、Manifest、平台适配及尚未迁出的业务 UI
baselineprofile/     Baseline Profile / Macrobenchmark
core/
  model/             Kotlin/JVM 共享模型
  domain/            Kotlin/JVM 领域契约
  data/              网络、数据库、COS 和 Repository 实现
  common/            日志、配置、图片与通用 Android 基础能力
  ui/                通用 Compose/UI 支撑
feature/
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

- [文档索引](docs/README.md)
- [产品概览](docs/product/overview.md)
- [系统架构](docs/architecture/system-overview.md)
- [技术栈](docs/architecture/tech-stack.md)
- [页面与路由](docs/architecture/ui-and-screen-map.md)
- [路线图与开放问题](docs/architecture/roadmap-and-open-gaps.md)
- 开始代码协作前阅读 [AGENT.md](AGENT.md)

仓库不再维护一次性计划、执行日志或生成报告型 Markdown；需要历史上下文时使用 Git、PR 或 Issue。
