# Repository层统一使用OrderKey方案

## 目标
将所有使用`orderId: Long`参数的Repository方法统一改为`orderKey: OrderKey`，确保整个数据层使用统一的订单标识符模型。

---

## 改造范围

### UnifiedOrderRepository (~15个方法)

| 方法 | 当前 | 改为 |
|------|------|------|
| `observeOrderWithDetails` | orderId: Long | orderKey: OrderKey |
| `observeSelectedProjects` | orderId: Long | orderKey: OrderKey |
| `getOrderDetails` | orderId: Long | orderKey: OrderKey |
| `refreshOrderFromApi` | orderId: Long | orderKey: OrderKey |
| `updateProjectSelection` | orderId: Long | orderKey: OrderKey |
| `updateSelectedProjects` | orderId: Long | orderKey: OrderKey |
| `getSelectedProjectIds` | orderId: Long | orderKey: OrderKey |
| `startLocalService` | orderId: Long | orderKey: OrderKey |
| `endLocalService` | orderId: Long | orderKey: OrderKey |
| `updateFaceVerification` | orderId: Long | orderKey: OrderKey |
| `getLocalState` | orderId: Long | orderKey: OrderKey |
| `deleteOrder` | orderId: Long | orderKey: OrderKey |

---

### ImageRepository (~17个方法)

| 方法 | 当前 | 改为 |
|------|------|------|
| `getImagesByOrderId` | orderId: Long | orderKey: OrderKey |
| `observeImagesByOrderId` | orderId: Long | orderKey: OrderKey |
| `getImagesByType` | orderId: Long | orderKey: OrderKey |
| `observeImagesByType` | orderId: Long | orderKey: OrderKey |
| `getPendingImages` | orderId: Long | orderKey: OrderKey |
| `getFailedImages` | orderId: Long | orderKey: OrderKey |
| `addImage` | orderId: Long | orderKey: OrderKey |
| `addImages` | orderId: Long | orderKey: OrderKey |
| `deleteImagesByOrderId` | orderId: Long | orderKey: OrderKey |
| `deleteImagesByType` | orderId: Long | orderKey: OrderKey |
| `countPendingImages` | orderId: Long | orderKey: OrderKey |
| `countSuccessImages` | orderId: Long | orderKey: OrderKey |
| `countFailedImages` | orderId: Long | orderKey: OrderKey |
| `getUploadedImageUrls` | orderId: Long | orderKey: OrderKey |
| `getBeforeCareImageUrls` | orderId: Long | orderKey: OrderKey |
| `getCenterCareImageUrls` | orderId: Long | orderKey: OrderKey |
| `getAfterCareImageUrls` | orderId: Long | orderKey: OrderKey |

---

## 实施步骤

1. **修改UnifiedOrderRepository** - 所有public方法改为OrderKey参数
2. **修改ImageRepository** - 所有public方法改为OrderKey参数
3. **更新调用方** - ViewModel、Screen等使用处改为传递OrderKey
4. **编译验证**

---

## 注意事项

> [!NOTE]
> - DAO层保持`Long orderId`（Room无法直接用自定义类型作参数）
> - Repository内部调用DAO时使用`orderKey.orderId`
> - 未来如需支持planId区分，只需修改DAO和Entity的主键设计
