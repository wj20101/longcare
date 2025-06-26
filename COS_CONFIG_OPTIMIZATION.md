# COS配置优化重构说明

## 概述

本次重构将 `CosRepositoryImpl` 中的 token 缓存机制升级为统一的 `CosConfig` 缓存机制，实现了更好的架构设计和代码复用。

## 主要改进

### 1. 统一配置管理

#### 扩展 CosConfig 数据类
- 整合了 `UploadTokenResultModel` 的所有字段
- 添加了智能属性判断（`isTemporaryCredentials`、`isStaticCredentials`）
- 提供了统一的过期时间处理（`effectiveExpiredTime`）
- 增加了过期检查方法（`isExpiringSoon`）

```kotlin
data class CosConfig(
    val region: String,
    val bucket: String,
    val secretId: String? = null,
    val secretKey: String? = null,
    val sessionToken: String? = null,
    val expiredTime: Long? = null,
    // 从UploadTokenResultModel扩展的字段
    val tmpSecretId: String? = null,
    val tmpSecretKey: String? = null,
    val startTime: String? = null,
    val requestId: String? = null,
    val expiration: String? = null,
    val expiredTimeStr: String? = null
) {
    // 智能属性和方法...
}
```

#### 转换扩展函数
- `UploadTokenResultModel.toCosConfig()`: 将API响应转换为统一配置
- `CosCredentials.toCosConfig()`: 将临时密钥转换为统一配置

### 2. 缓存机制优化

#### 从 TokenCache 升级为 CosConfigCache
```kotlin
// 旧版本
private class TokenCache {
    var tokenResult: UploadTokenResultModel? = null
    var expireTime: Long = 0L
}

// 新版本
private class CosConfigCache {
    var cosConfig: CosConfig? = null
    
    fun isValid(): Boolean {
        val config = cosConfig ?: return false
        return !config.isExpiringSoon(TOKEN_REFRESH_THRESHOLD_SECONDS)
    }
}
```

#### 优势
- **统一管理**: bucket、region、密钥信息都在一个对象中
- **智能过期**: 利用 `CosConfig` 的内置过期检查逻辑
- **类型安全**: 减少了类型转换和空值检查

### 3. 动态密钥提供者增强

#### 支持多种认证方式
```kotlin
override fun getCredentials(): QCloudLifecycleCredentials? {
    return configCache.cosConfig?.let { config ->
        when {
            config.isTemporaryCredentials -> {
                // 临时密钥认证
                SessionQCloudCredentials(
                    config.tmpSecretId!!,
                    config.tmpSecretKey!!,
                    config.sessionToken!!,
                    config.startTime?.toLongOrNull() ?: 0L,
                    config.effectiveExpiredTime
                )
            }
            config.isStaticCredentials -> {
                // 静态密钥认证
                SessionQCloudCredentials(
                    config.secretId!!,
                    config.secretKey!!,
                    null,
                    0L,
                    config.effectiveExpiredTime
                )
            }
            else -> null
        }
    }
}
```

### 4. 代码简化和优化

#### 移除冗余状态变量
- 删除了 `currentBucket` 和 `currentRegion` 变量
- 所有bucket和region信息都从 `CosConfig` 获取
- 减少了状态同步的复杂性

#### 方法重命名和优化
- `getValidUploadToken()` → `getValidCosConfig()`
- `refreshUploadToken()` → `refreshCosConfig()`
- `refreshTokenSync()` → `refreshConfigSync()`

#### URL生成优化
```kotlin
// 新增重载方法，支持直接传入配置
private fun getPublicUrl(key: String, config: CosConfig): String {
    return "https://${config.bucket}.cos.${config.region}.myqcloud.com/$key"
}
```

### 5. 上传结果增强

在 `CosUploadResult` 中增加了 bucket 和 region 信息：
```kotlin
CosUploadResult(
    success = true,
    key = params.key,
    bucket = config.bucket,  // 新增
    region = config.region,  // 新增
    url = getPublicUrl(params.key, config)
)
```

## 架构优势

### 1. 单一数据源
- 所有COS相关配置都集中在 `CosConfig` 中
- 避免了多个状态变量的同步问题
- 提高了数据一致性

### 2. 类型安全
- 减少了字符串和数字类型的转换
- 利用Kotlin的空安全特性
- 编译时检查更严格

### 3. 扩展性
- 新的配置字段可以轻松添加到 `CosConfig`
- 转换逻辑集中在扩展函数中
- 支持多种认证方式的无缝切换

### 4. 可维护性
- 代码逻辑更清晰
- 减少了重复代码
- 错误处理更统一

## 兼容性

### 保持的接口
- 所有公共API接口保持不变
- 现有的调用代码无需修改
- 向后兼容所有功能

### 内部优化
- 仅优化了内部实现
- 提升了性能和稳定性
- 增强了错误处理能力

## 性能提升

### 1. 减少对象创建
- 统一使用 `CosConfig` 对象
- 减少了临时对象的创建
- 降低了GC压力

### 2. 智能缓存
- 更精确的过期时间计算
- 减少了不必要的API调用
- 提高了响应速度

### 3. 线程安全
- 保持了原有的线程安全机制
- 使用 `Mutex` 确保并发安全
- 原子操作保证状态一致性

## 使用示例

### 1. 使用临时密钥初始化
```kotlin
// API返回的token会自动转换为CosConfig
val response = apiService.getUploadToken()
val config = response.data.toCosConfig()
cosRepository.initCosService(config)
```

### 2. 使用静态密钥初始化
```kotlin
val config = CosConfig(
    region = "ap-beijing",
    bucket = "my-bucket",
    secretId = "your-secret-id",
    secretKey = "your-secret-key"
)
cosRepository.initCosService(config)
```

### 3. 文件上传
```kotlin
// 上传结果现在包含更多信息
val result = cosRepository.uploadFile(uploadParams)
println("Uploaded to bucket: ${result.bucket}, region: ${result.region}")
```

## 总结

本次重构通过引入统一的 `CosConfig` 配置管理，显著提升了代码的可维护性、类型安全性和性能。同时保持了完全的向后兼容性，为后续功能扩展奠定了良好的基础。

主要收益：
- ✅ 统一配置管理
- ✅ 减少状态变量
- ✅ 提升类型安全
- ✅ 增强扩展性
- ✅ 保持兼容性
- ✅ 提升性能
- ✅ 简化代码逻辑