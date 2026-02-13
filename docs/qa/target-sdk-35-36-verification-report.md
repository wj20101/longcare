# targetSdk 35/36 适配核查报告

## 核查时间
- 2026-02-13

## 核查范围
- targetSdk 36 的配置与门禁
- API 36 设备侧 smoke
- API 35 设备侧 smoke（回归验证）

## 结果结论
- 35、36 适配已完成，当前门禁与本地验证均通过。

## 证据明细
1. 配置检查
   - `constants.gradle.kts`：`appTargetSdkVersion=36`，`appCompileSdkVersion=36`
   - `app/build.gradle.kts`：`targetSdk/compileSdk` 由项目常量统一注入

2. 质量门检查（全部通过）
   - `bash scripts/quality/verify_target_sdk_upgrade.sh constants.gradle.kts .github/workflows/android-ci.yml`
   - `bash scripts/quality/verify_exact_alarm_permission_config.sh app/src/main/AndroidManifest.xml`
   - `bash scripts/quality/verify_release_exported_components.sh`

3. 基础构建检查（通过）
   - `./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest`
   - 输出：`BUILD SUCCESSFUL`

4. API 36 设备侧 smoke（通过）
   - 命令：`bash scripts/quality/run_target_sdk_local_smoke.sh`
   - 结果：`Local target SDK smoke verification passed.`
   - 用例：
     - `com.ytone.longcare.smoke.MainActivitySmokeTest`（1/1）
     - `com.ytone.longcare.features.service.ServiceTimeNotificationIntegrationTest`（3/3）

5. API 35 设备侧 smoke（通过）
   - AVD：`Medium_Phone_API_35`
   - 命令：
     - `TARGET_SDK_AVD=Medium_Phone_API_35 bash scripts/quality/run_target_sdk_local_smoke.sh /tmp/target_sdk_35.constants.gradle.kts`
   - 结果：`Local target SDK smoke verification passed.`
   - 用例：
     - `com.ytone.longcare.smoke.MainActivitySmokeTest`（1/1）
     - `com.ytone.longcare.features.service.ServiceTimeNotificationIntegrationTest`（3/3）

## 风险与后续
- 当前未发现 targetSdk 35/36 适配阻塞项。
- 后续若上调 targetSdk，继续沿用 `docs/qa/target-sdk-upgrade-checklist.md` 的同一流程。
