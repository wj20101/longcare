# 人脸捕获功能测试指南

## 功能概述
现代化的人脸捕获功能已经实现，包含以下特性：

### 核心功能
- ✅ **CameraX 1.4.0+ 集成** - 使用最新的相机API
- ✅ **ML Kit 人脸检测** - 实时人脸检测和质量评估
- ✅ **智能跳帧优化** - 性能优化，避免过度处理
- ✅ **实时用户反馈** - 动态提示和质量指示器
- ✅ **内存优化** - 使用WeakReference防止内存泄漏
- ✅ **现代化UI** - Compose UI，Material 3设计
- ✅ **权限管理** - 优雅的相机权限处理

### 实现的组件
1. **FaceCaptureUiState.kt** - UI状态管理
2. **FaceCaptureViewModel.kt** - 业务逻辑和状态管理
3. **FaceCaptureAnalyzer.kt** - 图像分析和人脸检测
4. **FaceCaptureScreen.kt** - Compose UI界面
5. **FaceCaptureTestActivity.kt** - 测试Activity

## 测试方法

### 方法1: 通过URI启动
```bash
adb shell am start -W -a android.intent.action.VIEW -d "longcare://facecapture" com.ytone.longcare
```

### 方法2: 直接启动Activity
```bash
adb shell am start -n com.ytone.longcare/.features.facecapture.FaceCaptureTestActivity
```

### 方法3: 在代码中启动
```kotlin
// 在任何Activity或Fragment中
FaceCaptureTestLauncher.launch(context)
```

## 测试要点

### 1. 相机权限测试
- [ ] 首次启动时正确请求相机权限
- [ ] 权限被拒绝时显示友好的提示界面
- [ ] 权限授予后正常显示相机预览

### 2. 人脸检测测试
- [ ] 检测到人脸时显示绿色/黄色/红色边框（根据质量）
- [ ] 人脸质量评估正常工作
- [ ] 实时提示信息更新

### 3. 人脸捕获测试
- [ ] 高质量人脸自动捕获
- [ ] 最多捕获5张人脸照片
- [ ] 捕获的照片显示在底部缩略图列表

### 4. 用户交互测试
- [ ] 点击缩略图可以选择人脸
- [ ] 选择人脸后显示确认按钮
- [ ] 确认选择后正确返回结果
- [ ] 清空按钮正常工作

### 5. 性能测试
- [ ] 相机预览流畅，无明显卡顿
- [ ] 人脸检测响应及时
- [ ] 内存使用稳定，无泄漏

## 预期结果
- 相机预览正常显示
- 人脸检测实时工作
- UI响应流畅
- 功能完整可用

## 故障排除

### 常见问题
1. **相机权限问题** - 确保在设置中授予了相机权限
2. **人脸检测不工作** - 确保光线充足，人脸清晰可见
3. **性能问题** - 检查设备性能，确保有足够的内存

### 调试信息
- 查看Logcat输出中的人脸检测日志
- 检查相机初始化状态
- 监控内存使用情况

## 构建状态
✅ **构建成功** - 所有依赖正确配置，代码编译通过