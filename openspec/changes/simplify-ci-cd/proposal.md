## Why

当前 CI/CD 同时存在重复构建、无调用旧脚本、过度缓存清理和只展示不驱动测试的模块分析，增加迭代成本却未覆盖完整业务单测。本次直接删除明确冗余，保留必要验证，不建设新的通用流水线框架。

## What Changes

- 删除无效 Tag 发布触发及对应拒绝分支，正式发布保留手动入口。
- 删除 Release 内重复的 Baseline Profile 生成链路，保留独立工作流；删除无引用的旧 `.github/scripts/run-baseline-profile.sh`。
- 删除 CI、Release、Baseline 工作流各自的缓存清理 job，保留单一定期维护入口，修正近期缓存保护与产物保留策略。
- 删除无实际作用的受影响模块输出、固定 App 专项过滤与 Debug AAB 常规上传；CI 执行完整 JVM 业务单测，保留小型、真实有效的模拟器冒烟选择。
- 删除包名断言模板用例、重复报告上传及只约束旧步骤名称/文本的守卫；保留真实行为回归。
- Release 不重建无用途的 Debug APK；重复验证仅在同提交、同配置、证据完整时复用，否则保留必要 Lint/厂商风险检查。
- 正式构建/校验成功前不推送版本提交，发布中不自动取消；删除发布过程中的依赖临时切换选项，SDK 来源通过版本化构建配置管理。
- 应用版本仅由构建配置维护，删除文档中的版本副本、对应校验及发布同步逻辑；保留工具链/依赖文档检查及正式产物版本验证。
- 同步清除失效测试、文档引用和参数，不保留空壳、注释掉的实现或旧方案兼容层。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `ci-upgrade-validation`：完整业务单测、真实冒烟、单一维护与精简守卫契约。
- `single-release-flow`：单一发布职责、构建后远端写入、不可取消发布及验证证据复用。

## Impact

- 范围：`.github/`、CI 相关 `scripts/`、模板 instrumentation 测试、门禁测试、现有 CI 文档及规格。
- 非目标：不改业务 UI/API、数据库、SDK/AAR、AGP/Gradle 版本；不删除历史 Release 或正在执行的工作；不清空 OpenSpec 历史归档。
- 保留：签名、R8/资源压缩、Lint、导出组件、产物身份和完整性、读卡隔离、厂商风险政策、真实业务测试、独立 Baseline Profile 和已有健康监测。
- 风险与外部依赖：GitHub Actions 权限、缓存和 artifact 可用性；Linux KVM/模拟器；完整单测可能暴露既有失败，不以跳过测试处理。线上推送、发布及存储删除不因本提案自动获得授权。
