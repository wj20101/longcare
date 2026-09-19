## 1. 固化基线与依赖

- [x] 1.1 在当前工作树重新核对两应用 route、返回结果 key 和 Home 共享 owner，补充旧导航的登录/退出、服务完成清栈、身份认证替换和相机返回基线测试；运行 focused tests，保留可对比的迁移前证据。
- [x] 1.2 在 version catalog 加入 Navigation 3 `1.1.7` 和 Lifecycle 导航 decorator，替换 Hilt Compose 依赖为 `hilt-lifecycle-viewmodel-compose:1.4.0`；编译两应用及受影响 Feature，核对依赖解析和现有 SDK 基线未改变。

## 2. 可保存导航基础

- [x] 2.1 将两应用真实 destination 接入可序列化 NavKey，保留 payload 字段，增加可保存 entry ID；用序列化单测覆盖全部路由、中文/特殊字符、空集合、大订单 ID 和相同参数不同实例。
- [x] 2.2 实现正式应用单栈 navigator，包含 push、pop、replaceTop、singleTop、Home 清栈和会话切换操作；单测覆盖空/根栈保护、重复动作、来源已失效和多次登录状态通知不重复建栈。
- [x] 2.3 实现 entry 级可保存结果存储与消费接口，保留现有 key 和页面 actions；测试不同 entry 隔离、消费后空值、连续回传、复杂图片 map 编解码、迟到结果丢弃及清栈后清理。
- [x] 2.4 接入 saveable-state/ViewModel decorators 与显式 Home 共享 owner；测试首页/计划/记录复用同一 TodayOrderViewModel，普通 entry 独立，配置重建保留且真正清栈/退出后销毁。

## 3. 迁移正式应用

- [x] 3.1 用 NavDisplay/entryProvider 替换正式 AppNavGraphs 与 NavHost，参数由 key 直接传递；迁移 navigation actions 和必要返回工具，运行路由覆盖测试并完成 `:app:compileDebugKotlin`。
- [x] 3.2 接入登录、退出、Unknown 和隐私 gate，恢复栈前校验会话；以 mock 会话 UI 测试验证未同意隐私不创建业务页面、登录只进入一次 Home、退出/失效不能返回受保护页、换号不保留旧状态。
- [x] 3.3 迁移 Camera/手动人脸/默认人脸/PhotoUpload 结果投递，保留调用者 entry 身份；用 Compose 交互测试覆盖成功、取消、嵌套相机返回、同类重复页和消费后再次回传。
- [x] 3.4 保留护理 Pager、SalesNavigationState 及 QLZ 会话边界；运行销售内部返回、mock 检测和报告/相机返回测试，确认未新增 BLE 自动重启或厂商内置 UI 调用。

## 4. 迁移助手及清理旧实现

- [x] 4.1 用助手自有可保存栈和 NavDisplay 替换 AssistantRoot 导航，保留匿名首页、受保护工具、pending target 和返回首页逻辑；扩展并运行 AssistantNavigationTest/AssistantSessionNavigationTest，覆盖取消、登录恢复一次、失效和重建。
- [x] 4.2 删除无引用的 Navigation 2 NavType、NavController 扩展和依赖；运行源码引用守卫、双 APK debug/release 依赖树检查与 Feature 边界测试，确认无旧导航运行时残留且两应用不相互依赖。
- [x] 4.3 更新 AGENT、技术栈、系统概览和页面地图中导航框架、Home 作用域和结果存储说明；运行文档链接检查与 `preflight_local.sh --local-fast`，保留 QLZ 真实 BLE 待验收说明。

## 5. 集成回归

- [x] 5.1 验证 Navigation 3 自身的状态恢复：参数栈、entry ID、未消费结果、消费后不重放、Home 共享状态和助手 pending target，并在隔离模拟器验证助手页面配置重建与进程重启后的正常返回；不做旧 Navigation 2 栈兼容验收。
- [x] 5.2 在 API 36 设备/模拟器验证普通与预测返回完成/取消、销售内部返回优先级、栈顶一次 pop 和页面资源释放；使用可控平台/服务替身记录导航证据，不以替身替代 BLE/NFC/相机硬件验收。
- [x] 5.3 运行两应用 `testDebugUnitTest`、`lintDebug`、`assembleDebug`，以及完整 `preflight_local.sh --full`、lint warning allowlist、双应用隔离守卫和 `openspec validate --all --strict --no-interactive`；最终检查 diff 未覆盖 QLZ 改动，报告实际测试结果及未验证项。
