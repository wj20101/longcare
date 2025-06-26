# CosRepositoryImpl 重构说明

## 重构概述

本次重构对 `CosRepositoryImpl` 类进行了全面的代码优化和架构改进，解决了原有代码中存在的多个问题，提升了代码质量、可维护性和性能。

## 主要问题及解决方案

### 1. 代码重复问题

**原问题：**
- `uploadFile` 和 `uploadFileWithProgress` 方法存在大量重复代码
- 错误处理逻辑在多个方法中重复
- 重试机制代码重复

**解决方案：**
- 提取统一的 `uploadFileInternal` 方法处理核心上传逻辑
- 实现 `executeWithRetry` 通用重试机制
- 通过参数控制是否启用进度回调

### 2. 线程安全问题

**原问题：**
- 使用 `synchronized` 和普通对象锁，性能较差
- 多个可变状态缺乏统一的并发控制

**解决方案：**
- 使用 `Mutex` 替代 `synchronized`，提供更好的协程支持
- 使用 `AtomicReference` 管理服务实例
- 实现 `TokenCache` 类统一管理token状态

### 3. Flow 实现问题

**原问题：**
- `uploadFileFlow` 中进度回调无法正确 emit
- Flow 构建方式不当

**解决方案：**
- 使用 `callbackFlow` 替代 `flow`
- 通过 `trySend` 正确发送进度更新
- 添加 `awaitClose` 确保资源清理

### 4. 架构设计问题

**原问题：**
- 职责不清晰，单一类承担过多功能
- 缺乏清晰的分层结构

**解决方案：**
- 提取 `TokenCache` 类专门管理token缓存
- 重新设计 `DynamicCredentialProvider` 内部类
- 明确分离初始化、上传、管理等职责

## 重构后的架构特点

### 1. 清晰的类结构

```kotlin
CosRepositoryImpl
├── TokenCache                    // Token缓存管理
├── DynamicCredentialProvider     // 动态密钥提供者
├── 公共接口实现                   // Repository接口实现
└── 私有辅助方法                   // 内部工具方法
```

### 2. 线程安全设计

- **Mutex**: 保护关键资源的并发访问
- **AtomicReference**: 管理服务实例的原子操作
- **@Volatile**: 确保token缓存的可见性
- **双重检查锁定**: 优化性能的同时保证线程安全

### 3. 统一的错误处理

```kotlin
private suspend fun <T> executeWithRetry(operation: suspend () -> T): T {
    return try {
        operation()
    } catch (e: Exception) {
        Log.w(TAG, "Operation failed, attempting retry after cache clear", e)
        try {
            clearCache()
            operation()
        } catch (retryException: Exception) {
            Log.e(TAG, "Operation failed after retry", retryException)
            throw retryException
        }
    }
}
```

### 4. 优化的Flow实现

```kotlin
override fun uploadFileFlow(params: UploadParams): Flow<Result<UploadProgress>> {
    return callbackFlow {
        // 使用callbackFlow确保进度回调能正确emit
        request.setProgressListener { complete, target ->
            val progress = UploadProgress(complete, target)
            trySend(Result.success(progress))
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)
}
```

## 性能优化

### 1. 缓存优化

- **智能token刷新**: 提前5分钟自动刷新，避免过期
- **服务实例复用**: 避免重复创建COS服务实例
- **双重检查**: 减少不必要的锁竞争

### 2. 内存优化

- **弱引用管理**: 合理管理对象生命周期
- **及时清理**: 错误时主动清理缓存
- **原子操作**: 减少对象创建开销

### 3. 并发优化

- **协程友好**: 使用Mutex替代synchronized
- **非阻塞操作**: 优化IO操作的并发性能
- **流式处理**: 改进文件上传的流式处理

## 使用指南

### 1. 基本文件上传

```kotlin
// 简单上传
val result = cosRepository.uploadFile(uploadParams)

// 带进度的上传
val result = cosRepository.uploadFileWithProgress(uploadParams) { progress ->
    // 处理进度更新
    println("Progress: ${progress.bytesTransferred}/${progress.totalBytes}")
}
```

### 2. 流式上传（推荐用于大文件）

```kotlin
cosRepository.uploadFileFlow(uploadParams)
    .collect { result ->
        result.onSuccess { progress ->
            // 处理进度
        }.onFailure { error ->
            // 处理错误
        }
    }
```

### 3. 初始化方式

```kotlin
// 生产环境：使用临时密钥（推荐）
cosRepository.initCosServiceWithCredentials(credentials, region, bucket)

// 测试环境：使用固定密钥
cosRepository.initCosService(config)
```

## 安全性改进

### 1. 密钥管理

- **自动刷新**: 临时密钥自动刷新机制
- **安全存储**: 内存中安全管理密钥信息
- **过期检查**: 主动检查并更新过期密钥

### 2. 错误处理

- **敏感信息保护**: 错误日志中不暴露敏感信息
- **优雅降级**: 网络错误时的优雅处理
- **重试机制**: 智能重试避免频繁失败

## 兼容性说明

### 保持的接口

- 所有公共接口保持不变
- 方法签名完全兼容
- 行为语义保持一致

### 内部变化

- 重构了内部实现逻辑
- 优化了性能和线程安全
- 改进了错误处理机制

## 测试建议

### 1. 功能测试

- 验证文件上传功能
- 测试进度回调机制
- 检查错误处理逻辑

### 2. 性能测试

- 并发上传测试
- 大文件上传测试
- 内存使用监控

### 3. 稳定性测试

- 网络异常场景
- 密钥过期处理
- 长时间运行稳定性

## 后续优化建议

### 1. 监控和日志

- 添加更详细的性能监控
- 实现结构化日志记录
- 添加关键指标统计

### 2. 功能扩展

- 支持断点续传
- 添加文件压缩选项
- 实现批量上传功能

### 3. 配置优化

- 支持动态配置调整
- 添加更多自定义选项
- 实现配置热更新

---

**重构完成时间**: 2024年12月
**重构版本**: v2.0
**兼容性**: 完全向后兼容