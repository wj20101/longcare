## 1. 建立可比较基线与保护边界

- [x] 1.1 重新读取当前 git diff、`app/proguard-rules.pro`、Release `proguardFiles` 和既有 Profile change 的重叠质量脚本，列出本 change 实际拥有的文件；用 `git diff --check` 确认开始状态，不覆盖用户已有改动。
- [x] 1.2 在不修改依赖图和 Release 属性的前提下运行 `./gradlew --no-daemon :app:analyzeReleaseR8Config`，确认当前 Optimization/Shrinking/Obfuscation 为 65.7%/72.7%/65.8%、`-keepnames class *` 匹配 26,601 个类且既定 unused/subsumed 关系仍成立；证据只保留在终端或 `build/`/CI artifact。
- [x] 1.3 记录厂商 AAR/JAR、`app/txkyc-face-consumer-proguard-rules.pro`、依赖版本和 production readiness 配置的开始校验值/差异状态，后续用相同只读检查证明本 change 未修改这些受保护输入。

## 2. 分组清理项目 R8 规则

- [x] 2.1 从 `app/proguard-rules.pro` 删除已由 `proguard-android-optimize.txt` 提供的通用 JNI 与 enum 项，以及 AndroidX Annotation 已提供的三条 `@Keep` 项；重新运行 Analyzer，确认必要规则改由默认/consumer origin 提供且对应项目 origin 消失。
- [x] 2.2 删除 Kotlinx Serialization 手工 keep 块和该区域重复的 `Signature` 属性，仅保留通用属性区的一份 `Signature`；运行 app 导航/route 序列化 focused tests 与 Analyzer，确认类型安全 route 仍可序列化、错误 `-keepnames` 不再限制无关类且官方 conditional rules 仍在合并配置中。
- [x] 2.3 删除当前零命中的 `com.evenmed.util.**`、`TreatmentBaseAct` 等项目项，以及被更宽保留规则覆盖的 `com.autonavi.aps.amapapi.model.**`、`com.comm.*` 和 `com.falth.data.*`；重新运行 Analyzer，确认窄重复项消失、更宽厂商规则保持原样。
- [x] 2.4 审查清理 diff，确认仍有实际命中的 Bugly、高德、ML Kit、QLZ、腾讯人脸/COS 规则未被收窄或过滤，且没有新增 `-dont*`、整包补偿 keep、AAR 修改或依赖升级。

## 3. 建立项目规则防回归守卫

- [x] 3.1 新增仅覆盖 `app/proguard-rules.pro` 的 package-wide keep allowlist，为每条仍保留的宽规则记录精确规则、owner 与理由；运行一致性检查确认无遗漏、无陈旧条目且不复制厂商 consumer rules 内容。
- [x] 3.2 新增无第三方依赖的项目 R8 verifier，检查 Release 仍合并 `proguard-android-optimize.txt`、项目规则和腾讯人脸规则文件，拒绝全局 `-dontshrink`/`-dontoptimize`/`-dontobfuscate`、本次已删除指纹及未登记宽规则；对清理后的真实项目运行并确认通过。
- [x] 3.3 新增 verifier fixture tests，覆盖合法配置、三个全局禁用项、已删除 Kotlinx/AndroidX/默认规则回归、未登记 package-wide keep、缺失默认优化文件和缺失腾讯规则文件，并确认每个失败场景给出规则与文件诊断。
- [x] 3.4 将 verifier/fixtures 增量接入 `test_android_build_governance.sh`、`preflight_local.sh` 和 `quality_gate_registry.json`，同步现有 workflow 治理断言；运行 governance 与 workflow focused tests，确认 local-fast/CI 只执行一次且保持 fail-closed。

## 4. Analyzer 与构建级回归验证

- [x] 4.1 对最终规则运行 `./gradlew --no-daemon :app:analyzeReleaseR8Config`，与任务 1.2 的同输入基线比较：三个分数均不得下降，项目来源的 unused/subsumed/错误 Serialization 规则消失，依赖相同规则即使改由 consumer origin 显示也不误判失败。
- [x] 4.2 运行 `bash scripts/quality/preflight_local.sh --full`、R8 verifier fixtures、受影响导航/状态恢复 focused tests、`./gradlew --no-daemon :app:lintDebug :app:assembleDebug` 和 Lint warning allowlist，确认静态与 Debug 门禁全部通过。
- [x] 4.3 构建 `./gradlew --no-daemon :app:assembleRelease :app:bundleRelease -Prelease.production=false -Prelease.acceptance=true`，把当次显式 APK/AAB 路径传给 release artifact verifier，并确认 mapping、资源 shrinking、ART Profile、R8/DEX checksum、签名和 Manifest 全部有效且产物仍标识为非生产。

## 5. 设备与厂商链路验收

- [ ] 5.1 在 API 36 设备的 minified acceptance 构建上执行登录、类型安全导航参数/状态恢复、定位、身份/ML Kit/腾讯核验、照片上传、倒计时、QLZ 评估、视频呼叫和应用更新 smoke，确认无 `ClassNotFoundException`、`NoSuchMethodException`、`NoSuchMethodError`、`UnsatisfiedLinkError` 或序列化恢复错误。
- [x] 5.2 单独运行现有 production Release 配置与 vendor readiness 门禁，确认 QLZ 固定测试配置/弱 TLS、腾讯人脸 16 KB 对齐和 consumer rule 问题仍按原原因 fail-closed，且任务 1.3 的厂商输入校验值/差异状态未变化。
- [x] 5.3 核对 `separate-startup-and-baseline-profile-semantics` 的 ARM64 两轮性能任务仍保持未完成/`unverified`，确认本 change 不以模拟器、R8 分数或 acceptance 构建替代真实性能收益证据。

## 6. 长期文档与最终一致性

- [x] 6.1 更新 `docs/architecture/ci-quality-gates.md` 记录项目 R8 静态守卫、Analyzer 与 acceptance 验收职责，更新 `roadmap-and-open-gaps.md` 将确定性清理从“下一批次”移为完成事实，同时保留厂商宽规则和 ARM64 真机性能开放项；运行文档/技术栈相关守卫确认无事实漂移。
- [x] 6.2 最终运行 `bash scripts/quality/preflight_local.sh --full`、全部新增 focused tests、`openspec validate --all --strict --no-interactive` 与 `git diff --check`，确认 diff 只包含本 change 规划范围、没有 build 报告/本机路径/凭据，并再次证明厂商 AAR/consumer rules、依赖、targetSdk、Navigation、WebView、数据库和启动时序未被夹带修改。
