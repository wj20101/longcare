## 1. 实施基线与授权

- [x] 1.1 取得本计划实施授权，刷新主分支和 #125 的 head/base/差异/CI；记录本轮允许的本地修改与远端操作范围，不沿用旧计划排除 #125 的合入授权，以明确授权及 Git/PR 状态核验。
- [x] 1.2 复核官方 4.36.2 产物、QLZ AAR 哈希与真实消息/Gzip API，核对旧运行库解析及已有 R8 规则；确认无 AAR 变更、无可证明的精确生成器版本时如实记录，以只读产物检查和依赖图核验。

## 2. 旧版基线与运行库升级

- [x] 2.1 在 App 测试源集添加真实 AAR 消息专项和合成 fixture，覆盖默认值、非 ASCII 文本、多条采样、五指及生理数据、未知字段、错误输入和实例隔离；先在 4.28.3 运行通过并记录生成版本，不调用网络/遥测或使用真实业务数据。
- [x] 2.2 核对并测试 SDK 原有 Gzip 编解码入口，断言解压后消息语义与错误处理；在 4.28.3 基线通过，不以自写 Gzip 替身或仅 mock 回调冒充 SDK 覆盖。
- [x] 2.3 将版本目录升级为 4.36.2，并将 App 构建注释改为准确的示例基线/运行库说明；原样执行消息与 Gzip 专项，验证旧 fixture 在新版读取、新版产物在旧版读取的字段语义，记录具体失败而非删断言。
- [x] 2.4 核对 App Debug/Release/测试运行时最终解析为目标 Lite 版本，无 Full/Lite 重复类、强制覆盖或其他无关依赖变化；对照助手依赖图确认没有新增 QLZ/Protobuf 污染，AAR 哈希不变。

## 3. 本地门禁与 Android 验证

- [x] 3.1 串行运行完整 preflight、双应用 Debug/Lint、既有 warning allowlist 与助手隔离守卫，并运行受影响 QLZ/销售状态机与 mock 测试；全部通过且无新增豁免，以实际测试报告核验。
- [x] 3.2 在 API 24 与现代 Android 设备运行真实 AAR 消息/Gzip instrumentation，覆盖最终 4.36.2 运行库加载及编解码；记录设备、变体、候选提交和通过数量，不以 JVM 或 build-only 代替。
- [x] 3.3 使用合法签名、显式 acceptance、禁用 unsigned/debug fallback 构建混淆产物，执行混淆目标包中的消息专项或真实 QLZ 路径；核验实际执行的是目标 APK 而非测试 APK 自带副本，R8 字段访问与编解码通过，不新增生产测试入口。
- [x] 3.4 修复混淆测试运行器启动问题，限定于测试侧且不放宽正式 R8 规则；按用户追加确认，完整 13 项保留在 JVM/Debug，混淆专项验证实际 SDK 消息/Gzip 路径及字段完整性，并通过受控失败验证运行器不会把失败报告为成功。

## 4. 授权真实流程验收

- [x] 4.1 在操作前确认本轮测试账号/客户、QLZ 设备、手机就绪及检测/问卷提交授权；核验覆盖安装保留账号与数据，未授权不新增客户或提交业务数据。
- [x] 4.2 使用最终候选完成 SDK 初始化、Token 校验、扫描连接、真实采样、上传成功及自动打开 H5；随后验证 H5 提交、左上角 closeWebView 返回和原生 GetCheckResult 等级展示，记录脱敏结果，不能用 mock 替代真实上传成功。
- [x] 4.3 复核离开检测页面的资源释放和返回行为，确认现有失败/重试/迟到回调测试仍通过；恢复约定 Debug 包并保留用户数据，以测试结果和设备安装/启动状态核验。
- [x] 4.4 按追加授权复现和定位扫描停滞，基于真实 AAR 证据实施最小生命周期修复；覆盖扫描结束、重试、取消及释放，重跑受影响门禁并在最终候选完成后再次恢复 Debug，不将回调修复等同于真实设备验收。

## 5. 文档与 PR 交付

- [x] 5.1 同步技术栈、QLZ 接入说明的运行库版本、实测组合及验证边界，保留生产阻塞；运行 local-fast、`openspec validate --all --strict --no-interactive`、`git diff --check` 并审查全部差异，不提交日志、凭据或构建产物。
- [x] 5.2 获远端写入授权后更新/推送现有 #125，标题与说明同步为 4.36.2，保留历史且无无关覆盖；核验最新 head/base 的专项证据与 CI，变化后补充受影响验证，不沿用旧绿色检查。
- [x] 5.3 所有必需专项和真实验收通过后按授权合入 #125，核对合入提交及主分支 CI；交付实际覆盖与仍存在的生产 blocker。任何必需项未完成时保留 PR 未合入，不将内部验收称为生产可发布。

## 验收记录（2026-09-19）

- 旧版基线提交 `83d95ccb`，候选基于 `176361ce` 加当前升级差异；4.28.3/4.36.2 同套 JVM 消息专项各 13 项通过，双向合成消息/Gzip 语义校验通过。4.36.2 Debug 在 API 24 模拟器与 API 37 Pixel 各 13 项通过。
- 最终配置完整 preflight、双应用 Debug/Lint、App warning allowlist、助手隔离检查通过；App 383 项、助手 34 项单测，无新增豁免。
- 首轮已签名 acceptance Release 与测试 APK 均完成 R8 构建，签名一致，测试 APK 未定义厂商消息或 Google Protobuf 运行库副本。但两台设备均在 AndroidJUnitRunner 启动时因 `Trace.beginSection` 缺失而退出，当时未将 3.3 计为完成；后续修复与最终证据见下文。
- 用户明确授权本轮使用或新增测试客户并提交检测、问卷。Pixel 覆盖安装后保留销售登录态，已新增仅含测试姓名的客户。Release 正常登记、初始化并开始扫描，蓝牙及精确定位权限齐全、定位服务开启；两次扫描均未显示设备且超过 30 秒仍停留在搜索。返回可取消评估。尚无真实采样、上传或问卷提交证据，不归因于 Protobuf，不计 4.2 完成。
- 首轮 AAR 扫描弱引用只作为待核查线索，未直接归因或修改接入逻辑，PR 保持未合入；随后取得修复授权并通过真实 AAR 回归验证。
- API 24 的 `SalesEvaluationMockFlowTest` 8 项通过，覆盖取消、重试、后台扫描与迟到回调；Pixel 已覆盖恢复 Debug 及匹配测试包，包标志含 DEBUGGABLE，销售首页与本轮新增客户仍可见，未卸载或清数据。
- 追加修复轮：真实 AAR 的 GC 回归先复现 2 项失败（停止回调丢失、设备回调被回收）；`QlzVendorScanner` 强持有回调后 3 项全部通过。完整 preflight 再次通过，App 386 项、助手 34 项单测无失败/跳过，双应用 Debug/Lint、既有 App warning allowlist 均通过。
- 追加混淆专项：正式 R8 规则不变，最终 acceptance Release 在 API 24/37 各 8 项实际 SDK 消息/Gzip 合约通过。最终测试 APK 不含厂商消息或 Google Protobuf 实现副本；R8 mapping 确认扫描强引用字段仍保留。受控 `java.lang.String` 用例返回 1 项失败及 `INSTRUMENTATION_CODE: 0`，正常专项返回 `OK` 与 `-1`。
- 用户解锁后，追加修复的 acceptance Release 成功发现并连接 QLZ，完成真实采样、上传及自动 H5 跳转。用户填写后由客户端操作提交，H5 显示“A级(12分)”；确认仅刷新 H5，左上角返回后原生完成页显示“评估成功，评估等级为：A级”。系统返回回到保留销售登录态的首页。
- 验收目标 APK SHA-256：`aa93b076b43abaab6feb4dacb632c535797d2278f9ce0e418549bcba9d8137e7`。最终模拟器 Debug 完整消息+mock 共 21 项、Pixel Debug 完整消息 13 项通过；两台均覆盖恢复 Debug，Pixel 已正常启动，未卸载或清数据。
- 最终候选 `cbf1a2fd` 已非强制推送至现有 #125，保留 Dependabot 历史；合入前核对 base 为 `ba150fb7`、head 未变。[Android CI 35439181236](https://github.com/wj20101/longcare/actions/runs/35439181236) 的构建、Lint、instrumentation smoke 全部通过，Face SDK Migration Check 同样通过。
- #125 已按授权合入，合入提交为 `830b680f`，与已验收候选的文件树一致。[主分支 Android CI 35439833065](https://github.com/wj20101/longcare/actions/runs/35439833065) 全部通过，包括构建/Lint 与 instrumentation smoke。17 项任务全部完成，既有 QLZ/腾讯 SDK 生产 blocker 保持不变；本 change 尚未归档。
