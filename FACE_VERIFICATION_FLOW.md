# 人脸验证业务流程文档

## 📋 业务需求

### 完整流程

```
1. 先检查本地是否存在人脸信息
   ├─ 有 → 使用本地信息验证 ✅
   └─ 无 → 2

2. 通过接口获取人脸信息
   ├─ 有 → 下载并保存到本地 → 使用验证 ✅
   └─ 无 → 3

3. 跳转到人脸捕获
   └─ 拍照 → 验证 → 上传接口 → 保存本地 ✅
```

## 🔄 详细流程图

### 场景1：首次使用（本地和服务器都没有）

```
用户点击"验证服务人员"
    ↓
检查本地缓存
    ├─ 无 ❌
    ↓
调用 getFace() 接口
    ├─ 返回空URL ❌
    ↓
跳转到人脸捕获页面 ✅
    ↓
用户拍照 → base64_1
    ↓
启动人脸验证（自带源比对）
    ├─ 验证成功 ✅
    ↓
上传到COS
    ├─ 上传成功 ✅
    ↓
调用 setFace() 接口
    ├─ 保存成功 ✅
    ↓
保存到本地 DataStore
    ├─ base64_1 ✅
    ↓
完成 ✅
```

### 场景2：正常使用（本地有缓存）

```
用户点击"验证服务人员"
    ↓
检查本地缓存
    ├─ 有：base64_1 ✅
    ↓
使用本地缓存进行验证
    ├─ 验证成功 ✅
    ↓
完成 ✅
```

### 场景3：卸载重装（本地无，服务器有）

```
用户点击"验证服务人员"
    ↓
检查本地缓存
    ├─ 无 ❌（卸载后清空）
    ↓
调用 getFace() 接口
    ├─ 返回URL ✅
    ↓
下载图片并转换为Base64
    ├─ base64_1 ✅
    ↓
立即保存到本地 DataStore
    ├─ base64_1 ✅
    ↓
使用下载的Base64进行验证
    ├─ 验证成功 ✅
    ↓
完成 ✅
```

### 场景4：重新设置人脸

```
用户手动选择"重新设置"
    ↓
跳转到人脸捕获页面
    ↓
用户拍照 → base64_2（新照片）
    ↓
启动人脸验证（自带源比对）
    ├─ 验证成功 ✅
    ↓
上传到COS
    ├─ 上传成功 ✅
    ↓
调用 setFace() 接口
    ├─ 更新成功 ✅
    ↓
更新本地 DataStore
    ├─ base64_2 ✅（覆盖旧的）
    ↓
完成 ✅
```

## 💻 代码实现

### 核心方法：verifyServicePerson

```kotlin
fun verifyServicePerson(context: Context) {
    viewModelScope.launch {
        val currentUser = getCurrentUser() ?: return@launch
        
        // 步骤1：检查本地缓存
        val cachedBase64 = readUserFaceBase64(currentUser.userId)
        
        if (!cachedBase64.isNullOrBlank()) {
            // ✅ 使用本地缓存验证
            startSelfProvidedFaceVerificationWithBase64(...)
        } else {
            // 步骤2：调用接口获取
            when (val faceResult = identificationRepository.getFace()) {
                is ApiResult.Success -> {
                    val url = faceResult.data.faceImgUrl
                    if (url.isBlank()) {
                        // 步骤3：跳转到人脸捕获
                        _navigateToFaceCapture.value = true
                    } else {
                        // 下载并保存到本地，然后验证
                        startSelfProvidedFaceVerificationAndCache(...)
                    }
                }
                // 错误处理...
            }
        }
    }
}
```

### 方法1：使用本地缓存验证

```kotlin
private fun startSelfProvidedFaceVerificationWithBase64(
    context: Context,
    name: String,
    idNo: String,
    orderNo: String,
    userId: String,
    sourcePhotoBase64: String
) {
    // 直接使用本地缓存的Base64进行验证
    // 无需再次保存，因为已经是从缓存读取的
}
```

### 方法2：下载并缓存后验证

```kotlin
private fun startSelfProvidedFaceVerificationAndCache(
    context: Context,
    name: String,
    idNo: String,
    orderNo: String,
    userId: String,
    sourcePhotoUrl: String
) {
    // 1. 下载图片
    val sourcePhotoBase64 = downloadAndConvertToBase64(sourcePhotoUrl)
    
    // 2. 立即保存到本地（在验证之前）
    writeUserFaceBase64(currentUser.userId, sourcePhotoBase64)
    
    // 3. 使用下载的Base64进行验证
    faceVerificationManager.startFaceVerification(...)
}
```

### 方法3：拍照上传并保存

```kotlin
private fun uploadAndSetFaceInfo(imageFile: File, base64Image: String) {
    // 1. 上传图片到 COS
    val uploadResult = cosRepository.uploadFile(uploadParams)
    
    // 2. 调用 setFace() 接口
    val setFaceResult = identificationRepository.setFace(...)
    
    // 3. 保存到本地 DataStore
    if (base64Image.isNotBlank()) {
        val faceKey = stringPreferencesKey(DataStoreKeys.FACE_BASE64_KEY_PREFIX + userId)
        userDataStore.edit { prefs ->
            prefs[faceKey] = base64Image  // 直接使用新图片
        }
    }
}
```

## 📊 数据流向

### 数据存储位置

```
1. 本地 DataStore
   - Key: "face_base64_{userId}"
   - Value: Base64编码的人脸图片
   - 用途: 快速验证，避免每次都下载

2. 服务器 COS
   - 存储: 原始图片文件
   - 返回: 图片URL
   - 用途: 跨设备同步

3. 服务器数据库
   - 字段: faceImgUrl
   - 值: COS图片URL
   - 用途: 持久化存储
```

### 数据同步策略

```
本地 DataStore ←→ 服务器
    ↓
优先使用本地
    ↓
本地无则从服务器下载
    ↓
下载后立即保存到本地
    ↓
保证数据一致性
```

## 🎯 关键设计点

### 1. 优先使用本地缓存

**原因**：
- ✅ 快速响应，无需网络请求
- ✅ 节省流量
- ✅ 离线可用

**实现**：
```kotlin
val cachedBase64 = readUserFaceBase64(currentUser.userId)
if (!cachedBase64.isNullOrBlank()) {
    // 直接使用本地缓存
}
```

### 2. 下载后立即保存

**原因**：
- ✅ 避免重复下载
- ✅ 提升后续验证速度
- ✅ 支持离线验证

**实现**：
```kotlin
val sourcePhotoBase64 = downloadAndConvertToBase64(sourcePhotoUrl)
// 立即保存到本地（在验证之前）
writeUserFaceBase64(currentUser.userId, sourcePhotoBase64)
```

### 3. 上传后保存到本地

**原因**：
- ✅ 保证数据一致性
- ✅ 后续验证使用本地缓存
- ✅ 避免再次下载

**实现**：
```kotlin
// 上传成功后
if (base64Image.isNotBlank()) {
    userDataStore.edit { prefs ->
        prefs[faceKey] = base64Image  // 直接使用新上传的
    }
}
```

### 4. 错误处理不覆盖数据

**原因**：
- ✅ 保护已有数据
- ✅ 避免误操作
- ✅ 用户可以重试

**实现**：
```kotlin
is ApiResult.Failure -> {
    // 提示错误但不跳转到人脸捕获
    toastHelper.showShort("获取人脸信息失败")
    // 不设置 _navigateToFaceCapture.value = true
}
```

## 🧪 测试用例

### 测试1：首次使用

```
前置条件：本地无缓存，服务器无数据
操作步骤：
1. 点击"验证服务人员"
2. 自动跳转到人脸捕获
3. 拍照
4. 验证成功
5. 上传成功

预期结果：
✅ 本地 DataStore 有数据
✅ 服务器有数据
✅ 身份验证状态更新
```

### 测试2：正常使用

```
前置条件：本地有缓存
操作步骤：
1. 点击"验证服务人员"
2. 自动使用本地缓存验证

预期结果：
✅ 无网络请求
✅ 验证成功
✅ 速度快
```

### 测试3：卸载重装

```
前置条件：本地无缓存（卸载清空），服务器有数据
操作步骤：
1. 点击"验证服务人员"
2. 自动从服务器下载
3. 保存到本地
4. 使用下载的数据验证

预期结果：
✅ 本地 DataStore 恢复数据
✅ 验证成功
✅ 后续使用本地缓存
```

### 测试4：重新设置

```
前置条件：本地和服务器都有数据
操作步骤：
1. 手动选择"重新设置"
2. 跳转到人脸捕获
3. 拍新照片
4. 验证成功
5. 上传成功

预期结果：
✅ 本地 DataStore 更新为新数据
✅ 服务器更新为新数据
✅ 后续验证使用新数据
```

### 测试5：网络异常

```
前置条件：本地无缓存，网络异常
操作步骤：
1. 点击"验证服务人员"
2. 接口调用失败

预期结果：
✅ 提示"网络异常"
✅ 不跳转到人脸捕获
✅ 用户可以重试
```

## 📝 注意事项

### 1. 数据一致性

- ✅ 上传成功后立即保存到本地
- ✅ 下载成功后立即保存到本地
- ✅ 始终使用最新的数据

### 2. 错误处理

- ✅ 网络异常不触发重新设置
- ✅ 下载失败不覆盖已有数据
- ✅ 提供清晰的错误提示

### 3. 用户体验

- ✅ 优先使用本地缓存，速度快
- ✅ 自动从服务器恢复数据
- ✅ 支持手动重新设置

### 4. 安全性

- ✅ Base64数据存储在用户特定的DataStore
- ✅ 每个用户独立存储
- ✅ 卸载后自动清除

## 🎉 总结

通过这个设计，我们实现了：

1. ✅ **快速验证** - 优先使用本地缓存
2. ✅ **自动恢复** - 卸载重装后自动从服务器恢复
3. ✅ **数据一致** - 本地和服务器数据始终同步
4. ✅ **用户友好** - 自动处理各种场景，无需用户干预
5. ✅ **健壮性强** - 完善的错误处理，不会丢失数据

这个流程完全符合你的业务需求，并且考虑了各种边界情况。
