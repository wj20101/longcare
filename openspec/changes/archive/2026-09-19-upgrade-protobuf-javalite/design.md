## Context

动机和范围见 [proposal.md](proposal.md)。调研基线为 `ba150fb7`，上一轮升级已完成，本轮单独处理此前暂缓的 Protobuf，不修改旧计划的历史验收结论。

2026-09-19 只读核查：

- `gradle/libs.versions.toml` 的 `protobufJavalite` 为 4.28.3，由 `:app` 直接依赖。助手不应因本次升级新增 QLZ 或完整 Protobuf 运行库。
- [#125](https://github.com/wj20101/longcare/pull/125) 当前目标为 4.36.1，head `50e820b1`，API 返回 base `bdf15a37`；其旧 CI 通过且 instrumentation 跳过，不能代替本轮最终主分支和真机证据。实施时重新检查 head/base，保留他人后续修改。
- QLZ AAR 为 `qlzsdk-1.3.0.5-protobufLiteRelease-ui.aar`，SHA-256 为 `5a0a5d647ceaf23d8660e4def556b6eb2caa73e3a0e2a0aa204bce69eb77b3ab`。消息根类型为 `RecordDataProto.RecordData`，包含 `AssessedData`、`FingerSample`、`Physiology`，继承 `GeneratedMessageLite`。AAR 另含 `ProtobufGzipKit` 和调用消息 builder 的上传实现。
- 检查到的 `RecordData` 静态初始化只注册默认实例，没有可据以确认精确 protoc 版本的版本校验调用。4.28.3 是现有接入文档的运行库基线，不应仅据此宣称已经证明生成器版本。
- App 现有 R8 规则保留厂商 protobuf 类；现有 QLZ 状态机和销售 mock 测试主要验证回调行为，不等同于执行 AAR 真实编解码。

已核对的官方资料：

- [Maven Central 元数据](https://repo.maven.apache.org/maven2/com/google/protobuf/protobuf-javalite/maven-metadata.xml) 与 [v36.2 发布记录](https://github.com/protocolbuffers/protobuf/releases/tag/v36.2) 确认 Java Lite 目标为 4.36.2，非预览版。
- [跨版本运行库规则](https://protobuf.dev/support/cross-version-runtime-guarantee/)通常允许旧生成代码配合较新运行库，但不能忽略语言实现的例外。
- [v36.2 Java Lite 说明](https://github.com/protocolbuffers/protobuf/blob/v36.2/java/lite.md)明确不保证 API/ABI 稳定，并说明 R8 字段重命名可能破坏消息反射路径。因此采用实测门禁，而非将通用兼容窗口作为免测依据。

## Goals / Non-Goals

**Goals:**

- 在不改变 AAR 和业务行为的前提下验证 4.36.2；将旧、新运行库测试对应到同一套合成样本和明确提交。
- 区分消息兼容、Android/R8 运行、真实上传三层证据，所有必需验收通过后才允许合入。
- 失败可定位并可定向回滚，无需清除账号、数据库或服务数据。

**Non-Goals:**

- 不建立通用序列化框架、版本路由、双运行库或自动降级机制，不引入新的生产 API。
- 不替换厂商 SDK、不反编译后重打包，不修改采样协议、SDK 数据模型、H5 bridge 或结果查询规则。
- 不解决既有厂商生产 blocker，也不以本轮测试宣称全量协议、性能或厂商支持得到保证。

## Decisions

### 1. 单一 Lite 运行库，最小版本变更

将目录目标设为 4.36.2，复用 #125；运行库升级与测试分步提交便于比较，但最终候选包含完整验证。检查 App 的 Debug/Release 与测试运行时解析，并检查助手不受污染；缓存中存在 `protobuf-java` JAR 不代表它已进入 APK，判断以对应配置的依赖图和产物为准。

不使用 `force`、全局 exclusion 或同时装配 Full/Lite 来掩盖冲突。若发现无法解决的 ABI 断裂，停下报告具体成员与调用路径，另议厂商新 AAR；不自行扩大为适配层改造。

### 2. 先在旧版本建立消息基线，再原样运行新版本

测试放在 App 测试源集，使用 AAR 原有类而非复制 `.proto`、生成替身或 mock 掉 protobuf。先核对 `ProtobufGzipKit` 可离线执行的实际方法，调用编解码路径但不触发 SDK 初始化、遥测、网络上传或 BLE。

最小样本矩阵：

- 默认实例、空集合、默认标量，以及包含非 ASCII 文本的合成记录。
- 多条检测数据、五指采样及嵌套生理数据，核对字段值、集合长度和次序。
- builder 构建、序列化、`parseFrom`、SDK Gzip 编解码，旧版生成的样本由新版读取；新版产物由旧版读取并核对语义。
- 未知字段保留与再次解析；截断、损坏和非法输入应按可识别异常失败，不挂起或输出伪成功记录。
- 重复序列化/解析与独立消息实例不串数据；需要覆盖具体运行库调用而非仅比较对象字符串。

合成样本不得含真实身份、位置、MAC、Token 或采样数据。可提交小型测试 fixture 及生成版本说明，不提交临时报告。Protobuf 编码不保证通用规范化，跨版本比较以字段语义和可解析性为主；Gzip 不以压缩字节完全相同为成功条件。发现错误输入行为差异时按官方变化和业务影响判断，不能为让测试变绿而直接删除断言。

### 3. Android 与混淆运行验证不可由 JVM 构建替代

原样消息测试在 API 24 和现代 Android 设备执行，覆盖真实运行库加载与编解码。JVM 测试使用项目现有 JDK 21 / Robolectric 配置，不新增模块访问豁免，除非独立说明并获批。

双应用 Debug/Lint、完整 preflight、warning allowlist、隔离守卫均保留。Release 使用显式 `release.production=false`、`release.acceptance=true` 和合法 Release 签名，关闭 unsigned/debug fallback。先检查 R8 构建和解析产物，再在实际混淆目标包中运行消息专项或受控真实 QLZ 路径；另一个未混淆 test APK 单独加载消息类，不构成混淆目标执行证据。若现有测试变体不能验证该路径，只增加最小测试专用配置，不向正式源码添加验证入口。

### 4. 真实链路作为合入前独立门禁

先确认测试账号、客户、QLZ 设备、手机解锁状态及本次检测/问卷提交授权；没有授权时继续可离线检查并保留真机项未完成。优先使用指定的可复用测试客户，不擅自新增或改动其他客户。

最终候选验证 SDK 初始化、Token 校验、扫描连接、真实采样、上传成功并取得 recordId、自动打开业务接口返回的 H5。H5 提交确认仅刷新页面，左上角返回调用 `window.NativeBridge.closeWebView()` 才关闭；随后原生完成页按当前契约查询 GetCheckResult 并显示实际等级。不得为了验收引入第二套完成信号或网页结果关联。

失败重试、取消和迟到回调由现有 mock/状态机测试补充，不强制用真实设备制造每种故障。离线兼容和真实服务可用性分别记录；网络失败不能直接归因于 Protobuf，也不能把未完成上传标为通过。使用覆盖安装保留数据，结束后恢复约定 Debug 包；不打印敏感请求或完整 H5 URL。

### 5. PR 与长期文档按实测事实收尾

得到实施和远端写入授权后，基于最新主分支更新现有 #125，保留历史和其他人的改动，不盲目 force-push。更新 PR 标题/说明中的目标版本和专项验证结论。候选 head/base 变化后复核受影响测试及最新 CI，不能沿用旧检查。

技术栈记录实际运行库版本；QLZ 文档区分厂商示例依赖、本项目验证的 AAR/运行库组合和仍未验证范围。仅同步当前事实，不重写上一轮计划中“当时暂缓”的历史。生产 fail-closed 不变。

### 6. 已授权的验收阻塞修复

用户在首轮失败后明确允许修复测试运行器并定位、修复扫描停滞。AndroidJUnitRunner 的 `Trace.beginSection` 调用在正式 R8 目标中不存在，失败发生于测试启动而非消息断言；采用最小测试侧修复，为纯 JUnit 消息专项使用独立 Instrumentation 入口。正常 Debug UI 测试仍用 AndroidJUnitRunner，不添加正式 keep 规则、不复制目标运行库；必须验证测试失败会被正确报告。

运行器修复后发现测试直接使用的部分 Protobuf 辅助 API 也被 R8 正常删除。用户明确同意完整 13 项保持在 JVM/Debug；Release 另测实际 SDK 消息/Gzip 调用链、嵌套字段及未知字段完整性，配合真实上传作为门禁。不同测试范围明确标注，不将测试专用 API 缺失作为正式兼容失败，也不把缩减后的混淆专项冒称完整 13 项通过。

扫描侧先用真实 AAR 验证回调生命周期。已知 AAR 将匿名内部回调仅存为弱引用，这是待验证线索而非已定根因。若证明确为回调被回收，仅在扫描对象生命周期内保持所需强引用，并在关闭时释放；不另造蓝牙协议、反射兼容层、轮询或无界重试。补充扫描结束、重试、取消、关闭后迟到回调与资源释放回归，再跑混淆包真实设备流程。硬件未发现与回调停滞分别记录，不能互相替代。

## Risks / Trade-offs

- Lite ABI 无稳定保证、精确生成器版本未知 → 测真实 AAR 消息和 SDK Gzip，再做 Android/R8 与真实上传，不用 Full Java 兼容声明兜底。
- 默认实例测试遗漏业务字段 → 包含采样、嵌套生理数据、未知字段及错误输入，双向验证字段语义。
- 混淆包与测试 APK 各带消息类导致假覆盖 → 明确记录目标 APK、变体和实际执行路径，不以测试 APK 自身成功替代。
- 服务或硬件不可用 → 将离线通过与在线未验收分开，不合入，不降低完成标准。
- 真实业务测试产生外部数据 → 本轮另行确认对象与提交授权，最小化样本且不清理用户数据。
- 单版本定向回滚可能影响本轮以后工作 → 按具体差异回滚并重测，不重置整个主分支、不降级其他依赖。

## Migration Plan

1. 计划评审后取得实施授权，刷新主分支与 #125 状态，确认 AAR 哈希和官方目标未变。
2. 先在 4.28.3 建立专项基线，再切换 4.36.2 执行同套测试及双向 fixture 检查。
3. 依次完成依赖图、完整本地门禁、API 24/现代设备消息专项、合法混淆验收及授权真实流程。
4. 同步文档并在获授权后推送 #125；当前候选专项和 CI 均通过后按授权合入，核对合入后的主分支 CI。
5. 合入前失败则保留主分支 4.28.3，不放宽门禁；合入后发现回归则经授权定向回滚版本及关联实现差异，保留有价值的回归测试并重新验证，不清数据。
