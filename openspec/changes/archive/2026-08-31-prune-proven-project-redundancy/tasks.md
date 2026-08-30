## 1. 固定实施基线与证据

- [x] 1.1 记录当前分支、`git status --short --branch`、两个活动 OpenSpec 变更状态及本变更涉及文件的现有 diff；确认后续编辑不覆盖 R8、Startup/Baseline Profile 或其他未提交修改，并以再次执行相同只读命令可辨认本变更增量为验收。
- [x] 1.2 对 proposal/design 中的每类候选重新扫描生产源码、测试、资源、Gradle、Manifest、workflow、文档和脚本引用，并核对 Hilt/KSP、序列化、反射、JNI、typed route、资源与人工入口；只有仍满足三层证据门槛的候选进入删除，以零未解释引用为验收。
- [x] 1.3 在清理前运行现有架构、Android build governance、entry navigation、legacy allowlist 和受影响模块 focused tests，确认基线命令可执行；若存在与本变更无关的既有失败，必须先定位并保持失败集合不扩大。

## 2. 先建立行为与防回归守卫

- [x] 2.1 扩展现有 entry navigation focused tests，覆盖开始服务构造 `NfcSignInRoute` 的 `START_ORDER` 模式、完整订单参数透传、返回行为和 back stack 不含设备选择目的地；运行对应 JVM/API 36 focused tests 验证正向场景通过。
- [x] 2.2 在现有架构 verifier 及 fixture 中增加退役冗余类别检查，覆盖 Placeholder、伪 `FeatureEntry`、选设备 legacy package/route 和旧更新弹窗；运行正向工程与逐类负向 fixture，确认失败信息定位文件和分类。
- [x] 2.3 扩展现有 Android build baseline verifier/fixture，验证四个目标 Feature 的关键构建能力与直接依赖边界；运行 `test_android_build_governance.sh`，确认每项无用配置回流都能稳定失败。
- [x] 2.4 将 `test_affected_modules.sh` 接入既有 Android build governance 测试入口，运行其全部 changed-path fixture，确认 app、login feature、文档和质量脚本映射仍正确。

## 3. 删除纯占位与空注册

- [x] 3.1 删除 Core Common、Domain、Model、UI 的四个 Placeholder 声明，运行四个 Core 模块的编译/单元测试及架构 verifier，确认没有生产引用或 Android-free 边界回归。
- [x] 3.2 删除 Core Data、Home、Identification、Login 的四个无 binding/provider Hilt module 及只验证空对象存在的 Core Data 测试，运行受影响模块 KSP、编译和单元测试，确认 Hilt 图生成成功且没有缺失 binding。
- [x] 3.3 删除 Home、Identification、Login 的伪 `FeatureEntry`、三套模块自证测试、应用私有 feature registry/check 与 `FeatureRouteContractTest`，运行三 Feature 与 App 的编译/测试，确认真实 typed route 注册不受影响。

## 4. 移除不可达导航与重复 UI

- [x] 4.1 将服务开始调用点和 `OrderKey` 扩展改为语义明确的开始服务 NFC 导航动作，删除两个 `navigateToSelectDevice` 包装；运行新增导航测试确认生成的 route、模式和订单参数完全不变。
- [x] 4.2 删除 `SelectDeviceRoute`、route 注册、SelectDevice actions/UI/components 和仅供该页面使用的字符串资源；运行资源合并、App 编译和 entry navigation focused tests，确认没有悬空资源或 route 引用。
- [x] 4.3 从 `legacy_feature_files_allowlist.txt` 精确移除三个 SelectDevice legacy 文件，运行 legacy allowlist verifier 与 fixture，确认 allowlist 只收缩且未登记替代空壳。
- [x] 4.4 删除零引用的旧 `ui/components/UpdateDialog.kt`，保留实际 `features/update/ui/AppUpdateDialog` 及仍被其使用的资源；运行 App 编译和更新弹窗相关测试/静态引用检查，确认实际更新入口唯一且可达。

## 5. 合并重复测试并删除空 Manifest

- [x] 5.1 将两套 `UriJsonAdapterTest` 合并为一套规范测试，保留 Uri 正反序列化、null 和 `ImageTask` 正反序列化用例并删除重复文件；运行该测试类确认用例并集全部通过。
- [x] 5.2 在删除前生成 Debug/显式验收 Release merged manifest 的组件、权限与 metadata 基线，验证基线包含定位 Service 与 AMap APS Service 且不依赖待删空 Manifest。
- [x] 5.3 删除 Core Common/Data/Domain/Model/UI 及 Home/Identification/Login/PhotoUpload/ServiceCountdown 的十个空 Manifest，重新生成并结构化比较 merged manifests；确认组件、权限、provider、service 和 metadata 集合没有非预期差异。

## 6. 精简 Feature 构建能力与依赖

- [x] 6.1 精简 `:feature:location`：移除 Compose plugin/feature 及 Compose、Activity Compose、Lifecycle Runtime Compose、Hilt Navigation Compose 和直接 Bugly 依赖，保留 Core KTX/AMap/Hilt/Service Manifest 并显式使用 Lifecycle ViewModel、Coroutines Core；运行 dependency insight、KSP、编译、单测和 `lintDebug`。
- [x] 6.2 精简 `:feature:photoupload`：移除 Core KTX、Coil bundle、直接 Bugly 和过宽 Lifecycle Runtime KTX，显式声明 Lifecycle ViewModel 与 Coroutines Core；运行 dependency insight、KSP、编译、单测和 `lintDebug`，确认图片处理/上传只通过 Core 契约工作。
- [x] 6.3 精简 `:feature:servicecountdown`：移除 Core KTX 并以 Coroutines Core 替代 Coroutines Android，保留 Lifecycle ViewModel 和 Hilt；运行 dependency insight、KSP、编译、单测和 `lintDebug`，确认计时、轮询与图片状态逻辑通过。
- [x] 6.4 精简 `:feature:identification`：移除直接 Bugly 并显式补齐 Coroutines Core，保留 Compose、Core KTX、CameraX、ML Kit、DataStore、OkHttp 与 Hilt Navigation Compose；运行 dependency insight、KSP、编译、单测、Android test 编译和 `lintDebug`。
- [x] 6.5 检查四个模块的最终 Debug/Release 依赖图与 `module_dependency_allowlist.txt`，确认不存在旧制品残留、偶然传递 API 或新增越层模块边；若真实用途失败，只补回最窄直接依赖并重跑 6.1–6.4 对应验证。

## 7. 收敛质量入口与长期事实

- [x] 7.1 再次确认 `collect_build_baseline.sh` 与 `collect_ci_run_metrics.sh` 没有 workflow、注册表、文档、脚本或明确人工所有者且输出已被现有工具覆盖后删除；运行 CI health monitor fixture、质量快照和 build governance 测试，确认替代入口完整；若发现独有受维护用途则保留并补齐所有权而不强删。
- [x] 7.2 更新 `quality_gate_registry.json` 及现有架构/构建治理说明，确保退役指纹、模块构建最小化和受影响模块自测具有单一入口；运行 registry/workflow quality fixture 确认没有新增平行门禁或孤立脚本。
- [x] 7.3 更新 `ui-and-screen-map.md`、`system-overview.md`、`dependency-rules.md`、`ci-quality-gates.md` 与 `roadmap-and-open-gaps.md` 的受影响事实，移除伪 Feature route、SelectDevice 页面和旧依赖描述；运行文档/技术栈漂移守卫并确认不记录本机执行日志或一次性性能数字。

## 8. 综合验收

- [x] 8.1 运行所有新增/修改 shell 与 Python fixture、架构边界、legacy allowlist、Android build governance、entry navigation、module API visibility、dependency policy、release validation entry 和 workflow quality 守卫，确认正向基线通过且负向 fixture 按预期失败。
- [x] 8.2 运行受影响 Core/Feature/App focused unit tests、四 Feature 的 `lintDebug`、`:app:lintDebug` 与 `:app:assembleDebug`，确认编译、KSP、资源合并和 Lint 无新增失败或 waiver。
- [x] 8.3 运行 `bash scripts/quality/preflight_local.sh --full`，确认普通 CI 主路径全绿且现有 production Release fail-closed 条件没有被删除、改写或扩大。
- [x] 8.4 构建 `./gradlew --no-daemon :app:assembleRelease :app:bundleRelease -Prelease.production=false -Prelease.acceptance=true` 并对当次 APK/AAB 执行 artifact、mapping、资源收缩、Profile、签名和 Manifest 校验；确认产物仍明确为非生产验收构建。
- [x] 8.5 使用 Android CLI 在 API 36 模拟器执行服务单开始→NFC 页面→返回、定位启动/停止、拍照上传、倒计时和应用更新 smoke，结合 UI tree/日志确认无 SelectDevice 页面、崩溃、组件或生命周期回归。
- [x] 8.6 最终运行退役名称/路径与依赖残留扫描、`git diff --check`、`openspec validate prune-proven-project-redundancy --type change --strict --no-interactive`，并复核两个既有活动 change 的文件和任务状态未被本变更意外修改。
