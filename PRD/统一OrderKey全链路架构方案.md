# 统一OrderKey全链路架构方案

## 问题背景
当前实现中存在数据一致性问题：
- ViewModel层方法仍接受`orderId: Long`参数，再通过`OrderKey(orderId)`创建对象
- 这导致`planId`信息可能在流程中丢失
- 数据来源不统一，违背了单一数据源原则

## 设计目标

> [!IMPORTANT]
> **核心原则**：从Screen入口到Repository出口，全程使用`OrderKey`作为唯一订单标识符

---

## 方案：统一使用OrderKey

### 层级结构

```
Screen (OrderNavParams) → toOrderKey() → ViewModel/Repository (OrderKey)
```

### 改造范围

---

#### 1. ViewModel层 - 统一接收OrderKey

| ViewModel | 改造方法 |
|-----------|----------|
| `PhotoProcessingViewModel` | `initWithOrderId(Long)` → `initWithOrderKey(OrderKey)` |
| `ServiceCountdownViewModel` | `loadUploadedImagesFromRepository(Long)` 等 |
| `OrderDetailViewModel` | `loadSelectedProjectIds(Long)` 等 |

---

#### 2. Screen层 - 统一在入口转换

```kotlin
// 示例：PhotoScreen
val orderKey = remember(orderParams) { orderParams.toOrderKey() }
viewModel.initWithOrderKey(orderKey)
```

---

#### 3. 保留模型职责分离

| 模型 | 用途 | 状态 |
|------|------|------|
| `OrderNavParams` | **导航层**：Compose Navigation参数传递 | 保留 |
| `OrderInfoRequestModel` | **网络层**：API请求参数 | 保留 |
| `OrderKey` | **业务层**：Repository/ViewModel/缓存 | 核心统一 |

---

## 改造文件清单

### ViewModel层
- [x] `PhotoProcessingViewModel.kt` - 4处
- [x] `ServiceCountdownViewModel.kt` - 3处
- [x] `OrderDetailViewModel.kt` - 2处

### Screen层（入口转换）
- [x] Screen调用ViewModel时统一传入`orderKey`

## 验证方案
- 编译验证：`./gradlew :app:compileDebugKotlin`
- 检查所有`orderId: Long`参数是否已替换为`orderKey: OrderKey`
