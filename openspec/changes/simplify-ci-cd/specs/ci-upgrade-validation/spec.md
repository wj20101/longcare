## MODIFIED Requirements

### Requirement: CI 关键路径改动触发冒烟检查

CI SHALL 将 Android CI workflow、instrumentation smoke 执行脚本和冒烟选择逻辑的变化视为需要执行真实 instrumentation smoke；MUST NOT 因没有业务源码变化而跳过该检查。默认冒烟 SHALL 验证实际应用启动或页面行为，不能仅以包名断言替代。

#### Scenario: 仅修改 CI 关键文件
- **WHEN** PR 仅改变上述 CI 关键路径之一
- **THEN** 在前置构建成功后执行真实基础冒烟并保存结果

#### Scenario: 前置构建失败
- **WHEN** 依赖下载或前置构建失败导致无法运行 smoke
- **THEN** 保留明确失败/未执行状态，不将 smoke 缺失作为升级验证通过的证据

#### Scenario: 手动执行 CI
- **WHEN** 开发者手动运行 Android CI
- **THEN** 执行完整基础验证与真实基础冒烟，不因分支与自身比较无差异而跳过

## ADDED Requirements

### Requirement: 普通 CI 覆盖现有 JVM 业务测试

代码或构建变更触发的 CI SHALL 执行 App、Core、Feature 和集成模块现有 JVM 业务测试，不以固定少数测试类代替完整集合。测试失败 MUST 阻断；报告 SHALL 区分通过、失败、跳过和未执行。

#### Scenario: 修改销售或数据逻辑
- **WHEN** 改动销售流程或数据模块
- **THEN** 对应既有 JVM 测试进入实际执行集合，不仅编译模块或展示模块名称

#### Scenario: 仅修改文档
- **WHEN** 变更仅涉及文档
- **THEN** 执行文档一致性检查，不启动无关模拟器或正式构建

### Requirement: 存储维护与构建解耦

缓存及历史产物清理 SHALL 由独立维护入口执行，不作为每次构建的尾部任务。声明的近期保护期 MUST 实际生效，不因超过容量预算提前删除受保护对象；维护失败 MUST NOT 改变已通过构建的结果。

#### Scenario: 缓存超过容量但仍在保护期
- **WHEN** 缓存均在创建或访问保护期内且容量超标
- **THEN** 保留这些缓存并报告超限，不伪称保留而继续删除

#### Scenario: 排查最近构建失败
- **WHEN** 构建诊断和测试报告未满 7 天
- **THEN** 定期维护不因 2 天规则或容量阈值删除该报告

### Requirement: 清理不得削弱有效验证

CI/CD 清理 SHALL 删除无引用旧实现及重复入口，而非仅注释或保留空壳；有效的业务、安全、架构和产物验证 MUST 保留或由等价可执行检查替代。

#### Scenario: 删除模板用例与旧守卫
- **WHEN** 删除仅断言包名的模板用例或依赖旧步骤名称的守卫
- **THEN** 实际页面冒烟和发布安全行为仍有测试，旧引用与文档同步清除
