# 定位模块说明（解耦版）

## 目标

- 定位能力独立：负责采集、缓存、保活。
- 上报能力独立：负责从定位流取点并上报。
- 业务模块只依赖统一门面，不直接耦合具体定位实现。

## 核心组件

1. `LocationFacade`
   - 统一定位入口：实时流、单次定位、缓存定位、保活 acquire/release。
2. `LocationKeepAliveManager`
   - 基于 owner 引用计数管理前台保活服务。
3. `LocationTrackingService`
   - 纯保活服务，只处理前台通知和高德后台定位绑定。
4. `LocationReportingManager`
   - 上报任务管理器：入队、本地重试、调用 `addPosition`。
5. `ContinuousAmapLocationManager`
   - 高德持续定位引擎（Flow）。
6. `SystemLocationProvider`
   - 系统定位回退实现。

## 调用方式

### 1. 业务取定位（推荐）

```kotlin
@Inject
lateinit var locationFacade: LocationFacade

val location = locationFacade.getCurrentLocation()
```

`getCurrentLocation()` 策略：
- 先用有效缓存
- 再尝试高德定位
- 高德失败时回退系统定位

### 2. 启停上报

通过 `LocationTrackingManager`（兼容入口）或直接通过 `LocationReportingManager`：

```kotlin
trackingManager.startTracking(request)
trackingManager.stopTracking()
```

### 3. UI 会话保活

```kotlin
trackingManager.startLocationSession()
trackingManager.stopLocationSession()
```

## 离线补偿

- 上报前先写入 `order_locations`（`PENDING`）。
- 上传失败标记 `FAILED`，后续持续重试。
- 上传成功标记 `SUCCESS`。
- 定期清理历史成功记录。

## 注意事项

- 运行时仍需定位权限与系统定位开关。
- `LocationTrackingService` 当前只支持：
  - `ACTION_ACQUIRE_KEEP_ALIVE`
  - `ACTION_RELEASE_KEEP_ALIVE`
