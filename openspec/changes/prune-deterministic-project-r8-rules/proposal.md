## Why

当前 Release R8 Configuration Analyzer 的 Optimization、Shrinking、Obfuscation 分数分别为 65.7%、72.7%、65.8%，项目规则中仍存在零命中项、被覆盖的重复项，以及一条意外保留 26,601 个类名的 Kotlinx Serialization 手工规则。这些规则不能提升兼容性，却扩大包体和运行时优化受限范围，也增加后续排查反射、JNI 与厂商 SDK 问题的成本，因此应按路线图进入独立、可回滚的确定性清理批次。

## What Changes

- 删除已由 `proguard-android-optimize.txt`、AndroidX Annotation 或 Kotlinx Serialization consumer rules 提供的项目副本，包括通用 JNI、枚举、`@Keep` 与宽泛 Serialization 规则；源码中的 `@Keep` 注解保持不变。
- 删除当前 Release 全程序分析中匹配 0 items 的项目规则，以及明确被更宽项目规则覆盖的 `com.autonavi.aps.amapapi.model.**`、`com.comm.*`、`com.falth.data.*` 和 `**$$serializer` member 重复规则。
- 为项目自有 R8 配置增加确定性守卫，阻止已确认的冗余规则、全局优化禁用项和无来源的宽泛项目规则重新进入，并保留规则来源与例外边界的可维护说明。
- 用同一输入下的 R8 Analyzer 前后对照、minified acceptance APK/AAB、mapping/资源收缩/Baseline Profile/签名与 Manifest 校验，以及登录、导航恢复、定位、身份核验、照片上传、倒计时、视频呼叫和应用更新 smoke 验证清理结果。
- 不修改或过滤 COS、高德、Bugly、QLZ、腾讯人脸等厂商 AAR/consumer rules，不收窄仍有实际命中的厂商 package-wide 规则，不解除 production Release fail-closed 条件，也不处理已延期的 ARM64 真机性能收益对照。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `android-build-baseline`：增加项目 R8 配置最小化、冗余规则防回归和 minified Release 行为保持要求。

## Impact

- 主要影响 `app/proguard-rules.pro`、R8 配置质量守卫及其测试、质量门禁注册和相关架构/CI 长期文档。
- 不改变用户可见流程、Navigation 2 route contract、网络/数据契约、数据库、权限、targetSdk、依赖版本或模块边界。
- 清理会改变 Release 混淆、优化与收缩结果，因此必须以新的 mapping 和当次 APK/AAB 为验收对象；旧 mapping 不得与新产物混用。
- 厂商反射/JNI 行为仍是主要外部风险。任何厂商流程回归都按单条规则回滚，不通过新增整包 `-keep`、修改 AAR 或放宽发布守卫掩盖问题。
