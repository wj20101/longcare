# 架构优化 Walkthrough

## Phase 3: 图片管理改造 ✅

### ImageRepository 创建
- 位置: [ImageRepository.kt](file:///Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/data/repository/ImageRepository.kt)
- 功能: 封装`OrderImageDao`，提供图片CRUD、状态管理和统计功能

### PhotoProcessingViewModel 重构
- 位置: [PhotoProcessingViewModel.kt](file:///Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/photoupload/viewmodel/PhotoProcessingViewModel.kt)
- 变更:
  - ✅ 注入 `ImageRepository`
  - ✅ 添加 `setOrderId()` 方法初始化订单ID
  - ✅ 添加 `loadImagesFromRoom()` 从数据库加载图片
  - ✅ 添加类型转换方法 (ImageType↔ImageTaskType, ImageUploadStatus→ImageTaskStatus)
  - ✅ 修改 `addImagesToProcess()` 同时写入Room
  - ✅ 修改 `updateTaskUploadStatus()` 同步上传成功状态到Room
  - ✅ 修改 `removeTask()` / `clearAllTasks()` 同步删除到Room

---

## Phase 4: 导航改造 ✅

### OrderNavParams 模型
- 位置: [OrderNavParams.kt](file:///Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/navigation/OrderNavParams.kt)
- 包含: `orderId: Long`, `planId: Int = 0`

### 改造范围
| 类型 | 数量 |
|------|------|
| Route 定义 | 11个 |
| 导航扩展函数 | 11个 |
| Screen 签名 | 12个 |
| 调用位置修复 | ~30处 |

---

## Phase 5: 定位持续监听模式 ✅

### LocationStateManager 创建
- 位置: [LocationStateManager.kt](file:///Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationStateManager.kt)
- 功能: 增强版定位状态管理（追踪状态、位置信息、统计数据、错误跟踪）

### ContinuousAmapLocationManager 创建
- 位置: [ContinuousAmapLocationManager.kt](file:///Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt)
- 功能: 持续定位模式，提供Flow形式的位置更新

### LocationTrackingService 改造
- 位置: [LocationTrackingService.kt](file:///Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/location/service/LocationTrackingService.kt)
- 变更:
  - ✅ 注入 `LocationStateManager`
  - ✅ 定位成功时调用 `recordLocationSuccess()`
  - ✅ 定位失败时调用 `recordLocationFailure()`

---

## Phase 6: 删除旧Manager依赖 ✅

### 删除的文件（6个）
| 文件 | 替代方案 |
|------|----------|
| `ServiceTimeManager.kt` | `UnifiedOrderRepository.startLocalService()` |
| `SelectedProjectsManager.kt` | `UnifiedOrderRepository.updateSelectedProjects()` |
| `UploadedImagesManager.kt` | `ImageRepository` |
| `FaceVerificationStatusManager.kt` | `UnifiedOrderRepository.updateFaceVerification()` |
| `OrderInfoRequestModelNavType.kt` | 已被`OrderNavParams`替代 |
| `SharedOrderRepository.kt` | 整合到`UnifiedOrderRepository`（内存缓存+Room） |

### UnifiedOrderRepository扩展
新增方法支持`ServiceOrderInfoModel`缓存：
- `getOrderInfo(orderId, forceRefresh)` - 内存缓存→API→同步Room
- `getCachedOrderInfo(orderId)` - 获取缓存
- `updateCachedOrderInfo(orderId, orderInfo)` - 更新缓存
- `clearOrderInfoCache(orderId)` - 清除缓存
- `preloadOrderInfo(orderId)` - 预加载

### 迁移的ViewModel
- `SharedOrderDetailViewModel`
- `EndServiceSelectionViewModel`
- `IdentificationViewModel`
- `PhotoProcessingViewModel`

---

## 编译验证
✅ `./gradlew :app:compileDebugKotlin` 通过，无错误

---

## 并发安全审查 ✅

### 发现的问题
`UnifiedOrderRepository`中的内存缓存使用了非线程安全的`mutableMapOf`和`mutableSetOf`，以及不正确的`while`循环等待机制。

### 修复方案
| 问题 | 修复 |
|------|------|
| `mutableMapOf` 非线程安全 | 改用`ConcurrentHashMap` |
| `mutableSetOf` 非线程安全 | 改用每个orderId一个`Mutex`锁 |
| `while`循环等待 | 使用`Mutex.withLock`实现协程挂起等待 |
| 竞态条件 | 双重检查锁定模式（DCL） |

### 结论
- `UnifiedOrderRepository` ✅ 已修复
- `ImageRepository` ✅ 仅封装Room DAO，天然线程安全
- `NavigationHelper` ✅ 无状态，只调用suspend方法

---

## Phase 8: 统一OrderKey全链路架构 ✅

### 核心变更
- **ViewModel层**：公开方法统一使用`OrderKey`参数，不再接受`orderId: Long`。
- **Screen层**：统一在入口处调用`toOrderKey()`转换。
- **数据一致性**：确保全链路持有完整的`OrderKey`（包含`orderId`和`planId`）。

### 改造的文件
| 文件 | 改动 |
|------|------|
| `UnifiedOrderRepository` | 所有方法参数统一为`OrderKey` |
| `ImageRepository` | 所有方法参数统一为`OrderKey` |
| [PhotoProcessingViewModel.kt](file:///Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/photoupload/viewmodel/PhotoProcessingViewModel.kt) | `setOrderId(Long)` → `setOrderKey(OrderKey)`，内部状态使用`OrderKey` |
| [ServiceCountdownViewModel.kt](file:///Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/vm/ServiceCountdownViewModel.kt) | `startOrderStatePolling(Long)` → `(OrderKey)`，修复5处`calculateCountdownState`调用 |
| [OrderDetailViewModel.kt](file:///Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/shared/vm/OrderDetailViewModel.kt) | `getOrderInfo(Long, Int)` → `getOrderInfo(OrderKey)` |
| `NavigationHelper.kt` | 内部调用改为传递`OrderKey` |

### 验证
✅ 编译通过，所有Screen的调用已修复。
