## Context

见 `proposal.md` 的动机。当前 `verify_architecture_boundaries.sh` 中的 `check_kotlin_file_allowlist` 会扫描 `app/src/main/kotlin/com/ytone/longcare/features/**/*.kt`，将实际路径逐条与 allowlist 做精确匹配，但没有反向检查 allowlist 项是否仍存在。`check_new_files_guard.sh` 负责 changed-files 的快速反馈，不能替代全仓库快照的一致性校验。

当前扫描得到 234 个 Kotlin 文件，allowlist 有 252 个有效条目；18 个失效条目集中在已经迁出或删除的 face capture、home 和 select-device 文件。架构守卫目前整体通过，说明这是守卫表达缺口，而不是生产代码编译失败。

## Goals / Non-Goals

**Goals:**

- 让 legacy 文件清单成为当前冻结边界的精确、可执行快照。
- 对实际新增文件和陈旧 allowlist 项分别给出稳定、可定位的失败输出。
- 用隔离 fixture 验证成功路径与负向路径，避免测试修改真实仓库。
- 保持现有 `preflight_local.sh`、Android CI 和架构守卫入口不变。

**Non-Goals:**

- 不移动、拆分、恢复或删除任何生产 Kotlin 文件。
- 不调整模块依赖、visibility、架构预算或 legacy import 规则。
- 不修改 Room 破坏性重建、厂商 AAR、Manifest、R8、依赖版本或用户流程。
- 不把本次工具治理扩张为销售模块拆分或其他路线图工作。

## Decisions

### 1. 使用精确的双向集合比较

扫描结果和 allowlist 都转换为项目根目录相对路径、过滤空行和注释并排序去重，然后计算两个方向的差集：

- `actual - allowlist`：新增或重新引入但未获允许的 legacy 文件。
- `allowlist - actual`：已经不存在、应从债务账本删除的陈旧路径。

两个差集任一非空都返回失败；输出必须区分类别并逐条打印路径。继续使用精确路径，不引入 glob、目录级豁免或自动扩充 allowlist。

备选方案是只删除当前 18 条记录而不修改守卫；该方案无法阻止未来再次积累陈旧项，因此不采用。另一个备选方案是用数量预算代替集合比较；相同数量不能证明路径相同，也不采用。

### 2. 将集合校验提取为可隔离执行的小型守卫

新增 focused shell 守卫，接收项目根目录并对固定 legacy 目录与 allowlist 执行上述比较；`verify_architecture_boundaries.sh` 的 rule-10 调用它并合并退出状态。这样可以在临时 fixture 根目录中直接验证规则，而无需复制完整 Android 工程或修改生产源码。

`check_new_files_guard.sh` 保持不变：它继续在 changed-only 场景提供快速提示，完整快照一致性由 architecture rule-10 负责。

备选方案是在大型 `verify_architecture_boundaries.sh` 中增加测试专用分支；这会让生产入口混入测试控制流并增加参数兼容负担，因此不采用。

### 3. 使用临时目录覆盖正向和负向 fixture

新增 shell 测试通过 `mktemp -d` 创建最小目录树，并覆盖：

1. 实际文件与 allowlist 完全一致时成功。
2. 实际新增未允许文件时失败并报告该路径。
3. allowlist 包含不存在文件时失败并报告该路径。
4. 删除过的历史同名路径被重新创建时按未允许文件失败。
5. allowlist 或扫描目录缺失时 fail-closed。

fixture 只写临时目录，结束时清理；测试不得改写真实 allowlist。

### 4. 文档只同步长期事实

更新 `dependency-rules.md` 说明 rule-10 使用双向一致性；更新路线图中该项目的状态和当前条目数。不创建额外执行日志或报告文档。

## Risks / Trade-offs

- [分支并行期间真实 legacy 文件发生变化] → 实施前重新计算两个集合；只删除在当前目标分支确实不存在的路径，发现并行新增则停止并重新评估。
- [路径规范化差异产生误报] → 所有路径统一以项目根目录为基准、使用 `/` 分隔并进行精确排序比较；fixture 覆盖嵌套路径。
- [守卫拆分改变现有输出或退出语义] → 保留 rule-10 顶层标签和修复提示，增加 focused 测试，并运行完整 architecture/preflight 回归。
- [更严格校验短期阻断已有分支] → 这是预期的 fail-closed 行为；分支应删除无效条目或把仍存在文件恢复到真实一致状态，而不是放宽规则。

## Migration Plan

1. 增加 focused 守卫及 fixture 测试，先验证其能识别当前 18 条陈旧项。
2. 删除确认不存在的 18 条 allowlist 记录，使当前仓库达到集合一致。
3. 将 architecture rule-10 切换到 focused 守卫，并运行其正负 fixture、完整架构守卫和本地快速 preflight。
4. 同步长期架构文档，并确认生产源码、资源、Manifest、数据库和厂商制品均无 diff。

该变更不涉及运行时迁移或灰度。若出现非预期阻断，可整体回滚守卫、测试、allowlist 和文档提交；回滚不会影响安装包数据或用户行为。
