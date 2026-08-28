## 1. 固定基线与覆盖清单

- [x] 1.1 记录 HEAD/工作树状态、日期、JDK/Gradle/AGP/Kotlin/Android CLI/SDK 版本和可用设备到忽略的 `build/reports/project-health/<baseline-id>/`，并验证基线能够唯一关联本次所有证据且不包含凭据或本机秘密。
- [x] 1.2 合并 `settings.gradle.kts`、included build、Android CLI `describe`、Gradle tasks 和源码集生成模块/变体/测试/产物清单，并验证每个声明模块及 Debug、验收/生产 Release、benchmark/profile 路径在覆盖矩阵中恰有明确状态入口。
- [x] 1.3 枚举 Manifest 组件、权限、外部 SDK、持久化/网络/文件边界、质量脚本、GitHub workflows 和当前真相文档，并验证这些工程表面均映射到后续检查项而没有匿名“其他”区域。
- [x] 1.4 使用 `android docs search/fetch` 复核当前应用质量、测试、性能、安全和大屏基线，将四个 Android 质量支柱映射到检查域，并验证每个支柱至少有一个证据来源和完成条件。

## 2. 构建、工具链与依赖健康

- [x] 2.1 审查 Wrapper、JDK toolchain、AGP/Kotlin/KSP、SDK 级别、version catalog、仓库策略、插件和 build-logic 一致性，并用 Gradle 配置/任务解析验证所有模块可以被正确发现且没有未经解释的动态版本、重复版本源或仓库旁路。
- [x] 2.2 生成项目依赖图和关键变体依赖证据，检查模块边、`api`/`implementation` 暴露、Jetifier、本地/私有 Maven 切换和本地 AAR 来源，并验证结论与 dependency allowlist、技术栈及厂商集成文档一致。
- [x] 2.3 运行 `bash scripts/quality/preflight_local.sh --local-fast` 及未被该入口涵盖的 ci-required 静态守卫，逐项记录退出状态，并验证 registry、脚本、workflow 和文档对“哪些门禁实际执行”的描述没有漂移。
- [x] 2.4 运行 `bash scripts/quality/preflight_local.sh --full` 和全部可发现模块/build-logic JVM 单测任务，分别记录编译与测试结果，并验证聚合成功不会掩盖零测试、未发现测试或未执行模块。
- [x] 2.5 运行普通 CI 对齐的 Lint、Debug APK/AAB 构建和 warning allowlist 检查，验证产物存在、Lint 报告可解析且 waiver 没有新增、失效或缺少退出条件的条目。
- [x] 2.6 在具备受控配置时构建显式 acceptance Release，并分别运行 production 配置/厂商 readiness 守卫；验证已知厂商阻断记为 `EXPECTED_FAIL_CLOSED`+P0、任何不同根因记为 `FAIL_NEW`，缺少签名或外部配置则记为 `UNVERIFIED`/`ENVIRONMENT_ERROR`。

## 3. 架构与代码质量深查

- [x] 3.1 在现有自动守卫之外人工复核模块依赖、包所有权、Feature API、DI 绑定、legacy 冻结区域和 oversized 文件/类型，并用具体导入、依赖边或符号证据验证每个架构结论。
- [x] 3.2 审查 ViewModel/UseCase/Repository 的状态、事件、取消传播、异常恢复和 dispatcher 注入，抽查所有高风险协程与 Flow 链路，并用 focused tests 或可复现调用路径验证新增发现而非只依据命名推测。
- [x] 3.3 审查 Activity/Service/Receiver/相机/NFC/定位/闹钟/安装器/WorkManager 的生命周期、后台限制、资源释放和进程重建语义，并验证每个高风险组件至少具有静态契约证据及现有测试或明确的运行时补证项。
- [x] 3.4 检查 TODO/FIXME、空 catch、宽泛 suppress/ignore、过宽 R8 规则、重复/不可达代码、公共 API 暴露和长期 allowlist/预算，验证每个报告项都有实际匹配范围且不把厂商规则与项目规则混为一谈。

## 4. 测试可信度与业务契约

- [x] 4.1 按模块和 `test`/`androidTest`/benchmark 源码集建立测试清单，检查 ignored/disabled、无断言、硬编码陈旧预期、环境耦合和 flaky 等风险，并验证 CI/workflow 的实际 task 能发现并执行声称覆盖的测试。
- [x] 4.2 运行登录/会话、类型安全导航与返回 key、Retrofit 路径/字段、Room migration、DataStore/WorkManager、图片文件生命周期和上传队列的 focused tests，并验证每个稳定契约都有通过证据或明确测试缺口。
- [x] 4.3 运行身份核验/眨眼状态机、定位会话、照片数量与重试、倒计时/闹钟以及销售草稿/QLZ 编排的 focused tests，并验证失败、取消、恢复、换号和重建分支没有被成功路径结果代替。
- [x] 4.4 检查 instrumentation 聚合、runner、测试 APK、affected scope、`run_instrumentation` 和 smoke class 编排；在可用模拟器上执行所有可发现 instrumentation，并验证空模块不会伪失败、未执行测试不会伪绿。
- [x] 4.5 把护理端和销售端关键旅程逐一映射到 unit/contract/instrumentation/manual/device 证据，验证身份/NFC、定位、拍照、倒计时、QLZ、持久化和应用更新没有被通用启动 smoke 误算为已覆盖。

## 5. 安全、隐私、数据与供应链

- [x] 5.1 检查源码 Manifest 与 Debug/验收/Release 合并 Manifest 的 exported 组件、intent surface、权限、foregroundServiceType、FileProvider、PendingIntent、网络安全和 debug-only 能力，运行现有 release 守卫并验证每个暴露面均有最小权限依据。
- [x] 5.2 对源码、资源、Gradle、日志、BuildConfig、workflow 和历史跟踪文件执行 secret/PII/敏感日志检查，验证不存在可提交凭据、固定生产秘密、宽泛日志泄露或 debug 签名 fallback；命中项只记录脱敏证据。
- [x] 5.3 审查隐私同意前后初始化、会话失效/退出换号、Room/DataStore/文件删除、图片 EXIF/上传、WebView、TLS/证书验证和临时凭据生命周期，并通过 focused tests 或受控路径验证数据不会越过其同意、账号或所有权边界。
- [x] 5.4 审查 QLZ、腾讯人脸、AMap、COS、Bugly 等依赖的 AAR/Manifest/权限/native ABI/16 KB 对齐/consumer rules 和来源校验，并验证可观察证据与现有 P0 blocker 一致；闭源不可见部分明确记为外部证据缺口。
- [x] 5.5 检查依赖树中的废弃、重复、版本冲突和已知风险信息来源，在无法访问可信漏洞/版本数据库时标为 `UNVERIFIED`，并验证不会仅凭“可编译”把供应链判定为健康。

## 6. 受控运行时、用户体验与自适应

- [x] 6.1 构建启用本地 mock 的 Debug APK，在可用模拟器上用 Android CLI 安装、启动、抓取布局和截图，验证首次启动、隐私同意、登录入口、会话切换及主要导航可达；无设备时记录补证条件。
- [x] 6.2 在受控账号/设备可用时验证护理链路的 NFC/读卡、身份与相机、定位、照片上传、倒计时和完成恢复，并验证拒绝权限、后台/前台、任务移除、失败重试和退出换号；会触达真实数据的步骤未经授权保持 `UNVERIFIED`。
- [x] 6.3 在受控账号/硬件可用时验证销售链路的客户登记、照片上限、QLZ 蓝牙权限/Token、表单与报告 WebView，并验证硬件或服务端缺失不会被静态分析结论替代。
- [x] 6.4 在手机、平板/折叠或可调整窗口配置上检查方向、多窗口、低高度、字体缩放、触控目标、语义/无障碍和状态恢复，重点验证 CameraX/ML Kit 坐标、水印及全屏提醒；缺少真机的配置明确记录证据强度。
- [x] 6.5 对前后台切换、进程重建、网络切换/离线、低内存和配置变化执行可控恢复测试，验证持久状态、一次性动作、WorkManager、Service 和受管文件不会重复、丢失或跨账号泄漏。

## 7. 性能与资源健康

- [x] 7.1 检查 Debug/验收/Release APK/AAB 的大小、ABI、资源 shrinking、mapping、R8 分析、`baseline.prof`/`baseline.profm` 和 Startup Profile 打包，验证各产物语义与构建变体一致且不使用 Debug 产物推断生产结果。
- [x] 7.2 在代表性设备可用时用 Macrobenchmark 多次测量冷/热启动 TTID、TTFD、Profile/None 和关键旅程，验证预置状态和旅程等价并报告分布而非单次最好值；无设备时保留 `UNVERIFIED`。
- [x] 7.3 对启动、列表/Compose 渲染、相机、人脸、定位、图片处理和长时服务收集 jank、CPU、内存、网络与电量证据，验证异常峰值能够关联到具体旅程；模拟器结果不得宣称为真机性能结论。
- [x] 7.4 若可访问 Android Vitals/崩溃平台，则核对 crash、ANR、启动、渲染和设备分布与本地结论；若不可访问，验证总结明确排除线上健康度判断。

## 8. CI、发布与文档一致性

- [x] 8.1 逐个审查 Android CI、Release、Baseline Profile、Face SDK Migration、CI Health 和清理 workflows 的触发、权限、固定版本、timeout、retention、affected scope、缓存和失败上传，验证文档声称的阻断/观察语义与实际 job 条件一致。
- [x] 8.2 核对验收/生产配置、签名、Manifest、导出组件、厂商 readiness、AAR 来源、R8 和发布元数据检查，验证 acceptance 不会被标成 production，production 已知 blocker 不能被 waiver 或 fallback 绕过。
- [x] 8.3 将 README、产品概览、系统架构、技术栈、页面路由、依赖规则、质量门禁、集成/合规和路线图逐项与代码事实比对，并验证每个漂移都有具体事实来源和目标维护文档。

## 9. 汇总、分级与交付

- [x] 9.1 补齐覆盖矩阵的每个计划检查项，验证不存在空状态；汇总 `PASS`、`FAIL_NEW`、`KNOWN_RISK`、`EXPECTED_FAIL_CLOSED`、`UNVERIFIED`、`ENVIRONMENT_ERROR` 和 `N/A` 数量及范围。
- [x] 9.2 去重并复核所有发现的复现证据、影响、置信度和 P0/P1/P2，验证已知风险不重复报新、预期门禁通过不降低原风险、静态推断不冒充运行时证据。
- [x] 9.3 仅将复现稳定且长期有效的事实同步到 `docs/README.md` 维护矩阵指定的现有文档或必要 ADR，运行 `preflight_local.sh --local-fast` 和链接检查，并验证未提交原始报告、本机路径、凭据或 `build/` 产物。
- [x] 9.4 为每个需整改的独立问题给出后续 OpenSpec change 名称、范围、验收证据和依赖关系，验证互不相关修复未合并且本 change 没有修改业务代码或质量守卫。
- [x] 9.5 输出最终体检摘要，明确可确认健康的范围、P0/P1/P2、已知 fail-closed、未验证项及补证条件，并验证只要存在关键未验证项就不使用“项目完全健康”的结论。
- [x] 9.6 运行 `openspec validate assess-project-health --strict` 与 `openspec validate --all --strict --no-interactive`，检查 `git diff` 和任务完成状态，验证所有规划契约满足后再提交用户进行归档评审。
