## Why

冻结的 `app/features` legacy 区域当前有 234 个 Kotlin 文件，但 allowlist 仍保留 252 条记录，其中 18 条对应文件已经迁出或删除。现有守卫只拒绝“实际文件未被允许”，不会拒绝“allowlist 指向不存在文件”，导致技术债账本持续漂移，也允许已删除的同名 legacy 文件在未来被悄然重新引入。

## What Changes

- 从 `legacy_feature_files_allowlist.txt` 删除 18 条已失效路径，使清单只描述当前仍存在、已明确冻结的 legacy 文件。
- 将 legacy 文件守卫从单向包含校验升级为双向集合一致性校验：实际文件不得超出 allowlist，allowlist 也不得包含不存在的文件。
- 为“新增未允许文件”“陈旧 allowlist 项”和“重新引入已删除路径”补充可重复的负向 fixture，保证守卫失败信息包含具体路径和修复方向。
- 保持现有 architecture boundary、模块依赖、公共 API、路由、网络、持久化及生产行为不变。
- 同步长期架构文档中的 allowlist 数量和该治理项状态。
- 明确排除 Room 破坏性重建策略、QLZ/腾讯人脸等厂商 AAR、依赖升级、销售模块拆分和 API 37 UI 适配；这些由独立 change 管理。

## Capabilities

### New Capabilities

无。本 change 只修正工程守卫和技术债清单，不引入可观察的产品能力。

### Modified Capabilities

无。本 change 已在 `.openspec.yaml` 中设置 `skip_specs: true`。

## Impact

- **架构治理**：`scripts/quality/legacy_feature_files_allowlist.txt`、`scripts/quality/verify_architecture_boundaries.sh` 及其测试 fixture。
- **本地与 CI**：`preflight_local.sh` 和 Android CI 继续调用同一架构守卫；新增的双向校验会让陈旧清单直接失败。
- **长期文档**：架构路线图和依赖边界说明中的 legacy freeze 事实。
- **兼容性**：不修改任何生产 Kotlin/资源/Manifest、构建变体、数据库、厂商制品或用户可见行为。
