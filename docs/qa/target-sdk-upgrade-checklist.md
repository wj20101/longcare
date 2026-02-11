# Target SDK 升级校验清单

## 目标
在每次 `appTargetSdkVersion` 升级后，确保本地与 CI 使用一致规则完成构建与 smoke instrumentation 验证。

## 必跑项（本地）
1. 质量门
   - `bash scripts/quality/verify_target_sdk_upgrade.sh constants.gradle.kts .github/workflows/android-ci.yml`
   - `bash scripts/quality/verify_release_exported_components.sh`
   - `bash scripts/quality/verify_exact_alarm_permission_config.sh app/src/main/AndroidManifest.xml`
2. 基础构建
   - `./gradlew --no-daemon :app:lintDebug :app:testDebugUnitTest`
3. 模拟器适配 smoke（自动匹配 targetSdk 对应 AVD）
   - `bash scripts/quality/run_target_sdk_local_smoke.sh`

## 本地脚本可选参数
- `TARGET_SDK_AVD`
  - 指定 AVD 名称，例如：`TARGET_SDK_AVD=Medium_Phone_API_36.1`
- `SMOKE_TEST_CLASSES`
  - 覆盖默认 smoke 类列表（逗号分隔）
- `ADB_BIN` / `EMULATOR_BIN`
  - 指定 `adb` 与 `emulator` 可执行文件路径
- `TARGET_SDK_SMOKE_BOOT_TIMEOUT_SECS`
  - 模拟器启动超时，默认 `240`

示例：

```bash
TARGET_SDK_AVD=Medium_Phone_API_36.1 \
SMOKE_TEST_CLASSES="com.ytone.longcare.smoke.MainActivitySmokeTest,com.ytone.longcare.features.service.ServiceTimeNotificationIntegrationTest" \
bash scripts/quality/run_target_sdk_local_smoke.sh
```

## CI 对齐项
- `android-ci.yml` 必须包含：
  - `Enforce target SDK upgrade gate`
  - `Enforce exact alarm permission config`
  - instrumentation job 使用 targetSdk 对应 API（当前通过 `steps.target_sdk.outputs.value` 动态解析）
- 若 targetSdk 上调，`verify_target_sdk_upgrade.sh` 不通过时禁止合入。

## 通过标准
- `verify_target_sdk_upgrade.sh` 返回 `Target SDK gate passed`
- `verify_release_exported_components.sh` 返回 `Release exported component check passed`
- `verify_exact_alarm_permission_config.sh` 返回 `Exact alarm permission config check passed`
- `lintDebug`、`testDebugUnitTest` 通过
- `run_target_sdk_local_smoke.sh` 返回 `Local target SDK smoke verification passed.`
