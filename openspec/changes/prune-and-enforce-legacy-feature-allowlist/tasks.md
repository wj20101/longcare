## 1. 收紧 legacy 文件守卫

- [x] 1.1 重新计算 legacy 实际文件与 allowlist 的双向差集，只删除当前确实不存在的 18 条记录，并验证 `actual - allowlist` 与 `allowlist - actual` 均为空且没有删除生产源码。
- [x] 1.2 新增可隔离执行的 legacy 文件 allowlist 守卫，实现根目录相对路径规范化、双向精确差集、缺失输入 fail-closed 和分类修复提示，并通过 `bash -n` 与最小正向 fixture 验证。
- [x] 1.3 将 `verify_architecture_boundaries.sh` 的 rule-10 切换为调用 focused 守卫，保留现有顶层标签与退出语义，并验证当前仓库通过、临时新增未允许文件时返回非零。

## 2. 固化回归测试

- [x] 2.1 新增不修改真实仓库的 shell fixture 测试，覆盖集合一致、新增未允许文件、陈旧 allowlist 项、历史同名路径重新引入、缺失扫描目录和缺失 allowlist，并验证每个负向场景同时检查非零退出码与对应路径输出。
- [x] 2.2 将 focused fixture 测试接入现有本地快速架构验证路径且不新增独立质量门禁名称，并验证 `preflight_local.sh --local-fast` 能发现故意破坏的 fixture、恢复后通过。

## 3. 同步长期文档

- [x] 3.1 更新 `docs/architecture/dependency-rules.md`，说明 legacy freeze 采用实际文件与 allowlist 双向一致性，并验证文档引用的脚本和路径真实存在。
- [x] 3.2 更新 `docs/architecture/roadmap-and-open-gaps.md` 中该治理项的当前状态和 234 条基线，保持 Room 破坏性重建与厂商 AAR 策略不变，并验证相对链接和事实与代码一致。

## 4. 综合验证

- [x] 4.1 运行 focused fixture、`verify_architecture_boundaries.sh`、`check_new_files_guard.sh --project-root .` 和 `preflight_local.sh --local-fast`，确认全部通过且负向 fixture 能稳定阻断。
- [x] 4.2 运行 `openspec validate --all --strict --no-interactive`，检查 `git diff` 仅包含本 change 规划的守卫、测试、allowlist、文档和 OpenSpec 产物，并确认生产 Kotlin、资源、Manifest、Room 与厂商 AAR 均无变化。
