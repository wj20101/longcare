# ci-upgrade-validation Specification

## Purpose

明确本次模拟器持续集成修复的可观察行为，使 PR 更新能够取消自身过期运行，CI 配置变更能够触发真实设备冒烟检查，并在硬件加速不可用时给出明确失败而非误报通过。

## Requirements

### Requirement: 模拟器启动前验证硬件加速

Linux 模拟器 CI SHALL 在启动模拟器前确认 KVM 设备存在且执行用户具备读写权限；无法满足时 MUST 明确失败并说明原因，不以跳过模拟器测试视为通过。

#### Scenario: KVM 正常
- **WHEN** CI 执行用户可读写可用的 KVM 设备
- **THEN** 允许继续启动模拟器并运行原定 instrumentation smoke，保留测试结果

#### Scenario: KVM 缺失或权限不足
- **WHEN** KVM 不存在或执行用户没有所需权限
- **THEN** 前置检查失败并报告原因，不将未执行的 smoke 标记成功

### Requirement: 同一 PR 的新运行取消过期运行

同一 workflow 的同一 PR SHALL 使用跨提交稳定的并发分组，新运行 SHALL 取消该 PR 尚未完成的旧运行；不同 PR MUST NOT 互相取消。

#### Scenario: PR 推送新提交
- **WHEN** 同一 PR 的旧运行尚未结束且新提交触发新运行
- **THEN** 旧运行被取消，新运行继续针对新提交执行检查

#### Scenario: 两个不同 PR 同时运行
- **WHEN** 两个不同 PR 触发相同 workflow
- **THEN** 两者使用独立分组，不因其中一个更新而取消另一个

### Requirement: CI 关键路径改动触发冒烟检查

受影响范围检测 SHALL 将 Android CI workflow、instrumentation smoke 执行脚本和受影响模块检测脚本的变化视为需要执行 instrumentation smoke；MUST NOT 因没有业务源码变化而跳过该检查。

#### Scenario: 仅修改 CI 关键文件
- **WHEN** PR 仅改变上述 CI 关键路径之一
- **THEN** 在前置构建成功后执行 instrumentation smoke，不误判为无受影响业务模块而跳过

#### Scenario: 前置构建失败
- **WHEN** 依赖下载或前置构建失败导致无法运行 smoke
- **THEN** 保留明确失败/未执行状态，不将 smoke 缺失作为升级验证通过的证据
