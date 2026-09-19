## Why

工程迁移和发布流程已收敛，但仍存在模块初始化占位文件、无业务调用的工具代码、跨模块重复资源，以及与当前实现矛盾的旧文档。清除已确认的冗余，减少维护入口和误导信息，保持当前用户体验与正式发布能力不变。

## What Changes

- 删除四个 Core 模块的占位声明，以及纯 JVM 的 core/model、core/domain 遗留空 AndroidManifest.xml。
- 删除已确认无调用的 JSON 扩展、Toast 重载、URI 扩展和 throttleClick 分支；保留实际使用的 singleClick、短 Toast 等。仅被测试使用的 DefaultMoshi 移入测试源集，保留有效序列化回归。
- 将 common_back、common_confirm、common_cancel、common_close、common_retry、common_processing 六项重复字符串统一到 core/ui，删除其他模块重复声明并同步资源引用，文案和界面不变。
- 删除过时的 `.kiro/steering/project-standards.md`，清理当前路线图中已完成或互相矛盾的内容、测试文件中的过时分析与计时演示；不删除仍有效的业务测试、合规记录和图标维护说明。
- 归档七个已完成的 OpenSpec change，并按已确认的最终实现同步主规格；未完成的 QLZ 异常场景验收继续保留，不补勾、不重新实施厂商修复。
- 精简 OpenSpec 当前 change 与 archive 中的重复进度、重试、临时路径和测试输出，保留需求、决策、任务状态及简洁的最终验收结论，不以搬入归档代替清理。
- 清理准确定位的 Python 字节码缓存，并补充忽略规则；不删除本地签名、APK/AAB、SDK、Gradle 缓存或用户数据。
- 以跨源集引用、生成代码、资源合并和 R8 报告为依据复核删除清单，验证双应用 Debug/Release、序列化和受影响 UI。

## Capabilities

### New Capabilities

无。仅做无行为变化的清理，设置 `skip_specs: true`，不为删除代码新造业务要求。

### Modified Capabilities

无新业务契约变化。历史 change 的归档同步只整理已经完成的行为，不引入新的导航、网页桥接或发布协议。

## Impact

- 涉及 app、assistant、core/common、core/domain、core/model、core/ui、feature/identification 的源码/测试/资源，及 docs、OpenSpec、旧 Kiro 文档和 `.gitignore`。
- 不变更依赖版本、数据库 schema、API、导航、H5 关闭语义、SDK AAR、签名、R8 keep 规则、厂商风险策略或资源密度；不新增清理框架、兼容层或运行时兜底。
- 已接受的 QLZ/腾讯风险仍保持可见，不以清理之名移除安全处理或质量门禁。
- 不把 R8 删除记录单独当作源码无用证明，不把资源同名误判为重复；尤其保留双应用不同 app_name、ViewBinding 水印布局、图标密度/API 变体、Room 历史 schema 和 Baseline Profile。
- 本次实施默认只修改并验证本地；不自动提交、推送、触发发布或改动既有下载资产。
