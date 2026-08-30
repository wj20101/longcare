## Purpose

本能力用于固定应用入口的隐私与会话门禁、认证边界 back stack、HomeGraph 共享状态以及护理/销售体验的恢复语义，使后续模块迁移或导航框架升级能够证明行为等价。

## ADDED Requirements

### Requirement: Privacy consent precedes session navigation
应用 SHALL 在用户尚未同意隐私政策时只显示隐私同意界面，不得创建 Login、Home 或其他受保护目的地，也不得启动依赖同意状态的入口初始化。

#### Scenario: First launch has no consent
- **WHEN** 应用启动且持久化隐私状态为未同意
- **THEN** 应用只显示隐私同意界面
- **AND** Login/Home 导航图与其 screen-level state holder 尚未创建

#### Scenario: User grants consent
- **WHEN** 用户在隐私同意界面确认同意
- **THEN** 应用持久化同意状态并执行一次 post-consent 初始化
- **AND** 应用随后进入会话解析流程

#### Scenario: Consent survives UI recreation
- **WHEN** 用户已同意隐私政策且入口 UI 因配置变化重建
- **THEN** 应用不得再次显示同意界面或重复执行本次进程内的 post-consent 初始化

### Requirement: Resolved session selects one root experience
应用 SHALL 以已解析的会话状态作为认证边界的单一事实来源，并且任一时刻只保留一个有效的 Login 或 Home 根体验。

#### Scenario: Session is unresolved
- **WHEN** 会话状态为 `Unknown`
- **THEN** 应用显示解析中的启动界面
- **AND** 不创建 Login 或 Home 根目的地

#### Scenario: Session resolves logged out
- **WHEN** 会话状态从 `Unknown` 解析为 `LoggedOut`
- **THEN** Login 成为当前根目的地
- **AND** back stack 不包含受保护的 Home 目的地

#### Scenario: Session resolves logged in
- **WHEN** 会话状态从 `Unknown` 解析为 `LoggedIn`
- **THEN** HomeGraph 及其 Home 起点成为当前根体验
- **AND** 应用不得先短暂显示 Login

### Requirement: Authentication transitions are idempotent and clear stale history
应用 SHALL 将认证状态变化协调为幂等的根导航转换，避免重复根目的地，并阻止用户通过返回操作进入与当前认证状态不一致的历史页面。

#### Scenario: Login succeeds
- **WHEN** Login 完成成功认证且会话变为 `LoggedIn`
- **THEN** 应用进入唯一的 HomeGraph 根实例
- **AND** Login 从 back stack 移除
- **AND** 系统返回操作不能回到 Login

#### Scenario: Success signals arrive more than once
- **WHEN** Login 成功回调和已登录会话状态先后或重复到达
- **THEN** 应用只保留一个 HomeGraph 根实例
- **AND** 当前 Home 页面状态不因重复信号被无条件重建

#### Scenario: Session is invalidated on a protected destination
- **WHEN** 用户位于 HomeGraph 或其子目的地且会话变为 `LoggedOut`
- **THEN** 应用切换到 Login 根目的地
- **AND** 所有受保护目的地从 back stack 移除
- **AND** 系统返回操作不能重新打开先前账号的页面

### Requirement: HomeGraph owns shared order state for its lifetime
每个 HomeGraph 实例 SHALL 为 Home 与其服务单子目的地提供同一份图级订单共享状态；该状态 MUST 在图被清除后释放，并且不得泄漏到下一次登录创建的新 HomeGraph。

#### Scenario: Navigate between Home and order list destinations
- **WHEN** 用户在同一 HomeGraph 内从 Home 进入今日订单或进行中订单子目的地并返回
- **THEN** 这些目的地观察到同一个图级订单共享状态实例

#### Scenario: Authentication boundary replaces HomeGraph
- **WHEN** 用户退出登录或会话失效后再次登录
- **THEN** 新 HomeGraph 获得新的图级订单共享状态实例
- **AND** 前一账号的内存状态不可被新图访问

### Requirement: Home selects the experience from the current user
Home 根目的地 SHALL 根据当前会话用户的角色稳定选择护理体验或销售体验，并在当前用户尚不可用时隐藏两种业务内容。

#### Scenario: Sales user enters Home
- **WHEN** 当前已登录用户的 `userIdentity` 为销售角色值 `2`
- **THEN** Home 显示销售体验根页面
- **AND** 不显示护理端 pager 内容

#### Scenario: Care user enters Home
- **WHEN** 当前已登录用户的 `userIdentity` 不是销售角色值 `2`
- **THEN** Home 显示护理端 pager 内容
- **AND** 不显示销售体验根页面

#### Scenario: Current user is not yet available
- **WHEN** Home 正在等待当前会话用户
- **THEN** Home 显示加载状态
- **AND** 不复用或闪现前一用户的护理/销售内容

### Requirement: Sales internal navigation restores deterministic back behavior
销售体验 SHALL 保存其当前页面、根 Tab、详情返回目标、评估返回目标和提醒选择，使配置变化或可保存状态重建后继续提供与重建前相同的返回行为。

#### Scenario: Sales state is recreated
- **WHEN** 销售用户位于非根页面且 UI 保存后重建
- **THEN** 当前页面、根 Tab、详情返回目标、评估返回目标和提醒选择恢复为保存前的值

#### Scenario: Back from nested sales pages
- **WHEN** 用户从提醒详情、客户详情、登记确认或评估步骤触发系统返回或页面返回
- **THEN** 两种返回入口执行同一条页面返回规则
- **AND** 返回到该流程已记录的父页面而不是任意重置到 Home

#### Scenario: Back from a non-home root tab
- **WHEN** 销售体验位于 Home 页面但根 Tab 不是首页 Tab
- **THEN** 第一次返回切换到首页 Tab
- **AND** 不退出 HomeGraph
