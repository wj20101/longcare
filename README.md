# long care

## 本地常用命令

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :app:testDebugUnitTest
./gradlew :app:generateBaselineProfile
```

## CI/CD（GitHub Actions）

- `Android CI`：PR/Push 自动执行 `lint + unit test + assembleDebug + bundleDebug + baselineprofile:assemble + bundleRelease`，并上传 debug/release AAB、APK、baselineprofile APK 与报告。
- `Baseline Profile`：在模拟器上生成 Baseline Profile，执行产物存在性校验并上传 baseline 相关文件。
- `Android Release`：Tag（`v*`）或手动触发，执行质量校验并产出 release APK/AAB，同时生成 SHA256 校验文件并自动创建 GitHub Release。

## Gradle / AGP 兼容状态

- `compileSdk/targetSdk = 36`
- `AGP = 9.0.0`
- 已启用 AGP 9 默认链路（Built-in Kotlin + New DSL），不再依赖 `android.builtInKotlin` / `android.newDsl` 兼容参数。
- 已移除 Wire Gradle 插件，`User` 会话模型改为本地 JSON 编解码实现。

## Release 签名（GitHub）

- `Android Release` workflow 支持自动读取以下 Secrets：
- `ANDROID_KEYSTORE_BASE64`：keystore 的 base64 字符串
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`
- 若 secrets 未配置，构建会自动回退为 debug 签名，保证 CI 打包流程可用。

## 更新 Gradle Wrapper

```bash
./gradlew wrapper --gradle-version latest --distribution-type all
```
