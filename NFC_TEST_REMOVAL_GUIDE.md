# NFC测试功能删除指南

## 概述
该文档描述了如何完全删除NFC测试功能，确保不影响正常的业务逻辑。

## 删除步骤

### 1. 禁用测试功能
修改 `/debug/NfcTestConfig.kt`:
```kotlin
const val ENABLE_NFC_TEST = false  // 设为false
```

### 2. 删除测试相关文件
删除以下文件：
- `/debug/NfcTestConfig.kt`
- `/features/maindashboard/utils/NfcTestHelper.kt`
- 本文档：`NFC_TEST_REMOVAL_GUIDE.md`

### 3. 清理MainDashboardScreen
在 `/features/maindashboard/ui/MainDashboardScreen.kt` 中删除：

```kotlin
// 删除import
import com.ytone.longcare.debug.NfcTestConfig

// 删除整个代码块（约20行）
// 【测试功能】获取NfcTestHelper实例 - 后期可删除
val nfcTestHelper = if (NfcTestConfig.ENABLE_NFC_TEST) {
    entryPoint.nfcTestHelper()
} else null

// 删除整个代码块（约20行）
// 【测试功能】NFC测试功能管理 - 后期可删除整个代码块
if (NfcTestConfig.ENABLE_NFC_TEST && nfcTestHelper != null) {
    // ... 整个LaunchedEffect和DisposableEffect块
}

// 删除整个代码块（约4行）
// 【测试功能】NFC标签检测弹窗 - 后期可删除整行
if (NfcTestConfig.ENABLE_NFC_TEST && nfcTestHelper != null) {
    nfcTestHelper.NfcTagDialog()
}
```

### 4. 清理AppModule
在 `/di/AppModule.kt` 中删除：

```kotlin
// 删除import
import com.ytone.longcare.features.maindashboard.utils.NfcTestHelper
import com.ytone.longcare.debug.NfcTestConfig

// 删除整个方法（约12行）
/**
 * 【测试功能】提供 NfcTestHelper 的单例 - 后期可删除整个方法
 */
@Provides
@Singleton
fun provideNfcTestHelper(...): NfcTestHelper { ... }
```

### 5. 清理MainActivity（可选）
在 `/MainActivity.kt` 中删除：

```kotlin
// 删除import（如果不再需要）
import com.ytone.longcare.debug.NfcTestConfig

// 可选：删除注释中的【测试功能】和【业务功能】标记
```

### 6. 清理Entry Point
在对应的EntryPoint中删除NfcTestHelper相关方法。

## 验证
1. 编译项目，确保没有编译错误
2. 运行应用，确保NFC业务功能正常工作
3. 确认测试弹窗不再出现

## 核心业务保护
以下组件不受测试功能影响，删除测试功能后仍正常工作：
- `NfcManager.kt` - 核心NFC管理器
- `NfcUtils.kt` - NFC工具类
- `NfcWorkflowScreen.kt` - 业务NFC流程页面
- `AppEventBus.kt` - 事件总线

## 注意事项
- 所有测试相关代码都有明确的【测试功能】标记
- MainActivity中的NFC Intent处理是业务功能，不要删除
- NfcManager的功能是业务核心，不要删除