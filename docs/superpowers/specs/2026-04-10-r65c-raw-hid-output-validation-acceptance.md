# R65C 原始 HID 输出验证验收清单

## 目的

本文档用于验收以宿主层驱动的 `R65C 原始 HID 输出验证` 重构。

这份清单主要验证三件事：

- 宿主层原始按键捕获已经成为权威数据源
- 文本框只保留为对照层
- 现有 `R65C HID 键盘口测试` 面板仍然可以作为 smoke test 使用

关联文档：

- 入口索引：[`../README.md`](../README.md)
- 设计文档：[`2026-04-09-r65c-raw-hid-output-validation-design.md`](2026-04-09-r65c-raw-hid-output-validation-design.md)
- 计划文档：[`../plans/2026-04-09-r65c-raw-hid-output-validation.md`](../plans/2026-04-09-r65c-raw-hid-output-validation.md)

## 文档导航

推荐阅读顺序：

1. 先看设计文档，理解目标、边界和方案取舍。
2. 再看计划文档，按任务执行实现。
3. 最后使用本文档完成验收。

## 范围

本次验收重点关注以下代码路径：

- [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawCaptureHost.kt)
- [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/vm/R65CHidRawValidationViewModel.kt)
- [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/R65CHidRawValidationPanel.kt)
- [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt)
- [`app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt`](../../../app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenContent.kt)

## 验收清单

### 1. 自动化验证

- [ ] 运行 `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationContractsTest"`
- [ ] 运行 `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.ui.R65CHidRawCaptureHostTest"`
- [ ] 运行 `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawCandidateMapperTest"`
- [ ] 运行 `./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationViewModelTest"`
- [ ] 运行 `./gradlew :app:compileDebugAndroidTestKotlin`
- [ ] 运行 `./gradlew :app:compileDebugKotlin`
- [ ] 运行 `./gradlew :app:lintDebug`
- [ ] 确认以上命令全部通过，没有新增编译错误、lint 错误或测试失败

### 2. 页面结构

- [ ] 打开 NFC 测试页面
- [ ] 确认 `R65C HID 键盘口测试` 仍然可见
- [ ] 确认 `R65C 原始 HID 输出验证` 与前者同时出现在同一页面
- [ ] 确认原始校验面板包含以下按钮
- [ ] `开始监听`
- [ ] `停止监听`
- [ ] `重新聚焦`
- [ ] `清空会话`
- [ ] 确认面板仍然展示以下信息
- [ ] 状态
- [ ] 文本框结果
- [ ] 原始按键拼装结果
- [ ] 结束原因
- [ ] 完成时间
- [ ] 候选值列表

### 3. 监听生命周期

- [ ] 初始状态为 `未开始监听`
- [ ] 点击 `开始监听` 后，确认状态切换为 `等待刷卡`
- [ ] 输入有效 HID 数据后，确认状态切换为 `正在接收按键`
- [ ] 在一轮会话末尾按 Enter，确认会话完成
- [ ] 确认结束原因为 `Enter结束`
- [ ] 不按 Enter 完成一轮输入，确认在空闲超时后自动结束
- [ ] 确认结束原因为 `超时结束`
- [ ] 点击 `停止监听` 后，确认状态回到 `未开始监听`
- [ ] 停止监听后再次输入，确认不会继续追加到当前 raw 会话

### 4. 结果正确性

- [ ] 确认文本框仍会记录可见的文本输入结果
- [ ] 确认宿主层捕获事件会独立拼装出 raw 输出字符串
- [ ] 确认会话完成后，两组结果都能继续显示
- [ ] 确认当文本框输出与宿主层拼装输出不一致时，界面上可以直接观察到差异
- [ ] 确认候选值列表基于完成后的会话结果生成
- [ ] 确认最终拼装结果中不包含 Enter 本身

### 5. 过滤与边界行为

- [ ] 确认 `Back` 不会被 raw capture 采集
- [ ] 确认 `Volume Up` 不会被 raw capture 采集
- [ ] 确认 `Volume Down` 不会被 raw capture 采集
- [ ] 确认 `Home` 和应用切换键在适用场景下不会被采集
- [ ] 确认非 `ACTION_DOWN` 事件会被忽略
- [ ] 确认清空会话后，上一轮结果展示恢复为 `-`
- [ ] 确认监听中执行清空后，raw capture 状态回到 `等待刷卡`
- [ ] 确认未监听时执行清空后，raw capture 状态回到 `未开始监听`
- [ ] 确认进入和离开该页面时不会导致页面崩溃

### 6. 行为结论

- [ ] 宿主层按键捕获已经成为 raw HID 校验的权威来源
- [ ] 原始校验面板中的文本框不再是采集权威
- [ ] smoke-test 面板仍然保留且未受影响
- [ ] 当前实现行为与已批准的设计和执行计划一致

## 建议记录项

可以在本节记录设备相关的实测结果，方便后续对比和追踪。

### 环境信息

- 设备型号：
- Android 版本：
- R65C 输出模式：
- 键盘 / IME：
- 构建变体：

### 观测结果

- 期望 UID：
- 文本框结果：
- 宿主层拼装结果：
- 最可信候选值：
- 结束原因：
- 备注：

## 退出标准

只有在全部自动化检查通过，且手动验证确认“宿主层 raw capture，而不是文本输入”已经成为诊断真值来源时，本次重构才算验收通过。

## 上游文档

- 入口索引：[`../README.md`](../README.md)
- 设计文档：[`2026-04-09-r65c-raw-hid-output-validation-design.md`](2026-04-09-r65c-raw-hid-output-validation-design.md)
- 计划文档：[`../plans/2026-04-09-r65c-raw-hid-output-validation.md`](../plans/2026-04-09-r65c-raw-hid-output-validation.md)
