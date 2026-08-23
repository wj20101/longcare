# 定位模块说明

## 业务边界

- 只有订单进行中时采集并实时上报位置。
- App 切到后台后，由 `location` 类型前台 Service 继续采集和上报。
- 单个定位点上传失败后直接丢弃；不落库、不排队、不补传。
- 订单结束成功、退出登录、Token 失效、账号切换、划掉任务或进程终止时停止。
- App 重启、设备重启或重新登录后不自动恢复旧订单定位。

## 核心组件

1. `LocationFacade`
   - 统一提供快速定位、新鲜定位、缓存定位和前台保活控制。
2. `LocationKeepAliveManager`
   - 以进程内 owner 和 generation 管理前台 Service，不持久化 desired state。
3. `LocationTrackingService`
   - 持有前台通知、高德后台模式和唯一持续定位 collector。
4. `LocationSampleStore`
   - 保存短时缓存并发布实时样本；网络较慢时仅保留一个最新待处理点。
5. `LocationReportingManager`
   - 校验当前会话样本并直接调用 `AddPostion`，失败不重试。
6. `LocationSessionLifecycleObserver`
   - 登出或账号切换时强制停止；登录时绝不恢复定位。

## 启停上报

```kotlin
trackingManager.startTracking(orderKey)
trackingManager.stopTracking()
```

订单结束必须在结束接口成功后调用 `stopTracking()`。接口失败时订单仍在进行中，定位继续。

## 单次业务定位

```kotlin
val location = locationFacade.getCurrentLocation()
val freshLocation = locationFacade.getFreshLocation()
```

单次定位使用独立高德客户端，不会创建第二个持续定位 collector，也不进入实时上报链路。

## Android 生命周期

- 前台 Service 必须从用户可见的订单流程中启动，并声明 `foregroundServiceType="location"`。
- Service 返回 `START_NOT_STICKY`，不要求系统在进程终止后重建。
- Service 使用 `stopWithTask=true`，显式停止和 `onDestroy()` 共用幂等 SDK 清理路径。
- 进程被硬终止时 Android 不保证调用 `onDestroy()`；不恢复的保证来自“没有任何持久队列或调度任务”。
