# 重构发布前回归清单

## 1. 构建与质量门禁

- [ ] `./gradlew :app:lintDebug :app:testDebugUnitTest --no-daemon`
- [ ] `./gradlew :app:assembleDebug :app:bundleDebug --no-daemon`
- [ ] `./gradlew :app:bundleRelease --no-daemon`
- [ ] `bash scripts/quality/verify_gradle_stability.sh`
- [ ] `bash scripts/quality/verify_architecture_boundaries.sh .`
- [ ] `bash scripts/quality/verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare`

## 2. 模块化与依赖边界

- [ ] `feature/*` 不直接依赖 `data/*Impl`
- [ ] `domain` 层无 `import android.*`
- [ ] `core/*` 无 `import com.ytone.longcare.feature.*`
- [ ] `:app` 仅承载壳层组装，不引入新业务实现

## 3. 关键链路功能回归

- [ ] 登录链路（登录成功/失败态）
- [ ] 首页拉取与展示（空态/异常态）
- [ ] 身份识别链路（建档、拍照、上传、校验）
- [ ] 服务倒计时与通知链路
- [ ] 手动人脸采集弹窗与确认流程

## 4. 性能与产物

- [ ] 更新 `docs/refactor/baseline-metrics.md` 最新基线
- [ ] 更新 `docs/refactor/build-performance-comparison.md` 对比结论
- [ ] 检查 APK 体积变化在可接受范围内
- [ ] 检查 Dex 文件数量变化在可接受范围内

## 5. CI 与发布

- [ ] `Android CI` 按 affected 结果执行成功
- [ ] `Baseline Profile` workflow 可正常生成与上传产物
- [ ] `Android Release` workflow 可完成签名构建与产物归档
- [ ] 回归结果与偏差已写入主计划文档执行日志/偏差说明
