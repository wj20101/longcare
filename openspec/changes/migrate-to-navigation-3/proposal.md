## Why

项目当前以 Navigation Compose 2 管理正式应用和内部助手导航。用户已明确要求升级 Navigation 3；需要在保留业务行为的前提下，让应用显式管理可保存返回栈、页面状态和 ViewModel 作用域，为后续导航维护建立统一基础。

## What Changes

- 建议在同一变更中迁移 `:app` 与 `:assistant` 的根导航至 Navigation 3 稳定版 `1.1.7`，不在交付状态长期混用两代导航。
- 将现有可序列化路由接入 `NavKey`，以应用自有 navigator 和可保存返回栈替换 `NavController`，以 `entryProvider` / `NavDisplay` 替换图注册和 `NavHost`。
- 显式保留登录/退出清栈、身份核验替换当前页、服务完成回首页、照片与人脸结果回传、首页共享 ViewModel 以及重建恢复语义。
- 移除 Navigation 2 专属 `NavType`、扩展和依赖；Hilt Compose 改为不依赖 Navigation 2 的入口依赖。
- 保持护理页签和 `SalesNavigationState` 内部页面不变；保留当前 QLZ 自定义 UI、mock 测试与真实 BLE 待验收状态。
- 旧方案仅作为页面显示和跳转行为参考，不保留双框架适配，也不做旧版本临时导航栈兼容或升级安装恢复验收。
- 迁移相关单元/设备导航测试，更新技术栈、页面地图和架构说明。

## Capabilities

### New Capabilities

- `service-completion-navigation`：服务完成后保留首页返回路径，清除已完成服务的中间页面。

### Modified Capabilities

无。除用户在基线测试后明确确认的服务完成返回行为修正外，其余既有行为及双 APK 隔离契约保持不变；只为这一增量建立规格，不回填存量业务。

## Impact

- 正式应用导航注册、路由 payload、返回工具、结果存储与导航测试；助手 `AssistantRoot`、路由与相关测试。
- version catalog、两应用和使用 Hilt Compose 的 Feature 依赖声明；不向 Android-free Core 模块引入 `NavKey`。
- `compileSdk=37`、`minSdk=24` 已满足迁移指南要求，无需同时升级 targetSdk、AGP、Kotlin 或其他无关库。
- 风险集中在共享 ViewModel 销毁时机、相同参数页面的实例隔离、消费一次的结果、登录边界和进程恢复；必须有对应测试后才能宣布迁移完成。
- 不改网络接口、数据库、SDK AAR、Manifest 导出面、权限、签名或生产发布守卫。服务端当前不可用不阻塞 mock 导航测试，但 mock 不代表真实服务流程验收通过。
