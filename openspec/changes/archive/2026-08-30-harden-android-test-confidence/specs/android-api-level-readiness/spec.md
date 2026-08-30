## ADDED Requirements

### Requirement: 自适应界面测试必须表达真实配置边界
工程 SHALL 让自适应界面回归分别验证布局决策和实际渲染：断点选择 MUST 由可重复的纯逻辑测试覆盖临界值，设备测试 MUST 使用目标运行环境能够真实表达的窗口尺寸、字体缩放和资源配置。测试期望 MUST 来自当前产品资源或稳定语义，不得复制可能与实现同时漂移的显示文案，也不得用超出宿主可用窗口的子容器伪造设备级验证。

#### Scenario: 验证布局临界值
- **WHEN** 维护者运行自适应布局的 focused tests
- **THEN** 每个受管断点在临界值前后均有确定的布局模式断言
- **AND** 对应 UI 测试使用与断言相符的可用窗口和字体配置验证关键内容不重叠、不裁切且保持可达

#### Scenario: 产品资源发生变化
- **WHEN** 页面显示文案通过资源更新而布局行为没有改变
- **THEN** UI 测试从目标应用资源取得同一产品事实并继续验证节点
- **AND** 仓库中不存在仅为该断言复制的旧显示文案

#### Scenario: 测试请求不可表达的设备条件
- **WHEN** 测试容器尺寸、设备窗口或字体配置无法共同表达其声称覆盖的场景
- **THEN** 测试治理 MUST 失败或要求改用可表达的配置
- **AND** 不得把局部裁切后的偶然布局结果视为设备适配通过

### Requirement: Instrumentation 执行必须遵循测试制品所有权
工程 SHALL 为每个拥有 instrumentation 源码的模块维护唯一 test APK 所有权，并提供只调用这些模块的受支持聚合入口。聚合执行 MUST 跳过没有测试源码的模块；类选择器 MUST 交给声明该类的 test APK，且质量守卫 MUST 拒绝遗漏、陈旧、跨模块或 runner 不完整的所有权配置。

#### Scenario: 执行全量本地 instrumentation
- **WHEN** 维护者通过受支持入口运行全量连接设备测试
- **THEN** 只构建并执行实际包含 instrumentation 的模块 test APK
- **AND** 每个已登记模块至少执行一个可发现测试，空 Library 不会启动 0-test instrumentation 进程

#### Scenario: 新增或迁移 instrumentation 测试
- **WHEN** 测试源码新增到模块、迁移到另一个模块或从模块完全移除
- **THEN** 所有权验证要求聚合清单、runner 依赖、类选择器和报告路径同步更新
- **AND** 任一遗漏或陈旧条目都会给出模块、测试源码和修复方向后失败

#### Scenario: 选择器交给错误的 test APK
- **WHEN** app 或 feature 的测试类被配置给不拥有该类的 test APK
- **THEN** 测试矩阵验证 MUST 在启动模拟器前失败
- **AND** 不得以 class-not-found、0 tests 或 runner 崩溃作为正常跳过结果

### Requirement: Instrumentation 成本必须与受影响范围匹配
普通 CI SHALL 保持 build、Lint 与治理基线，并仅在变更影响已登记的 UI、平台或测试所有者时执行对应 API 36 instrumentation；无关变更 MUST NOT 承担全量模拟器成本。需要全量信心时 SHALL 使用显式本地或专项入口，不得把 build-only 成功表述为业务 instrumentation 已通过。

#### Scenario: 变更影响已登记测试范围
- **WHEN** affected detector 识别到 app 或独立 feature 的 UI、平台边界或 instrumentation 测试变化
- **THEN** CI 为受影响 test APK 选择正确的 API 36 测试集合
- **AND** 其他 test APK 不会因该变更被无条件启动

#### Scenario: 变更不影响 instrumentation 范围
- **WHEN** 变更仅涉及无需设备验证的文档或独立 JVM 逻辑
- **THEN** 普通 CI 不启动 instrumentation 设备
- **AND** compile、unit、Lint 与治理任务仍按受影响模块策略执行

#### Scenario: 汇报验证结果
- **WHEN** 构建任务成功但对应 instrumentation 未运行
- **THEN** CI 摘要与长期文档明确区分 build-only、focused instrumentation 和全量设备验证
- **AND** 不得用未执行的业务测试为 API 36 或 API 37 readiness 提供通过证据
