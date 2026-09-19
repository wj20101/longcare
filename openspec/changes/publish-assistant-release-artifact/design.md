## Context

动机见 [proposal.md](proposal.md)。当前 Android CI 已固定执行助手 Debug 单测、Lint、构建和上传；缺口只在 Android Release。助手 Gradle 已有 Release 签名插件、R8、资源压缩和独立包名，Android CLI 已识别 `assistant-release.apk`。现有发布专项测试反而禁止 workflow 包含 `:assistant:`，需要随新约定调整。

## Goals / Non-Goals

- **Goals:** 在现有单一发布工作流中补齐助手 Release APK，沿用一次版本递增、同一签名配置和原有正式发布状态。
- **Non-Goals:** 不新增模式、分支工作流、通用产物框架或助手 AAB；不改变助手业务、登录权限、主应用更新接口或商店发布；不自动触发发布或回填旧版本。

## Decisions

1. 现有构建命令追加 `:assistant:assembleRelease`。不依赖 Debug 产物、不从其他 run 下载旧包，保证三类产物来自同一提交与 versionCode。助手包内 versionName 保留现有 `-assistant`。
2. 主应用命名和 `app-release-artifacts` 保持不变；助手命名为 `assistant-v<versionName>-<yyMMdd>-<versionCode>-release.apk`，新增 `assistant-release-artifacts` 留存 APK 和 R8 mapping。复用既有 checksum 步骤，将助手 APK 纳入 `release-checksums.txt`。GitHub Release 新增助手 APK，说明中明确主应用与助手用途；助手 mapping 仅留 Actions artifact，不额外扩大公开附件范围。
3. 发布前要求助手 APK 存在且仅有一个目标文件，验证正式签名、预期包名、不可调试和版本号；复用现有导出组件守卫检查助手 Manifest，保持双应用隔离守卫。上传附件缺失必须失败。失败诊断补助手报告和输出目录，不输出 secrets。
4. 先扩展现有离线工作流专项测试，覆盖助手命名且内容不变、缺包失败、checksum 覆盖三类安装包、Actions/Release 上传路径和检查顺序。再执行 workflow/隔离守卫及双应用签名 Release 构建，检查实际 APK 身份和签名。无业务代码变化，不重复 QLZ/问卷真实流程。
5. 同步 AGENT.md、OpenSpec 配置、发布/双包相关长期文档与助手 Gradle 注释中的旧发布限制；主规格在实施验收后按 delta 同步。历史归档保留当时事实，不批量改写。

## Risks / Trade-offs

- [助手可被所有 Release 可访问者下载] → 按用户明确选择分发，附件与说明标识助手用途；不修改现有登录、隐私和应用沙箱边界。
- [签名或包混淆] → 双包分别验签并校验包名、版本、不可调试属性，缺失阻断；禁止 Debug 替代。
- [CI 时间增加] → 只追加助手 Release 及必要产物检查，复用当前 Gradle 缓存与共享模块。
- [旧测试/文档仍排除助手] → 修改对应发布断言，不移除模块、组件和签名安全约束。

## Migration Plan

实施并本地验证后交付工作流变更；下一次获授权的正式发布自动附带助手。现有版本、标签和下载链接保持不变。必要时回退本 change 的工作流、测试及约定即可恢复仅发布主应用；不删除已发布附件，附件处置另行确认。
