# WbCloudFaceLiveSdk 本地 AAR 替换迁移方案

## 1. 目标
- 从 `app/libs/*.aar` 迁移到可版本化、可审计的依赖来源（优先 Maven/私仓）。
- 消除对腾讯 SDK 类型在 UI/ViewModel 层的直接耦合，降低替换成本。
- 最终移除 `lint.xml` 中与该 SDK 相关的临时忽略项（如 `Aligned16KB`、`GlobalOptionInConsumerRules`、`TrustAllX509TrustManager`）。

## 2. 当前现状（已盘点）
- 本地 AAR 依赖：
  - `app/build.gradle.kts` -> `libs/WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc.aar`
  - `app/build.gradle.kts` -> `libs/WbCloudNormal-v5.1.10-4e3e198.aar`
- SDK 强耦合入口：
  - `app/src/main/kotlin/com/ytone/longcare/common/utils/FaceVerificationManager.kt`
- 业务使用层：
  - `app/src/main/kotlin/com/ytone/longcare/features/shared/vm/FaceVerificationViewModel.kt`
  - `app/src/main/kotlin/com/ytone/longcare/features/identification/vm/IdentificationViewModel.kt`
  - `app/src/main/kotlin/com/ytone/longcare/features/shared/FaceVerificationWithAutoSignScreen.kt`
- 规则与 native 约束：
  - `app/txkyc-face-consumer-proguard-rules.pro`
  - `app/build.gradle.kts` `packaging.jniLibs.keepDebugSymbols`

## 3. 已完成的迁移前置（本次已落地）
- 新增 SDK 无关领域模型：
  - `app/src/main/kotlin/com/ytone/longcare/domain/faceauth/model/FaceVerificationModels.kt`
  - 提供 `FaceVerifyResult`、`FaceVerifyError`、`FACE_AUTH_API_VERSION`。
- `FaceVerificationManager` 对外回调改为领域模型，SDK 类型收敛在 manager 内部。
- 两个 ViewModel 和共享页面移除 `WbFace*` 类型依赖。
- `TencentFaceApiService`、`GetFaceIdRequest` 不再依赖 `FaceVerificationManager` 常量。

## 4. 分阶段迁移执行

## 阶段 A：依赖源替换（AAR -> Maven/私仓）
1. 供应商确认目标版本（需明确修复项：16KB 对齐、consumer rules、TLS 安全实现）。
2. 在 `gradle/libs.versions.toml` 增加腾讯人脸 SDK 坐标（若官方无公网仓库，则上传到公司私仓）。
3. 使用已落地的构建开关切换依赖来源（无需改业务代码）：
   - 默认：`TX_FACE_SDK_SOURCE=local`（使用 `app/libs/*.aar`）
   - Maven：`TX_FACE_SDK_SOURCE=maven`，并提供：
     - `TX_FACE_LIVE_COORD=<group:artifact:version>`
     - `TX_FACE_NORMAL_COORD=<group:artifact:version>`
4. 在 CI 或本地 `gradle.properties` 配置上述变量，先在预检分支跑通编译与 lint。
5. 稳定后删除 `app/libs` 中旧 AAR（在分支中执行，确保可回滚）。

## 阶段 B：规则与 native 收敛
1. 按新 SDK 官方说明更新 `app/txkyc-face-consumer-proguard-rules.pro`。
2. 核查 `packaging.jniLibs.keepDebugSymbols`，仅保留新 SDK 实际存在的 `.so`。
3. 校验 APK/AAB 中 native 库 ABI 与页面对齐信息，确认 16KB 对齐符合目标机型要求。

## 阶段 C：质量门与告警回收
1. 在本地与 CI 同时执行：
   - `./gradlew :app:compileDebugKotlin`
   - `./gradlew :app:lintDebug`
   - `./gradlew :app:testDebugUnitTest`
2. 当上述任务通过后，移除 `app/lint.xml` 中以下忽略项：
   - `Aligned16KB`
   - `GlobalOptionInConsumerRules`
   - `TrustAllX509TrustManager`
3. 观察 release pipeline（`android-release.yml`）是否稳定通过。

## 阶段 D：灰度与回滚
1. 先灰度到小流量用户，重点观察：初始化失败率、取消率、验证成功率、崩溃率。
2. 保留一键回滚策略：
   - 依赖回退到旧坐标/旧分支。
   - 不改接口协议，确保服务端无感回滚。

## 5. 验收标准
- UI/ViewModel 不再直接依赖腾讯 SDK 类型（当前已完成）。
- release 与 debug 构建一致通过，无新增混淆/native 崩溃。
- `app/lint.xml` 中三项腾讯相关忽略已删除，lint 仍通过。
- 人脸验证主流程（服务人员、老人、首次录入）全链路可用。

## 6. 风险与对策
- 风险：供应商新版本仍带同类 lint 告警。
  - 对策：在引入前先对目标 AAR/Maven 版本做预检分支验证，未达标不切主线。
- 风险：新版本 native 库变更导致 ABI 或运行时问题。
  - 对策：补充真机矩阵验证（至少 arm64 主流机型 + 低端机）。
- 风险：回调字段变动影响业务提示文案。
  - 对策：`FaceVerifyResult/FaceVerifyError` 作为统一适配层，屏蔽上层改动。
