# LongCare 文档索引

先读根 [README](../README.md) 的构建入口，协作约束见 [AGENT](../AGENT.md)。每个主题只维护一份详细说明，其他入口使用摘要和链接。

## 当前文档

| 主题 | 入口 | 内容 |
|---|---|---|
| 工程风险 | [工程风险与维护边界](analysis/project-review.md) | 已知限制、验证边界和处理时机，不是发布任务清单 |
| 产品 | [产品概览](product/overview.md) | 角色、业务流程和规则 |
| 架构 | [系统概览](architecture/system-overview.md) | 模块、依赖、定位生命周期及 ADR |
| 工具链 | [技术栈](architecture/tech-stack.md) | SDK、依赖、版本、变体及配置 |
| 导航 | [页面与路由](architecture/ui-and-screen-map.md) | 页面归属、状态、返回与恢复 |
| 质量与交付 | [CI 与门禁](architecture/ci-quality-gates.md) | 本地/CI 执行范围和发布检查 |
| 厂商评估 | [QLZ 接入](integrations/qlz-sdk.md) | BLE、SDK、H5、接口与厂商风险 |
| 历史合规 | [2026-05 隐私整改](compliance/2026-05-app-store-privacy-remediation.md) | 历史材料，不证明当前线上政策已复核 |

## 文档维护规则

- 修改业务、路由、架构、SDK 或发布行为时，只同步相关主文档和测试，不再复制整体分析、路线图或逐文件清单。
- 版本与依赖以 `constants.gradle.kts`、`gradle/libs.versions.toml` 和 Wrapper 为准，模块以 `settings.gradle.kts` 为准，门禁以实际脚本/workflow 为准。
- 代码与文档不一致时，结合已确认需求核对；不能用当前实现自动覆盖需求，也不能用历史计划推导当前状态。
- 长期技术决策写入系统概览的 ADR，保留状态、背景和代价；未确认建议在对话或 PR 中讨论，不建立提案、任务清单或归档审批链。
- 已知限制解决后直接删除对应说明，历史通过 Git/PR 追溯。不得把已取消或已通过的验收重新列为阻塞项，未执行的验证也不能写成通过。
- 构建日志、截图、Lint/性能/测试报告放 `build/` 或 CI artifact，不复制到长期文档。
- 新增、删除或移动 Markdown 时更新本索引并检查链接。历史合规材料保留原时间边界，实际提审另核对线上内容。

## 检查

```bash
# 列出 Git 跟踪及未忽略的新 Markdown
python3 scripts/quality/verify_documentation.py --list

# 索引覆盖、本地链接/标题锚点、指定技术栈版本
python3 scripts/quality/verify_documentation.py
bash scripts/quality/preflight_local.sh --local-fast
git diff --check
```

文档脚本只读，排除已删除文件和 ignored 构建输出；工具技能参与链接检查，不作为产品要求。脚本不访问外链，不验证全部 Markdown 方言、全部依赖或自然语言与代码的一致性，不能替代业务测试和线上合规复核。
