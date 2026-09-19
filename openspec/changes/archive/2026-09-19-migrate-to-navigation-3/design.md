## Context

动机与范围见 [proposal.md](proposal.md)。以下为迁移前基线：version catalog 使用 Navigation Compose `2.10.0`，当时技术栈文档中的 `2.9.8` 已过时；Lifecycle 为 `2.11.0`、AndroidX Hilt 为 `1.4.0`，compileSdk/minSdk 为 37/24。迁移后的当前事实以源码和已同步的架构文档为准。

- `AppNavigation` 使用一个 NavHost，`HomeGraphRoute` 是一层嵌套图；`TodayOrderViewModel` 在首页、服务计划和服务记录页之间共享图级作用域。
- 护理首页的三个页签由 Pager 管理；销售页面由 `SalesNavigationState` 管理，它们不是多条 Navigation 返回栈。
- `AppNavigationActions` 包含普通 push、替换当前页、返回首页清栈、服务完成 popUpTo 等不同语义。
- 相机、人脸和照片上传通过当前/上一 `NavBackStackEntry.savedStateHandle` 传值与消费。路由参数从 `toRoute` 获取后传给页面，而非依赖 ViewModel 自动解码。
- 助手有自己的 NavHost，首页可匿名使用，需登录的工具先记录 pending target；其会话和结果独立于正式应用。
- 当前导航注册未发现 `navDeepLink` / `handleDeepLink`、多层嵌套图或自定义 destination；MainActivity 的 NFC Intent 交给 NfcManager，不是 Navigation URI 路由。自定义 `NavType` 属于参数编码，不属于自定义 destination。

2026-09-12 使用用户指定的 Android CLI 查询 `android studio version-lookup`：Navigation 3 runtime/ui 稳定版 `1.1.7`，预览版 `1.2.0-rc01`；lifecycle-viewmodel-navigation3 稳定版 `2.11.0`；hilt-lifecycle-viewmodel-compose 稳定版 `1.4.0`。

已核对官方迁移指南与配套 recipe 的说明和源码：

- [迁移指南](https://developer.android.com/guide/navigation/navigation-3/migration-guide)：NavKey、显式返回栈、entryProvider、NavDisplay 和移除旧依赖。
- [保存状态](https://developer.android.com/guide/navigation/navigation-3/save-state)：可保存返回栈与 entry 级状态。
- [共享 ViewModel](https://developer.android.com/guide/navigation/navigation-3/recipes/sharedviewmodel)：自定义 decorator 提供独立及父级 owner。
- [Hilt 参数](https://developer.android.com/guide/navigation/navigation-3/recipes/passingarguments)：entry decorator 与必要时 assisted factory。
- [条件导航](https://developer.android.com/guide/navigation/navigation-3/recipes/conditional)：显式鉴权与目标恢复。
- [返回结果](https://developer.android.com/guide/navigation/navigation-3/recipes/results-state)：结果状态与导航解耦；示例明确不保证重建/进程恢复，不能直接替代本项目 SavedStateHandle 契约。

指南默认“每个顶层页签一条栈、总从 Home 退出”，与本项目 Pager/销售内部状态以及匿名 Login 根页不完全相符。本设计选择保留既有单栈和条件入口，不引入多返回栈。此差异、共享 ViewModel 和结果恢复方案必须在实施前由用户确认。

## Goals / Non-Goals

实施基线补充：真实 Navigation 2 controller 的四项基线测试已通过。测试证实旧 `popUpTo(HomeGraphRoute)` 会在进入图外完成页后自动移除空 Home 图，旧完成页并无首页返回路径。用户已明确确认按新方案保留首页、返回键回首页；本项属于有意行为修正，单独建立增量规格。

**Goals:**

- 迁移两 APK 的导航实现，保留 route 参数、返回目标、页签和内部销售页面行为。
- 每个 entry 的状态和 ViewModel 有明确 owner；结果能定向传回发起 entry，消费后不会重放。
- 隐私前不构建业务导航，会话未解析不恢复受保护页面，退出/换号不泄漏旧页面及结果。
- 以可控 mock 驱动导航回归；真实硬件行为另保留实测边界。

**Non-Goals:**

- 不把护理页签或销售内部页面拆成新的 NavKey，不新增多栈、双栏、路由弹窗或 URI deep link。
- 不重写业务 ViewModel、数据层或 QLZ 会话；不搬迁无关 Feature。
- 不升级 SDK/工具链基线，不使用 Navigation 3 预览版，不改变发布守卫。

## Decisions

### 1. 两应用同批迁移，依赖统一使用稳定版本

版本目录增加 `navigation3-runtime/ui:1.1.7`、复用 Lifecycle 版本的 `lifecycle-viewmodel-navigation3`，以 `hilt-lifecycle-viewmodel-compose:1.4.0` 替换旧 `hilt-navigation-compose` 声明。核对所有消费该别名的 Feature，确保最终依赖树没有因 Hilt 间接引入 Navigation 2。

不选择长期双框架共存：助手和正式应用已共用版本目录，保留两套导航会增加维护和测试负担。允许按小切片编译，但最终交付两 APK 都迁移完毕；两应用不相互依赖，导航状态不放入全局单例。

### 2. 可序列化 entry 身份与业务路由分离

保留现有业务路由名称和 payload 字段，使真实 destination 实现 `NavKey`；`HomeGraphRoute` 作为旧框架图标识移除，其共享语义转成显式 owner。`OrderNavParams`、`EndOderInfo`、`ServiceCompleteData`、`WatermarkData` 保持原含义，不把 `NavKey` 放到 Android-free model/domain。

使用 `rememberNavBackStack` 保存栈。需要重复实例的路由以可保存的 entry ID 区分 contentKey（例如序列化 wrapper 包含 entry ID 与 route），避免两次打开相同 Camera/WebView/订单参数时共享状态。singleTop 只在原有业务动作需要时合并，不全局按 route 相等去重。

原 `NavType` JSON 编解码转成 NavKey/payload 序列化；中文、空值、集合、特殊字符和大订单 ID 以 round-trip 测试验证。不得序列化 Bitmap、Activity、BLE/厂商对象；照片只保留既有受管文件引用和必要元数据。

### 3. Navigator 显式实现原有操作

| 原行为 | 新栈操作 |
|---|---|
| 普通进入页面 | 为目标创建 entry 并 push |
| 普通返回 | 仅移除栈顶；根页交给宿主处理退出 |
| 登录成功 | 幂等替换为已登录 Home 栈，删除 Login 历史 |
| 退出/会话失效 | 清空受保护 entry、结果和共享 owner，显示 Login |
| 身份认证替换前页 | replaceTop，保留更早返回路径 |
| 服务完成 | 截断到 Home，再单次 push 完成页 |
| 回首页清栈 | 重置到 Home，不留下完成/护理执行历史 |

页面仍只接收现有 actions/callback，不向 Feature 传 navigator。原 `safeNavigate` 的防重复/生命周期条件在 entry 侧以当前 owner 和稳定来源 ID 实现；迟到的来源 entry 回调不得操作新的栈。普通系统/预测返回由 NavDisplay 处理，保留销售内部和业务确认 BackHandler 的优先级，避免两层同时 pop。

### 4. Entry 与 Home 共享 ViewModel 作用域

配置 saveable-state 与 ViewModel entry decorators，普通页面默认使用 entry owner。首页、服务计划、服务记录通过显式 Home owner 共享 `TodayOrderViewModel`，参照官方 sharedviewmodel recipe 提供父级 owner，但保持其他页面的独立 owner。

Home owner 在其逻辑栈存续时跨子页切换/配置重建保留，在 Home 栈被真正重置、退出或换号时清理。不得改成 Activity 全局作用域来规避迁移；这会让旧账号状态残留。Hilt 仍提供依赖；页面已经显式接受参数的地方继续直接传参，不无差别改造成 assisted ViewModel。

### 5. 使用 entry 级可保存结果邮箱

以 entry ID 绑定 app-owned 结果存储，提供现有 `StateFlow`/清除动作接口。相机等生产者进入时捕获调用者 entry ID；成功时先写结果、再返回，取消时沿用原有空值或 false 语义。调用者已移除或身份 epoch 不匹配时丢弃迟到结果。

保留 `CAPTURED_IMAGE_URI_KEY`、`FACE_IMAGE_PATH_KEY`、`DEFAULT_FACE_VERIFICATION_RESULT_KEY`、`PHOTO_UPLOAD_RESULT_KEY`、`EXISTING_IMAGES_KEY` 的值类型和消费时机。复杂图片 map 使用显式可保存 DTO/codec，而非任意 Any 或仅内存 map。既有图片输入与上传结果分别保存，PhotoUpload → Camera → PhotoUpload → Countdown 的层级不可串位。

结果未消费时随 entry 重建恢复，消费后清空并使订阅者看到空值；下一次拍照仍可正常投递。同类型重复页面也不互相消费。entry 被 pop、清栈或换号时清理邮箱。官方仅内存 ResultEventBus 不满足这些条件，因此不直接套用。

### 6. 恢复与应用入口保持鉴权约束

MainApp 保留隐私/Unknown gate，恢复 Navigation 3 栈时先校验当前会话身份。助手保留匿名首页、登录 pending target 的 SavedStateHandle 和消费一次语义，不共享正式应用栈。

支持 Navigation 3 的配置重建与状态恢复，保存 entry ID、payload 和未消费结果。用户最终明确范围：只实现 Navigation 3，旧流程仅用于参考页面展示、跳转和返回逻辑；不实现、不验收 Navigation 2 私有快照、临时返回栈或双框架兼容。账号、数据库和服务执行数据不在本次改动范围。

QLZ 连接不持久化。保留其现有重建后中断/重新开始策略；销售页到根 Camera/WebView 再返回，应恢复内部页面和已保存草稿，不自动重启 BLE。

## Risks / Trade-offs

- [官方样例使用的 API 可能超前于选定稳定版] → 编译前核对实际 1.1.7 API；不因示例不可编译自动升级预览依赖。
- [相同参数页面 owner 合并] → 使用独立 entry ID，测试两次 Camera/WebView 和两个订单实例。
- [共享 ViewModel 过早销毁或跨账号保留] → 测试共享实例、重建、真正清栈和 onCleared；不扩大到 Activity 作用域。
- [结果在恢复、订阅或清除后重复/丢失] → 实测未消费恢复、消费后恢复、连续两次回传和已移除调用者的迟到回传。
- [NavDisplay 预测返回与内部 BackHandler 双重处理] → 模拟返回完成/取消，验证一次 pop；继续执行销售内部返回回归。
- [服务端不可用掩盖集成问题] → 使用测试源集 fake 进行导航和状态验证，保留真实服务/硬件未验证说明，不接入生产 mock 开关。
- [迁移碰到未提交的 QLZ 修改] → 基于当前工作树增量修改，只改必要导航衔接；不还原、提交或归档另一变更。

## Migration Plan

1. 用户确认本方案（两 APK、单栈、共享 owner 与可保存结果）后开始 apply。
2. 先补原导航行为测试基线，再加入依赖、NavKey 和可测试 navigator/结果存储。
3. 迁移正式 entry 注册、Home 作用域和返回结果，再迁移助手入口；每切片进行编译及 focused tests。
4. 删除已无引用的 Navigation 2 适配，核对 debug/release 依赖树，更新长期文档中的框架版本和状态归属。
5. 运行两 APK 单测/lint/assemble、完整 preflight、OpenSpec 严格校验，以及隔离模拟器上的导航、重建和相关 QLZ mock 测试。至少在 API 36 设备/模拟器验证返回手势与取消。
6. 验收通过后再请求归档。若导航行为回归，在本变更范围内逐项回退框架/导航改动，保留业务数据、QLZ 改动和发布守卫；不使用清空应用数据作为修复。
