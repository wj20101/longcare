# 定位模块说明

最后核对：2026-09-08

## 业务边界

- 只有业务会话确认订单执行中时才启动订单持续定位与上报；单次定位不受订单限制。
- App 切到后台后，由 `location` 类型前台 Service 继续采集和上报。
- 单个定位点上传失败后直接丢弃；不落库、不排队、不补传。
- 订单结束成功、退出登录、Token 失效、账号切换、划掉任务或进程终止时停止。
- App 重启、设备重启或重新登录后不自动恢复旧订单定位。
- 用户重新进入服务订单时创建新会话并重新确认服务中，确认后恢复持续上报；
  不依赖旧进程状态，不补传旧定位，订单已结束则不启动。

## 核心组件

1. `LocationFacade`
   - 统一提供快速定位、新鲜定位、缓存定位和前台保活控制。
2. `LocationKeepAliveManager`
   - 以进程内 owner 和 generation 管理前台 Service，不持久化 desired state。
3. `LocationTrackingService`
   - 持有前台通知、高德持续定位 collector 和唯一 `AddPostion` 调用点，不查询订单状态。
4. `LocationSampleStore`
   - 保存短时缓存并发布实时样本；上报消费端使用 conflate，仅保留一个最新待处理点。
5. `LocationReportingManager`
   - 实现业务层 `ServiceOrderLifecycle`，统一同步订单状态，驱动上报会话启停，不执行上传。
   - 状态同步不依赖页面存活；倒计时页面仅订阅同一份状态，不重复请求。
6. `LocationSessionLifecycleObserver`
   - 登出或账号切换时强制停止；登录时绝不恢复定位。

## 启停上报

```kotlin
trackingManager.startTracking(orderKey)
trackingManager.stopTracking()
```

结束接口成功后，业务执行器立即调用 `ServiceOrderLifecycle.onOrderEnded(orderId)`，
先使对应会话失效并取消在途协程，再停止 Android Service，最后执行 UI/资源清理。
结束接口失败、仅打开结束确认流程、倒计时归零均不会结束真实业务会话。

正式开始接口成功是服务中的直接依据；随后通过定位权限入口启动 Service，
不必等待额外状态查询成功。重新打开订单、进程重建时则先重新确认状态。
正常业务状态同步每 5 秒一次，与上传结果无关。
初始状态不可用时不启动定位，按 5/10/20/40/60 秒退避持续复核，网络恢复后可继续。
已确认服务中时，查询异常保留最后已确认状态，不因异常次数永久停报；
查询确认非服务中则立即停止，不等待用户确认弹窗。

`AddPostion` 没有“非服务中”专用错误码。上传结果仅记录诊断，不触发状态反查、
不修改订单状态、不弹 Toast、不因业务失败停报，不重试同一个定位点。
既有全局登录失效安全处理保持不变。后台远程状态变化存在同步延迟，
已发出的请求无法撤回，服务端仍需校验订单状态。

## 单次业务定位

```kotlin
val location = locationFacade.getCurrentLocation()
val freshLocation = locationFacade.getFreshLocation()
```

单次定位使用独立高德客户端，不会创建第二个持续定位 collector，也不进入实时上报链路。

## Android 生命周期

- 前台 Service 必须从用户可见的订单流程中启动，并声明 `foregroundServiceType="location"`。
- 状态确认完成时若系统不允许启动 Service，在用户返回订单页面时显式重试；不使用后台重启调度。
- Service 返回 `START_NOT_STICKY`，不要求系统在进程终止后重建。
- Service 使用 `stopWithTask=true`，显式停止和 `onDestroy()` 共用幂等 SDK 清理路径。
- 进程被硬终止时 Android 不保证调用 `onDestroy()`；不恢复的保证来自“没有任何持久队列或调度任务”。
