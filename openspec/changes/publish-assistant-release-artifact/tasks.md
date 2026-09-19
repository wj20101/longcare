## 1. 补齐发布产物

- [x] 1.1 在 Android Release 追加助手 Release 构建、独立命名、Actions artifact、GitHub Release APK 附件和用途说明；扩展离线专项测试验证双应用构建任务、命名不修改包内容及正确上传路径。
- [x] 1.2 补齐助手签名/包名/版本/不可调试检查、导出组件检查、SHA-256、R8 mapping 留存及失败诊断；专项测试覆盖缺包阻断、校验覆盖全部安装包及发布前检查顺序，不放宽既有守卫。

## 2. 同步约定与验收

- [x] 2.1 同步 AGENT.md、OpenSpec 配置、受影响长期文档及 Gradle 注释的旧助手分发约定；全文检索确认现行说明无冲突，历史归档保持不变。
- [x] 2.2 运行发布专项测试、workflow/双应用隔离守卫和 local-fast；执行主应用 APK/AAB 与助手 APK 签名 Release 构建，核对包名、版本、签名、不可调试及 mapping，Android CLI 确认两个构建目标。
- [x] 2.3 同步两份 delta 到主规格并运行 `openspec validate --all --strict --no-interactive` 与 `git diff --check`；交付实际通过/未执行项，不自动提交、推送、递增版本或触发新 Release。
