## 1. 无用代码与占位文件

- [x] 1.1 复核四个 Core Placeholder 与两个纯 JVM 空 Manifest 的全源集/构建引用后删除；运行 Core 编译和模块边界守卫，确认未删除真正的模块入口。
- [x] 1.2 删除五个无调用 JSON 扩展，将仅供测试的 DefaultMoshi 移入 app/src/test；清理 MoshiBestPracticesAnalysis 的过时说明与重复计时演示，保留有效断言；运行 UriJsonAdapter、MoshiOptimization、ImageTaskStatusSerialization、SystemConfigManager 等受影响测试验证序列化不变。
- [x] 1.3 删除无调用的三种 Toast 重载、getFileExtension、throttleClick 及其独占辅助；复核函数/方法引用与 R8 旁证，编译 App/Common/UI 并执行倒计时和点击相关现有测试，保留实际短 Toast、singleClick 和资源释放逻辑。

## 2. 资源去重

- [x] 2.1 将六个指定 common_* 字符串统一到 core/ui，移除三处消费模块的重复声明并更新源码/测试 R 引用；检查文案和占位符逐字不变、每项只剩一个定义，完成双应用资源合并、编译和相关 UI 测试，不删除已确认由 ViewBinding 使用的水印布局及有效图片。

## 3. 文档与工作树整理

- [x] 3.1 删除旧 Kiro 规范，清理当前路线图中有完成证据的旧待办，更新文档索引及受影响的技术栈/架构说明；检查链接和事实一致，保留真实未完成项、合规证据及图标维护说明，运行 local-fast。
- [x] 3.2 按 design 清单使用 OpenSpec 技能归档七个已完成 change，按最终实现消解重叠规格并同步主 specs；确认不恢复旧 H5/发布协议，QLZ 未完成任务原样保留，运行全量 strict 校验并审查归档差异。
- [x] 3.3 精简 OpenSpec 当前 change 和 archive 的冗余执行日志、重试流水、临时路径和测试输出，保留需求/决策、真实任务状态与简洁最终验收结论；检查没有把未完成项改成完成，并通过 strict 校验。
- [x] 3.4 清点并可恢复地移出 scripts/quality/__pycache__ 的已确认 .pyc，添加 Python 缓存忽略规则；用 git check-ignore 与 git status 验证源码、测试、正常文档未被隐藏，未清理签名、产物或机器级缓存。

## 4. 综合验证与交付

- [x] 4.1 串行执行完整 preflight、双应用 lintDebug/assembleDebug、Lint allowlist、workflow 和隔离守卫；构建合法签名 App Release APK/AAB 与助手 Release，核对签名、不可调试、R8、资源压缩、包名和版本均未改变，不新增豁免。
- [x] 4.2 在隔离测试环境验证受影响的双应用通用按钮文案、显示及返回行为，不创建真实业务数据；检查最终 diff、OpenSpec strict 与删除后引用，列出实际删除/合并/保留项及未执行验证，未授权不提交推送或发布。

## 验证结论

完整 preflight 的 573 项 JVM 单测、双应用 Debug/Lint、合法签名 App APK/AAB 与助手 Release、既有质量守卫及 OpenSpec strict 通过；版本和签名保持不变，R8/资源压缩保持启用。API 24 隔离模拟器 App 27 项、助手 10 项 UI 回归通过，覆盖本次按钮文案/点击与返回；布局服务不可用时以截图和 Compose 语义断言核验，不把工具失败记为通过。

七个已完成变更已同步主规格并归档，真实未完成的 QLZ 验收项原样保留。未新增真实业务数据，未重新执行硬件异常或完整真实业务验收；未提交、推送或发布。
