# COS 动态密钥使用指南

## 概述

根据腾讯云官方文档的建议，我们已经将 COS SDK 的初始化方式调整为使用临时密钥回调方案，以提高安全性和可靠性。

## 主要改进

### 1. 动态密钥提供者

实现了 `DynamicCredentialProvider` 类，该类实现了 `QCloudCredentialProvider` 接口：

- **自动刷新**：当密钥即将过期时（提前5分钟），自动获取新的临时密钥
- **缓存机制**：缓存当前有效的密钥信息，避免频繁请求
- **错误处理**：提供完善的错误处理和日志记录

### 2. 初始化方法调整

#### 推荐方式：使用临时密钥

```kotlin
// 方式1：通过 token 初始化（推荐）
val tokenResult = cosRepository.refreshUploadToken()
cosRepository.initCosServiceWithToken(tokenResult)

// 方式2：通过 CosCredentials 初始化
val credentials = CosCredentials(
    tmpSecretId = "临时密钥ID",
    tmpSecretKey = "临时密钥Key", 
    sessionToken = "会话令牌",
    expiredTime = expiredTime,
    startTime = startTime
)
cosRepository.initCosServiceWithCredentials(credentials, region, bucket)
```

#### 测试环境：使用固定密钥

```kotlin
// 仅用于测试环境（不推荐生产环境使用）
val config = CosConfig(
    secretId = "固定密钥ID",
    secretKey = "固定密钥Key",
    region = "地域",
    bucket = "存储桶"
)
cosRepository.initCosService(config)
```

## 安全优势

1. **临时密钥**：使用有时效性的临时密钥，降低密钥泄露风险
2. **自动刷新**：SDK 自动处理密钥刷新，无需手动管理
3. **最小权限**：临时密钥可以配置最小权限范围
4. **审计追踪**：每次密钥获取都有完整的日志记录

## 注意事项

1. **网络环境**：密钥刷新需要网络请求，确保网络连接稳定
2. **线程安全**：密钥提供者内部使用 `runBlocking`，在主线程中使用时需要注意
3. **错误处理**：当密钥获取失败时，上传操作会失败，需要适当的重试机制
4. **缓存策略**：密钥会被缓存直到过期前5分钟，期间不会重复请求

## 迁移指南

如果你之前使用的是静态密钥方案，建议按以下步骤迁移：

1. **更新初始化代码**：将 `initCosService` 替换为 `initCosServiceWithCredentials` 或通过 token 初始化
2. **配置临时密钥服务**：确保后端服务能够提供临时密钥
3. **测试验证**：在测试环境验证密钥自动刷新功能
4. **监控日志**：关注密钥刷新相关的日志，确保正常运行

## 参考文档

- [腾讯云 COS Android SDK 快速入门](https://cloud.tencent.com/document/product/436/12159)
- [移动应用直传实践](https://cloud.tencent.com/document/product/436/9068)