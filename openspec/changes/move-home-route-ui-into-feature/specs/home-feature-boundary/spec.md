## Purpose

建立可由架构守卫与业务回归共同验证的 Home 功能边界，使 Home 根体验和护理端首页由同一 feature 持有，同时保持应用级导航、销售体验、平台能力与现有用户流程稳定。

## ADDED Requirements

### Requirement: Home 根与护理端页面具有单一功能所有权
工程 SHALL 由 Home feature 持有 Home 根页面、角色分流、加载态、护理端三页导航、护理仪表盘、护理工作、个人中心及其屏幕状态装配；应用壳层 MUST 仅承担根导航注册、图级状态 owner、Startup 报告、销售 renderer 和需要 Navigation、Camera、WebView 或平台环境的动作适配。质量验证 MUST 阻止这些页面或状态持有者回流 legacy 应用目录，也 MUST 阻止 Home feature 反向依赖应用壳层或 Data 实现。

#### Scenario: Home 页面边界符合约束
- **WHEN** 维护者运行模块、源码所有权和公开 API 边界验证
- **THEN** Home 根与护理端页面、状态和专属资源只归属于 Home feature
- **AND** 应用壳层仅通过 feature 的公开屏幕入口、不可变配置、销售 renderer 和显式动作完成组装

#### Scenario: Home 页面或内部状态回流壳层
- **WHEN** 变更在 legacy 应用功能目录重新创建 Home、护理仪表盘、护理工作或个人中心 UI/状态文件，或应用壳层导入 Home feature 内部 UI/ViewModel
- **THEN** 质量验证 MUST 失败
- **AND** 输出 MUST 指明违规文件、允许的公开入口和正确依赖方向

#### Scenario: Home feature 穿透数据或平台边界
- **WHEN** Home feature 直接引用 Data 实现、应用导航/平台包、应用资源命名空间、Activity、Context、Intent 或厂商类型
- **THEN** 质量验证 MUST 失败
- **AND** 修复方向 MUST 指向 Domain 契约、feature-local/Core 资源或 app-owned 动作适配

### Requirement: Home 角色分流与入口可观察行为保持稳定
Home SHALL 以当前已登录用户作为角色分流的单一事实来源；用户尚未可用时 MUST 只显示加载态，销售角色值 `2` MUST 进入销售体验，其他已登录角色 MUST 进入护理体验。迁移不得改变 Home 登录上报、根 Startup fully-drawn 判定或因重组产生重复副作用。

#### Scenario: 销售用户进入 Home
- **WHEN** 当前用户可用且 `userIdentity` 为 `2`
- **THEN** Home 显示现有销售体验及其当前内部导航状态
- **AND** 护理端三页内容不会被创建或短暂显示

#### Scenario: 护理用户进入 Home
- **WHEN** 当前用户可用且 `userIdentity` 不是 `2`
- **THEN** Home 显示现有护理端三页体验
- **AND** 销售体验不会被创建或短暂显示

#### Scenario: 当前用户尚未解析
- **WHEN** Home 已进入但当前用户仍不可用
- **THEN** 页面只显示既有加载状态
- **AND** 不复用上一账号的角色、页面、订单或销售状态

#### Scenario: Home 因重组或配置变化重建
- **WHEN** 当前 Home 根在同一会话和图实例内发生普通重组或配置变化
- **THEN** 登录上报与 Startup 完成动作不得被无条件重复执行
- **AND** 当前角色体验和可保存页面状态保持与重建前一致

### Requirement: 护理端三页导航与业务行为保持等价
护理 Home SHALL 保持“首页、护理工作、我的”三页顺序、不可手势横滑的受控切换和 adaptive navigation 表现；每页 MUST 保持迁移前的加载、刷新、错误反馈、业务动作和返回行为，不得因模块所有权变化修改订单、个人统计或登出契约。

#### Scenario: 护理人员切换根页面
- **WHEN** 用户在护理 Home 点击任一底部栏或导航栏项目
- **THEN** 系统切换到对应且唯一的根页面并更新选择状态
- **AND** 页面顺序、标签语义和禁止手势横滑的行为与迁移前一致

#### Scenario: 护理仪表盘恢复到前台
- **WHEN** 护理仪表盘进入活动前台状态
- **THEN** 系统刷新今日订单和进行中订单，并显示当前用户与公司名称
- **AND** 请求失败通过现有可消费反馈展示，不会泄漏上一用户的数据或重复导航

#### Scenario: 护理工作选择日期和订单
- **WHEN** 用户选择当月日期并点击对应护理订单
- **THEN** 系统按既有日期格式加载列表并依据订单状态请求正确的服务流程动作
- **AND** 未达到可执行状态的订单不会被误导航为已开始服务

#### Scenario: 个人中心执行操作
- **WHEN** 用户进入个人中心或选择统计列表、协议、隐私政策或退出登录
- **THEN** 页面显示当前用户、刷新既有统计并调用对应显式动作或登出契约
- **AND** App 版本展示、开放 WebView URL 和认证根清理语义保持不变

### Requirement: Home 屏幕状态遵循单向数据流且不泄漏状态持有者
Home feature SHALL 通过不可变、一致的可观察屏幕状态渲染当前用户、角色体验和护理页选择；用户交互 SHALL 作为事件进入状态持有者或显式应用动作。可复用/子页面 MUST 只接收实际需要的数据与事件，不得接收或自行解析 Home 的内部 ViewModel。

#### Scenario: 子页面消费 Home 状态
- **WHEN** 护理仪表盘、个人中心、销售内容或兼容人脸页面需要当前用户或 Home 局部状态
- **THEN** 调用方只传递不可变用户模型、具体状态值与对应事件
- **AND** 子页面不得持有、查找或跨目的地共享 Home 内部 ViewModel

#### Scenario: Home 状态在生命周期暂停后恢复
- **WHEN** 页面停止活动状态收集后重新进入活动生命周期
- **THEN** UI 以最新会话和屏幕状态恢复
- **AND** 已消费反馈或上一账号状态不得重放，未消费的用户可见反馈仍按既有队列语义处理

#### Scenario: HomeGraph 被认证边界替换
- **WHEN** 用户退出、会话失效或切换账号导致当前 HomeGraph 被清除
- **THEN** 当前图级订单状态和 Home 屏幕状态均被释放
- **AND** 新 HomeGraph 使用新状态实例且不能访问上一账号的内存状态

### Requirement: 应用级导航、销售与平台能力保持隔离兼容
迁移后系统 SHALL 继续由应用壳层持有 Navigation 2 route 注册、HomeGraph back stack、跨目的地结果、Startup reporter、销售内部页面以及 Camera/WebView/厂商平台实现。Home feature MUST 以显式动作或窄 renderer 请求这些能力，且不得改变 route payload、结果 key、WebView 开放 host 策略或销售保存恢复契约。

#### Scenario: Home 请求应用级导航或页面
- **WHEN** 护理或个人中心页面请求订单列表、服务流程、倒计时、Camera、协议或隐私页面
- **THEN** Home feature 只调用对应显式动作并保留原参数
- **AND** 应用壳层使用现有类型安全 route、结果 key 和开放 WebView 入口执行导航

#### Scenario: 销售体验嵌入 Home
- **WHEN** 销售角色进入 Home 或销售页面发生配置/可保存状态重建
- **THEN** 应用壳层提供的销售 renderer 继续显示相同页面、Tab、详情/评估返回目标与提醒选择
- **AND** Home feature 不依赖销售内部 ViewModel、导航状态或平台控制器

#### Scenario: 生产与厂商边界复核
- **WHEN** Home 模块迁移通过 Debug、模拟器或验收构建验证
- **THEN** 结果只证明模块所有权和现有 Home 行为等价
- **AND** QLZ/腾讯厂商问题、production Release 门禁及既有真机 verdict 仍 MUST 独立保持其当前 fail-closed 或 `unverified` 状态
