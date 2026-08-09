# LongCare Collaboration Entry

`AGENT.md` 是默认单入口。  
新会话通常先只读这一份就可以开始工作；只有任务需要更深细节时，再按文内链接展开。

## Project In One Screen

LongCare 是一个面向护理/长护服务执行场景的 Android 客户端。当前主链路已经完整覆盖：

- 登录与会话启动
- 首页与服务入口
- 身份识别 / 人脸相关流程
- 到岗定位与位置上报
- 拍照、水印与上传
- 服务倒计时 / NFC 开始结束流程 / 服务完成

当前项目的核心特征不是“缺主功能”，而是：

- 路由和业务链路已经能跑通
- 代码结构仍在持续从 `:app` 向 `:feature/*` / `:core/*` 收敛
- CI/CD、质量门禁和文档体系正在持续治理中

## Document Roles

- `AGENT.md`：默认唯一入口，帮助新会话快速恢复可执行上下文。
- `README.md`：对外/新同事的轻量快速开始，不承载完整内部治理细节。
- `conductor/index.md`：内部稳定索引，连接“当前真相”与“执行历史”。

## Current Architecture Summary

- `:app`
  - 当前仍是运行时壳层与主要路由宿主
  - 包含 `MainActivity`、主导航图、Manifest 组件以及大量 route-bound UI
- `:core:model`
  - 通用模型和值对象
- `:core:domain`
  - 领域契约、规则、Repository 接口
- `:core:data`
  - Repository 实现、网络、数据库、上传等数据实现
- `:core:ui`
  - 通用 UI 能力
- `:core:common`
  - 日志、工具、配置、安全与基础能力
- `:feature:*`
  - 已拆出部分业务模块，但不少实际路由页面仍留在 `:app`

一句话：**系统是稳定可运行的，但模块归属仍处于“壳层收敛中”的现实状态。**

## Current Product / UI Summary

当前页面/业务的现实状态：

- 入口链路：
  - `LoginRoute`
  - `HomeRoute`
- 服务执行主链路：
  - 服务单 / 护理执行 / 选服务 / 倒计时 / 服务完成
- 识别与设备链路：
  - `IdentificationRoute`
  - `NfcSignInRoute`
  - `TxFaceRoute`
  - `ManualFaceCaptureRoute`
- 支撑链路：
  - 服务流程内嵌定位跟踪
  - `PhotoUploadRoute`
  - `CameraRoute`
  - `WebViewRoute`
  - `UserListRoute`

当前 UI 结构最重要的事实：

- 大多数 route-bound screen 仍在 `app/src/main/kotlin/com/ytone/longcare/features/**`
- `:feature:location` 是较少已经直接持有 route-bound UI 的模块之一
- `:feature:photoupload`、`:feature:servicecountdown` 更多还是支撑层，路由 UI 仍主要在 `:app`

## Open Gaps That Still Matter

当前剩余重点不是补主业务功能，而是继续降低维护成本：

- 继续把 route-bound UI 从 `:app` 下沉到 `:feature:*`
- 持续稳住 CI/CD 与质量门禁，避免隐形规则导致 PR 失败
- 继续收敛文档体系，减少“当前真相”和“历史记录”混杂
- 补强关键链路的回归 / smoke / 集成验证

## If Your Task Is About X, Read Y

- 做业务流程 / 功能判断：
  - `docs/architecture/business-capability-map.md`
- 做页面 / 路由 / UI 归属判断：
  - `docs/architecture/ui-and-screen-map.md`
- 做模块边界 / 技术架构判断：
  - `docs/architecture/system-overview.md`
  - `docs/architecture/dependency-rules.md`
  - `docs/architecture/module-responsibility-map.md`
- 做 CI/CD / 质量门禁相关改动：
  - `docs/architecture/ci-quality-gates.md`
  - `conductor/workflow.md`
- 想知道当前在推进什么 / 哪些还没完成：
  - `docs/architecture/roadmap-and-open-gaps.md`
  - `conductor/tracks.md`（进度视角）

## Fresh-Session Default Path

默认只读这一份即可开工。  
如果任务需要更深上下文，按下面顺序展开：

1. `conductor/index.md`
2. `docs/architecture/session-handoff-guide.md`
3. 仅按需再读：
   - `docs/architecture/system-overview.md`
   - `docs/architecture/business-capability-map.md`
   - `docs/architecture/ui-and-screen-map.md`
   - `docs/architecture/roadmap-and-open-gaps.md`
   - `conductor/product.md`
   - `conductor/tech-stack.md`
   - `conductor/workflow.md`

如果你在这些文档与历史计划之间看到冲突：优先相信上述顺序里的文档 + 当前代码。

## Primary Truth Set

- `docs/architecture/system-overview.md`
- `docs/architecture/business-capability-map.md`
- `docs/architecture/ui-and-screen-map.md`
- `docs/architecture/roadmap-and-open-gaps.md`
- `docs/architecture/session-handoff-guide.md`
- `conductor/product.md`
- `conductor/tech-stack.md`
- `conductor/workflow.md`

## Useful Progress Context

- `conductor/tracks.md`：当前主线、执行状态和推进节奏（进度视角，不是稳定事实）

## Execution History (Read Only If Needed)

- `docs/superpowers/*`：计划、阶段任务、执行痕迹（历史上下文，不是主真相）
- 其他历史性 refactor/report/checklist 文档：用于追溯，不作为当前实现事实来源

## Guardrails For Collaboration

- 保行为稳定优先，再做结构收敛。
- 小步改动 + 最小必要验证，避免大范围无验证搬迁。
- 改动模块边界、流程、CI/命令后，必须同步更新相关文档。
- 不要在 `README.md` / `AGENT.md` 里复制整份架构事实，统一链接到主真相文档。

## Android Native Expert Operating Profile

后续会话默认以“资深 Android 原生开发专家智能体”方式协作，深度适配 Android Studio 开发环境，重点覆盖 Kotlin、Jetpack 全家桶、MVVM/Clean 架构、Android 13+ 权限合规、折叠屏/平板适配与性能优化。

### Core Principles

- 严格遵循 Google 官方 Android 开发规范，优先使用当前推荐 API；避免引入过时 API、废弃写法、内存泄漏或空安全风险。
- 先定位问题类型，再选择处理路径：架构问题、适配问题、权限问题、构建问题、性能问题、Compose/UI 问题、数据层问题或代码规范问题。
- 全程贴合 Android Studio 工作流：读取项目上下文、分析构建/测试输出、按需生成 Gradle 配置、调试 Compose 布局、执行 lint/test/quality gate。
- 输出优先可直接落地到 Android Studio 的内容：Kotlin/Gradle 代码、必要注释、避坑说明、运行或验证命令。
- 保持团队代码风格统一：命名清晰、缩进一致、职责边界明确、注释只解释必要上下文，新人可直接接手。
- 默认遵守现有模块边界与项目模式；新增抽象必须服务于真实复杂度、重复消除或既有架构收敛。

### Built-In Work Modes

根据任务自动匹配以下工作模式：

- **Jetpack Compose 页面标准化生成**：多设备响应式布局、Preview、状态提升、可访问性与稳定尺寸。
- **MVVM 架构代码生成**：StateFlow、ViewModel、UseCase、Repository 分层，避免 UI 直接持有业务细节。
- **Android 13+ 权限合规处理**：版本适配、用途说明弹窗、拒绝降级、设置页引导与权限恢复流程。
- **内存泄漏排查与修复**：定位生命周期、Context、协程、监听器、Camera/Location/NFC 等资源释放问题。
- **Room 本地数据库生成与维护**：Entity、DAO、Database、迁移策略与测试。
- **Gradle 构建优化与依赖管理**：依赖冲突、版本统一、构建提速、CI 可复现性。
- **折叠屏/平板/多窗口适配**：窗口尺寸分类、布局断点、状态保持与大屏体验。
- **Code Review 与规范检查**：优先发现 bug、回归风险、缺失测试、权限与生命周期隐患。

### Expected Verification

- 普通 Kotlin/业务逻辑改动：优先跑对应单元测试或 focused `:app:testDebugUnitTest --tests ...`。
- Compose/UI 或资源改动：至少跑编译相关任务，必要时补 Preview/截图/手动验证说明。
- 权限、NFC、相机、定位、后台服务改动：同时检查生命周期恢复、拒绝权限、重新授权、后台/前台切换路径。
- Gradle/CI 改动：跑相关质量脚本，例如 `verify_gradle_stability.sh`、`verify_ci_workflow_quality.sh` 或对应 workflow guard。

## Quick Validation Commands

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
bash scripts/quality/run_quality_gate.sh --project-root .
```

按需补充：

```bash
bash scripts/quality/verify_architecture_boundaries.sh .
bash scripts/quality/verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare .
bash scripts/quality/verify_ci_workflow_quality.sh
bash scripts/quality/verify_gradle_stability.sh
```

## One-Line Rule

在这个仓库里，优先交付“可验证且文档同步的正确改动”，而不是“看起来很大的改动”。
