# API变更总结文档

## 概述

本次更新包含两个主要的API变更：
1. 新增 `/V1/System/Start` 接口 - 获取用户协议和隐私政策URL
2. 更新 `/V1/System/Config` 接口 - 新增 `thirdKeyStr` 字段

## 1. 新增启动配置接口

### 接口信息
- **路径**: `/V1/System/Start`
- **方法**: GET
- **用途**: 获取用户协议和隐私政策的URL链接

### 响应模型
```kotlin
data class StartConfigResultModel(
    val userXieYiUrl: String = "",      // 用户协议地址
    val yinSiXieYiUrl: String = ""      // 隐私协议地址
)
```

### 实现文件
- ✅ `api/response/StartConfigResultModel.kt` - 响应模型
- ✅ `api/LongCareApiService.kt` - API接口定义
- ✅ `domain/login/LoginRepository.kt` - Repository接口
- ✅ `data/repository/LoginRepositoryImpl.kt` - Repository实现
- ✅ `features/login/vm/LoginViewModel.kt` - ViewModel支持
- ✅ `features/login/ui/LoginScreen.kt` - UI集成
- ✅ `features/webview/ui/WebViewScreen.kt` - WebView页面
- ✅ `navigation/NavigationRoutes.kt` - 导航路由
- ✅ `navigation/AppNavigation.kt` - 导航配置

### Mock数据
- ✅ `app/src/debug/assets/mock/start_config.json`
- ✅ `app/src/debug/kotlin/com/ytone/longcare/network/interceptor/MockInterceptor.kt`

### 功能说明
1. 应用启动时，LoginViewModel会自动调用 `getStartConfig()` 获取配置
2. 用户点击登录页面底部的"用户协议"或"隐私政策"链接时：
   - 如果URL不为空，打开WebView显示网页内容
   - 如果URL为空，显示Toast提示
3. WebView页面支持：
   - 标题栏显示
   - 返回按钮
   - 加载进度指示
   - JavaScript支持

## 2. 系统配置接口更新

### 接口信息
- **路径**: `/V1/System/Config`
- **方法**: GET
- **变更**: 新增 `thirdKeyStr` 字段

### 更新的响应模型
```kotlin
data class SystemConfigModel(
    val companyName: String = "",
    val maxImgNum: Int = 0,
    val syLogoImg: String = "",
    val selectServiceType: Int = 0,
    val thirdKeyStr: String = ""        // 新增：加密的第三方密钥字符串
)
```

### 第三方密钥模型
```kotlin
data class ThirdKeyReturnModel(
    val cosSecretId: String = "",       // 腾讯云COS SecretId
    val cosSecretKey: String = "",      // 腾讯云COS SecretKey
    val faceSecretId: String = "",      // 腾讯云人脸识别SecretId
    val faceSecretKey: String = "",     // 腾讯云人脸识别SecretKey
    val amapKey: String = ""            // 高德地图Key
)
```

### 实现文件
- ✅ `api/response/SystemConfigModel.kt` - 更新响应模型
- ✅ `api/response/ThirdKeyReturnModel.kt` - 第三方密钥模型
- ✅ `common/utils/ThirdKeyDecryptUtils.kt` - 解密工具类
- ✅ `network/interceptor/AesKeyManager.kt` - AES密钥管理器（新增）
- ✅ `network/interceptor/RequestInterceptor.kt` - 请求拦截器（已更新）
- ✅ `network/interceptor/ResponseDecryptInterceptor.kt` - 响应解密拦截器（新增）
- ✅ `di/NetworkModule.kt` - 网络模块配置（已更新）

### 加密规则
`thirdKeyStr` 的生成规则：
```
thirdKeyStr = AES加密(ThirdKeyReturnModel的JSON字符串, 请求Config接口头部的AESKEY)
```

加密参数：
- **算法**: AES
- **模式**: CBC
- **填充**: PKCS7Padding
- **IV**: 固定16字节（与RequestInterceptor保持一致）
- **密钥**: 来自请求头的AESKEY（32字节随机字符串）
- **输出格式**: 16进制字符串（IV + 密文）

### 自动解密机制

**重要更新**：`thirdKeyStr` 现在由网络层自动解密，业务层直接获取解密后的JSON字符串！

#### 工作原理
1. `RequestInterceptor` 生成AES密钥并保存到 `AesKeyManager`
2. `ResponseDecryptInterceptor` 自动拦截 `/V1/System/Config` 响应
3. 使用保存的AES密钥解密 `thirdKeyStr`
4. 将解密后的JSON字符串替换原始加密数据
5. 业务层直接获取解密后的内容

#### 基本用法
```kotlin
// 1. 获取SystemConfig（thirdKeyStr已自动解密）
val systemConfig: SystemConfigModel = apiService.getSystemConfig()

// 2. thirdKeyStr现在是JSON字符串，需要解析为对象
val thirdKeyModel = parseThirdKeyJson(systemConfig.thirdKeyStr)

// 3. 使用解密后的密钥
thirdKeyModel?.let {
    configureTencentCOS(it.cosSecretId, it.cosSecretKey)
    configureTencentFace(it.faceSecretId, it.faceSecretKey)
    configureAmapSDK(it.amapKey)
}

// 辅助函数：解析JSON
private fun parseThirdKeyJson(json: String): ThirdKeyReturnModel? {
    return try {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(ThirdKeyReturnModel::class.java)
        adapter.fromJson(json)
    } catch (e: Exception) {
        null
    }
}
```

#### 测试用法
```kotlin
// 加密测试数据
val testModel = ThirdKeyReturnModel(
    cosSecretId = "test_id",
    cosSecretKey = "test_key",
    faceSecretId = "face_id",
    faceSecretKey = "face_key",
    amapKey = "amap_key"
)

val aesKey = "12345678901234567890123456789012" // 32字节
val encrypted = ThirdKeyDecryptUtils.encryptThirdKeyModel(testModel, aesKey)

// 解密
val decrypted = ThirdKeyDecryptUtils.decryptThirdKeyStr(encrypted!!, aesKey)
```

### Mock数据更新
- ✅ `app/src/debug/assets/mock/system_config.json` - 添加 `thirdKeyStr` 字段

## 3. 其他变更

### 字符串资源
- ✅ 添加 `back_button_description` 到 `strings.xml`

### 导航系统
- ✅ 新增 `WebViewRoute` 路由
- ✅ 新增 `navigateToWebView()` 扩展函数
- ✅ 在 `AppNavigation` 中注册 WebView composable

## 4. 文件清单

### 新增文件
```
app/src/main/kotlin/com/ytone/longcare/
├── api/response/
│   ├── StartConfigResultModel.kt          # 启动配置响应模型
│   └── ThirdKeyReturnModel.kt             # 第三方密钥模型
├── common/utils/
│   ├── ThirdKeyDecryptUtils.kt            # 解密工具类
│   └── ThirdKeyDecryptUsageExample.kt     # 使用示例
├── network/interceptor/
│   ├── AesKeyManager.kt                   # AES密钥管理器
│   └── ResponseDecryptInterceptor.kt      # 响应解密拦截器
└── features/webview/ui/
    └── WebViewScreen.kt                    # WebView页面

app/src/debug/assets/mock/
└── start_config.json                       # 启动配置Mock数据
```

### 修改文件
```
app/src/main/kotlin/com/ytone/longcare/
├── api/
│   ├── LongCareApiService.kt              # 添加getStartConfig接口
│   └── response/SystemConfigModel.kt       # 添加thirdKeyStr字段
├── domain/login/
│   └── LoginRepository.kt                  # 添加getStartConfig方法
├── data/repository/
│   └── LoginRepositoryImpl.kt              # 实现getStartConfig方法
├── features/login/
│   ├── vm/LoginViewModel.kt                # 添加启动配置状态管理
│   └── ui/LoginScreen.kt                   # 集成协议链接点击
├── navigation/
│   ├── NavigationRoutes.kt                 # 添加WebViewRoute
│   └── AppNavigation.kt                    # 添加WebView导航
├── network/interceptor/
│   └── RequestInterceptor.kt               # 添加AES密钥保存逻辑
└── di/
    └── NetworkModule.kt                    # 注册响应解密拦截器

app/src/debug/
├── assets/mock/
│   └── system_config.json                  # 添加thirdKeyStr字段
└── kotlin/.../network/interceptor/
    └── MockInterceptor.kt                  # 添加Start接口Mock

app/src/main/res/values/
└── strings.xml                             # 添加back_button_description
```

## 5. 测试建议

### 启动配置测试
1. ✅ 启动应用，检查LoginViewModel是否自动加载启动配置
2. ✅ 点击"用户协议"链接，验证WebView是否正确打开
3. ✅ 点击"隐私政策"链接，验证WebView是否正确打开
4. ✅ 测试WebView的返回按钮功能
5. ✅ 测试网络异常情况下的降级处理（显示Toast）

### 第三方密钥解密测试
1. ✅ AES密钥管理机制已实现（AesKeyManager + ThreadLocal）
2. ✅ 自动解密功能已实现（ResponseDecryptInterceptor）
3. ✅ 解密失败的错误处理已实现
4. ⚠️ 需要测试实际API返回的加密数据
5. ⚠️ 需要验证解密后的密钥格式是否正确

## 6. 待完成事项

### 高优先级
- [x] **实现AES密钥管理机制** ✅
  - ✅ 创建 `AesKeyManager` 使用ThreadLocal管理密钥
  - ✅ 在 `RequestInterceptor` 中保存AES密钥
  - ✅ 在 `ResponseDecryptInterceptor` 中自动解密thirdKeyStr
  - ✅ 响应处理完成后自动清除密钥

- [ ] **集成第三方SDK配置**
  - 在获取并解密thirdKeyStr后，配置腾讯云COS SDK
  - 配置腾讯云人脸识别SDK
  - 配置高德地图SDK
  - 创建统一的SDK配置管理器

### 中优先级
- [ ] **添加单元测试**
  - ThirdKeyDecryptUtils的加密解密测试
  - LoginViewModel的启动配置加载测试
  - WebView页面的UI测试

- [ ] **优化用户体验**
  - WebView添加加载失败的错误页面
  - 添加网络状态检测
  - 优化加载动画

### 低优先级
- [ ] **文档完善**
  - 添加API文档注释
  - 更新项目README
  - 添加架构图

## 7. 注意事项

### 安全性
1. ✅ `thirdKeyStr` 在网络层自动解密，业务层获取JSON格式
2. ✅ AES密钥使用ThreadLocal存储，线程隔离，用后即清
3. ✅ 密钥不会持久化存储，每个请求独立
4. ⚠️ 解密后的ThirdKeyReturnModel应该安全存储（考虑使用EncryptedSharedPreferences）
5. ⚠️ 业务层需要将JSON解析为对象后使用

### 兼容性
1. ✅ 所有新增字段都有默认值，向后兼容
2. ✅ Mock数据已更新，支持开发和测试
3. ✅ 遵循项目现有的代码规范和架构模式

### 性能
1. ✅ 启动配置在LoginViewModel初始化时异步加载，不阻塞UI
2. ✅ WebView使用AndroidView包装，性能良好
3. ⚠️ 解密操作应该在IO线程执行（已在ThirdKeyDecryptUtils中处理）

## 8. 相关文档

- [通用本地通知系统技术架构文档](通用本地通知系统技术架构文档.md)
- [项目开发规范](project-standards.md)
- [ThirdKey自动解密指南](THIRD_KEY_AUTO_DECRYPT_GUIDE.md) ⭐ **重要**
- [ThirdKeyDecryptUtils使用示例](app/src/main/kotlin/com/ytone/longcare/common/utils/ThirdKeyDecryptUsageExample.kt)

## 9. 联系方式

如有问题，请联系开发团队或查看项目文档。
