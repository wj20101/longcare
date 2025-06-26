# 腾讯云COS存储服务集成指南

本文档介绍了如何在Android项目中集成和使用腾讯云COS（Cloud Object Storage）存储服务。

## 📋 目录

- [集成概述](#集成概述)
- [依赖配置](#依赖配置)
- [架构设计](#架构设计)
- [核心组件](#核心组件)
- [使用示例](#使用示例)
- [最佳实践](#最佳实践)
- [注意事项](#注意事项)

## 🎯 集成概述

本项目采用Clean Architecture架构，通过Repository模式和UseCase模式实现了COS存储服务的解耦设计，便于管理和维护。

### 主要特性

- ✅ 支持文件上传、下载、删除
- ✅ 支持上传进度监听
- ✅ 支持批量文件操作
- ✅ 支持临时密钥管理
- ✅ 完整的错误处理
- ✅ 类型安全的API设计
- ✅ 协程支持
- ✅ Flow响应式编程

## 📦 依赖配置

### 1. 版本配置

在 `gradle/libs.versions.toml` 中已添加：

```toml
# Tencent COS
tencentCos = "5.9.46"

# Libraries
tencent-cos-android = { group = "com.qcloud.cos", name = "cos-android", version.ref = "tencentCos" }
```

### 2. 模块依赖

在 `app/build.gradle.kts` 中已添加：

```kotlin
// 腾讯云COS
implementation(libs.tencent.cos.android)
```

### 3. 权限配置

在 `AndroidManifest.xml` 中添加必要权限：

```xml
<!-- 网络权限 -->
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

<!-- 存储权限 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
```

## 🏗️ 架构设计

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Presentation  │    │     Domain      │    │      Data       │
│     Layer       │    │     Layer       │    │     Layer       │
├─────────────────┤    ├─────────────────┤    ├─────────────────┤
│ • UI Components │───▶│ • Use Cases     │───▶│ • Repository    │
│ • ViewModels    │    │ • Interfaces    │    │   Implementation│
│ • Activities    │    │ • Models        │    │ • Data Sources  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🧩 核心组件

### 1. 数据模型 (`data/cos/model/`)

- **CosModels.kt**: 定义COS相关的数据类
  - `CosUploadResult`: 上传结果
  - `CosConfig`: 配置信息
  - `CosCredentials`: 临时密钥
  - `UploadProgress`: 上传进度
  - `UploadParams`: 上传参数

### 2. Repository接口 (`domain/cos/repository/`)

- **CosRepository.kt**: 定义COS服务接口
  - 文件上传/下载/删除
  - 进度监听
  - 文件存在性检查

### 3. Repository实现 (`data/cos/repository/`)

- **CosRepositoryImpl.kt**: COS服务具体实现
  - 使用腾讯云COS SDK
  - 完整的错误处理
  - 协程支持

### 4. UseCase层 (`domain/cos/usecase/`)

- **CosUseCases.kt**: 业务逻辑封装
  - `InitCosServiceUseCase`: 初始化服务
  - `UploadFileUseCase`: 文件上传
  - `DeleteFileUseCase`: 文件删除
  - `CosServiceManagerUseCase`: 统一管理

### 5. 配置管理 (`data/cos/config/`)

- **CosConfigManager.kt**: 配置管理器
  - 临时密钥管理
  - 配置验证
  - 过期检查

### 6. 工具类 (`common/utils/`)

- **CosUtils.kt**: 便捷工具方法
  - 文件类型检测
  - 键名生成
  - 路径处理

### 7. 依赖注入 (`di/`)

- **CosModule.kt**: Hilt依赖注入配置

### 8. 使用示例 (`features/shared/cos/`)

- **CosServiceExample.kt**: 完整的使用示例

## 💡 使用示例

### 1. 基本初始化

```kotlin
@Inject
lateinit var cosServiceExample: CosServiceExample

// 初始化COS服务
val success = cosServiceExample.initializeCosService(
    region = "ap-beijing",
    bucket = "your-bucket-name"
)
```

### 2. 上传单个文件

```kotlin
// 简单上传
val result = cosServiceExample.uploadFile(
    filePath = "/path/to/file.jpg",
    category = "avatar",
    userId = "user123"
)

if (result.success) {
    println("上传成功: ${result.url}")
} else {
    println("上传失败: ${result.errorMessage}")
}
```

### 3. 带进度的文件上传

```kotlin
// 带进度监听的上传
val result = cosServiceExample.uploadFileWithProgress(
    filePath = "/path/to/file.jpg",
    category = "document",
    userId = "user123"
) { progress ->
    println("上传进度: ${progress.progressPercent}%")
}
```

### 4. 使用Flow进行上传

```kotlin
// 使用Flow监听上传进度
cosServiceExample.uploadFileFlow(
    filePath = "/path/to/file.jpg",
    category = "image",
    userId = "user123"
).collect { result ->
    result.onSuccess { progress ->
        println("进度: ${progress.progressPercent}%")
    }.onFailure { error ->
        println("错误: ${error.message}")
    }
}
```

### 5. 从Uri上传文件

```kotlin
// 从Uri上传（如相册选择的图片）
val result = cosServiceExample.uploadFileFromUri(
    uri = selectedImageUri,
    category = "photo",
    userId = "user123"
)
```

### 6. 批量上传文件

```kotlin
// 批量上传
val filePaths = listOf("/path/to/file1.jpg", "/path/to/file2.jpg")
val results = cosServiceExample.uploadMultipleFiles(
    filePaths = filePaths,
    category = "gallery",
    userId = "user123"
) { key, progress ->
    println("文件 $key 上传进度: ${progress.progressPercent}%")
}
```

### 7. 文件管理操作

```kotlin
// 检查文件是否存在
val exists = cosServiceExample.fileExists("users/user123/avatar/image.jpg")

// 获取下载链接
val downloadUrl = cosServiceExample.getDownloadUrl(
    key = "users/user123/avatar/image.jpg",
    expireTimeInSeconds = 3600 // 1小时
)

// 删除文件
val deleted = cosServiceExample.deleteFile("users/user123/avatar/old_image.jpg")
```

## 🔧 在ViewModel中使用

```kotlin
class PhotoUploadViewModel @Inject constructor(
    private val cosServiceExample: CosServiceExample
) : ViewModel() {
    
    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress = _uploadProgress.asStateFlow()
    
    private val _uploadResult = MutableStateFlow<CosUploadResult?>(null)
    val uploadResult = _uploadResult.asStateFlow()
    
    fun uploadPhoto(uri: Uri, userId: String) {
        viewModelScope.launch {
            try {
                val result = cosServiceExample.uploadFileFromUri(
                    uri = uri,
                    category = "photos",
                    userId = userId
                )
                _uploadResult.value = result
            } catch (e: Exception) {
                _uploadResult.value = CosUploadResult(
                    success = false,
                    errorMessage = e.message
                )
            }
        }
    }
    
    fun uploadPhotoWithProgress(filePath: String, userId: String) {
        viewModelScope.launch {
            val result = cosServiceExample.uploadFileWithProgress(
                filePath = filePath,
                category = "photos",
                userId = userId
            ) { progress ->
                _uploadProgress.value = progress.progressPercent
            }
            _uploadResult.value = result
        }
    }
}
```

## 📋 最佳实践

### 1. 临时密钥管理

- ✅ 使用临时密钥而非永久密钥
- ✅ 定期检查密钥过期时间
- ✅ 实现自动刷新机制

### 2. 文件命名规范

```kotlin
// 推荐的文件路径结构
"users/{userId}/{category}/{timestamp}_{uuid}.{extension}"

// 示例
"users/user123/avatar/20241201_120000_abc12345.jpg"
"users/user123/documents/20241201_120000_def67890.pdf"
```

### 3. 错误处理

```kotlin
try {
    val result = cosServiceExample.uploadFile(params)
    if (result.success) {
        // 处理成功情况
    } else {
        // 处理业务失败
        Log.e(TAG, "Upload failed: ${result.errorMessage}")
    }
} catch (e: Exception) {
    // 处理异常情况
    Log.e(TAG, "Upload error", e)
}
```

### 4. 内存管理

```kotlin
// 及时清理临时文件
CosUtils.cleanTempFiles(context)

// 大文件上传时使用流式处理
cosServiceExample.uploadFileFlow(params)
    .flowOn(Dispatchers.IO)
    .collect { /* 处理进度 */ }
```

## ⚠️ 注意事项

### 1. 安全性

- 🔒 **永远不要在客户端硬编码永久密钥**
- 🔒 使用临时密钥，从服务器动态获取
- 🔒 设置合理的权限策略

### 2. 性能优化

- 📱 大文件上传使用分片上传
- 📱 合理设置超时时间
- 📱 避免在主线程进行网络操作

### 3. 网络处理

- 🌐 处理网络异常和重试机制
- 🌐 考虑弱网环境下的用户体验
- 🌐 实现断点续传（如需要）

### 4. 存储管理

- 💾 定期清理临时文件
- 💾 监控存储空间使用
- 💾 实现文件生命周期管理

## 🔗 相关链接

- [腾讯云COS Android SDK文档](https://cloud.tencent.com/document/product/436/12159)
- [COS API文档](https://cloud.tencent.com/document/product/436/7751)
- [临时密钥生成指南](https://cloud.tencent.com/document/product/436/14048)

## 📝 TODO

- [ ] 实现服务器端临时密钥获取API
- [ ] 添加断点续传功能
- [ ] 实现图片压缩和缩略图生成
- [ ] 添加上传队列管理
- [ ] 实现文件加密功能

---

**注意**: 在生产环境使用前，请确保：
1. 实现服务器端临时密钥获取接口
2. 配置正确的存储桶和地域信息
3. 设置合适的权限策略
4. 进行充分的测试