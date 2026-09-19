## Why

Protobuf #125 在上一轮依赖升级中因 QLZ Lite AAR 的兼容性尚未验证而暂缓。现在独立验证并升级运行库，消除长期停留在厂商示例版本的维护缺口，同时避免将编译成功误判为检测数据和上传兼容。

## What Changes

- 目标将 `com.google.protobuf:protobuf-javalite` 从 4.28.3 升级到已核对的稳定版 4.36.2；沿用现有 #125，将其原目标 4.36.1 更新为本次目标，不创建重复 PR。
- 使用现有 QLZ 1.3.0.5 AAR 的真实消息类与 Gzip 处理入口，对比旧、新运行库的序列化、解析、嵌套采样、生理数据、未知字段及错误输入行为；测试仅使用合成数据。
- 验证最终依赖解析、双应用单测/Debug/Lint、合法签名的显式 acceptance 混淆产物及 API 24/现代设备上的实际消息处理。
- 合入前完成真实 QLZ 初始化、BLE 检测、上传、自动打开 H5、返回和原生结果查询验收；真实业务数据提交须在执行时另行确认测试对象与授权。
- 更新技术栈和 QLZ 接入文档，区分厂商示例基线与本项目实测运行库；保留可追溯的测试和 PR 证据。

## Capabilities

### New Capabilities

无。本次只更新依赖实现与兼容性验证，不新增业务能力。

### Modified Capabilities

无。检测、上传、H5 关闭、结果查询、持久化及双 APK 隔离契约均保持不变；本 change 设置 `skip_specs: true`，不为纯依赖升级补造行为规格。

## Impact

- 预期修改版本目录、`app/build.gradle.kts` 中版本说明、App 测试源集及 `docs/architecture/tech-stack.md`、`docs/integrations/qlz-sdk.md`；仅在确有需要时修改测试专用构建配置。
- 不替换或重打包 QLZ AAR，不重新生成厂商消息类，不引入完整 `protobuf-java`、双运行库、运行时切换或兼容适配框架；不改变 AGP/Gradle/JDK/SDK、数据库或接口。
- Java Lite 官方不保证 API/ABI 稳定，厂商生成器精确版本尚缺少可验证元数据。测试通过只证明所测组合和路径，不代表厂商背书；失败时保留 4.28.3 并报告具体障碍，不放宽断言或安全门禁。
- QLZ 固定测试配置/弱 TLS、腾讯人脸 16 KB 对齐及 consumer rules 的生产阻塞仍在，不因 Protobuf 验收解除。
- 用户已确认本计划并授权本地实施与验证，后续明确允许更新/推送 #125，并在本轮专项、真实流程与最新 CI 全部通过后合入；真实业务提交仍在执行前单独确认对象与授权。
