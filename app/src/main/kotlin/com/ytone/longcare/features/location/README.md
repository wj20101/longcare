# 定位系统使用指南

## 概述

本项目集成了双定位方案：系统定位和高德定位，支持灵活切换和自动回退机制。

## 架构设计

### 核心组件

1. **LocationProvider接口** - 定位提供者抽象接口
2. **SystemLocationProvider** - 系统定位实现
3. **AmapLocationProvider** - 高德定位实现
4. **CompositeLocationProvider** - 复合定位提供者，支持策略切换
5. **AmapLocationManager** - 高德定位管理器
6. **LocationTrackingService** - 定位跟踪服务

### 定位策略

- **SYSTEM**: 仅使用系统定位（GPS + 网络定位）
- **AMAP**: 仅使用高德定位
- **AUTO**: 自动模式（推荐），优先高德，失败时回退到系统定位

## 配置说明

### 1. 高德API Key配置

在 `app/build.gradle.kts` 中配置你的高德API Key：

```kotlin
buildConfigField("String", "AMAP_API_KEY", "\"your_actual_amap_api_key_here\"")
```

### 2. 权限配置

已在 `AndroidManifest.xml` 中添加了必要权限：

```xml
<!-- 基础定位权限 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- 高德地图定位所需权限 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.ACCESS_LOCATION_EXTRA_COMMANDS" />
```

## 使用方法

### 1. 在Service中使用

`LocationTrackingService` 已经集成了新的定位系统：

```kotlin
// 设置定位策略
service.setLocationStrategy(LocationStrategy.AUTO)

// 获取当前策略
val currentStrategy = service.getCurrentLocationStrategy()
```

### 2. 直接使用CompositeLocationProvider

```kotlin
@Inject
lateinit var compositeLocationProvider: CompositeLocationProvider

// 设置策略
compositeLocationProvider.setLocationStrategy(LocationStrategy.AMAP)

// 获取位置
val location = compositeLocationProvider.getCurrentLocation()
```

### 3. 在UI中切换策略

使用提供的 `LocationStrategySelector` 组件：

```kotlin
LocationStrategySelector(
    currentStrategy = currentStrategy,
    onStrategyChanged = { newStrategy ->
        // 更新策略
        viewModel.updateLocationStrategy(newStrategy)
    }
)
```

## 优势特性

### 1. 灵活切换
- 支持运行时动态切换定位策略
- 无需重启应用或服务

### 2. 自动回退
- AUTO模式下，高德定位失败时自动使用系统定位
- 确保定位成功率

### 3. 统一接口
- 所有定位提供者实现相同接口
- 便于扩展和维护

### 4. 资源管理
- 自动管理定位客户端生命周期
- 防止内存泄漏

## 注意事项

### 1. API Key配置
- 确保在build.gradle.kts中配置了正确的高德API Key
- 不同环境（debug/release）可以配置不同的Key

### 2. 权限处理
- 应用启动时需要请求定位权限
- 高德定位可能需要额外的存储权限

### 3. 网络依赖
- 高德定位需要网络连接
- 建议在AUTO模式下使用，确保离线时也能定位

### 4. 性能考虑
- 高德定位通常更快更准确
- 但会消耗更多网络流量
- 根据应用场景选择合适策略

## 故障排除

### 1. 高德定位失败
- 检查API Key是否正确配置
- 确认网络连接正常
- 查看日志中的错误信息

### 2. 系统定位失败
- 检查GPS是否开启
- 确认定位权限已授予
- 尝试在室外环境测试

### 3. 服务无法启动
- 检查前台服务权限
- 确认所有依赖注入正确配置
- 查看Service的生命周期日志

## 扩展开发

### 添加新的定位提供者

1. 实现 `LocationProvider` 接口
2. 在 `CompositeLocationProvider` 中添加新策略
3. 更新 `LocationStrategy` 枚举
4. 在UI组件中添加新选项

### 自定义定位参数

可以在各个Provider中调整定位参数：
- 定位精度要求
- 超时时间
- 定位间隔
- 是否需要地址信息