# 快速开始指南

## SystemConfig thirdKeyStr 自动解密

### 🎯 核心要点

**好消息**：你不需要手动解密 `thirdKeyStr`！网络层已经自动处理了。

### 📝 使用步骤

#### 1. 调用API获取SystemConfig

```kotlin
class MyViewModel @Inject constructor(
    private val apiService: LongCareApiService
) : ViewModel() {
    
    fun loadSystemConfig() {
        viewModelScope.launch {
            try {
                val response = apiService.getSystemConfig()
                if (response.resultCode == 1000) {
                    val systemConfig = response.data
                    handleSystemConfig(systemConfig)
                }
            } catch (e: Exception) {
                // 处理错误
            }
        }
    }
}
```

#### 2. 解析thirdKeyStr（已自动解密为JSON）

```kotlin
private fun handleSystemConfig(systemConfig: SystemConfigModel) {
    // thirdKeyStr 已经是解密后的JSON字符串
    val thirdKeyJson = systemConfig.thirdKeyStr
    
    // 解析JSON为对象
    val thirdKeyModel = parseThirdKeyJson(thirdKeyJson)
    
    thirdKeyModel?.let {
        // 使用解密后的密钥配置SDK
        setupSDKs(it)
    }
}

private fun parseThirdKeyJson(json: String): ThirdKeyReturnModel? {
    return try {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(ThirdKeyReturnModel::class.java)
        adapter.fromJson(json)
    } catch (e: Exception) {
        logE("MyViewModel", "Failed to parse thirdKeyStr", e)
        null
    }
}
```

#### 3. 配置第三方SDK

```kotlin
private fun setupSDKs(thirdKey: ThirdKeyReturnModel) {
    // 配置腾讯云COS
    TencentCOSManager.init(
        secretId = thirdKey.cosSecretId,
        secretKey = thirdKey.cosSecretKey
    )
    
    // 配置腾讯云人脸识别
    TencentFaceManager.init(
        secretId = thirdKey.faceSecretId,
        secretKey = thirdKey.faceSecretKey
    )
    
    // 配置高德地图
    AmapManager.init(
        apiKey = thirdKey.amapKey
    )
}
```

### 🔍 数据格式说明

#### 原始API响应（服务器返回）
```json
{
  "resultCode": 1000,
  "resultMsg": "成功",
  "data": {
    "thirdKeyStr": "4172657975...（16进制加密字符串）"
  }
}
```

#### 拦截器处理后（业务层获取）
```json
{
  "resultCode": 1000,
  "resultMsg": "成功",
  "data": {
    "thirdKeyStr": "{\"cosSecretId\":\"xxx\",\"cosSecretKey\":\"xxx\",\"faceSecretId\":\"xxx\",\"faceSecretKey\":\"xxx\",\"amapKey\":\"xxx\"}"
  }
}
```

### ⚠️ 注意事项

1. **thirdKeyStr是JSON字符串**：需要使用Moshi解析为 `ThirdKeyReturnModel` 对象
2. **自动解密仅限SystemConfig接口**：其他接口不受影响
3. **Mock数据格式**：在Mock数据中，`thirdKeyStr` 应该直接使用JSON字符串格式

### 🧪 Mock数据示例

```json
{
  "resultCode": 1000,
  "resultMsg": "成功",
  "data": {
    "companyName": "长护科技有限公司",
    "maxImgNum": 9,
    "syLogoImg": "https://example.com/logo.png",
    "selectServiceType": 0,
    "thirdKeyStr": "{\"cosSecretId\":\"mock_cos_id\",\"cosSecretKey\":\"mock_cos_key\",\"faceSecretId\":\"mock_face_id\",\"faceSecretKey\":\"mock_face_key\",\"amapKey\":\"mock_amap_key\"}"
  }
}
```

### 📚 完整示例

```kotlin
@HiltViewModel
class SystemConfigViewModel @Inject constructor(
    private val apiService: LongCareApiService,
    private val moshi: Moshi
) : ViewModel() {
    
    private val _configState = MutableStateFlow<ConfigState>(ConfigState.Idle)
    val configState: StateFlow<ConfigState> = _configState
    
    fun loadConfig() {
        viewModelScope.launch {
            _configState.value = ConfigState.Loading
            
            try {
                val response = apiService.getSystemConfig()
                
                if (response.resultCode == 1000 && response.data != null) {
                    val systemConfig = response.data
                    
                    // 解析thirdKeyStr
                    val thirdKeyModel = parseThirdKeyJson(systemConfig.thirdKeyStr)
                    
                    if (thirdKeyModel != null) {
                        // 配置SDK
                        setupSDKs(thirdKeyModel)
                        
                        _configState.value = ConfigState.Success(systemConfig, thirdKeyModel)
                    } else {
                        _configState.value = ConfigState.Error("Failed to parse thirdKeyStr")
                    }
                } else {
                    _configState.value = ConfigState.Error(response.resultMsg)
                }
            } catch (e: Exception) {
                _configState.value = ConfigState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private fun parseThirdKeyJson(json: String): ThirdKeyReturnModel? {
        return try {
            val adapter = moshi.adapter(ThirdKeyReturnModel::class.java)
            adapter.fromJson(json)
        } catch (e: Exception) {
            logE("SystemConfigViewModel", "Failed to parse thirdKeyStr", e)
            null
        }
    }
    
    private fun setupSDKs(thirdKey: ThirdKeyReturnModel) {
        // 配置各个SDK
        logD("SystemConfigViewModel", "Setting up SDKs with decrypted keys")
        
        // TODO: 实际的SDK配置代码
    }
}

sealed class ConfigState {
    object Idle : ConfigState()
    object Loading : ConfigState()
    data class Success(
        val systemConfig: SystemConfigModel,
        val thirdKey: ThirdKeyReturnModel
    ) : ConfigState()
    data class Error(val message: String) : ConfigState()
}
```

### 🔧 故障排查

#### 问题：thirdKeyStr仍然是加密字符串

**检查清单**：
- [ ] 确认 `ResponseDecryptInterceptor` 已在 `NetworkModule` 中注册
- [ ] 确认拦截器顺序正确
- [ ] 查看日志中是否有解密错误信息

#### 问题：JSON解析失败

**检查清单**：
- [ ] 确认 `thirdKeyStr` 是有效的JSON格式
- [ ] 确认 `ThirdKeyReturnModel` 的字段名与JSON匹配
- [ ] 查看异常堆栈信息

### 📖 更多信息

详细文档请参考：
- [ThirdKey自动解密指南](THIRD_KEY_AUTO_DECRYPT_GUIDE.md)
- [API变更总结](API_CHANGES_SUMMARY.md)
