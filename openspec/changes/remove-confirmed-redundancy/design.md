## Context

动机与范围见 proposal.md。核查基线为已推送的 `471bf5d5`；工作树只有原有 Python 缓存，尚未删除实现文件。Android CLI 1.0.16261425 已成功识别正式 App、助手、共享模块和现有构建变体。

初步证据：四个 Core Placeholder 仅有声明；core/model 和 core/domain 使用 Kotlin JVM 插件，其空 Manifest 无 Android 构建入口；JSON 扩展无业务调用，DefaultMoshi 只供 JVM 测试使用；R8 usage 同时显示相关 JSON、throttleClick、Toast 重载和 getFileExtension 被裁剪。R8 只作旁证，不能单独决定删除。

资源名扫描发现的 watermark_view 实际由 WatermarkViewBinding.inflate 使用，属于误报，必须保留。六项 common_* 字符串跨 app、assistant 和 feature/identification 同名同值，适合统一归属；两个应用的 app_name 不同，不能合并。

已通过 android docs search/fetch 核对[官方资源保留与裁剪说明](https://developer.android.com/topic/performance/app-optimization/customize-which-resources-to-keep)：资源合并存在变体优先级，动态名称和生成代码引用不能只靠源码文本搜索判断。保留现有优化设置，不更换 Gradle DSL。

## Goals / Non-Goals

**Goals:** 直接删除确定冗余，统一通用文案归属和当前文档入口；保持用户行为、文案、界面、存储和正式发布产物契约不变。

**Non-Goals:** 不重构业务架构、不改页面或导航、不升级依赖、不改厂商 AAR/keep 规则；不删除有效测试换取绿色；不清理机器级缓存、本地签名和发布产物，不自动发布。

## Decisions

### 1. 删除有闭合证据的源码，不增加替代框架

- 删除 core/common、core/domain、core/model、core/ui 中各自的 `core/<模块>/Placeholder.kt`，删除 core/domain 与 core/model 的空 AndroidManifest.xml。
- 删除 app 的 JsonExtensions.kt 内五个无调用扩展；将仍被测试依赖的 DefaultMoshi 作为同包测试辅助移入 app/src/test，不修改正式 NetworkDataModule 和自定义 JSON adapter。
- ContextExtensions.kt 只删两种 showLongToast 及无调用的 StringRes showShortToast，保留倒计时实际使用的 CharSequence 短 Toast 与主线程处理。
- UriExtensions.kt 只删除本轮确认无调用的 getFileExtension；ClickUtils.kt 删除 throttleClick 及其独占 MultipleEventsCutter，保留被页面使用的 singleClick 行为。
- 逐项复查全部源集、方法引用、Manifest、生成代码及 keep/JNI 字符串；若发现真实用途则不删除该项并说明证据，不扩大为新抽象或新兼容分支。

### 2. 只合并六个已确认重复的 UI 字符串

将 common_back、common_confirm、common_cancel、common_close、common_retry、common_processing 放入 core/ui 的现有 values/strings.xml。删除 app、assistant、feature/identification 的重复声明，消费方显式引用 core.ui.R（必要时使用别名），同步测试中的 R 引用。三方已有 core/ui 依赖，不新增依赖边。

不合并只是文字碰巧相同的业务资源，不调整 xxhdpi、夜间样式、自适应图标和 API 变体。保留 ViewBinding 水印布局、图标维护素材、有效测试 fixture、数据库历史 schema 和 Profile 文件。现阶段未确认存在可删除的图片，不以“清资源”为由强行删图。

### 3. 当前文档去重，历史证据归档

- 删除 511 行旧 Kiro 规范，统一以 AGENT.md 和 docs 索引为入口；修正索引中已过时的隐藏入口说明。
- 对路线图逐项对照代码和已完成任务，删除已完成的 Navigation 3 迁移、已完成的测试/CI 待办，不把仍未完成的性能、模块化与设备矩阵条目顺带删除。
- 移除 MoshiBestPracticesAnalysis.kt 中过时的大段说明和计时演示；有效枚举、集合、Map 与 URI 行为断言保留或合并到现有序列化测试，使用移入测试源集的配置。
- 使用 OpenSpec 归档/同步流程处理 migrate-to-navigation-3、align-evaluation-ui-and-h5-close、complete-device-h5-evaluation-flow、upgrade-dependencies-and-review-prs、upgrade-protobuf-javalite、allow-approved-vendor-release、simplify-release-modes。重叠规格按后续已确认方案与当前实现收敛，不能恢复网页返回结果关联、注入桥或旧发布模式。历史文件保留于 archive；QLZ 未完成计划及验收项保持原状。

OpenSpec 当前 change 和 archive 同步去除重复执行日志、重试流水、临时路径及大段测试输出；保留必要需求与决策、真实任务状态和最终验证范围/结论。归档不是日志保留的替代方案，未完成或未验证事项不得改写为完成。

### 4. 缓存处理限定明确目标

仅清理 scripts/quality/__pycache__ 内逐项核实为 Python 字节码的 `.pyc` 文件；删除前再次列出路径，使用可恢复的临时备份，不递归清理仓库或用户目录。在 `.gitignore` 增加 Python 缓存规则，不隐藏源码、测试和正常文档。

## Risks / Trade-offs

- [跨模块资源 ID 改变] → 文案、占位符和类型不变；编译全部消费模块及测试，检查双应用合并资源，并验证常用返回/确认/取消控件。
- [源码只有间接使用] → Android CLI/Studio（可用时）、全源集搜索、生成/Manifest 检查联合确认；不从 R8 usage 自动批量删除代码。
- [测试被误删] → DefaultMoshi 下沉后执行 UriJsonAdapter、MoshiOptimization、ImageTaskStatusSerialization、SystemConfigManager 等现有回归，保留有效契约断言。
- [历史规格覆盖当前约定] → 归档前逐个复查依赖与重叠项，使用当前导航/H5/Release 测试与最终确认行为校验，归档后全量 strict 校验。
- [减少源码但 APK 大小变化不明显] → 当前 R8 已裁剪很多死代码；目标是维护成本，不承诺包体下降或运行性能提升。

## Migration Plan

按代码、资源、文档、缓存分别形成可审查差异。先跑受影响单测，再串行执行完整 preflight、双应用 Debug/Lint、正式签名 Release APK/AAB 与助手 Release、隔离和发布守卫。资源变化用隔离测试环境验证，不新增客户、检测或问卷数据；使用设备前按 android-cli 读取 interact/journeys 参考。

全部删除文件可从 Git 恢复，未跟踪字节码有临时备份；发现回归只恢复对应删除项，不增加全局 keep、Lint 豁免或签名 fallback。最终列出删除/合并清单与未执行验证。本轮不提交推送或发布，除非收到后续明确指令。
