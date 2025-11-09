# SystemConfig thirdKeyStr 自动解密指南

## 概述

本文档说明如何使用自动解密功能来处理 `SystemConfig` 接口返回的 `thirdKeyStr` 字段。

## 工作原理

### 架构流程

```
1. 请求阶段 (RequestInterceptor)
   ├─ 生成32字节随机AES密钥 (randomString)
   ├─ 保存密钥到 AesKeyManager (ThreadLocal)
   ├─ 使用密钥加密请求参数
   └─ 发送请求

2. 响应阶段 (ResponseDecryptInterceptor)
   ├─ 拦截 /V1/System/Config 响应
   ├─ 从 AesKeyManager 获取AES密钥
   ├─ 解密 thirdKeyStr 字段
   ├─ 将解密后的JSON替换原始加密字符串
   ├─ 清除 AesKeyManager 中的密钥
   └─ 返回修改后的响应

3. 业务层
   └─ 直接获取解密后的 thirdKeyStr (JSON格式)
```

### 核心组件

#### 1. AesKeyManager
```kotlin
@Singleton
class AesKeyManager {
    private val keyStorage = ThreadLocal<String>()
    
    fun saveKey(key: String)    // 保存密钥
    fun getKey(): String?       // 获取密钥
    fun clearKey()              // 清除密钥
}
```

**特点**：
- 使用 `ThreadLocal` 确保线程安全
- 每个请求线程都有独立的密钥副本
- 自动在响应处理完成后清除

#### 2. RequestInterceptor (已更新)
```kotlin
class RequestInterceptor @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    private val deviceUtils: DeviceUtils,
    private val aesKeyManager: AesKeyManager  // 新增
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val randomString = RandomUtils.generateRandomStringKotlin(32)
        
        // 保存AES密钥供响应拦截器使用
        aesKeyManager.saveKey(randomString)
        
        // ... 其他加密逻辑
    }
}
```

#### 3. ResponseDecryptInterceptor (新增)
```kotlin
class ResponseDecryptInterceptor @Inject constructor(
    private val aesKeyManager: AesKeyManager,
    private val moshi: Moshi
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        // 1. 只处理 /V1/System/Config 接口
        // 2. 获取AES密钥
        // 3. 解密 thirdKeyStr
        // 4. 替换响应中的值
        // 5. 清除密钥
    }
}
```

## 使用方法

### 在业务层直接使用

```kotlin
// 在Repository中
class SystemRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService
) : SystemRepository {
    
    override suspend fun getSystemConfig(): ApiResult<SystemConfigModel> {
        return safeApiCall(ioDispatcher, eventBus) {
            apiService.getSystemConfig()
        }
    }
}

// 在ViewModel中
class MyViewModel @Inject constructor(
    private val systemRepository: SystemRepository
) : ViewModel() {
    
    fun loadSystemConfig() {
        viewModelScope.launch {
            when (val result = systemRepository.getSystemConfig()) {
                is ApiResult.Success -> {
                    val systemConfig = result.data
                    
                    // thirdKeyStr 已经是解密后的JSON字符串
                    val thirdKeyJson = systemConfig.thirdKeyStr
                    
                    // 解析JSON为ThirdKeyReturnModel对象
                    val thirdKeyModel = parseThirdKeyJson(thirdKeyJson)
                    
                    thirdKeyModel?.let {
                        // 使用解密后的密钥
                        configureTencentCOS(it.cosSecretId, it.cosSecretKey)
                        configureTencentFace(it.faceSecretId, it.faceSecretKey)
                        configureAmapSDK(it.amapKey)
                    }
                }
                // ... 处理其他情况
            }
        }
    }
    
    private fun parseThirdKeyJson(json: String): ThirdKeyReturnModel? {
        return try {
            val moshi = Moshi.Builder().build()
            val adapter = moshi.adapter(ThirdKeyReturnModel::class.java)
            adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
```

### 响应数据格式

#### 原始响应（加密）
```json
{
  "resultCode": 1000,
  "resultMsg": "成功",
  "data": {
    "companyName": "长护科技有限公司",
    "maxImgNum": 9,
    "syLogoImg": "https://example.com/logo.png",
    "selectServiceType": 0,
    "thirdKeyStr": "4172657975...（16进制加密字符串）"
  }
}
```

#### 拦截器处理后的响应（解密）
```json
{
  "resultCode": 1000,
  "resultMsg": "成功",
  "data": {
    "companyName": "长护科技有限公司",
    "maxImgNum": 9,
    "syLogoImg": "https://example.com/logo.png",
    "selectServiceType": 0,
    "thirdKeyStr": "{\"cosSecretId\":\"xxx\",\"cosSecretKey\":\"xxx\",\"faceSecretId\":\"xxx\",\"faceSecretKey\":\"xxx\",\"amapKey\":\"xxx\"}"
  }
}
```

## 配置说明

### 1. 依赖注入配置

在 `NetworkModule.kt` 中已经配置好：

```kotlin
@Provides
@Singleton
fun provideOkHttpClient(
    @ApplicationContext context: Context,
    loggingInterceptor: HttpLoggingInterceptor,
    requestInterceptor: RequestInterceptor,
    responseDecryptInterceptor: ResponseDecryptInterceptor,  // 新增
    cache: Cache
): OkHttpClient {
    return OkHttpClient.Builder()
        .addInterceptor(requestInterceptor)           // 请求加密
        .addInterceptor(responseDecryptInterceptor)   // 响应解密
        .addInterceptor(loggingInterceptor)           // 日志
        .cache(cache)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addFlavorInterceptors(context)
        .build()
}
```

### 2. 拦截器顺序

**重要**：拦截器的顺序很关键！

```
请求流程：
RequestInterceptor → ResponseDecryptInterceptor → LoggingInterceptor → Network

响应流程：
Network → LoggingInterceptor → ResponseDecryptInterceptor → RequestInterceptor
```

- `RequestInterceptor` 必须在最前面，以便保存AES密钥
- `ResponseDecryptInterceptor` 在 `LoggingInterceptor` 之前，这样日志会显示解密后的内容
- 响应时按相反顺序执行

## 安全性说明

### 1. 密钥管理
- ✅ AES密钥使用 `ThreadLocal` 存储，线程隔离
- ✅ 密钥在响应处理完成后立即清除
- ✅ 密钥不会持久化存储
- ✅ 每个请求使用独立的随机密钥

### 2. 解密过程
- ✅ 只解密 `/V1/System/Config` 接口的响应
- ✅ 解密失败时返回原始响应，不影响业务
- ✅ 使用与加密相同的参数（CBC模式，PKCS7填充）
- ✅ IV从密文中自动提取

### 3. 错误处理
- ✅ 密钥不存在时记录错误日志
- ✅ 解密失败时记录错误日志
- ✅ 异常情况下返回原始响应
- ✅ 确保密钥始终被清除（finally块）

## 测试建议

### 1. 单元测试

```kotlin
@Test
fun testAesKeyManager() {
    val keyManager = AesKeyManager()
    
    // 测试保存和获取
    keyManager.saveKey("test_key_123")
    assertEquals("test_key_123", keyManager.getKey())
    
    // 测试清除
    keyManager.clearKey()
    assertNull(keyManager.getKey())
}

@Test
fun testThreadLocalIsolation() {
    val keyManager = AesKeyManager()
    
    // 主线程保存密钥
    keyManager.saveKey("main_thread_key")
    
    // 子线程应该获取不到
    val thread = Thread {
        assertNull(keyManager.getKey())
    }
    thread.start()
    thread.join()
    
    // 主线程仍然可以获取
    assertEquals("main_thread_key", keyManager.getKey())
}
```

### 2. 集成测试

```kotlin
@Test
fun testSystemConfigDecryption() = runTest {
    // 调用API
    val result = apiService.getSystemConfig()
    
    // 验证响应成功
    assertTrue(result.isSuccess)
    
    val systemConfig = result.data
    assertNotNull(systemConfig)
    
    // 验证thirdKeyStr已解密（应该是JSON格式）
    val thirdKeyStr = systemConfig.thirdKeyStr
    assertTrue(thirdKeyStr.startsWith("{"))
    assertTrue(thirdKeyStr.contains("cosSecretId"))
    
    // 验证可以解析为ThirdKeyReturnModel
    val moshi = Moshi.Builder().build()
    val adapter = moshi.adapter(ThirdKeyReturnModel::class.java)
    val thirdKeyModel = adapter.fromJson(thirdKeyStr)
    
    assertNotNull(thirdKeyModel)
    assertNotNull(thirdKeyModel.cosSecretId)
    assertNotNull(thirdKeyModel.faceSecretId)
    assertNotNull(thirdKeyModel.amapKey)
}
```

### 3. Mock测试

在 `MockInterceptor` 中，`thirdKeyStr` 字段可以直接使用JSON字符串：

```json
{
  "resultCode": 1000,
  "resultMsg": "成功",
  "data": {
    "companyName": "长护科技有限公司",
    "maxImgNum": 9,
    "syLogoImg": "https://file.ytone.cn/public/sylogo/sy_logo_1001_v1.png",
    "selectServiceType": 0,
    "thirdKeyStr": "{\"cosSecretId\":\"mock_cos_id\",\"cosSecretKey\":\"mock_cos_key\",\"faceSecretId\":\"mock_face_id\",\"faceSecretKey\":\"mock_face_key\",\"amapKey\":\"mock_amap_key\"}"
  }
}
```

## 故障排查

### 问题1：thirdKeyStr 仍然是加密字符串

**可能原因**：
1. 响应拦截器未正确注册
2. 拦截器顺序不正确
3. AES密钥未保存或已被清除

**解决方法**：
1. 检查 `NetworkModule` 中的拦截器配置
2. 确认拦截器顺序：RequestInterceptor → ResponseDecryptInterceptor
3. 在 `RequestInterceptor` 中添加日志确认密钥已保存

### 问题2：解密失败

**可能原因**：
1. AES密钥不匹配
2. 加密格式不正确
3. IV提取失败

**解决方法**：
1. 检查 `RequestInterceptor` 和 `ResponseDecryptInterceptor` 使用的是同一个 `AesKeyManager` 实例
2. 确认加密参数：CBC模式，PKCS7填充
3. 查看日志中的错误信息

### 问题3：多线程环境下密钥混乱

**可能原因**：
1. `ThreadLocal` 未正确使用
2. 密钥未及时清除

**解决方法**：
1. 确认 `AesKeyManager` 使用 `@Singleton` 注解
2. 确认在 `finally` 块中清除密钥
3. 使用线程池时注意 `ThreadLocal` 的生命周期

## 性能影响

### 1. 内存开销
- `ThreadLocal` 存储：每个线程约32字节（密钥长度）
- JSON解析：临时对象，GC自动回收
- 总体影响：**可忽略**

### 2. 时间开销
- AES解密：约1-2ms
- JSON解析和替换：约1-2ms
- 总体影响：**约2-4ms**（相比网络延迟可忽略）

### 3. 优化建议
- ✅ 只处理 SystemConfig 接口，不影响其他接口
- ✅ 使用 `ThreadLocal` 避免同步开销
- ✅ 解密失败时快速返回原始响应

## 总结

### 优点
1. ✅ **透明化**：业务层无需关心加密解密细节
2. ✅ **自动化**：拦截器自动处理，无需手动调用
3. ✅ **安全性**：密钥线程隔离，用后即清
4. ✅ **可靠性**：异常情况下不影响业务
5. ✅ **可维护性**：集中管理，易于调试

### 注意事项
1. ⚠️ 确保拦截器顺序正确
2. ⚠️ Mock数据中 `thirdKeyStr` 应使用JSON格式
3. ⚠️ 业务层需要解析JSON字符串为对象
4. ⚠️ 注意 `ThreadLocal` 在线程池中的使用

## 相关文件

- `AesKeyManager.kt` - AES密钥管理器
- `RequestInterceptor.kt` - 请求拦截器（已更新）
- `ResponseDecryptInterceptor.kt` - 响应解密拦截器（新增）
- `NetworkModule.kt` - 网络模块配置（已更新）
- `ThirdKeyDecryptUtils.kt` - 解密工具类
- `ThirdKeyReturnModel.kt` - 第三方密钥模型
