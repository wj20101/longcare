# 工程风险与维护边界

最后核对：2026-09-29；代码基线：`fe9e0e24`。本页只保留已知限制及其处理时机，不是待办清单或新增发布前置条件。业务流程见[产品概览](../product/overview.md)，模块和运行契约见[系统概览](../architecture/system-overview.md)，发布检查见[CI 与门禁](../architecture/ci-quality-gates.md)。

## 验证边界

- 定位已完成本轮可执行的权限、生命周期、标准 Release 冒烟及 10 次独立定位采集，未提交护理业务数据；这些结果不等于任何环境都不会漂移。定位契约见[定位会话与生命周期](../architecture/system-overview.md#定位会话与生命周期)。
- 按用户确认，不再保留长时静置、功耗或服务端数据对照验收任务。定位问题按实际复现和多次采集证据排查，不将这些已取消项目重新作为发布阻塞。
- QLZ 正式上传联调、NFC/R65C 实际读卡由用户于 2026-09-29 确认已通过，已关闭相关待验收项；本轮文档整理没有重新进行硬件测试。
- 普通 Android CI 与本地 `--full` 已通过 [run_jvm_tests.sh](../../scripts/quality/run_jvm_tests.sh) 选择全部 Android 模块的 Debug 单测及两个 Kotlin/JVM 模块测试。“普通 CI 不运行完整 JVM 业务单测”不再是当前事实；测试通过仍不代表全部线上流程或硬件组合均已覆盖。

## 已知限制

### 数据库与缓存

[DatabaseModule](../../core/data/src/main/kotlin/com/ytone/longcare/di/DatabaseModule.kt) 仍使用 `fallbackToDestructiveMigration(dropAllTables = true)`：缺少迁移路径时会重建表，不保证保留原数据。下次变更 schema 时明确数据保留需求，提供对应迁移及测试；不得把重建测试当作保留数据证明。

跨账号缓存、照片文件与数据库关联、重复业务提交的服务端幂等性需要具体调用链和复现证据才能判断。本页不将历史分析中的推测列为已确认故障，也不要求为了文档收尾新建整套验证工程。

### 厂商依赖

QLZ 当前使用默认正式环境；环境配置、单次 token、上报及结果页流程以 [QLZ 接入](../integrations/qlz-sdk.md) 为准，不再沿用旧测试模式说明。

当前 QLZ 弱 TLS、腾讯人脸 16 KB 对齐及 consumer rules 风险已获用户接受，正式构建继续告警。这不代表问题已修复；更新对应厂商包时重新验证，不能扩大为签名、Lint 或其他错误放行。具体门禁见[发布检查](../architecture/ci-quality-gates.md)。

R8 规则调整必须结合真实调用链、反射/JNI 与混淆产物验证，不依据历史候选列表直接删除。保留正式包实际需要的规则，不为测试专用 API 扩大保留范围。

### 性能采集

[BaselineProfileGenerator](../../baselineprofile/src/main/java/com/ytone/longcare/baselineprofile/BaselineProfileGenerator.kt) 当前等待包级根节点，随后执行滑动和返回，并将整个采集块包含到 Startup Profile；没有断言具体业务页面就绪，也不覆盖登录后的完整业务旅程。

因此，生成成功不能证明业务性能收益。只有实际开展性能优化时，再按目标页面调整采集、区分启动和交互路径，并用真机测量收益；这不是普通业务发布的新前置任务。

### 模块与平台

多数 route UI 和销售组装仍在 `:app`，`SalesViewModel` 职责较集中。后续修改相关功能时按需拆分，保持行为和导航栈测试，不为收尾强制进行全量模块迁移。

当前大屏方向约束和定位生命周期规则见[系统概览](../architecture/system-overview.md)，SDK/工具链以[技术栈](../architecture/tech-stack.md)为准。升级目标平台或修改相机、权限、前台服务时，再执行相应场景验证，不重复维护版本快照和通用验收矩阵。

### 合规材料

[2026-05 隐私整改](../compliance/2026-05-app-store-privacy-remediation.md) 是历史材料，不证明当前线上隐私政策或商店披露已经完成复核。实际提审时核对线上材料和当前包的行为，不把历史整改记录写成新的通过结论。

## 维护方式

只在限制被证实、发生变化或已解决时更新本页；已解决项直接移除，历史由 Git/PR 追溯。不保存执行日志、重复流程图、阶段计划、逐项任务表或机器报告。
