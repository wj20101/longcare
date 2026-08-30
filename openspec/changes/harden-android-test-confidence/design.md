## Context

见 [proposal.md](proposal.md) 的 Why。本次审查得到的当前事实如下：

- `DashboardGridCompactModeTest` 与 `TopHeaderAdaptationTest` 在 API 36 `small_phone` 上共 3 项通过，因此本 change 不以修改生产布局为目标；风险来自测试仍复制资源文案，并以固定子容器和手动 `LocalDensity` 组合模拟配置。
- 根级 `connectedDebugAndroidTest --dry-run` 会包含所有 Android Library 的同名任务。实际执行无 `androidTest` 的 `:feature:home` 时产生 0 tests，并因 test APK 不含 `AndroidJUnitRunner` 而在启动前崩溃。
- 当前真正拥有 instrumentation 源码的模块只有 `:app`、`:core:data`、`:feature:identification` 和 `:feature:login`；API 36 Managed Device 的普通 CI 阻断范围当前只属于 app 与 login feature，二者使用不同 test APK。
- `legacy_feature_files_allowlist.txt` 的真实计数是 218，路线图中的 222 已过期；路线图所述 645dp 测试形态也已不符合当前 328/324dp 固定子容器实现。
- 已通过 Android CLI 核对 Google 官方 [Compose 测试常用模式](https://developer.android.com/develop/ui/compose/testing/common-patterns)、[窗口尺寸类别](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes)和[受管设备](https://developer.android.com/studio/test/managed-devices)说明。官方建议用测试配置覆盖表达窗口/字体条件、分别验证断点，并按模块配置的设备任务运行对应 test APK。

## Goals / Non-Goals

**Goals:**

- 在不改变当前 UI 结果的前提下，使 TopHeader 布局决策可在 JVM 精确覆盖临界值，使 Compose 测试覆盖真实可表达的窗口和字体配置。
- 消除 Dashboard 测试中的显示文案副本，让资源文件继续作为唯一产品事实来源。
- 建立确定性的 instrumentation 模块所有权、聚合入口和 fail-closed 漂移守卫。
- 保持 affected CI 的设备成本与变更范围匹配，并清楚区分 build-only、focused 和 full connected 结果。

**Non-Goals:**

- 不重新设计 Dashboard、TopHeader 或 Home，不改变 340dp/1.3 字体缩放、480dp 用户块宽度等现有布局阈值。
- 不把 Home UI 迁入 `:feature:home`，不迁移测试源码的业务所有权，也不引入截图测试框架。
- 不让每个 Android Library 都依赖 test runner，不把全量 instrumentation 加到普通 CI 的每次提交。
- 不升级 Compose、AGP、targetSdk、Navigation 或厂商 SDK，不修改 AAR、用户存储、WebView、路由、权限与生产发布门禁。

## Decisions

### 1. 将布局决策与渲染验证分层

从 TopHeader 当前条件中提取 module-internal 的不可变布局规格解析器，输入仅为可用宽度和字体缩放，输出布局模式及用户信息块宽度。生产 Composable 仍从当前约束和 Density 取得输入，阈值和渲染分支保持逐值等价。

JVM tests 覆盖至少以下边界：

- 宽度 339dp/340dp 与字体缩放 1.29/1.3 的紧凑模式组合。
- 宽度 479dp/480dp 的普通用户信息块宽度。
- 与 badge 无关的 Dashboard 卡片断点仍由现有 resolver tests 负责，不合并两套决策器。

Compose instrumentation 使用 `DeviceConfigurationOverride.ForcedSize` 与 `FontScale` 建立测试根配置，不再通过手动替换 `LocalDensity` 或仅给子 `Box` 固定宽度来声称设备条件。UI 层只断言稳定语义节点、可达性和相对边界，不增加颜色、圆角或逐像素断言。

**替代方案：**只扩大现有 Box 宽度或增加更多 bounds 断言。该方案仍把断点逻辑和渲染耦合在设备测试中，难以精确覆盖临界值，因此不采用。

### 2. 所有用户可见文案期望均从目标资源取得

Dashboard focused test 继续验证四个现有语义文本，但标题、描述和带数量格式的副标题全部从 instrumentation target context 的 `R.string` 解析。测试仍能发现 Composable 引用错误资源或漏渲染，同时不会因中文常量副本过期产生误报。

**替代方案：**只用 test tag 替代文本断言。tag 可以确认容器存在，却不能证明用户实际看到正确标题和描述，因此保留资源驱动的文本断言。

### 3. 用显式清单声明 connected instrumentation 所有者

新增简单、可评审的 instrumentation 所有权清单，初始精确列出四个模块：`:app`、`:core:data`、`:feature:identification`、`:feature:login`。清单表示“拥有至少一个 `src/androidTest` 测试并可通过 connected task 运行”；API 36/37 Managed Device 和类选择器仍由现有 target matrix 管理，不在第二份清单重复。

新增受支持的聚合脚本，从清单生成四个模块限定的 `connectedDebugAndroidTest` task，并允许透传 `ANDROID_SERIAL`。脚本不调用无模块限定的根任务，也不把 runner 依赖加入空 Library。`:core:data` 补充显式 runner 声明，使四个 test APK 的构建契约一致且可静态核对。

**替代方案：**运行时扫描目录后直接执行所有非空模块。纯扫描缺少可评审的所有权事实，也无法阻止意外新增测试悄悄扩大设备成本，因此采用“显式清单 + 双向守卫”。

### 4. 守卫同时验证源码、runner、选择器和执行计划

新增独立守卫及 shell fixtures，验证：

1. 清单模块与实际非空 `src/androidTest` 模块集合双向一致。
2. 每个清单模块声明正确 runner 且包含 runner 运行时依赖。
3. 聚合脚本只生成清单中的模块限定 task，并拒绝空清单、未知模块和根级无范围 task。
4. target matrix 中 app/login 的类仍归属于各自 test APK；通用类完整性检查不能把“在任意 feature 找到同名类”当作所有权正确。
5. affected detector 只在相关路径变化时启用对应 app/login smoke，不因本清单存在而无条件启动设备。

守卫接入 `preflight_local.sh --local-fast`、`verify_architecture_boundaries.sh` 或质量注册表中的单一合适入口，避免同一检查被重复执行。负向 fixture 至少覆盖遗漏模块、陈旧空模块、缺 runner、跨 test APK 选择器和无关变更触发设备五类情况。

### 5. CI 策略保持 scoped，full connected 明确作为专项入口

普通 Android CI 继续使用现有 `run_instrumentation` 与 `run_login_feature_instrumentation` 输出，在 app/login 各自的 API 36 Managed Device 上执行选择器；本 change 只强化所有权验证和摘要措辞，不默认增加 `:core:data`、`:feature:identification` 的 GMD job。

完整设备回归由新聚合脚本在已连接设备上执行四个 test APK。实施验收需要至少运行 app 的全部当前 instrumentation、core:data 的 Room/用户存储测试、identification feature 测试和 login feature 测试，并分别保存 Gradle 报告；结果不得合并成无法定位 test APK 的单一状态。

### 6. 文档只记录最终真相

完成后从路线图删除已完成的批次 A 明细，把下一优先级推进到 Baseline/Startup Profile 批次；同步 `ci-quality-gates.md`、`dependency-rules.md` 和 `system-overview.md` 中的 instrumentation 所有权、聚合入口及 scoped CI 语义，并把 legacy 快照修正为 218。文档不保留本次命令输出、设备序列号或本机报告路径。

## Risks / Trade-offs

- [提取解析器时无意改变阈值] → 先用现有 API 36 UI tests 固化基线，再以 339/340、1.29/1.3、479/480 的 characterization tests 约束逐值等价，生产差异立即回滚。
- [bounds 断言在字体栅格化差异下波动] → 只断言稳定的相对位置、非重叠和可达性，保留合理容差，不使用逐像素或固定绝对高度证明适配。
- [显式所有权清单增加维护项] → 双向守卫在新增、移动或删除测试时给出精确修复方向，避免静默遗漏。
- [full connected 耗时增加] → 仅作为显式专项/本地入口；普通 CI 仍按 affected scope 运行 focused GMD。
- [core:data 或厂商相关测试受设备状态影响] → 聚合输出保留模块级报告并 fail-fast；测试失败不得通过移除模块或扩大 ignore 处理。
- [Compose 测试 API 在当前 BOM 中不可用] → 实施前以 `:app:compileDebugAndroidTestKotlin` 验证当前 BOM；若官方 API 不可编译，先更新 OpenSpec 重新评审，不在本 change 顺带升级依赖。

## Migration Plan

1. 记录当前 3 个 Dashboard/TopHeader API 36 用例通过，以及空 `:feature:home` test APK 的 runner 失败特征。
2. 先完成资源驱动断言和纯布局 resolver，并验证生产 UI 等价。
3. 再加入所有权清单、聚合入口、守卫与 fixtures；先以 dry-run/fake Gradle 验证 task 选择，再运行真实四模块 connected suite。
4. 更新 CI 守卫和长期文档，执行 OpenSpec、架构、普通 CI、full preflight 与显式验收 Release 验证。
5. 本批次独立提交。若出现生产行为差异，回滚 resolver 提取；若仅聚合编排回归，回滚清单/脚本而不改动业务或增加空模块 runner。
