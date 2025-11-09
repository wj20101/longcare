# 响应处理器架构设计文档

## 📐 架构概述

本架构采用**策略模式 + 依赖注入**设计，实现了低耦合、高扩展的响应处理机制。

### 核心组件

```
ResponseDecryptInterceptor (调度器)
         ↓
    查找处理器
         ↓
Set<ResponseProcessor> (处理器集合)
    ├── SystemConfigResponseProcessor
    ├── XxxResponseProcessor (未来扩展)
    └── YyyResponseProcessor (未来扩展)
```

## 🎯 设计原则

### 1. 单一职责原则 (SRP)
- **ResponseDecryptInterceptor**：只负责调度，不包含具体业务逻辑
- **ResponseProcessor**：每个处理器只处理一个接口的响应

### 2. 开闭原则 (OCP)
- 对扩展开放：新增接口处理器无需修改现有代码
- 对修改关闭：现有处理器互不影响

### 3. 依赖倒置原则 (DIP)
- 拦截器依赖抽象接口 `ResponseProcessor`
- 具体处理器实现接口，通过依赖注入提供

## 📦 核心接口

### ResponseProcessor

```kotlin
interface ResponseProcessor {
    /**
     * 判断是否可以处理该响应
     */
    fun canProcess(path: String): Boolean
    
    /**
     * 处理响应
     */
    fun process(response: Response, aesKey: String?): Response
}
```

## 🔧 实现示例

### 1. SystemConfig处理器

```kotlin
@Singleton
class SystemConfigResponseProcessor @Inject constructor(
    private val moshi: Moshi
) : ResponseProcessor {
    
    // 创建Moshi适配器（懒加载）
    private val responseAdapter by lazy {
        val type = Types.newParameterizedType(
            ApiResponse::class.java, 
            SystemConfigModel::class.java
        )
        moshi.adapter<ApiResponse<SystemConfigModel>>(type)
    }
    
    override fun canProcess(path: String): Boolean {
        return path.endsWith("/V1/System/Config")
    }
    
    override fun process(response: Response, aesKey: String?): Response {
        // 1. 检查AES密钥
        // 2. 使用Moshi解析响应
        // 3. 解密thirdKeyStr字段
        // 4. 使用copy()创建新对象
        // 5. 使用Moshi序列化回JSON
        // 6. 返回新响应
    }
}
```

### 2. 注册处理器

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class ResponseProcessorModule {
    
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSystemConfigProcessor(
        processor: SystemConfigResponseProcessor
    ): ResponseProcessor
}
```

### 3. 拦截器调度

```kotlin
class ResponseDecryptInterceptor @Inject constructor(
    private val aesKeyManager: AesKeyManager,
    private val processors: Set<@JvmSuppressWildcards ResponseProcessor>
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val path = chain.request().url.encodedPath
        
        // 查找处理器
        val processor = processors.firstOrNull { it.canProcess(path) }
        
        // 使用处理器处理响应
        return processor?.process(response, aesKeyManager.getKey()) ?: response
    }
}
```

## 🚀 如何添加新的处理器

### 步骤1：创建处理器类

```kotlin
package com.ytone.longcare.network.processor

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.model.Response as ApiResponse

@Singleton
class UserInfoResponseProcessor @Inject constructor(
    private val moshi: Moshi
) : ResponseProcessor {
    
    companion object {
        private const val TAG = "UserInfoProcessor"
        private const val USER_INFO_PATH = "/V1/User/Info"
    }
    
    // 创建Moshi适配器
    private val responseAdapter by lazy {
        val type = Types.newParameterizedType(
            ApiResponse::class.java,
            UserInfoModel::class.java
        )
        moshi.adapter<ApiResponse<UserInfoModel>>(type)
    }
    
    override fun canProcess(path: String): Boolean {
        return path.endsWith(USER_INFO_PATH)
    }
    
    override fun process(response: Response, aesKey: String?): Response {
        try {
            val responseBody = response.body?.string() ?: return response
            
            // 1. 使用Moshi解析JSON
            val apiResponse = responseAdapter.fromJson(responseBody)
                ?: return response
            
            // 2. 处理数据（解密、转换等）
            val userInfo = apiResponse.data ?: return response
            val processedUserInfo = processUserInfo(userInfo, aesKey)
            
            // 3. 创建新的响应对象
            val updatedApiResponse = apiResponse.copy(data = processedUserInfo)
            
            // 4. 序列化回JSON
            val updatedResponseString = responseAdapter.toJson(updatedApiResponse)
            
            // 5. 返回新响应
            val newResponseBody = updatedResponseString.toResponseBody(
                response.body?.contentType()
            )
            
            return response.newBuilder()
                .body(newResponseBody)
                .build()
                
        } catch (e: Exception) {
            logE(TAG, "Error processing response", e)
            return response
        }
    }
    
    private fun processUserInfo(userInfo: UserInfoModel, aesKey: String?): UserInfoModel {
        // 你的处理逻辑
        return userInfo
    }
}
```

### 步骤2：注册处理器

在 `ResponseProcessorModule.kt` 中添加：

```kotlin
@Binds
@IntoSet
@Singleton
abstract fun bindUserInfoProcessor(
    processor: UserInfoResponseProcessor
): ResponseProcessor
```

### 完成！

无需修改任何其他代码，新的处理器就会自动生效。

## 📊 架构优势

### 1. 低耦合
- 每个处理器独立实现，互不影响
- 拦截器不依赖具体处理器，只依赖接口

### 2. 高扩展
- 新增处理器只需两步：创建类 + 注册
- 无需修改现有代码

### 3. 易维护
- 每个处理器职责单一，代码清晰
- 问题定位快速，修改影响范围小

### 4. 易测试
- 处理器可以独立单元测试
- Mock注入简单

## 🔍 工作流程

```
1. 请求发送
   ↓
2. RequestInterceptor 保存AES密钥
   ↓
3. 服务器返回响应
   ↓
4. ResponseDecryptInterceptor 拦截
   ↓
5. 遍历 processors 查找匹配的处理器
   ↓
6. 调用 processor.canProcess(path)
   ↓
7. 找到处理器，调用 processor.process(response, aesKey)
   ↓
8. 处理器执行业务逻辑（解密、转换等）
   ↓
9. 返回处理后的响应
   ↓
10. 清除AES密钥
   ↓
11. 业务层获取处理后的数据
```

## 📝 最佳实践

### 1. 处理器命名规范
- 格式：`{接口名}ResponseProcessor`
- 示例：`SystemConfigResponseProcessor`、`UserInfoResponseProcessor`

### 2. 使用Moshi处理JSON
```kotlin
// ✅ 推荐：统一使用Moshi
private val responseAdapter by lazy {
    val type = Types.newParameterizedType(
        ApiResponse::class.java,
        YourDataModel::class.java
    )
    moshi.adapter<ApiResponse<YourDataModel>>(type)
}

// ❌ 不推荐：混用JSONObject和Moshi
// 会导致代码不一致，增加维护成本
```

### 3. 路径匹配
```kotlin
// ✅ 推荐：使用 endsWith 匹配路径
override fun canProcess(path: String): Boolean {
    return path.endsWith("/V1/System/Config")
}

// ❌ 不推荐：使用 contains 可能误匹配
override fun canProcess(path: String): Boolean {
    return path.contains("Config")  // 可能匹配到其他包含Config的路径
}
```

### 4. 使用data class的copy()方法
```kotlin
// ✅ 推荐：使用copy()创建新对象
val updatedConfig = systemConfig.copy(thirdKeyStr = decryptedJson)
val updatedResponse = apiResponse.copy(data = updatedConfig)

// ❌ 不推荐：手动修改JSON字符串
// 容易出错，且不类型安全
```

### 5. 错误处理
```kotlin
override fun process(response: Response, aesKey: String?): Response {
    try {
        // 处理逻辑
    } catch (e: Exception) {
        logE(TAG, "Error processing response", e)
        // 返回原始响应，不影响业务
        return response
    }
}
```

### 6. 日志记录
```kotlin
// 记录关键步骤
logD(TAG, "Processing response for path: $path")
logD(TAG, "Successfully decrypted data")
logE(TAG, "Failed to decrypt data")
```

## 🧪 测试示例

### 单元测试处理器

```kotlin
@Test
fun testSystemConfigProcessor() {
    val processor = SystemConfigResponseProcessor(moshi)
    
    // 测试路径匹配
    assertTrue(processor.canProcess("/V1/System/Config"))
    assertFalse(processor.canProcess("/V1/User/Info"))
    
    // 测试处理逻辑
    val mockResponse = createMockResponse()
    val result = processor.process(mockResponse, "test_aes_key")
    
    // 验证结果
    assertNotNull(result)
    // ...
}
```

### 集成测试

```kotlin
@Test
fun testResponseDecryptInterceptor() {
    val interceptor = ResponseDecryptInterceptor(
        aesKeyManager = mockAesKeyManager,
        processors = setOf(mockProcessor1, mockProcessor2)
    )
    
    // 测试调度逻辑
    val response = interceptor.intercept(mockChain)
    
    // 验证正确的处理器被调用
    verify(mockProcessor1).process(any(), any())
}
```

## 📚 相关文档

- [快速开始指南](QUICK_START.md)
- [ThirdKey自动解密指南](THIRD_KEY_AUTO_DECRYPT_GUIDE.md)
- [API变更总结](API_CHANGES_SUMMARY.md)

## 🎓 总结

这个架构设计实现了：

1. ✅ **低耦合**：处理器独立，互不影响
2. ✅ **高扩展**：新增处理器只需两步
3. ✅ **易维护**：职责单一，代码清晰
4. ✅ **易测试**：可独立测试每个组件
5. ✅ **高性能**：只处理需要的接口，无额外开销

通过这个架构，我们可以轻松地为任何需要特殊处理的API接口添加自定义逻辑，而无需修改核心拦截器代码。
