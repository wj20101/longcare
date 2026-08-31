## 1. 固化统一验收契约与治理入口

- [x] 1.1 重新读取当前 git diff、两个原 change 的剩余任务、设备矩阵、Startup 质量配置、connected instrumentation 入口和 acceptance artifact verifier，列出本 change 实际拥有的脚本/配置/文档文件；运行 `git diff --check` 并确认不覆盖用户已有改动。
- [x] 1.2 新增机器可读真机验收配置，声明 API 36 physical/`arm64-v8a`/核心数/电量与热状态资格、十类 Release smoke、外部条件、禁止异常、双 verdict、报告 schema 和既有 5% TTID/TTFD 预算引用；运行配置 verifier 确认与 `target_platform_test_matrix.properties`、`startup_profile_quality.json` 及两个原 change 的场景集合一致。
- [x] 1.3 为配置 verifier 增加正反 fixture，证明缺设备字段、错误 API/ABI、重复或缺失场景、未知外部条件、缺禁止异常、单一总 verdict、缺报告脱敏策略和预算漂移都会稳定失败并指出字段。
- [x] 1.4 将纯配置/verifier fixture 增量接入 `preflight_local.sh --local-fast`、`quality_gate_registry.json` 和既有 Android build/workflow 治理测试，验证本地与 CI-required 路径只执行一次且不启动设备或构建 Release。

## 2. 实现真机资格与构建身份清单

- [x] 2.1 实现要求显式 `ANDROID_SERIAL` 的设备预检，读取 qemu 标记、API、主 ABI、CPU 核心、fingerprint、型号、电量和热状态，只输出 serial 哈希；在当前 API 28 MI CC 9e 和 API 37 模拟器上运行，确认分别因 API/TTFD 条件和 emulator 条件在安装前 fail-closed。
- [x] 2.2 为设备预检增加 AOSP/OEM 合成输出 fixture，覆盖合格 API 36 ARM64 真机、模拟器、API 28、x86_64、单核、多设备未选择、低电量、过热和无法解析状态，确认只有完整合格输入能生成稳定匿名设备身份。
- [x] 2.3 实现 execution manifest 初始化器，接受显式 acceptance APK/AAB、benchmark 目标 APK、测试 APK 和 Profile 文本路径，记录 Git SHA、工作树摘要、应用版本、variant、规范路径与 SHA-256；用合成小型产物 fixture 验证旧路径、缺文件、重复角色、包名/版本/variant 不符和校验值变化均失败。
- [x] 2.4 把现有 Release artifact verifier 作为 acceptance 产物进入 manifest 的强制前置步骤，并在阶段前后复核所有产物哈希；运行正反 fixture 确认不能用工作区旧 Profile、旧 APK/AAB 或不同构建的 benchmark APK 拼接执行。

## 3. 建立 minified acceptance Release smoke 账本

- [x] 3.1 建立十类场景目录，逐项固化登录、类型安全导航/状态恢复、定位、身份/ML Kit/腾讯、照片上传、倒计时、QLZ、视频呼叫和应用更新的前置条件、准确动作/目标、允许执行方式与外部依赖；运行目录 verifier 确认缺项、重复 id、自由文本状态、无目标节点或把 mock 成功当真实外设成功都会失败。
- [x] 3.2 实现 Release smoke 入口：只安装 manifest 绑定的 minified acceptance APK，按场景清理目标包日志、记录进程和时间窗，并为自动步骤调用现有 Android/ADB/connected 专项入口；用 dry-run 与假 ADB fixture 验证不会安装未绑定包、运行 production Release、修改设备全局安全策略或创建生产测试组件。
- [x] 3.3 实现场景结果记录器，只接受已登记 id 的 `passed`、`failed`、`blocked`、准确目标证据和类型化外部条件；用 fixture 验证未知/重复场景、跳过必需动作、缺目标、任意总体通过和跨 execution 写入均被拒绝。
- [x] 3.4 实现目标包日志扫描与脱敏，检测 `ClassNotFoundException`、`NoSuchMethodException`、`NoSuchMethodError`、`UnsatisfiedLinkError`、序列化恢复错误、FATAL EXCEPTION、ANR、进程死亡和超时，并删除 Token、手机号、身份证、URL query 与原始 serial；运行合成日志 fixture 确认禁止异常能推翻人工 `passed` 且脱敏后不泄露样例秘密。
- [x] 3.5 实现 R8 运行 verdict 聚合，要求十类场景全部 `passed`、日志零禁止异常且设备/构建身份一致；运行 fixture 确认任一 `failed`/`blocked`/缺失、跨设备或跨构建都会输出精确原因并阻止 `r8RuntimeAcceptance=passed`。

## 4. 建立两轮真机 Startup/Profile 收益判定

- [x] 4.1 实现显式 physical connected benchmark runner，在每轮前复核设备/电量/热状态和产物哈希，不传 `suppressErrors`，连续运行四场景双模式各 10 次冷启动，并在每轮完成后立即复制原始 AndroidX JSON、计算哈希和调用现有 normalizer/verifier；用 dry-run 证明命令固定同一 serial、构建、helper、模式和迭代数。
- [x] 4.2 新增双轮 comparator，把“场景 × TTID/TTFD”作为比较项，校验两轮 `deviceType=physical`、同设备/构建、Profile `required-applied`、完整 10 样本，以中位数执行每轮 5% 退化预算和至少一个比较项两轮持续改善的判定；用合成通过报告验证输出 `verified`。
- [x] 4.3 为 comparator 增加模拟器、API 28/缺 TTFD、缺轮次、缺模式/样本、Profile 降级、跨设备、跨构建、单轮超预算、两轮改善不一致和零持续改善 fixture，确认全部保持 `unverified` 并指出轮次/场景/指标。
- [x] 4.4 将两轮原始/归一化报告路径、SHA-256、中位数与比较结果写入 execution manifest，并在 verifier 中拒绝报告覆盖、路径逃逸和哈希变化；运行 fixture 确认报告只允许位于 `build/reports/real-device-acceptance/` 或声明的受控 CI artifact 根。

## 5. 聚合、隐私与长期维护

- [x] 5.1 实现独立 verdict 聚合与原 change 映射：`r8RuntimeAcceptance` 只对应 R8 任务 5.1，`startupProfileBenefit` 只对应 Profile 任务 7.5；运行 fixture 确认一个通过、一个阻断时不会输出单一总成功或关闭错误任务。
- [x] 5.2 增加 execution manifest schema、报告秘密扫描和 Git 跟踪守卫，覆盖账号、验证码、Token、身份证、照片、原始 serial、本机绝对路径和完整 URL query；用正反 fixture 确认本地报告可验证但不能进入提交或上传未脱敏 artifact。
- [x] 5.3 更新 `system-overview.md`、`ci-quality-gates.md`、`tech-stack.md` 与 `roadmap-and-open-gaps.md`，记录统一入口、两个独立 verdict、设备资格、外部阻断与模拟器/API 28 非验收边界；运行文档漂移、技术栈和质量注册表守卫确认事实一致。
- [x] 5.4 重新核对厂商 AAR/JAR、腾讯 consumer rules、QLZ 固定配置、依赖、SDK、Navigation、WebView、数据库和启动初始化文件的开始/结束哈希或 git diff，运行受保护输入检查证明本 change 未修改这些边界。

## 6. 真实设备执行与最终验收

- [x] 6.1 运行全部新增 verifier/fixture、`bash scripts/quality/preflight_local.sh --full`、`bash scripts/quality/verify_release_validation_entry.sh .`、`:app:lintDebug :app:assembleDebug` 和 Lint warning allowlist，确认静态、Debug 和治理路径全部通过。
- [ ] 6.2 构建 `./gradlew --no-daemon :app:assembleRelease :app:bundleRelease -Prelease.production=false -Prelease.acceptance=true`，把本次显式 APK/AAB、mapping、Profile 和 R8/DEX 证据交给既有 artifact verifier 与新 manifest 初始化器，确认 minified acceptance 产物完整且明确非生产。
- [ ] 6.3 在具备测试账号、有效订单、NFC/R65C、相机/定位、QLZ BLE 与厂商环境的 API 36 ARM64 真机上运行 connected 辅助 suite 和全部十类 minified acceptance smoke，确认目标节点全部到达且无禁止异常；任一外部条件缺失时保留 `blocked`，不得勾选 R8 原任务 5.1。
- [ ] 6.4 在同一台 API 36 ARM64 真机和同一构建输入上、设备状态稳定后连续运行两轮完整 Startup benchmark，保存原始/归一化 JSON 并运行 comparator；只有输出 `startupProfileBenefit=verified` 时才允许勾选 Profile 原任务 7.5，否则保持 `unverified`。
- [x] 6.5 分别复核两个 verdict 并只更新满足条件的原 change 任务；随后单独运行 production 配置与 vendor readiness 门禁，确认已知 QLZ/腾讯问题仍按原原因 fail-closed，且 acceptance/性能成功未被汇总为生产可发布。
- [ ] 6.6 最终运行 `bash scripts/quality/preflight_local.sh --full`、全部真机证据 verifier、`openspec validate --all --strict --no-interactive` 与 `git diff --check`，检查 diff 只覆盖本 change 规划范围、两个原 change 状态与证据一致，且没有跟踪 `build/` 报告、本机路径、凭据或厂商制品修改。
