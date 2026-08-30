## Why

LongCare 当前的 API 36 focused instrumentation 能通过，但部分 Compose 自适应测试仍复制中文文案并用局部固定宽度/手动 Density 模拟设备条件；同时根级 `connectedDebugAndroidTest` 会为没有 `androidTest` 的 Library 生成并启动空 test APK，实测 `:feature:home` 在 0 个测试时因缺少 runner 崩溃。继续迁移 Home 或提升 API 37 验证深度前，需要先让测试断言、设备条件与模块执行范围真实可信。

## What Changes

- 将 Dashboard 卡片文案断言改为从目标应用资源取得期望值，避免资源已变更而测试常量仍自洽。
- 将 TopHeader 的断点决策提炼为可在 JVM 中验证的纯逻辑契约，并让 Compose UI 测试使用官方设备配置覆盖能力验证实际宽度、字体缩放及临界值；保持现有生产布局行为不变。
- 建立显式 instrumentation 模块所有权清单和受支持的聚合执行入口，只运行真正包含测试且 runner 完整的 `:app`、`:core:data`、`:feature:identification`、`:feature:login`，不为无测试模块添加伪 runner 或空测试。
- 增加正向与负向治理 fixture，阻止测试模块清单遗漏、陈旧条目、空模块执行、选择器跨 test APK 以及 CI affected scope 无关扩张。
- 同步 CI/质量门禁与长期文档中的真实执行语义，并修正 legacy 文件数和旧布局测试描述等已确认的事实漂移。
- 保持普通 Android CI 的 build/lint 基线以及 app/login API 36 affected smoke 策略；本 change 不把全量 instrumentation 强制到每个无关变更。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `android-api-level-readiness`：增加“自适应测试必须使用真实可表达的设备配置”和“instrumentation 必须按 test APK/模块所有权执行”的可验证要求。

## Impact

- 主要影响 `app/src/{test,androidTest}` 的 Dashboard/TopHeader 测试、少量仅用于保持等价的布局决策提取、instrumentation 聚合/所有权脚本及其 fixture、affected CI/matrix 守卫和架构质量文档。
- 不改变用户可见文案、Compose 布局结果、Navigation 2 route、网络/Room/DataStore/用户隔离、WebView 开放 host 策略、权限或 Manifest 组件。
- 不升级 target/compile SDK、依赖或 Navigation，不处理 Baseline Profile、R8 清理、Home UI 下沉，也不修改或绕过任何厂商 AAR 与 production fail-closed 门禁。
