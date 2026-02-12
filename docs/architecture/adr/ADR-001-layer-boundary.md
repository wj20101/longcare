# ADR-001: Layer Boundary And Dependency Direction

- Status: Accepted
- Date: 2026-02-13
- Owners: LongCare Android Team

## Context

当前项目历史上以单 `:app` 模块演进，目录上已有 `domain/data/features` 分层语义，但缺少可执行的依赖边界约束，导致：

1. UI 层偶发直接依赖 Data 实现细节。
2. 领域边界容易被 Android 类型污染。
3. 架构 review 缺少统一判定标准。

## Decision

采用明确的分层依赖方向（由外向内）：

1. `UI/Feature` 仅依赖 `Domain` 接口与模型，不依赖 Data 实现。
2. `Data` 实现依赖 `Domain` 接口，负责网络/数据库/存储等外部访问。
3. `Domain` 保持纯 Kotlin，不引入 `android.*` 依赖。
4. Hilt 绑定遵循“接口在 Domain、实现在 Data、装配在 DI”原则。

## Rules

1. 禁止 `features/**` 直接 import `data/**/Impl`。
2. 禁止 `domain/**` import `android.*`。
3. Repository 接口放 Domain，Repository 实现放 Data。
4. ViewModel 不直接实现数据访问细节，必须通过 UseCase 或 Repository 接口。

## Consequences

正向影响：

1. 分层一致性提升，review 判定明确。
2. 业务逻辑可测试性增强。
3. 后续模块化迁移风险可控。

成本与约束：

1. 早期会增加接口与绑定改造成本。
2. 需要在 CI 中加入边界检查脚本防止回退。

## Verification

1. CI 执行架构边界检查脚本。
2. PR Review 以本 ADR 的 Rules 作为强制项。
