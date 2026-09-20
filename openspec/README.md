# OpenSpec 规格与变更入口

状态核对：2026-09-20。OpenSpec 采用 delta-first；主规格并不覆盖仓库每项存量能力。产品全景见[产品概览](../docs/product/overview.md)，整体优化依据见[项目分析](../docs/analysis/project-review.md)。

## 如何阅读

- `specs/`：已同步的行为契约。重叠部分按最新已接受的主规格理解，不恢复旧实现。
- `changes/<name>/`：真实增量方案和任务；“planning complete”只代表产物齐全，不代表实现/验收全部完成。
- `changes/archive/`：历史决策与验收范围，保留当时信息。旧发布模式、旧网页方案或旧依赖版本不能直接用于当前开发。
- `config.yaml`：规划约束和工程上下文；版本和实现仍由代码核对。

## 当前未归档目录

| Change | 实际状态 | 当前适用说明 |
|---|---|---|
| [integrate-card-diagnostics-in-app](changes/integrate-card-diagnostics-in-app/tasks.md) | 长按入口已由用户确认真机验收，读卡硬件验收待安排 | 登录页中央大 Logo 长按确认进入 NFC/R65C，无震动，退役助手与双包发布 |
| [publish-assistant-release-artifact](changes/publish-assistant-release-artifact/tasks.md) | 任务全部勾选，相关主规格已同步，目录尚未归档 | 历史双应用同一 Release 方案；已由主应用内读卡及单应用发布变更替代，后续归档不得覆盖新契约 |
| [remove-confirmed-redundancy](changes/remove-confirmed-redundancy/tasks.md) | 任务全部勾选，目录尚未归档 | 无新行为规格；保留清理范围，不再次执行已完成删除 |
| [use-qlz-custom-evaluation-ui](changes/use-qlz-custom-evaluation-ui/tasks.md) | 实现及自动化已有记录；5.3 完整硬件异常矩阵未完成 | 已对齐后续 H5/权限/Release 契约；不能归档为全部验收完成 |

本轮整理未移动归档目录、未新增验收证据、未把未完成任务改为完成。已完成目录后续可按 archive 技能执行正式归档；不能仅凭 status 的 isComplete 判断硬件验收通过。

## 主规格分组

| 能力 | 当前主规格 |
|---|---|
| 单应用正式构建与分发 | [single-release-flow](specs/single-release-flow/spec.md) |
| 主应用本地读卡检测 | [validation-assistant-app](specs/validation-assistant-app/spec.md) |
| 厂商风险接受 | [approved-vendor-release](specs/approved-vendor-release/spec.md) |
| 连续设备/H5 评估 | [device-h5-evaluation-flow](specs/device-h5-evaluation-flow/spec.md) |
| H5 关闭与容器 | [legacy-h5-close](specs/legacy-h5-close/spec.md)、[evaluation-h5-close](specs/evaluation-h5-close/spec.md) |
| 评估展示 | [evaluation-design-presentation](specs/evaluation-design-presentation/spec.md) |
| 服务完成导航 | [service-completion-navigation](specs/service-completion-navigation/spec.md) |
| CI 升级验证 | [ci-upgrade-validation](specs/ci-upgrade-validation/spec.md) |

## 历史方案的替代关系

- 原 acceptance/production 发布选项已由单一标准 Release 取代，历史命令不再是当前操作步骤。
- 助手曾由 CI artifact 改为 Release 附件；现已退役独立助手，未来只发布主应用 APK/AAB。历史附件不删除。
- Navigation 2 迁移已完成，历史迁移任务不是当前待办。
- QLZ 上传后直接显示业务完成已由“上传 → 自动 H5 → JS 主动关闭 → 原生结果查询”取代。
- 旧 H5 来源限制/现代消息桥方案已由当前最小 NativeBridge 契约替代；历史讨论不授权新增敏感桥方法。
- QLZ/腾讯已接受风险的告警策略不代表二进制缺陷已修复，也不延伸到其他版本/失败。

完整逐文件清单见[文档治理](../docs/maintenance.md)。每次同步或归档运行 `openspec validate --all --strict --no-interactive`，同时核对相关实现和当前文档；语法校验不证明自然语言契约已经全部实现。
