# 审计发现（CI/CD 与自动化）

## 初始发现
- 仓库存在多个 workflow：`android-ci.yml`、`baseline-profile.yml`、`android-release.yml`、`face-sdk-migration-check.yml`。
- 存在较完整的质量脚本集（targetSdk、exported 组件、架构边界、gradle 稳定性等）。
- 已有 targetSdk 升级门禁与本地 smoke 脚本，基础质量框架较完整。

## 具体问题（审计后）
1. 重复实现
- `android-ci.yml`、`baseline-profile.yml`、`android-release.yml` 均包含 runner 磁盘清理内联脚本，维护成本高且阈值不可统一。

2. 规范缺失
- 缺少“workflow 自身质量”守卫，后续若误删 `concurrency/timeout/verify_gradle_stability`，无法第一时间在 CI 阻断。

3. 触发效率
- `android-ci` 未过滤纯文档改动，导致无效流水线触发。

## 已落地措施
1. 新增 `scripts/quality/free_runner_disk_space.sh`，统一磁盘清理与最小剩余空间检查。
2. 新增 `scripts/quality/verify_ci_workflow_quality.sh`，守卫 workflow 关键质量规则。
3. 三个 workflow 全量替换为统一磁盘清理脚本调用。
4. `android-ci` 新增 `paths-ignore`，减少文档变更触发成本。

## 仍待处理
- F5：失败诊断产物按 job 结构化归档（便于快速定位）。
- F6：release/baseline 公共步骤抽象为 reusable workflow，进一步降重复。
