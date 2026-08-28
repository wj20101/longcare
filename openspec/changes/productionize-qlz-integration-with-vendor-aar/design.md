## Context

参见 [proposal.md](proposal.md) 的责任边界、[QLZ 行为规格](specs/sales/qlz-device-assessment/spec.md) 和[项目体检增量规格](specs/engineering/project-health-assessment/spec.md)。当前项目把 QLZ 1.3.0.2 AAR 作为本地文件依赖，`QlzSdkClient` 是应用源码中唯一直接调用厂商类型的边界；但 `app/build.gradle.kts` 同时把一组固定测试配置写入所有变体，并把当前 AAR 文件存在视为 production 失败条件。vendor readiness 还会把该 AAR 的 `TrustAllX509TrustManager` Lint finding 作为阻断项。

厂商 AAR 是正式销售评估必需且暂时不可替换的外部输入。项目已经保存其版本、SHA-256、Lint、Manifest 和依赖基线，也确认内部 finding 属于厂商实现；新的产品决策是保留并接受该外部风险，不在项目侧修改二进制或中断业务。项目能够控制的是构建配置、调用封装、错误处理、发布脚本和回归证据。

production Release 仍可能被腾讯人脸等独立问题阻断。本设计只改变 QLZ 的责任分类，不能把其他厂商或通用门禁一并放宽。

## Goals / Non-Goals

**Goals:**

- 让 production APK/AAB 必然包含并使用当前批准的 QLZ AAR，同时把缺包、误裁剪或意外替换视为项目失败。
- 通过外部构建输入提供 QLZ 正式配置，消除源码内固定测试 key 和所有变体默认测试模式。
- 保持现有 `QlzSdkClient`、设备 ID、Token、BLE、SDK 页面事件和报告回查的业务语义。
- 将 QLZ 门禁拆成“项目可控条件阻断”与“厂商内部风险报告”，并保持其他 release-required 门禁不变。
- 用 focused tests、构建 fixture、APK/AAB 检查和受控真机旅程验证项目能够负责的范围。

**Non-Goals:**

- 不替换、重新打包、反编译修改或字节码转换 QLZ AAR。
- 不使用网络 Hook、自定义代理、伪造设备 ID、反射或未获厂商确认的内部 API 改变 AAR 行为。
- 不宣称修复厂商内部 TLS、遥测、权限或隐私实现，也不把这些问题从风险记录中删除。
- 不修改 `/V1/Sale/GetCheckToken`、客户/报告接口、Room、路由或销售页面结构。
- 不处理腾讯人脸、Jetifier、legacy protobuf、厂商 Manifest 权限最小化或其他独立整改。

## Decisions

### 1. 当前 AAR 作为强制且不可修改的版本化输入

保留 `app/libs/qlzsdk-1.3.0.2-protobufLiteRelease-ui.aar` 文件依赖及现有厂商 API。项目记录其批准文件名和 SHA-256；production 检查验证文件存在、摘要符合当前批准输入，并通过依赖/产物检查确认 QLZ 运行实现被打包。摘要变化表示“外部输入已变化，需要重新评估”，而不是自动把新文件视为安全或不安全。

不再使用 `knownUnsafeQlzSdkPresent` 这类“文件存在即失败”的判定。替代方案是移除 AAR 或对其重新打包；两者都会违反正式业务依赖和责任边界，因此不采用。

### 2. 正式配置通过 Gradle property 或 CI environment 注入

构建只从同名 Gradle property 或环境变量读取 QLZ SDK key 和模式，源码不再提供真实/测试 key 默认值。推荐 CI 使用环境变量，本机联调使用未纳入版本库的用户级 Gradle property；输入解析统一 trim，并把空值保留为可由门禁识别的“缺配置”。

构建策略如下：

- production：必须显式提供非空正式 key，测试模式固定为 false；任何 true、空值或已知测试配置均失败。
- 显式 Acceptance：允许显式提供测试 key 和测试模式，但继续带 acceptance 标识。
- Debug：允许开发者显式提供测试配置；未提供时项目仍可编译，但真实 QLZ 功能返回既有的可恢复配置错误，避免把凭据写回仓库。

SDK key 最终会作为客户端配置进入安装包，不能被当作真正秘密；设计只保证它不进入版本库、普通日志和提交证据。任何服务端签名 secret 继续不得下发客户端。与扩展服务端配置相比，构建注入不需要新增后端契约，符合“只处理项目当前可控逻辑”的范围；代价是更换 key 需要重新构建发布。

### 3. 保持单一 adapter，并只增加必要的可测试边界

所有厂商类型继续限制在 `:app` 的 QLZ integration/platform 区域，Feature/ViewModel 只通过现有销售设备 gateway/controller 使用能力。`QlzSdkClient` 保留当前调用顺序和同步初始化语义，不新增厂商未确认的 reset、设备 ID注入或传输替换逻辑。

若现有静态厂商 API 阻碍 focused tests，只增加包内可见的窄 facade，将 `setTestMode`、初始化、设备 ID、设备名和打开 Token 页面原样委托给当前公开 API；生产实现不改变参数与顺序，测试使用 fake 验证项目逻辑。直接在 ViewModel 中模拟厂商状态会扩大 vendor 类型和 Activity 生命周期耦合，因此不采用。

### 4. 发布门禁按责任边界拆分

`verify_production_release_config.sh` 继续 fail closed，但输入和错误语义改为：

- production 正式 key 是否存在且不是已登记测试值；
- production 测试模式是否关闭；
- 当前批准 QLZ AAR 是否存在且摘要匹配；
- Acceptance 与 production 是否互斥；
- 腾讯人脸等既有独立阻断是否仍成立。

`verify_vendor_sdk_release_readiness.sh` 不再把当前 QLZ AAR 的内部 Lint finding写入阻断集合；该 finding 保留精确、带原因的 Lint waiver 和长期风险说明。waiver 只匹配当前 QLZ 文件/版本与指定 issue，不扩大到应用源码、其他 AAR 或其他 TrustManager finding。合成 fixture 必须同时证明“当前批准 QLZ finding 非阻断”和“应用源码/未知依赖中的同类新增 finding 仍失败”。

把所有 QLZ TLS 检查直接删除会丢失可见性；让原检查继续失败又会违背已确认的业务决策。因此采用“报告/登记”和“发布阻断”分离。

### 5. 验收只覆盖项目能够证明的结果

自动化分为四层：

1. Gradle/脚本 fixture：production/Acceptance 互斥、配置矩阵、AAR 缺失/摘要变化、已知 finding 非阻断、未知 finding 仍阻断。
2. adapter/gateway tests：初始化顺序、空配置、异常脱敏、Token 单次恢复、取消/关闭/完成映射及协程取消传播。
3. 构建产物：Debug、显式 Acceptance 和可配置的 production dry-run；检查 APK/AAB 中存在 QLZ 实现且没有误用测试模式标记。
4. 受控销售账号与 BLE 设备：授权、连接、进度、完成、取消、断网/异常、Token 过期和报告回查。

测试结果只证明 LongCare 的配置与调用逻辑以及业务可用性，不把普通构建绿色解释为厂商内部安全已修复。

### 6. 新 change 取代旧替换方案但保留证据

`replace-qlz-sdk-and-remove-temporary-config` 不再进入 apply；其中已完成的 AAR/Android 官方资料基线可作为本 change 的输入，未完成的“获取修复包、替换 AAR、阻断旧 AAR”任务不迁移。长期文档在本 change 实施时统一改写，避免一边声明“必须替换”一边依赖当前包发布。

旧 change 暂不删除，防止丢失规划与基线来源；在新 change 实施并验证后，再通过明确的 OpenSpec 整理动作处理其状态。

## Risks / Trade-offs

- **[接受的厂商内部 TLS/遥测风险继续存在]** → 保留原严重度、AAR 版本/hash、厂商责任和复核触发条件；厂商新包、官方说明、监管要求或真实业务影响出现时新建独立 change，不伪称已修复。
- **[正式配置未进入 CI 导致发布失败]** → 提供稳定的 Gradle/environment 输入名称、脱敏诊断和 CI fixture；失败只指出缺项，不打印值。
- **[客户端 SDK key 可从 APK 提取]** → 明确其仅是客户端配置，不把服务端签名 secret 放入客户端；真正授权继续依赖服务端一次性 Token。
- **[hash 校验增加厂商升级成本]** → 摘要变化只要求显式评审并更新批准值，防止不明二进制静默进入正式包。
- **[Lint waiver 意外覆盖项目代码]** → 限定 issue、厂商文件名/版本，并用负向 fixture 验证应用源码和未知依赖仍被阻断。
- **[业务回归被构建绿色掩盖]** → 将受控账号与真实 BLE 旅程列为交付证据，缺设备时明确标为未验证而不伪造结论。
- **[腾讯人脸仍阻断整体 production]** → QLZ 门禁输出按根因独立断言；本 change 不修改腾讯人脸判断或宣称全量 release 已可发布。

## Migration Plan

1. 固定当前 AAR 文件名、SHA-256、依赖和已保存基线；将旧 change 标为不再实施，但保留证据。
2. 增加 QLZ Gradle/environment 配置入口和按构建用途的模式策略，删除源码固定测试配置；先用 Debug/Acceptance fixture 验证。
3. 更新 production config 与 vendor readiness/Lint policy，使项目可控问题 fail closed、当前厂商内部 finding 非阻断，并验证其他门禁未变化。
4. 在不改变厂商调用语义的前提下补齐 adapter/gateway focused tests和错误脱敏测试。
5. 构建并检查 Debug、Acceptance 和具备正式输入的 production 产物，确认 QLZ 实现被打包；再完成受控真机销售旅程。
6. 同步产品、技术栈、系统概览、QLZ 集成、CI 门禁、路线图和风险分类事实，运行全量 OpenSpec/质量验证。

若项目侧变更导致评估业务回归，回滚配置/adapter/门禁改动并保留原 AAR；不得通过移除 AAR、启用 production 测试模式或把 Acceptance 冒充 production 恢复业务。厂商内部风险接受决策保持记录，除非产品负责人明确变更该决策。

## Open Questions

- 正式 QLZ SDK key 的实际值及 CI secret 是否已经就绪；该值只作为实施/发布输入，不写入 OpenSpec 或仓库。
- 受控生产账号与 BLE 设备由谁执行最终真机矩阵；缺少该条件时自动化可完成，但业务验收必须标记为未验证。
