## 1. 实施基线与范围

- [x] 1.1 确认 `complete-device-h5-evaluation-flow` 的无原生标题栏任务和新 UI 验收完成；以对应提交和测试结果作为升级基线，不沿用 UI 修改前的验收结论。
- [x] 1.2 复核当前版本目录、官方发布说明和全部 11 个开放 PR 的 head/base/冲突/检查状态，更新 design 对照表；明确分支更新、新建/合入 PR 的授权，未授权不执行远端写入。
- [x] 1.3 核实 Protobuf 仍为 4.28.3、datetime 仍为兼容变体、AGP/Gradle/JDK/SDK 基线不变；在计划/获授权的 PR 说明中记录 #125 暂缓和 #119 已关闭无需处理，以版本差异和 PR 状态验证。

实施基线为 `34d599db`（已推送）：H5 change 20/20，完整 preflight、双应用 Debug/Lint、结果分支单测及 API 24/现代 WebView 回归通过；真实表单提交与后续只读等级查询的组合证据见该 change，不额外提交问卷。2026-09-19 复核快照及授权见 design；新主分支 CI 为 35404522867，尚不作为通过证据。

## 2. 恢复 CI 与处理 #115

- [x] 2.1 复查主分支最新 CI 与 Maven Central 429 根因，执行有界重试或有证据的最小缓存/并行调整；以依赖解析和 verify-build 恢复通过为验收，不覆盖 Lint 内部依赖或放宽门禁。
- [x] 2.2 将 #115 的有效修复整合到最新基线，保留助手和现有 workflow 变化；补充 KVM 存在/权限成功及失败、稳定并发分组隔离、三类 CI 路径触发 smoke 的守卫测试并通过。
- [x] 2.3 在授权后更新 #115 并运行真实 CI，核验同 PR 旧运行取消、不同 PR 不互相取消及 smoke 实际通过；审查当前候选差异和必需检查，通过后按授权合入，记录提交及运行链接。

#115 已用 merge 保留原历史并快进推送 `0b68f384`，未 force-push。相对 H5 基线只改 CI workflow、影响检测、守卫/测试及对应文档。7 项脚本测试、完整 workflow 守卫、local-fast 和严格 OpenSpec 校验通过。

主分支 `34d599db` 的 [CI 35404522867](https://github.com/wj20101/longcare/actions/runs/35404522867) 已完成依赖解析及 verify-build，429 未重现；未修改依赖仓库、Lint 内部版本或配置缓存。其 smoke 后续失败：emulator 明确报告无 KVM 权限，开机耗时 586 秒，ExampleInstrumentedTest 两次因进程启动 ANR 被终止，后续 H5 类未执行。smoke 不在 2.1 的通过声明内；继续以包含 KVM 修复的 #115 验收，不把未执行测试标为通过。

并发实测：#115 后续验收记录提交 `875f4116` 触发 [35404957399](https://github.com/wj20101/longcare/actions/runs/35404957399)，自动取消前一运行 [35404905439](https://github.com/wj20101/longcare/actions/runs/35404905439) 的 verify-build/smoke（always 清理结束后整体 cancelled），同时 #122 的 [35366151804 attempt 2](https://github.com/wj20101/longcare/actions/runs/35366151804/attempts/2) 继续独立运行并通过。#122 本次重跑只用来验证隔离，不作为新基线依赖合入证据。

#115 当前候选的 detect-affected、verify-build、instrumentation-smoke、cleanup 全部通过；KVM 可用后模拟器开机 55 秒，基础 smoke 为 `OK (1 test)`，不声称覆盖全部 H5。确认 head/base 和检查后，以锁定 head 的普通 merge 合入，合并提交 `aba9fb01dde44cdf03e3d825fa01ad1faa2b4517`。没有使用管理员绕过检查或手动取消伪造自动取消结果。

## 3. 运行时依赖补丁

前置条件检查：合法 Release 签名校验通过；当前 Coil 3.6.0 的显式 acceptance `:app:assembleRelease`（禁用 unsigned/debug fallback）已通过 R8，仅作升级前对照，不宣称已复现 Coil 3.6.3 所修复的问题，也不替代升级后验收。

- [x] 3.1 将 #120 的 Coil 候选更新为 3.6.3，核对解析图；运行图片加载/取消/缓存及统一图片管线测试，并用合法 acceptance 签名配置验证 R8 混淆构建，缺少条件时保留未验收。
- [x] 3.2 基于最新基线处理 #127 的 Okio 3.18.2，核对 OkHttp/Okio 解析与网络、文件读写相关测试，不引入强制版本覆盖或其他传递依赖升级。
- [x] 3.3 基于最新基线处理 #121 的 Room 2.8.5，验证 DAO/Flow、关闭/取消、既有迁移测试和 schema 无意外差异；不增加破坏性迁移。
- [x] 3.4 对上述三个 PR 逐项完成双应用构建/Lint和专项检查，按授权逐项合入；每次主分支变化后刷新后续 PR 基准并验证最新候选，不沿用旧 CI 结论。

#120 候选 `15b06fd5` 已整合 `9298f5ac`，版本改为 3.6.3；双应用实际 Coil 组件解析一致，12 项图片专项、完整 preflight、双应用 Debug/Lint、warning allowlist/隔离守卫及显式 acceptance R8 构建通过。新增解码测试使用 Robolectric Native Graphics，不用 fake engine 替代实际解码；取消/磁盘缓存资源释放有断言。PR [CI 35406981706](https://github.com/wj20101/longcare/actions/runs/35406981706) 通过（依赖改动未触发 instrumentation，不声称远端图片专项覆盖），锁定 head 正常合入为 `ff3765d5bae2839bbfd5a6c43a0d6f76e860fe04`。#115 合入后的主分支 [CI 35406666485](https://github.com/wj20101/longcare/actions/runs/35406666485) 也全部通过。3.4 仍待 Okio/Room 完成。

#127 候选 `865e5943` 已整合 `9e324b8c`。发现目录升级原本只影响正式 App，因此为直接使用 Okio 的 `core:data` 补齐显式目录依赖；正式 App、助手和 Data 实际解析均为 3.18.2，OkHttp 保持 5.5.0，无 force。22 项编码/请求/响应文件/图片/接口适配专项、完整 preflight、双应用 Debug/Lint、warning allowlist 和严格规格校验通过；[CI 35407666228](https://github.com/wj20101/longcare/actions/runs/35407666228) 通过（instrumentation 按范围跳过），锁定 head 正常合入为 `d7012b9c8393ac0381ea11a41194326dcd29b3a7`。

#121 候选 `fa881eb5` 已整合 `bdf15a37`。双应用 Room 组件解析为 2.8.5；新 5 项生命周期测试及既有 2 项迁移/重开测试在 API 24 模拟器与 API 37 Pixel 10 均通过，所有数据库均为隔离测试库，不触碰正式 App 数据。升级前 2.8.4 原样 7 项测试中仅关闭后失效通知未抛异常的一项失败，2.8.5 为 7/7；schema、数据库版本和既有迁移策略无改动。完整 preflight、双应用 Debug/Lint、既有 allowlist/隔离守卫和严格规格校验通过。[CI 35408236120](https://github.com/wj20101/longcare/actions/runs/35408236120) 通过（instrumentation 按范围跳过；数据库设备证据来自本地），锁定 head 正常合入为 `450a592ba2406cb31689765be4eca7593dfb4616`。三个运行时升级均在前一项合入后刷新基线并验证完成。

## 4. 编译与测试工具链

- [x] 4.1 处理 #126 的 KSP 2.3.12，先在现有 Kotlin 下验证 Hilt/Room/Moshi 生成、跨模块编译和 Lint，再纳入下一项目标组合验证。
- [x] 4.2 基于最新主分支整合 #122 并升级 Kotlin 2.4.20（初次复核无冲突，KSP 合入后需保留两个目标版本解决相邻行冲突），检查相关编译器/Compose 插件版本一致；通过主应用/助手完整 JVM 单测、代码生成与构建，审查并按授权逐项合入 #126/#122。
- [x] 4.2a 按追加确认核对 AGP/Gradle 稳定版兼容组合，保留 9.4.1/9.7.1；Kotlin 合入后以 Wrapper 生成任务同步 JAR/启动脚本及 properties，验证官方 SHA-256、实际运行版本、build-logic 测试、双应用 Debug/Lint 和完整 preflight；独立 PR 最新 CI 通过后按授权合入，不采用预览版或移除校验。
- [x] 4.3 升级 Compose BOM 2026.09.00 并在授权后建立独立 PR，核对实际解析映射；运行 Navigation 3 状态/返回、H5 无标题栏/键盘/弹窗/大字体回归，最新检查通过后按授权合入。
- [x] 4.4 处理 #128 的 Robolectric 4.17，核对 JDK/API 配置及受影响 shadow 用法；主应用和助手完整 JVM 单测通过后按授权合入，不以 build-only 替代。

#126 候选 `1ad9b625` 已整合 `46cd1529`，只升级 KSP 到 2.3.12，Kotlin 保持 2.4.10，不启用 backing field 新特性。Hilt 注入器、Room DAO/数据库和 Moshi DTO adapter 重新生成；双应用 Debug/Lint、Data 测试 APK 和完整 preflight 均通过，新生成 DAO 在 API 24 的 7 项测试通过，schema 无差异，既有 allowlist 通过。[CI 35408739082](https://github.com/wj20101/longcare/actions/runs/35408739082) 通过（instrumentation 按范围跳过），锁定 head 正常合入为 `ce06b1d0b3afcd1063b0b819b1de095cc5b897c3`；目标 Kotlin 组合验证仍属于 4.2。Room 合入后的主分支 CI 35408621120 也已通过。

#122 候选 `438b0542` 整合 `ce06b1d0`，仅解决 Kotlin/KSP 相邻行冲突并保留 2.4.20/2.3.12。实际 compiler-embeddable、KGP、Compose/serialization 插件及处理器 metadata 均为 2.4.20；双应用 Debug/Lint、Data 测试 APK、完整 preflight 和 API 24 数据库 7 项测试通过，schema/allowlist 无新增差异。[CI 35409314741](https://github.com/wj20101/longcare/actions/runs/35409314741) 通过（instrumentation 按范围跳过），锁定 head 合入为 `740d514b56750c11f6b1e82558db388c7ed86411`。Wrapper 追加范围未混入该 PR。

#129 候选 `2717c036` 基于 `740d514b`，通过官方 `wrapper` 任务同步 JAR；Unix/Windows 脚本重新生成后无差异，properties 仅排序变化，分发包摘要及 URL 验证保留。JAR 官方 SHA-256 匹配，实际 Gradle 为 9.7.1；build-logic 测试、双应用 Debug/Lint、完整 preflight、allowlist/隔离/严格规格检查通过。额外 API 24/WebView 52 的 24 项及 API 37 Pixel 10 的 34 项 Navigation/WebView 对照通过，均为本地测试内容。[CI 35409946447](https://github.com/wj20101/longcare/actions/runs/35409946447) 通过（远端 instrumentation 按范围跳过），锁定 head 合入为 `f836d2400520af26afb73de293b4760d3bc13931`。

#130 候选 `2eff88f5` 基于 `f836d240`，实际双应用 animation/foundation/runtime/ui 解析为 1.12.1，Material 3 保持 1.4.0。完整 preflight、双应用 Debug/Lint、allowlist/隔离和严格规格检查通过；[CI 35410617491](https://github.com/wj20101/longcare/actions/runs/35410617491) 通过（远端 instrumentation 按范围跳过）。API 24/WebView 52 的 24 项 Navigation/WebView 测试通过。Pixel 10 初轮键盘与系统栏两项受安全锁屏/DreamActivity 占用焦点影响；用户解锁且确认 `deviceLocked=0` 后，同一 APK 原样重跑 34 项全部通过（39.413 秒），未改断言或绕过锁屏。锁定 head 正常合入为 `c5bb538715cb697e01d1d1fc1622a3a928d1d949`。

#128 候选 `247ee106` 整合 `c5bb5387`，实际 Robolectric/sandbox/shadows 均为 4.17。初轮完整测试定位到 JDK 模块访问异常；依官方指引仅为 Android 模块的 Test JVM 添加 `java.base/jdk.internal.access` 的 `--add-opens`，不改变 App、daemon、纯 JVM 模块、JDK/SDK 或断言。正式应用 370 项、助手 34 项（无失败/跳过）及其他完整 preflight 模块通过；双应用 Debug/Lint、build-logic tests、allowlist/隔离/严格规格通过。强制重跑 458 项 Lint 相关任务后旧缓存 quickfix 提示消失。[CI 35411553102](https://github.com/wj20101/longcare/actions/runs/35411553102) 通过（instrumentation 按范围跳过），锁定 head 合入为 `b114b1b7f31b9fbb60fb8c80b6db77da0cd911ed`。

## 5. 性能工具与 #117

- [x] 5.1 处理 #124/#123，将 Baseline Profile 与 Benchmark 同步验证为 1.5.0 稳定版；核对插件/运行库解析及 benchmark 构建任务，按授权合入后确认无 RC 残留。
- [x] 5.2 在最终 H5/依赖提交上重新生成 baseline/startup profile，核验生成任务、设备/版本和产物来源，审查生成差异；更新现有 #117 而非直接接受旧基线产物或创建重复 PR。
- [x] 5.3 验证 profile 能被当前构建消费，执行相关启动/性能检查；核对最新 #117 差异及 CI 后按授权合入，保留可追溯的基准提交与证据。

#124 独立候选 `aad82eb2` 基于 `b114b1b7`，插件解析、采集 APK、双应用 Debug/Lint 和完整 preflight 通过；[CI 35412086127](https://github.com/wj20101/longcare/actions/runs/35412086127) 通过。#123 联合候选 `d8dfb852` 保留两稳定版解决相邻行冲突，实际 benchmark-common/macro/junit4 与插件均为 1.5.0，Benchmark R8/采集/双应用构建、完整 preflight 和守卫通过，[CI 35412361527](https://github.com/wj20101/longcare/actions/runs/35412361527) 通过。先锁定 #124 合入为 `7cd9abf6aa271cf1fa4dd4ab25775b49546e9b55`，再将 #123 刷新为 `3d9503f6`（源码树与联合候选完全一致）并复核构建/完整 preflight；[最新 CI 35412660891](https://github.com/wj20101/longcare/actions/runs/35412660891) 通过后锁定合入为 `48de70d86a50a061aa9924d05c9be63253879ca2`。远端 instrumentation 均按范围跳过；两个版本无 RC 残留，profile 重生成仍属于 5.2/5.3。

#117 已基于最终主分支 `48de70d8` 保留历史整合为生成基准 `a1d36804`。以稳定版 1.5.0 工具在 `pixel6Api33`（API 33 AOSP ARM64 Managed Device）执行 `:app:generateReleaseBaselineProfile`，显式 acceptance 且禁用签名兜底，不触碰个人手机。先采集成功，再以 class 参数单独执行 `BaselineProfileGenerator`，最终 XML 为 1 项、0 失败/错误/跳过，耗时 108.105 秒；不把按 enabledRules 排除的 StartupBenchmarks 当通过。baseline/startup 各 16,263 条规则，SHA-256 均为 `67a665be74b8f6ae868463475b5cb5b7ebe34f9b43bd6c53c5f95d5458a20bf1`，规则全部由工具生成，未手改；两次采集相差 3 条，未宣称字节级确定性。现有脚本仅启动/滚动/返回，两文件相同的语义限制已如实写入技术栈文档。继续验证打包消费、真机启动和新 CI 后才允许合入。

上述阶段性待验收项已在最终候选 `c3ccf566` 完成：重新构建的 Benchmark APK 包含 `assets/dexopt/baseline.prof`（12,743 字节）与 `baseline.profm`（707 字节）。Pixel 10 / API 37 使用相同合法签名覆盖安装，`StartupBenchmarks` 两项测试通过（342.398 秒），无异常豁免；None / BaselineProfileMode.Require 均配置 10 次冷启动。实际有效 TTID 样本分别为 10 / 9 个，中位数分别为 219.035 / 192.365 ms，如实保留缺失样本，不视为 20 个有效测量点或普遍性能收益；未测登录后业务旅程或 TTFD。

#117 的[最新候选 CI 35413272878](https://github.com/wj20101/longcare/actions/runs/35413272878) 通过后，复核差异并锁定 head 正常合入为 `8db746b8b60e20ff78686f13d0384c6017bdf236`。未使用管理员绕过检查、force-push 或旧 profile。

## 6. 综合回归与交付

- [x] 6.1 串行执行完整 preflight、`:app:lintDebug :app:assembleDebug :assistant:lintDebug :assistant:assembleDebug`、既有 warning allowlist 和助手隔离守卫，全部通过且不新增豁免。
- [x] 6.2 在 API 24 旧内核与现代设备执行受影响的 Navigation 3/WebView instrumentation 和 H5 UI 回归，验证真实关闭调用、系统返回、报告/隐私隔离与生命周期；记录实际覆盖，使用已有测试数据或 mock，不无授权新增业务提交。
- [x] 6.3 同步技术栈/CI/性能相关长期文档，核对所有升级目标、PR 处置和 Protobuf 暂缓理由与最终事实一致；运行 `openspec validate --all --strict --no-interactive`、`git diff --check` 并审查最终差异。
- [x] 6.4 复查最终主分支 CI、PR 合入提交及专项测试，向用户列明已升级/已合入/仍暂缓项和实际未验收项；生产 fail-closed 保持不变，不把内部验收构建宣称为生产可发布。

最终代码/依赖上完整 preflight 通过（有效增量缓存，不宣称强制重跑所有 JVM 单测），双应用 Debug/Lint 与 App 测试 APK 强制重跑 565 个任务全部通过，既有 warning allowlist、助手隔离和规格检查通过，无新增豁免。升级阶段正式 App 370 项、助手 34 项 JVM 单测已全部通过。

最终 API 24 / WebView 52 的 Navigation 3 与真实 WebView 测试 24/24 通过（12.379 秒）；API 37 Pixel 10 的对应测试及关闭/渲染退出专项 34/34 通过（38.524 秒）。覆盖本地 H5 首脚本调用、重复关闭、确认不关闭、H5 返回、系统返回/状态恢复、无原生标题栏、安全间距、键盘、大字体及报告/隐私生命周期隔离；不替代新的在线业务联调。本轮未新增客户或问卷，未清除个人设备数据；测试后已覆盖恢复并打开 Debug App。

最终合入提交 `8db746b8` 的[主分支 CI 35431845663](https://github.com/wj20101/longcare/actions/runs/35431845663) 全部完成并通过；instrumentation 按范围跳过，其专项证据为上述本地实测。技术栈、CI 与性能长期文档已同步；收尾 local-fast、严格 OpenSpec 校验（7/7）与 `git diff --check` 通过。

本计划已合入 #115、#120、#127、#121、#126、#122、#129、#130、#128、#124、#123、#117；仅 #125 仍开放并按授权暂缓，Protobuf 保持 4.28.3，未验证 QLZ 与新 Protobuf 的兼容性，不能声称已证实不兼容。AGP 9.4.1 / Gradle 9.7.1 稳定版组合及 Wrapper 同步完成，#119 仍关闭不重开。生产 QLZ 测试配置/弱 TLS、腾讯人脸 16 KB 对齐和 consumer rules 阻塞均未解除，内部验收通过不代表可生产发布。
