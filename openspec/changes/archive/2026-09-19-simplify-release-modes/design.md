## Context

当前 master 为 `7d5ccd1f`。Android Release 通过 `release_mode` 分流，App Gradle 读取两个模式参数，双包脚本将 `acceptance` 映射为标准 Release。业务实际仍只有 Debug/Release；这些额外名称不切换业务服务端，也不切换厂商测试配置。动机见 proposal.md。

## Goals / Non-Goals

**Goals:** 在现有文件中删除重复分支，使正式构建和发布只有一种语义，清理无用命名与测试。

**Non-Goals:** 不做业务重构、厂商替换、签名变更、旧参数兼容层、自动 CI 编排或历史 Release 重命名；不为验证流程擅自发布新版本。

## Decisions

1. 删除 workflow 的模式输入、环境变量、解析步骤、条件分支及 acceptance 双包上传步骤。保留现有手动发布、版本递增和签名流程；不改 tag 触发的既有限制。选择直接删除，不用单值枚举或隐藏默认模式替代。
2. App Gradle 删除模式属性和传参。将仍有价值的发布配置风险检查、脚本和测试命名改为普通 Release 名称，移除模式合法性用例，保留真实风险及错误输入检查。厂商扫描在 Release workflow 中无条件执行，不能连安全检查一并删除。
3. 文件名统一为 `app-v<versionName>-<日期>-<versionCode>-release.apk/aab`，artifact 为 `app-release-artifacts`，Release 名称为 `Release v<versionName> (<versionCode>)`；发布步骤明确设置非预发布及 Latest。删除不可达的验收命名分支，不通过额外发布后脚本修正。
4. 双包脚本使用 `debug|release`；输出目录、文件名、元数据及测试同步改为变体命名。Debug 保持真实接口，Release 保持合法签名要求。助手 applicationId、内部用途与对外隔离不变。
5. 更新 workflow 守卫以断言旧模式已移除、签名/R8/门禁和正式发布状态仍存在；实际执行打包正负用例验证命名、版本、校验和与旧参数拒绝。同步当前操作文档及上下文，历史验收记录不批量替换。

## Risks / Trade-offs

- 旧手动命令失效 → 当前文档统一提供标准 Release 命令，不保留兼容层。
- 简化时误删质量检查 → 以现有守卫及正负回归确认签名、厂商风险和隔离约束仍有效。
- 自动测试误触发发布或覆盖旧下载 → 本地/离线验证命名和元数据；不重命名现有 `v1.0.6-60` 资产，不自动启动 Android Release。
- 历史未归档 change 含旧模式 → 保留历史事实；归档和合并规格时以本次后续变更为当前契约，不恢复已删除逻辑。

## Migration Plan

确认后一次性更新 workflow、Gradle、脚本、测试及当前文档；执行守卫与合法签名 Release 构建，核对产物和模式残留。回滚使用本次定向提交恢复，不修改旧版本文件或设备数据。后续正式发布使用新工作流，无需选择模式。
