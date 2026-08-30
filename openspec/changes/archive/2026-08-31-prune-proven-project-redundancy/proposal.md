## Why

多轮架构迁移后，工程中已经积累了会被 R8 完整移除的占位类型、无绑定的 DI 模块、未参与真实导航的伪入口、不可达页面、重复测试和与源码不匹配的模块依赖。这些残留不会承载业务价值，却持续扩大搜索、构建、审查和回归验证范围，因此需要以可复核证据为前提进行一次边界明确的清理。

## What Changes

- 建立“可直接删除、验证后精简、暂缓保留”三类清理清单；每项删除都要求零生产引用、构建或静态证据以及删除后的 focused 验证，禁止仅凭文件名、R8 输出或当前没有调用就推断冗余。
- 删除已确认的四个 Core 占位声明、四个无绑定 Hilt 模块、三个不代表真实 Navigation 目的地的 `FeatureEntry` 及其自证测试/应用注册表，并让长期文档只描述实际 typed route 与模块边界。
- 删除当前没有任何入口的 `SelectDeviceRoute`、选设备 UI/动作/专属资源，同时用导航契约固定“开始服务后直接进入 NFC 身份核验”的现有行为；删除被实际 `AppUpdateDialog` 替代的旧 `UpdateDialog`。
- 合并两套重复的 `UriJsonAdapterTest`，保留用例并集；删除不声明组件、权限或 metadata 的空 Manifest，但保留注册定位服务的 `feature:location` Manifest。
- 按源码直接使用情况精简 `feature:location`、`feature:photoupload`、`feature:servicecountdown` 和 `feature:identification` 的构建能力与依赖；移除无 Compose 源码模块的 Compose 配置，并以精确的直接依赖替代无使用证据的 UI、Coil、Bugly、Core KTX 或 Android dispatcher 依赖。
- 审核未被 workflow、门禁注册表、文档或其他脚本引用的质量脚本；仅在确认没有人工维护入口后退役被现有质量快照/CI 健康监控替代的收集器，并把仍有价值的受影响模块自测接入既有治理测试。
- 扩展现有架构/构建治理守卫，阻止已删除指纹、空壳模块和无依据构建能力回流；同步收紧 frozen legacy allowlist、路由图和长期架构文档。
- 不合并现有 Gradle 模块，不拆分销售大文件，不迁移 Navigation 3，不修改数据库、用户存储、WebView、targetSdk、厂商 AAR/consumer rules、R8 当前变更、Startup/Baseline Profile 当前变更或 production Release 的 fail-closed 条件。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `android-build-baseline`：增加模块构建能力/直接依赖最小化、可证明冗余的删除证据和冗余指纹防回归要求。
- `entry-navigation-contracts`：明确开始服务直接进入 NFC 身份核验且不经过设备选择中间页的现有导航契约。

## Impact

- 主要影响 Core/Feature 中的占位与空 DI 文件、`:app` 根导航及 legacy feature allowlist、四个 Feature 的 Gradle 配置、重复单元测试、空 Manifest、少量质量脚本和架构/质量文档。
- 用户可见流程保持不变：开始服务仍直接进入 NFC 核验；登录、Home、订单、定位、拍照上传、倒计时、销售流程、类型安全 route、`SavedStateHandle` key、网络与持久化契约均不得改变。
- 依赖精简可能暴露先前通过传递依赖偶然获得的 API，因此每个模块必须独立编译、测试和 lint；一旦发现真实运行时用途，该候选项回到保留清单，不通过增加宽泛依赖或放宽门禁制造通过结果。
- 当前工作区包含尚未提交的 R8 与启动性能变更。实施前必须记录基线并声明重叠文件所有权；本变更不得改写它们的代码、生成 Profile、规则、任务状态或尚待真机验证的结论。
- 厂商 AAR、Jetifier、反射/JNI 与远端媒体格式属于外部或运行时边界；本轮不以“当前搜索不到调用”为由删除相关适配、Coil 可选解码能力或一次性用户存储切换逻辑。
