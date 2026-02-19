# Module Migration Map (P0)

## Scope
- Date: 2026-02-18
- Goal: establish single-source ownership for overlapping feature/data implementations.
- Current phase: P0 execution (T1/T3/T5) with prerequisite tracking for T2/T4.

## Overlap Matrix
| Domain | app location | target module | current status | action |
|---|---|---|---|---|
| login UI | `app/.../features/login/ui/*` | `feature/login` | split (VM in feature, UI in app) | migrate after shared theme/common-ui extraction |
| home UI | `app/.../features/home/ui/*` | `feature/home` | split (VM in feature, UI in app) | migrate after shared dashboard/profile/nursing boundary cleanup |
| identification UI | `app/.../features/identification/ui/*` | `feature/identification` | split (VM/usecase in feature, UI in app) | migrate after shared VM and BuildConfig exposure strategy |
| photoupload UI | `app/.../features/photoupload/ui/*` | `feature/photoupload` | split (VM/tools in feature, UI in app) | migrate after camera UI dependency extraction |
| servicecountdown UI/service | `app/.../features/servicecountdown/*` | `feature/servicecountdown` | split (state/vm in feature, UI+service in app) | migrate after service/MainActivity coupling decoupling |
| local database infra | `app/.../data/database/*` | `core/data` | app-owned | **migrate in P0** |

## P0 Ownership Decision
- `core:data` becomes the owner of:
  - `com.ytone.longcare.data.database.LongCareDatabase`
  - `com.ytone.longcare.data.database.dao.*`
- `app` keeps temporary owner role for:
  - feature UI compositions with direct app-only dependencies (`theme`, `shared.vm`, `MainActivity` coupling)
  - 部分网络封装与仓储实现（待后续继续下沉）

## 2026-02-18 Progress Update
- `theme/*` moved from `app` to `core:ui` (package remains `com.ytone.longcare.theme`).
- `shared/vm/*` moved from `app` to `core:ui` (package remains `com.ytone.longcare.shared.vm`).
- `OrderDetailViewModel` decoupled from `feature/location` implementation:
  - replaced `LocationTrackingManager` dependency with `LocationFacade`.
- `SharedOrderDetailViewModel` removed dependency on `app`-side `UnifiedPermissionHelper`:
  - location permission/service checks now internalized in ViewModel.
- `app` now depends explicitly on `:core:ui`.
- `servicecountdown` 前台服务启动解耦完成：
  - 新增 `ServiceCountdownAppLauncher` 抽象（位于 `feature/servicecountdown`）
  - `CountdownForegroundService` 改为依赖抽象，不再直接引用 `MainActivity`
  - app 侧通过 Hilt 绑定 `ServiceCountdownAppLauncherImpl`（已落位到 `app/shell/servicecountdown`）提供具体跳转实现
- `BuildConfig.USE_MOCK_DATA` 跨模块访问解耦完成：
  - 新增 `RuntimeConfigProvider` 抽象（`core:common`）
  - app 侧通过 Hilt 提供实现，feature ViewModel 通过接口获取 mock 标记
- `app/api` 迁移完成：
  - `LongCareApiService`、`TencentFaceApiService` 已迁至 `core:data`
  - app 仅保留 Retrofit/DI 装配，不再定义业务 API 协议
- 网络安全封装已部分下沉到 `core:data`：
  - `AesKeyTag`、`RequestInterceptor`、`ResponseDecryptInterceptor`、`ResponseProcessor`、`SystemConfigResponseProcessor` 已迁出 app
  - `RequestInterceptor` 通过 `RuntimeConfigProvider`、`RequestDeviceInfoProvider`、`RequestCryptoProvider` 解耦 app 实现
  - `SystemConfigResponseProcessor` 通过 `ThirdKeyDecryptor` 抽象隔离 app 加密实现
  - app 侧仅保留 bridge impl 与 Hilt 绑定，不再承载网络封装核心逻辑

## Prerequisites Blocking Full T2
1. Extract app-only UI dependencies to reusable modules:
   - `com.ytone.longcare.theme.*`（已完成）
   - `com.ytone.longcare.shared.vm.*`（已完成）
   - app-only util set under `app/common/utils/*`（已部分完成，剩余按依赖分批下沉）
2. Split service/runtime coupling from feature modules:
   - `CountdownForegroundService` 的 `MainActivity` 直接依赖已移除；剩余是 app 资源与 countdown manager 归属清理。
3. BuildConfig exposure approach for feature modules requiring env flags（已完成，`RuntimeConfigProvider`）。
4. 资源边界待清理：
   - 目标 UI 文件大量直接依赖 `com.ytone.longcare.R`（app 资源），需先完成资源归位或参数化才能批量下沉到 `feature/*`。

## Next Execution Order
1. P0.1 (done in this batch): move local DB infra to `core:data`.
2. P0.2 (done in this batch): extract API interfaces (`LongCareApiService`, `TencentFaceApiService`) into `core:data`.
3. P0.3 (done): move safe network wrappers from app to `core:common` (or `core:data`).
   - done: `AesKeyTag` / `RequestInterceptor` / `ResponseDecryptInterceptor` / `ResponseProcessor` / `SystemConfigResponseProcessor`
4. P1: migrate split feature UIs module by module once dependencies are decoupled.
