# Location Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make service-period location samples self-describing and diagnosable from AMap callback through the local upload queue, without changing the server upload contract in this phase.

**Architecture:** Preserve the current AMap-only production source strategy. Extend existing location models, Room entities, mappers, reporting, diagnostics, and tracking state so each queued sample carries SDK sample time and AMap quality metadata while the server still receives the existing longitude and latitude payload.

**Tech Stack:** Kotlin, Android, AMap Location SDK, Room, Hilt, Coroutines, MockK, JUnit4, Gradle.

## Global Constraints

- Do not reintroduce Android system `LocationManager` as a production fallback.
- Do not perform client-side coordinate conversion for fresh AMap locations.
- Do not change server distance validation rules in the first client-focused phase.
- Do not block uploads based only on accuracy unless the business explicitly accepts missed location points.
- Do not redesign the NFC workflow UI beyond existing loading and retry states.
- Phase 1 does not require API changes; the client keeps sending the current order id, longitude, and latitude.
- Keep stale replayed continuous samples skipped before enqueue.

---

## File Structure

- `core/model/src/main/kotlin/com/ytone/longcare/model/LocationResult.kt`
  - In-memory location sample from AMap, including coordinates and SDK quality metadata.
- `core/model/src/main/kotlin/com/ytone/longcare/model/OrderLocationEntity.kt`
  - Domain model for queued service-period upload rows.
- `core/data/src/main/kotlin/com/ytone/longcare/data/database/entity/RoomEntities.kt`
  - Room entity for `order_locations`, including persisted metadata columns.
- `core/data/src/main/kotlin/com/ytone/longcare/data/database/entity/EntityMappers.kt`
  - Converts queued location rows between domain and Room.
- `core/data/src/main/kotlin/com/ytone/longcare/data/database/LongCareDatabase.kt`
  - Bumps Room schema version after adding columns.
- `core/data/src/main/kotlin/com/ytone/longcare/di/DatabaseModule.kt`
  - Registers a safe migration for existing version 1 databases.
- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt`
  - Copies AMap `coordType`, `locationType`, `trustedLevel`, and `time` into `LocationResult`.
- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/tracker/LocationEventTracker.kt`
  - Reports Bugly diagnostic events with the same metadata persisted locally.
- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/reporting/LocationReportingManager.kt`
  - Skips stale replayed samples, writes metadata to the queue, and emits sample/jump diagnostics.
- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationStateManager.kt`
  - Tracks active order id and tracking start time.
- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/README.md`
  - Documents the current AMap-only production behavior and metadata flow.
- `app/src/test/kotlin/com/ytone/longcare/features/location/reporting/LocationReportingManagerTest.kt`
  - Covers enqueue metadata, stale sample skip, retry behavior, and tracking state calls.
- `app/src/test/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManagerFreshLocationSourceTest.kt`
  - Guards AMap metadata propagation in fresh and continuous source construction.
- `app/src/test/kotlin/com/ytone/longcare/data/database/entity/OrderLocationEntityMapperTest.kt`
  - Covers domain <-> Room mapper metadata preservation.
- `app/src/test/kotlin/com/ytone/longcare/features/location/manager/LocationStateManagerTest.kt`
  - Covers active order and start-time lifecycle.

---

### Task 1: Propagate AMap Metadata Into LocationResult and Bugly Diagnostics

**Files:**
- Modify: `core/model/src/main/kotlin/com/ytone/longcare/model/LocationResult.kt`
- Modify: `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt`
- Modify: `feature/location/src/main/kotlin/com/ytone/longcare/features/location/tracker/LocationEventTracker.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManagerFreshLocationSourceTest.kt`

**Interfaces:**
- Consumes: AMap `AMapLocation.coordType`, `locationType`, `trustedLevel`, and `time`.
- Produces:
  - `LocationResult.coordType: String`
  - `LocationResult.locationType: Int`
  - `LocationResult.trustedLevel: Int`
  - `LocationResult.locationTime: Long`
  - `LocationEventTracker.trackLocationSample(eventType: EventType, orderId: Long, location: LocationResult, extras: Map<String, Any?> = emptyMap())`

- [ ] **Step 1: Write the failing metadata source regression test**

Update `app/src/test/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManagerFreshLocationSourceTest.kt` so it includes this test:

```kotlin
@Test
fun `amap location results carry sdk diagnostic metadata`() {
    val source = File(
        "../feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt"
    ).readText()

    assertTrue(source.contains("coordType = location.coordType.orEmpty()"))
    assertTrue(source.contains("locationType = location.locationType"))
    assertTrue(source.contains("trustedLevel = location.trustedLevel"))
    assertTrue(source.contains("locationTime = location.time"))
}
```

- [ ] **Step 2: Run the focused test and verify it fails if metadata is absent**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.features.location.manager.ContinuousAmapLocationManagerFreshLocationSourceTest'
```

Expected before implementation: FAIL with an assertion that one of the metadata assignments is missing.

- [ ] **Step 3: Extend the in-memory model**

Edit `core/model/src/main/kotlin/com/ytone/longcare/model/LocationResult.kt` to:

```kotlin
package com.ytone.longcare.model

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val accuracy: Float = 0f,
    val coordType: String = "",
    val locationType: Int = 0,
    val trustedLevel: Int = 0,
    val locationTime: Long = 0L
)
```

- [ ] **Step 4: Copy SDK metadata in continuous AMap callback**

In `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt`, update the continuous callback `LocationResult` construction to:

```kotlin
val result = LocationResult(
    latitude = location.latitude,
    longitude = location.longitude,
    provider = "amap_continuous",
    accuracy = location.accuracy,
    coordType = location.coordType.orEmpty(),
    locationType = location.locationType,
    trustedLevel = location.trustedLevel,
    locationTime = location.time
)
```

- [ ] **Step 5: Copy SDK metadata in fresh AMap callback**

In the same file, update the fresh callback `LocationResult` construction to:

```kotlin
LocationResult(
    latitude = location.latitude,
    longitude = location.longitude,
    provider = "amap_fresh",
    accuracy = location.accuracy,
    coordType = location.coordType.orEmpty(),
    locationType = location.locationType,
    trustedLevel = location.trustedLevel,
    locationTime = location.time
)
```

- [ ] **Step 6: Add Bugly sample tracking helper**

Edit `feature/location/src/main/kotlin/com/ytone/longcare/features/location/tracker/LocationEventTracker.kt`.

Add imports:

```kotlin
import com.ytone.longcare.model.LocationResult
import java.util.Locale
```

Add event types under `// LocationReportingManager 相关`:

```kotlin
REPORTING_START("reporting_start", "位置上报任务启动"),
REPORTING_STOP("reporting_stop", "位置上报任务停止"),
LOCATION_SAMPLE_RECORDED("location_sample_recorded", "采集到定位样本"),
LOCATION_JUMP_DETECTED("location_jump_detected", "检测到疑似定位跳点"),
LOCATION_STALE_SKIPPED("location_stale_skipped", "跳过陈旧定位样本"),
```

Add this public helper:

```kotlin
fun trackLocationSample(
    eventType: EventType,
    orderId: Long,
    location: LocationResult,
    extras: Map<String, Any?> = emptyMap()
) {
    trackEvent(
        eventType = eventType,
        extras = buildLocationExtras(orderId, location, extras)
    )
}
```

Add these private helpers:

```kotlin
private fun buildLocationExtras(
    orderId: Long,
    location: LocationResult,
    extras: Map<String, Any?>
): Map<String, Any?> {
    val locationExtras = linkedMapOf<String, Any?>(
        "orderId" to orderId,
        "latitude" to location.latitude.formatCoordinate(),
        "longitude" to location.longitude.formatCoordinate(),
        "provider" to location.provider,
        "accuracy" to location.accuracy,
        "coordType" to location.coordType,
        "locationType" to location.locationType,
        "trustedLevel" to location.trustedLevel,
        "locationTime" to location.locationTime
    )
    locationExtras.putAll(extras)
    return locationExtras
}

private fun Double.formatCoordinate(): String {
    return String.format(Locale.US, "%.5f", this)
}
```

- [ ] **Step 7: Verify Task 1**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.features.location.manager.ContinuousAmapLocationManagerFreshLocationSourceTest'
```

Expected: PASS.

- [ ] **Step 8: Commit Task 1**

```bash
git add core/model/src/main/kotlin/com/ytone/longcare/model/LocationResult.kt \
  feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManager.kt \
  feature/location/src/main/kotlin/com/ytone/longcare/features/location/tracker/LocationEventTracker.kt \
  app/src/test/kotlin/com/ytone/longcare/features/location/manager/ContinuousAmapLocationManagerFreshLocationSourceTest.kt
git commit -m "feat: add location diagnostic metadata"
```

---

### Task 2: Persist Location Metadata in the Local Upload Queue

**Files:**
- Modify: `core/model/src/main/kotlin/com/ytone/longcare/model/OrderLocationEntity.kt`
- Modify: `core/data/src/main/kotlin/com/ytone/longcare/data/database/entity/RoomEntities.kt`
- Modify: `core/data/src/main/kotlin/com/ytone/longcare/data/database/entity/EntityMappers.kt`
- Modify: `core/data/src/main/kotlin/com/ytone/longcare/data/database/LongCareDatabase.kt`
- Modify: `core/data/src/main/kotlin/com/ytone/longcare/di/DatabaseModule.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/data/database/entity/OrderLocationEntityMapperTest.kt`
- Update generated schema: `app/schemas/com.ytone.longcare.data.database.LongCareDatabase/2.json`

**Interfaces:**
- Consumes: `OrderLocationEntity` with SDK metadata from reporting.
- Produces persisted queue fields:
  - `coordType: String`
  - `locationType: Int`
  - `trustedLevel: Int`
  - `locationTime: Long`

- [ ] **Step 1: Write the failing mapper test**

Create `app/src/test/kotlin/com/ytone/longcare/data/database/entity/OrderLocationEntityMapperTest.kt`:

```kotlin
package com.ytone.longcare.data.database.entity

import com.ytone.longcare.model.LocationUploadStatus
import com.ytone.longcare.model.OrderLocationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderLocationEntityMapperTest {

    @Test
    fun `order location mapper preserves sdk metadata round trip`() {
        val model = OrderLocationEntity(
            id = 9L,
            orderId = 100L,
            latitude = 30.506,
            longitude = 120.226,
            accuracy = 8.5f,
            provider = "amap_continuous",
            uploadStatus = LocationUploadStatus.PENDING.value,
            timestamp = 1_717_000_000_000L,
            coordType = "GCJ02",
            locationType = 5,
            trustedLevel = 2,
            locationTime = 1_717_000_000_123L
        )

        val roundTrip = model.toDb().toModel()

        assertEquals(model, roundTrip)
    }
}
```

- [ ] **Step 2: Run the mapper test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.data.database.entity.OrderLocationEntityMapperTest'
```

Expected before implementation: FAIL at compile time because `OrderLocationEntity` does not expose the metadata constructor parameters.

- [ ] **Step 3: Extend the domain queue model**

Edit `core/model/src/main/kotlin/com/ytone/longcare/model/OrderLocationEntity.kt` so the constructor includes metadata after `provider`:

```kotlin
val provider: String = "",

val coordType: String = "",

val locationType: Int = 0,

val trustedLevel: Int = 0,

val locationTime: Long = 0L,

// ========== 上传状态 ==========
```

- [ ] **Step 4: Extend the Room entity**

Edit `core/data/src/main/kotlin/com/ytone/longcare/data/database/entity/RoomEntities.kt` so `OrderLocationEntityDb` includes:

```kotlin
@ColumnInfo(name = "coord_type", defaultValue = "")
val coordType: String = "",
@ColumnInfo(name = "location_type", defaultValue = "0")
val locationType: Int = 0,
@ColumnInfo(name = "trusted_level", defaultValue = "0")
val trustedLevel: Int = 0,
@ColumnInfo(name = "location_time", defaultValue = "0")
val locationTime: Long = 0L,
```

Place these fields after `provider` and before `uploadStatus`.

- [ ] **Step 5: Preserve metadata in mappers**

Edit `core/data/src/main/kotlin/com/ytone/longcare/data/database/entity/EntityMappers.kt`.

Update `OrderLocationEntityDb.toModel()`:

```kotlin
fun OrderLocationEntityDb.toModel(): OrderLocationEntity = OrderLocationEntity(
    id = id,
    orderId = orderId,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    provider = provider,
    coordType = coordType,
    locationType = locationType,
    trustedLevel = trustedLevel,
    locationTime = locationTime,
    uploadStatus = uploadStatus,
    timestamp = timestamp
)
```

Update `OrderLocationEntity.toDb()`:

```kotlin
fun OrderLocationEntity.toDb(): OrderLocationEntityDb = OrderLocationEntityDb(
    id = id,
    orderId = orderId,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracy,
    provider = provider,
    coordType = coordType,
    locationType = locationType,
    trustedLevel = trustedLevel,
    locationTime = locationTime,
    uploadStatus = uploadStatus,
    timestamp = timestamp
)
```

- [ ] **Step 6: Add a versioned Room migration**

Edit `core/data/src/main/kotlin/com/ytone/longcare/data/database/LongCareDatabase.kt`:

```kotlin
@Database(
    entities = [
        OrderEntityDb::class,
        OrderElderInfoEntityDb::class,
        OrderLocalStateEntityDb::class,
        OrderProjectEntityDb::class,
        OrderImageEntityDb::class,
        OrderLocationEntityDb::class
    ],
    version = 2,
    exportSchema = true
)
abstract class LongCareDatabase : RoomDatabase() {
```

Edit `core/data/src/main/kotlin/com/ytone/longcare/di/DatabaseModule.kt`.

Add imports:

```kotlin
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
```

Add the migration inside `DatabaseModule`:

```kotlin
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE order_locations ADD COLUMN coord_type TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE order_locations ADD COLUMN location_type INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE order_locations ADD COLUMN trusted_level INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE order_locations ADD COLUMN location_time INTEGER NOT NULL DEFAULT 0")
    }
}
```

Update `buildDatabase()` to register it before fallback calls:

```kotlin
return Room.databaseBuilder(
    context = context,
    klass = LongCareDatabase::class.java,
    name = LongCareDatabase.DATABASE_NAME
).addMigrations(MIGRATION_1_2)
    .fallbackToDestructiveMigration(dropAllTables = true)
    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
    .build()
```

- [ ] **Step 7: Run mapper test and Room compile**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.data.database.entity.OrderLocationEntityMapperTest'
./gradlew :app:assembleDebug
```

Expected: both commands PASS. `assembleDebug` may update Room schema JSON under `app/schemas/com.ytone.longcare.data.database.LongCareDatabase/2.json`.

- [ ] **Step 8: Commit Task 2**

```bash
git add core/model/src/main/kotlin/com/ytone/longcare/model/OrderLocationEntity.kt \
  core/data/src/main/kotlin/com/ytone/longcare/data/database/entity/RoomEntities.kt \
  core/data/src/main/kotlin/com/ytone/longcare/data/database/entity/EntityMappers.kt \
  core/data/src/main/kotlin/com/ytone/longcare/data/database/LongCareDatabase.kt \
  core/data/src/main/kotlin/com/ytone/longcare/di/DatabaseModule.kt \
  app/src/test/kotlin/com/ytone/longcare/data/database/entity/OrderLocationEntityMapperTest.kt \
  app/schemas/com.ytone.longcare.data.database.LongCareDatabase/2.json
git commit -m "feat: persist location upload metadata"
```

---

### Task 3: Write Metadata During Reporting and Maintain Tracking State

**Files:**
- Modify: `feature/location/src/main/kotlin/com/ytone/longcare/features/location/reporting/LocationReportingManager.kt`
- Modify: `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationStateManager.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/location/reporting/LocationReportingManagerTest.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/features/location/manager/LocationStateManagerTest.kt`

**Interfaces:**
- Consumes: `LocationResult` metadata from Task 1 and queue fields from Task 2.
- Produces:
  - `LocationStateManager.startTracking(orderKey: OrderKey)`
  - `LocationStateManager.stopTracking()`
  - queued `OrderLocationEntity` rows with persisted `coordType`, `locationType`, `trustedLevel`, and `locationTime`.

- [ ] **Step 1: Write failing LocationStateManager tests**

Create `app/src/test/kotlin/com/ytone/longcare/features/location/manager/LocationStateManagerTest.kt`:

```kotlin
package com.ytone.longcare.features.location.manager

import com.ytone.longcare.model.OrderKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStateManagerTest {

    @Test
    fun `startTracking records active order and start time`() {
        val manager = LocationStateManager()
        val orderKey = OrderKey(orderId = 100L, planId = 0)

        manager.startTracking(orderKey)

        val state = manager.state.value
        assertTrue(state.isTracking)
        assertEquals(100L, state.currentOrderId)
        assertNotNull(state.startTime)
        assertNotNull(manager.getRunningDuration())
    }

    @Test
    fun `stopTracking clears active order and start time`() {
        val manager = LocationStateManager()
        manager.startTracking(OrderKey(orderId = 100L, planId = 0))

        manager.stopTracking()

        val state = manager.state.value
        assertFalse(state.isTracking)
        assertEquals(null, state.currentOrderId)
        assertEquals(null, state.startTime)
        assertEquals(null, manager.getRunningDuration())
    }
}
```

- [ ] **Step 2: Write failing reporting metadata assertion**

Update the first test in `app/src/test/kotlin/com/ytone/longcare/features/location/reporting/LocationReportingManagerTest.kt` so `sample` includes metadata:

```kotlin
val sample = LocationResult(
    latitude = 31.2,
    longitude = 121.5,
    provider = "amap_continuous",
    accuracy = 5f,
    coordType = "GCJ02",
    locationType = 5,
    trustedLevel = 2,
    locationTime = 1_717_000_000_123L
)
```

Update the insert verification to:

```kotlin
coVerify(exactly = 1) {
    queueRepository.insert(match {
        it.orderId == 100L &&
            it.latitude == 31.2 &&
            it.longitude == 121.5 &&
            it.coordType == "GCJ02" &&
            it.locationType == 5 &&
            it.trustedLevel == 2 &&
            it.locationTime == 1_717_000_000_123L
    })
}
```

Add state lifecycle verifications to the same test:

```kotlin
verify { locationStateManager.startTracking(orderKey) }
verify { locationStateManager.stopTracking() }
```

- [ ] **Step 3: Run the focused tests and verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.features.location.manager.LocationStateManagerTest' \
  --tests 'com.ytone.longcare.features.location.reporting.LocationReportingManagerTest'
```

Expected before implementation: FAIL because `startTracking`, `stopTracking`, or queued metadata writes are missing.

- [ ] **Step 4: Add tracking lifecycle methods**

Edit `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationStateManager.kt`.

Add import:

```kotlin
import com.ytone.longcare.model.OrderKey
```

Replace the existing simple `updateTrackingState` usage path with explicit lifecycle methods:

```kotlin
fun startTracking(orderKey: OrderKey) {
    _state.update { state ->
        state.copy(
            isTracking = true,
            currentOrderId = orderKey.orderId,
            startTime = System.currentTimeMillis(),
            error = null
        )
    }
}

fun stopTracking() {
    _state.update { state ->
        state.copy(
            isTracking = false,
            currentOrderId = null,
            startTime = null
        )
    }
}

fun updateTrackingState(isTracking: Boolean) {
    if (!isTracking) {
        stopTracking()
    } else {
        _state.update { it.copy(isTracking = true) }
    }
}
```

Keep `updateTrackingState` for compatibility with existing callers during this task.

- [ ] **Step 5: Use lifecycle methods in reporting**

Edit `feature/location/src/main/kotlin/com/ytone/longcare/features/location/reporting/LocationReportingManager.kt`.

In `startReporting(orderKey)`, replace:

```kotlin
locationStateManager.updateTrackingState(true)
```

with:

```kotlin
locationStateManager.startTracking(orderKey)
```

In `stopReporting()`, replace:

```kotlin
locationStateManager.updateTrackingState(false)
```

with:

```kotlin
locationStateManager.stopTracking()
```

- [ ] **Step 6: Write metadata to queued rows**

In `LocationReportingManager.enqueueLocation()`, update the `OrderLocationEntity` construction to:

```kotlin
OrderLocationEntity(
    orderId = orderId,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracy = location.accuracy,
    provider = location.provider,
    coordType = location.coordType,
    locationType = location.locationType,
    trustedLevel = location.trustedLevel,
    locationTime = location.locationTime,
    uploadStatus = LocationUploadStatus.PENDING.value,
    timestamp = System.currentTimeMillis()
)
```

- [ ] **Step 7: Keep stale replay protection and diagnostics**

Ensure `LocationReportingManager` still contains:

```kotlin
private const val STALE_LOCATION_MAX_AGE_MS = 2 * 60 * 1000L
```

and this check before enqueue:

```kotlin
val now = System.currentTimeMillis()
if (shouldSkipStaleLocation(orderKey.orderId, location, now)) {
    return@collect
}
trackLocationDiagnostics(orderKey.orderId, location, now)
enqueueLocation(orderKey.orderId, location)
flushUploadQueue()
```

- [ ] **Step 8: Run focused tests**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.features.location.manager.LocationStateManagerTest' \
  --tests 'com.ytone.longcare.features.location.reporting.LocationReportingManagerTest'
```

Expected: PASS.

- [ ] **Step 9: Commit Task 3**

```bash
git add feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationStateManager.kt \
  feature/location/src/main/kotlin/com/ytone/longcare/features/location/reporting/LocationReportingManager.kt \
  app/src/test/kotlin/com/ytone/longcare/features/location/manager/LocationStateManagerTest.kt \
  app/src/test/kotlin/com/ytone/longcare/features/location/reporting/LocationReportingManagerTest.kt
git commit -m "feat: record location reporting state and metadata"
```

---

### Task 4: Refresh Documentation and Run Final Verification

**Files:**
- Modify: `feature/location/src/main/kotlin/com/ytone/longcare/features/location/README.md`

**Interfaces:**
- Consumes: completed Tasks 1-3.
- Produces: accurate module documentation and final verification evidence.

- [ ] **Step 1: Update the location README**

Edit `feature/location/src/main/kotlin/com/ytone/longcare/features/location/README.md` so the core component list is:

```markdown
## 核心组件

1. `LocationFacade`
   - 统一定位入口：实时流、fresh 单次定位、缓存定位、保活 acquire/release。
2. `LocationKeepAliveManager`
   - 基于 owner 引用计数管理前台保活服务和定位缓存采集。
3. `LocationTrackingService`
   - 纯保活服务，只处理前台通知和高德后台定位绑定。
4. `LocationReportingManager`
   - 上报任务管理器：跳过陈旧样本、入队、本地重试、调用 `addPosition`。
5. `ContinuousAmapLocationManager`
   - 高德持续定位引擎和 isolated fresh 定位实现。
```

Replace the `getCurrentLocation()` strategy section with:

```markdown
`getCurrentLocation()` 策略：
- 先用有效缓存
- 再尝试高德连续定位流
- 不回退系统定位，避免坐标系混用

`getFreshLocation()` 策略：
- 不读取业务缓存
- 使用独立高德单次定位客户端
- 使用 SignIn purpose、once/latest、禁用 SDK 定位缓存
```

Replace the offline compensation section with:

```markdown
## 离线补偿

- 上报前先写入 `order_locations`（`PENDING`）。
- 本地记录经纬度、精度、provider、SDK location time、coord type、location type、trusted level。
- 上传失败标记 `FAILED`，后续持续重试。
- 上传成功标记 `SUCCESS`。
- 定期清理历史成功记录。
```

- [ ] **Step 2: Search for stale documentation claims**

Run:

```bash
rg -n "SystemLocationProvider|系统定位回退|回退系统定位" feature/location docs/superpowers/specs/2026-06-24-location-optimization-design.md
```

Expected: no stale production-behavior claim remains in `feature/location/src/main/kotlin/com/ytone/longcare/features/location/README.md`. The design spec may mention the system fallback only as a non-goal.

- [ ] **Step 3: Run final focused unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.features.location.manager.ContinuousAmapLocationManagerFreshLocationSourceTest' \
  --tests 'com.ytone.longcare.features.location.manager.LocationStateManagerTest' \
  --tests 'com.ytone.longcare.features.location.reporting.LocationReportingManagerTest' \
  --tests 'com.ytone.longcare.data.database.entity.OrderLocationEntityMapperTest' \
  --tests 'com.ytone.longcare.features.nfc.vm.NfcActivityAndLocationDelegateTest'
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run debug build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Existing native-strip or Gradle deprecation warnings may appear; they are not failures.

- [ ] **Step 5: Check whitespace and git state**

Run:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` prints no output. `git status --short` lists only the intended implementation files before commit.

- [ ] **Step 6: Commit Task 4**

```bash
git add feature/location/src/main/kotlin/com/ytone/longcare/features/location/README.md
git commit -m "docs: update location module behavior"
```

---

## Plan Self-Review

- Spec coverage: Option A is covered by Tasks 1-4. Option B stale replay skip is retained in Task 3. Option B upload expiry and Option C server governance remain explicit follow-up work and are not implemented in this phase.
- Placeholder scan: the plan contains no placeholder markers or vague implementation-only steps.
- Type consistency: metadata fields use `coordType`, `locationType`, `trustedLevel`, and `locationTime` consistently across `LocationResult`, `OrderLocationEntity`, Room entity, mapper, queue insert, diagnostics, and tests.
