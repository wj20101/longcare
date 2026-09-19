## 1. 实施基线与范围

- [x] 1.1 确认 `complete-device-h5-evaluation-flow` 的无原生标题栏任务和新 UI 验收完成；以对应提交和测试结果作为升级基线，不沿用 UI 修改前的验收结论。
- [x] 1.2 复核当前版本目录、官方发布说明和全部 11 个开放 PR 的 head/base/冲突/检查状态，更新 design 对照表；明确分支更新、新建/合入 PR 的授权，未授权不执行远端写入。
- [x] 1.3 核实 Protobuf 仍为 4.28.3、datetime 仍为兼容变体、AGP/Gradle/JDK/SDK 基线不变；在计划/获授权的 PR 说明中记录 #125 暂缓和 #119 已关闭无需处理，以版本差异和 PR 状态验证。

## 2. 恢复 CI 与处理 #115

- [x] 2.1 复查主分支最新 CI 与 Maven Central 429 根因，执行有界重试或有证据的最小缓存/并行调整；以依赖解析和 verify-build 恢复通过为验收，不覆盖 Lint 内部依赖或放宽门禁。
- [x] 2.2 将 #115 的有效修复整合到最新基线，保留助手和现有 workflow 变化；补充 KVM 存在/权限成功及失败、稳定并发分组隔离、三类 CI 路径触发 smoke 的守卫测试并通过。
- [x] 2.3 在授权后更新 #115 并运行真实 CI，核验同 PR 旧运行取消、不同 PR 不互相取消及 smoke 实际通过；审查当前候选差异和必需检查，通过后按授权合入，记录提交及运行链接。

## 3. 运行时依赖补丁

- [x] 3.1 将 #120 的 Coil 候选更新为 3.6.3，核对解析图；运行图片加载/取消/缓存及统一图片管线测试，并用合法 acceptance 签名配置验证 R8 混淆构建，缺少条件时保留未验收。
- [x] 3.2 基于最新基线处理 #127 的 Okio 3.18.2，核对 OkHttp/Okio 解析与网络、文件读写相关测试，不引入强制版本覆盖或其他传递依赖升级。
- [x] 3.3 基于最新基线处理 #121 的 Room 2.8.5，验证 DAO/Flow、关闭/取消、既有迁移测试和 schema 无意外差异；不增加破坏性迁移。
- [x] 3.4 对上述三个 PR 逐项完成双应用构建/Lint和专项检查，按授权逐项合入；每次主分支变化后刷新后续 PR 基准并验证最新候选，不沿用旧 CI 结论。

## 4. 编译与测试工具链

- [x] 4.1 处理 #126 的 KSP 2.3.12，先在现有 Kotlin 下验证 Hilt/Room/Moshi 生成、跨模块编译和 Lint，再纳入下一项目标组合验证。
- [x] 4.2 基于最新主分支整合 #122 并升级 Kotlin 2.4.20（初次复核无冲突，KSP 合入后需保留两个目标版本解决相邻行冲突），检查相关编译器/Compose 插件版本一致；通过主应用/助手完整 JVM 单测、代码生成与构建，审查并按授权逐项合入 #126/#122。
- [x] 4.2a 按追加确认核对 AGP/Gradle 稳定版兼容组合，保留 9.4.1/9.7.1；Kotlin 合入后以 Wrapper 生成任务同步 JAR/启动脚本及 properties，验证官方 SHA-256、实际运行版本、build-logic 测试、双应用 Debug/Lint 和完整 preflight；独立 PR 最新 CI 通过后按授权合入，不采用预览版或移除校验。
- [x] 4.3 升级 Compose BOM 2026.09.00 并在授权后建立独立 PR，核对实际解析映射；运行 Navigation 3 状态/返回、H5 无标题栏/键盘/弹窗/大字体回归，最新检查通过后按授权合入。
- [x] 4.4 处理 #128 的 Robolectric 4.17，核对 JDK/API 配置及受影响 shadow 用法；主应用和助手完整 JVM 单测通过后按授权合入，不以 build-only 替代。

## 5. 性能工具与 #117

- [x] 5.1 处理 #124/#123，将 Baseline Profile 与 Benchmark 同步验证为 1.5.0 稳定版；核对插件/运行库解析及 benchmark 构建任务，按授权合入后确认无 RC 残留。
- [x] 5.2 在最终 H5/依赖提交上重新生成 baseline/startup profile，核验生成任务、设备/版本和产物来源，审查生成差异；更新现有 #117 而非直接接受旧基线产物或创建重复 PR。
- [x] 5.3 验证 profile 能被当前构建消费，执行相关启动/性能检查；核对最新 #117 差异及 CI 后按授权合入，保留可追溯的基准提交与证据。

## 6. 综合回归与交付

- [x] 6.1 串行执行完整 preflight、`:app:lintDebug :app:assembleDebug :assistant:lintDebug :assistant:assembleDebug`、既有 warning allowlist 和助手隔离守卫，全部通过且不新增豁免。
- [x] 6.2 在 API 24 旧内核与现代设备执行受影响的 Navigation 3/WebView instrumentation 和 H5 UI 回归，验证真实关闭调用、系统返回、报告/隐私隔离与生命周期；记录实际覆盖，使用已有测试数据或 mock，不无授权新增业务提交。
- [x] 6.3 同步技术栈/CI/性能相关长期文档，核对所有升级目标、PR 处置和 Protobuf 暂缓理由与最终事实一致；运行 `openspec validate --all --strict --no-interactive`、`git diff --check` 并审查最终差异。
- [x] 6.4 复查最终主分支 CI、PR 合入提交及专项测试，向用户列明已升级/已合入/仍暂缓项和实际未验收项；生产 fail-closed 保持不变，不把内部验收构建宣称为生产可发布。

## 验收结论

22 项任务完成，已合入 #115、#120、#127、#121、#126、#122、#129、#130、#128、#124、#123、#117；#119 保持关闭。版本目标及关键验证见 design。#125 在本变更内暂缓，后来由 upgrade-protobuf-javalite 独立验证合入，不能把当时暂缓理解为已证明不兼容。

CI 修复通过 KVM、同 PR 自动取消和跨 PR 隔离实测。Coil 完成图片/取消/缓存及合法签名 R8 验证，Room 在 API 24/37 通过 7 项生命周期/迁移测试，schema 不变；KSP/Kotlin 代码生成、Wrapper 官方摘要与 build-logic 测试通过。Robolectric 仅增加测试 JVM 所需模块访问参数，未放宽 App 或 Lint。

最终完整 preflight、双应用 Debug/Lint、既有 allowlist/隔离/严格规格检查通过；升级阶段 App 370 项、助手 34 项 JVM 测试通过。最终 API 24 / WebView 52 的 Navigation 3/WebView 24 项和 API 37 的 34 项通过，无新客户/问卷提交。远端 instrumentation 按范围跳过时不计为设备覆盖。

Profile 在 API 33 独立 Managed Device、基准 a1d36804 上重新生成并验证打包消费。Pixel 冷启动 None/Require 各配置 10 次，实际有效 TTID 样本为 10/9，中位数 219.035/192.365 ms；不声称 20 个有效样本或普遍收益。baseline/startup 语义尚未拆分，未测登录后旅程或 TTFD，保留路线图待办。

最终合入 8db746b8 的[主分支 CI](https://github.com/wj20101/longcare/actions/runs/35431845663) 通过。厂商问题未修复；其后发布风险策略独立调整，不将本次依赖验收视为对外发布。
